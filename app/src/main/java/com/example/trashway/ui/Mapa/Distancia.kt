package com.example.trashway.ui.Mapa

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private const val RAIO_TERRA_METROS = 6_371_000.0
private val LOCALE_BR = Locale("pt", "BR")

// Distância em metros entre dois pontos (fórmula de Haversine)
fun calcularDistanciaMetros(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)

    val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLng / 2) * sin(dLng / 2)
    val c = 2 * atan2(sqrt(a), sqrt(1 - a))
    return RAIO_TERRA_METROS * c
}

// "A 350 m" abaixo de 1 km, "A 1,2 km" a partir disso
fun formatarDistancia(metros: Double): String =
    if (metros < 1000) {
        "A ${metros.toInt()} m"
    } else {
        "A ${String.format(LOCALE_BR, "%.1f", metros / 1000)} km"
    }
