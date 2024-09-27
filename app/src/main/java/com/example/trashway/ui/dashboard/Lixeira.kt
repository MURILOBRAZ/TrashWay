package com.example.trashway.ui.dashboard

import com.google.android.gms.maps.model.LatLng

data class Lixeira(
    val nome: String,
    val local: String,
    val distancia: String,
    val latLng: LatLng
)
