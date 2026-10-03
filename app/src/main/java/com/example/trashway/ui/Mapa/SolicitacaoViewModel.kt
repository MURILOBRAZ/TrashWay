package com.example.trashway.ui.Mapa

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.trashway.conta.aguardar
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ListenerRegistration

enum class ResultadoConfirmacao { CONFIRMADA, APROVADA, JA_CONFIRMOU, ENCERRADA }

// Solicitações de lixeiras novas e a votação delas.
// As regras do Firestore garantem um voto por pessoa e que a lixeira
// só é criada com VOTOS_PARA_APROVAR votos (ou por um administrador).
class SolicitacaoViewModel(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val _solicitacoes = MutableLiveData<List<Solicitacao>>(emptyList())
    val solicitacoes: LiveData<List<Solicitacao>> get() = _solicitacoes

    private val colecao = db.collection("solicitacoes")

    // Acompanha as pendentes em tempo real: o contador de votos muda na tela de todos
    private val registro: ListenerRegistration = colecao
        .whereEqualTo("status", STATUS_PENDENTE)
        .addSnapshotListener { resultado, erro ->
            if (erro != null) {
                Log.w("SolicitacaoViewModel", "Falha ao ler as solicitações", erro)
                return@addSnapshotListener
            }
            _solicitacoes.value = resultado?.documents.orEmpty().mapNotNull { doc ->
                val ponto = doc.getGeoPoint("coordenada") ?: return@mapNotNull null
                Solicitacao(
                    id = doc.id,
                    latLng = LatLng(ponto.latitude, ponto.longitude),
                    referencia = doc.getString("referencia").orEmpty(),
                    votos = doc.getLong("votos")?.toInt() ?: 0
                )
            }
        }

    // Cria a solicitação já com o voto de quem pediu
    suspend fun solicitar(posicao: LatLng, referencia: String, uid: String) {
        val solicitacao = colecao.document()
        db.batch()
            .set(
                solicitacao, mapOf(
                    "coordenada" to GeoPoint(posicao.latitude, posicao.longitude),
                    "referencia" to referencia,
                    "criadoPor" to uid,
                    "criadoEm" to FieldValue.serverTimestamp(),
                    "votos" to 1,
                    "status" to STATUS_PENDENTE
                )
            )
            .set(solicitacao.collection("votos").document(uid), mapOf("criadoEm" to FieldValue.serverTimestamp()))
            .commit()
            .aguardar()
    }

    suspend fun jaConfirmou(solicitacaoId: String, uid: String): Boolean =
        colecao.document(solicitacaoId).collection("votos").document(uid).get().aguardar().exists()

    // Soma o voto; o voto que completa o total já cria a lixeira, na mesma transação
    suspend fun confirmar(solicitacaoId: String, uid: String, nomeNovaLixeira: String): ResultadoConfirmacao {
        val solicitacaoRef = colecao.document(solicitacaoId)
        val votoRef = solicitacaoRef.collection("votos").document(uid)
        val lixeiraRef = db.collection("lixeiras").document(solicitacaoId)

        return db.runTransaction { transacao ->
            val solicitacao = transacao.get(solicitacaoRef)
            if (transacao.get(votoRef).exists()) return@runTransaction ResultadoConfirmacao.JA_CONFIRMOU
            if (solicitacao.getString("status") != STATUS_PENDENTE) {
                return@runTransaction ResultadoConfirmacao.ENCERRADA
            }

            val votos = (solicitacao.getLong("votos") ?: 0) + 1
            val aprovada = votos >= VOTOS_PARA_APROVAR
            transacao.update(
                solicitacaoRef, mapOf(
                    "votos" to votos,
                    "status" to if (aprovada) STATUS_APROVADA else STATUS_PENDENTE
                )
            )
            transacao.set(votoRef, mapOf("criadoEm" to FieldValue.serverTimestamp()))
            if (aprovada) {
                transacao.set(lixeiraRef, novaLixeira(
                    nomeNovaLixeira,
                    solicitacao.getString("referencia").orEmpty(),
                    solicitacao.getGeoPoint("coordenada")!!,
                    ORIGEM_COMUNIDADE
                ))
                ResultadoConfirmacao.APROVADA
            } else {
                ResultadoConfirmacao.CONFIRMADA
            }
        }.aguardar()
    }

    // Administrador: aprova sem esperar os votos
    suspend fun aprovar(solicitacao: Solicitacao, nomeNovaLixeira: String) {
        val coordenada = GeoPoint(solicitacao.latLng.latitude, solicitacao.latLng.longitude)
        db.batch()
            .update(colecao.document(solicitacao.id), "status", STATUS_APROVADA)
            .set(
                db.collection("lixeiras").document(solicitacao.id),
                novaLixeira(nomeNovaLixeira, solicitacao.referencia, coordenada, ORIGEM_ADMIN)
            )
            .commit()
            .aguardar()
    }

    // Administrador: descarta um pedido (ex.: spam ou lugar errado)
    suspend fun recusar(solicitacao: Solicitacao) {
        colecao.document(solicitacao.id).update("status", STATUS_RECUSADA).aguardar()
    }

    // Administrador: cadastra direto, sem votação
    suspend fun cadastrarDireto(posicao: LatLng, referencia: String, nome: String) {
        db.collection("lixeiras").document()
            .set(novaLixeira(nome, referencia, GeoPoint(posicao.latitude, posicao.longitude), ORIGEM_ADMIN))
            .aguardar()
    }

    private fun novaLixeira(nome: String, local: String, coordenada: GeoPoint, origem: String) = mapOf(
        "nome" to nome,
        "local" to local,
        "coordenada" to coordenada,
        "criadoEm" to FieldValue.serverTimestamp(),
        "origem" to origem
    )

    override fun onCleared() {
        registro.remove()
    }

    private companion object {
        const val STATUS_PENDENTE = "pendente"
        const val STATUS_APROVADA = "aprovada"
        const val STATUS_RECUSADA = "recusada"
        const val ORIGEM_COMUNIDADE = "comunidade"
        const val ORIGEM_ADMIN = "admin"
    }
}
