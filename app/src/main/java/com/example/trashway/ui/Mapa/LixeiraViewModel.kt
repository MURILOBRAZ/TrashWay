package com.example.trashway.ui.Mapa

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore

class LixeiraViewModel(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) : ViewModel() {

    // Lista de Lixeiras, ordenada da mais próxima para a mais distante
    private val _lixeiras = MutableLiveData<List<Lixeira>>(emptyList())
    val lixeiras: LiveData<List<Lixeira>> get() = _lixeiras

    // true quando a leitura do Firestore falhou
    private val _erroCarregamento = MutableLiveData(false)
    val erroCarregamento: LiveData<Boolean> get() = _erroCarregamento

    // Posição usada no último cálculo de distâncias
    private var ultimaPosicaoCalculada: LatLng? = null

    // Coordenadas do usuário
    var userLocation: LatLng? = null
        set(value) {
            field = value
            value?.let { atualizarDistancias(it) }
        }

    init {
        obterLixeirasDoFirestore()
    }

    fun obterLixeirasDoFirestore() {
        _erroCarregamento.value = false
        db.collection("lixeiras")
            .get()
            .addOnSuccessListener { result ->
                val listaLixeiras = result.mapNotNull { document ->
                    val coordenada = document.getGeoPoint("coordenada") ?: return@mapNotNull null
                    Lixeira(
                        id = document.id,
                        nome = document.getString("nome") ?: "",
                        local = document.getString("local") ?: "",
                        latLng = LatLng(coordenada.latitude, coordenada.longitude)
                    )
                }
                Log.d("LixeiraViewModel", "Total de lixeiras encontradas: ${listaLixeiras.size}")
                _lixeiras.value = listaLixeiras.sortedBy { it.nome }
                ultimaPosicaoCalculada = null
                userLocation?.let { atualizarDistancias(it) }
            }
            .addOnFailureListener { exception ->
                Log.w("LixeiraViewModel", "Error getting documents.", exception)
                _erroCarregamento.value = true
            }
    }

    // Recalcula as distâncias e reordena a lista.
    // Ignora deslocamentos pequenos para a lista não ficar se reorganizando à toa.
    private fun atualizarDistancias(userLatLng: LatLng) {
        val atuais = _lixeiras.value.orEmpty()
        if (atuais.isEmpty()) return

        ultimaPosicaoCalculada?.let { ultima ->
            val deslocamento = calcularDistanciaMetros(
                ultima.latitude, ultima.longitude, userLatLng.latitude, userLatLng.longitude
            )
            if (deslocamento < DESLOCAMENTO_MINIMO_METROS) return
        }
        ultimaPosicaoCalculada = userLatLng

        _lixeiras.value = atuais
            .map { lixeira ->
                lixeira.copy(
                    distanciaMetros = calcularDistanciaMetros(
                        userLatLng.latitude, userLatLng.longitude,
                        lixeira.latLng.latitude, lixeira.latLng.longitude
                    )
                )
            }
            .sortedBy { it.distanciaMetros }
    }

    private companion object {
        const val DESLOCAMENTO_MINIMO_METROS = 10.0
    }
}
