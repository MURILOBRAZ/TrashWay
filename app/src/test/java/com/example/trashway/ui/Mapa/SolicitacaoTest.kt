package com.example.trashway.ui.Mapa

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SolicitacaoTest {

    private val aqui = LatLng(-23.9600, -46.3300)

    private fun solicitacao(id: String, lat: Double, lng: Double) =
        Solicitacao(id, LatLng(lat, lng), "ref", votos = 1)

    @Test
    fun maisProximo_escolheOMaisPertoDentroDoRaio() {
        val itens = listOf(
            solicitacao("a", -23.96020, -46.3300), // ~22 m
            solicitacao("b", -23.96010, -46.3300), // ~11 m
            solicitacao("c", -23.96100, -46.3300)  // ~111 m
        )
        assertEquals("b", maisProximoDentroDe(aqui, itens, RAIO_DUPLICATA_METROS) { it.latLng }?.id)
    }

    @Test
    fun maisProximo_nadaDentroDoRaio_null() {
        val itens = listOf(solicitacao("c", -23.96100, -46.3300))
        assertNull(maisProximoDentroDe(aqui, itens, RAIO_DUPLICATA_METROS) { it.latLng })
        assertNull(maisProximoDentroDe(aqui, emptyList<Solicitacao>(), RAIO_DUPLICATA_METROS) { it.latLng })
    }

    @Test
    fun proximoNome_continuaANumeracao() {
        assertEquals("Lixeira N°100", proximoNomeDeLixeira(listOf("Lixeira N°01", "Lixeira N°99", "Lixeira N°7")))
    }

    @Test
    fun proximoNome_ignoraNomesForaDoPadrao() {
        assertEquals("Lixeira N°03", proximoNomeDeLixeira(listOf("Praça", "Lixeira N° 02", "")))
        assertEquals("Lixeira N°01", proximoNomeDeLixeira(emptyList()))
    }
}
