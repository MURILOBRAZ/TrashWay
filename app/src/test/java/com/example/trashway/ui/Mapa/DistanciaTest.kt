package com.example.trashway.ui.Mapa

import org.junit.Assert.assertEquals
import org.junit.Test

class DistanciaTest {

    @Test
    fun mesmoPonto_distanciaZero() {
        assertEquals(0.0, calcularDistanciaMetros(-23.9608, -46.3336, -23.9608, -46.3336), 0.001)
    }

    @Test
    fun umGrauDeLatitude_aproximadamente111km() {
        assertEquals(111_195.0, calcularDistanciaMetros(0.0, 0.0, 1.0, 0.0), 1.0)
    }

    @Test
    fun distanciaEhSimetrica() {
        val ida = calcularDistanciaMetros(-23.9608, -46.3336, -23.9700, -46.3200)
        val volta = calcularDistanciaMetros(-23.9700, -46.3200, -23.9608, -46.3336)
        assertEquals(ida, volta, 0.001)
    }

    @Test
    fun formata_metrosAbaixoDe1km() {
        assertEquals("350 m", formatarDistancia(350.7))
    }

    @Test
    fun formata_quilometrosComVirgula() {
        assertEquals("1,2 km", formatarDistancia(1234.0))
    }
}
