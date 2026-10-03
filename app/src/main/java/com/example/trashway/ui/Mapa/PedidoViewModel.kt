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

enum class ResultadoApoio { APOIADO, JA_APOIOU, ENCERRADO }

// Pedidos de lixeira para lugares que não têm ("voz do povo").
// As regras do Firestore garantem um apoio por pessoa.
class PedidoViewModel(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    private val _pedidos = MutableLiveData<List<Pedido>>(emptyList())
    val pedidos: LiveData<List<Pedido>> get() = _pedidos

    private val colecao = db.collection("pedidos")

    private val registro: ListenerRegistration = colecao
        .whereEqualTo("status", STATUS_ABERTO)
        .addSnapshotListener { resultado, erro ->
            if (erro != null) {
                Log.w("PedidoViewModel", "Falha ao ler os pedidos", erro)
                return@addSnapshotListener
            }
            _pedidos.value = resultado?.documents.orEmpty().mapNotNull { doc ->
                val ponto = doc.getGeoPoint("coordenada") ?: return@mapNotNull null
                Pedido(
                    id = doc.id,
                    latLng = LatLng(ponto.latitude, ponto.longitude),
                    referencia = doc.getString("referencia").orEmpty(),
                    apoios = doc.getLong("apoios")?.toInt() ?: 0
                )
            }
        }

    // Cria o pedido já com o apoio de quem pediu
    suspend fun pedir(posicao: LatLng, referencia: String, uid: String) {
        val pedido = colecao.document()
        db.batch()
            .set(
                pedido, mapOf(
                    "coordenada" to GeoPoint(posicao.latitude, posicao.longitude),
                    "referencia" to referencia,
                    "criadoPor" to uid,
                    "criadoEm" to FieldValue.serverTimestamp(),
                    "apoios" to 1,
                    "status" to STATUS_ABERTO
                )
            )
            .set(pedido.collection("apoios").document(uid), mapOf("criadoEm" to FieldValue.serverTimestamp()))
            .commit()
            .aguardar()
    }

    suspend fun apoiar(pedidoId: String, uid: String): ResultadoApoio {
        val pedidoRef = colecao.document(pedidoId)
        val apoioRef = pedidoRef.collection("apoios").document(uid)

        return db.runTransaction { transacao ->
            val pedido = transacao.get(pedidoRef)
            if (transacao.get(apoioRef).exists()) return@runTransaction ResultadoApoio.JA_APOIOU
            if (pedido.getString("status") != STATUS_ABERTO) return@runTransaction ResultadoApoio.ENCERRADO

            transacao.update(pedidoRef, "apoios", (pedido.getLong("apoios") ?: 0) + 1)
            transacao.set(apoioRef, mapOf("criadoEm" to FieldValue.serverTimestamp()))
            ResultadoApoio.APOIADO
        }.aguardar()
    }

    // Administrador: a lixeira foi instalada; entra no mapa com o mesmo id do pedido
    suspend fun marcarAtendido(pedido: Pedido, nomeNovaLixeira: String) {
        db.batch()
            .update(colecao.document(pedido.id), "status", STATUS_ATENDIDO)
            .set(
                db.collection("lixeiras").document(pedido.id), mapOf(
                    "nome" to nomeNovaLixeira,
                    "local" to pedido.referencia,
                    "coordenada" to GeoPoint(pedido.latLng.latitude, pedido.latLng.longitude),
                    "criadoEm" to FieldValue.serverTimestamp(),
                    "origem" to "admin"
                )
            )
            .commit()
            .aguardar()
    }

    // Administrador: descarta um pedido (ex.: spam ou lugar errado)
    suspend fun recusar(pedido: Pedido) {
        colecao.document(pedido.id).update("status", STATUS_RECUSADO).aguardar()
    }

    override fun onCleared() {
        registro.remove()
    }

    private companion object {
        const val STATUS_ABERTO = "aberto"
        const val STATUS_ATENDIDO = "atendido"
        const val STATUS_RECUSADO = "recusado"
    }
}
