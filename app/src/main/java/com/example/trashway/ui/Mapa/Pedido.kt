package com.example.trashway.ui.Mapa

import com.google.android.gms.maps.model.LatLng

// Pedido de lixeira para um lugar que não tem. Os apoios não criam a lixeira:
// mostram onde a população quer uma. Um administrador marca como atendido quando instalarem.
data class Pedido(
    val id: String,
    val latLng: LatLng,
    val referencia: String,
    val apoios: Int
)

// Com uma lixeira a menos que isso, não faz sentido pedir outra
const val RAIO_LIXEIRA_PROXIMA_METROS = 50.0
