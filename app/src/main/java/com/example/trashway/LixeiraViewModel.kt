package com.example.trashway

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.trashway.ui.dashboard.Lixeira
import com.google.android.gms.maps.model.LatLng
import kotlin.math.*

class LixeiraViewModel : ViewModel() {

    // Lista de Lixeiras
    private val _lixeiras = MutableLiveData<List<Lixeira>>()
    val lixeiras: LiveData<List<Lixeira>> get() = _lixeiras

    // Coordenadas do usuário
    var userLocation: LatLng? = null
        set(value) {
            field = value
            value?.let {
                // Recalcular distâncias quando a localização do usuário é definida
                atualizarDistancias(it)
            }
        }

    init {
        // Inicializando com as lixeiras e sem distâncias calculadas
        _lixeiras.value = listOf(
            Lixeira("Lixeira N°1", "Complexo esportivo Rebouças", "Distância desconhecida", LatLng(-23.9676, -46.3308)),
            Lixeira("Lixeira N°2", "Barca, Travessia Santos-Guarujá", "Distância desconhecida", LatLng(-23.9685, -46.3348)),
            Lixeira("Lixeira N°3", "Av. Bartolomeu de Gusmão", "Distância desconhecida", LatLng(-23.9635, -46.3285)),
            Lixeira("Lixeira N°4", "Av. Alm. Cochrane", "Distância desconhecida", LatLng(-23.96829, -46.3078433)),
            Lixeira("Lixeira N°5", "Av. Dr. Epitácio Pessoa", "Distância desconhecida", LatLng(-23.9786061, -46.3100418))
        )
    }

    // Função para recalcular as distâncias das lixeiras
    private fun atualizarDistancias(userLatLng: LatLng) {
        _lixeiras.value = _lixeiras.value?.map { lixeira ->
            val distancia = calcularDistancia(userLatLng, lixeira.latLng)
            lixeira.copy(distancia = distancia)
        }
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
