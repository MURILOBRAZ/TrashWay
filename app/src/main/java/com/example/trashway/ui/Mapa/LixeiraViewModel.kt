package com.example.trashway.ui.Mapa

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class LixeiraViewModel(private val db: FirebaseFirestore) : ViewModel() {

    // Lista de Lixeiras
    private val _lixeiras = MutableLiveData<List<Lixeira>>()
    val lixeiras: LiveData<List<Lixeira>> get() = _lixeiras

    // Coordenadas do usuário
    var userLocation: LatLng? = null
        set(value) {
            field = value
            value?.let {
                atualizarDistancias(it)
            }
        }

    init {
        obterLixeirasDoFirestore()
    }

    private fun obterLixeirasDoFirestore() {
        db.collection("lixeiras")
            .get()
            .addOnSuccessListener { result ->
                val listaLixeiras = mutableListOf<Lixeira>()
                for (document in result) {
                    Log.d("LixeiraViewModel", "${document.id} => ${document.data}") // Logando os dados
                    val nome = document.getString("nome") ?: ""
                    val local = document.getString("local") ?: ""
                    val coordenada = document.getGeoPoint("coordenada")

                    coordenada?.let {
                        val latLng = LatLng(it.latitude, it.longitude)
                        listaLixeiras.add(Lixeira(nome, local, "Distância desconhecida", latLng))
                    }
                }
                Log.d("LixeiraViewModel", "Total de lixeiras encontradas: ${listaLixeiras.size}") // Adicione esta linha
                _lixeiras.value = listaLixeiras
            }
            .addOnFailureListener { exception ->
                _lixeiras.value = emptyList()
                Log.w("LixeiraViewModel", "Error getting documents.", exception)
            }
    }

    // Função para recalcular as distâncias das lixeiras
    private fun atualizarDistancias(userLatLng: LatLng) {
        _lixeiras.value = _lixeiras.value?.map { lixeira ->
            val distancia = calcularDistancia(userLatLng, lixeira.latLng)
            lixeira.copy(distancia = distancia)        }
    }

    // Função para calcular a distância entre o usuário e a lixeira
    private fun calcularDistancia(origem: LatLng, destino: LatLng): String {
        val earthRadius = 6371.0 // Raio da Terra em Km
        val dLat = Math.toRadians(destino.latitude - origem.latitude)
        val dLng = Math.toRadians(destino.longitude - origem.longitude)

        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(origem.latitude)) * cos(Math.toRadians(destino.latitude)) *
                sin(dLng / 2) * sin(dLng / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val distancia = earthRadius * c

        return if (distancia < 1) {
            "A ${(distancia * 1000).toInt()}m" // Distância em metros
        } else {
            "A ${"%.1f".format(distancia)}Km" // Distância em quilômetros
        }
    }
}
