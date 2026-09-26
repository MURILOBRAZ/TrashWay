package com.example.trashway.ui.Mapa

import com.google.android.gms.maps.model.LatLng

data class Lixeira(
    val id: String,
    val nome: String,
    val local: String,
    val latLng: LatLng,
    // Distância até o usuário em metros; null enquanto a localização é desconhecida
    val distanciaMetros: Double? = null
)
