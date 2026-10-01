package com.example.trashway.ui.Mapa

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RotaTest {

    // Rota em "L": ~111 m para o norte e depois ~102 m para o leste
    private val a = LatLng(-23.9700, -46.3300)
    private val b = LatLng(-23.9690, -46.3300)
    private val c = LatLng(-23.9690, -46.3290)

    // Resposta no formato do computeRoutes (só os campos pedidos no FieldMask)
    private fun respostaRoutesApi(): String {
        fun passo(pontos: List<LatLng>, metros: Int, manobra: String, texto: String) = JSONObject()
            .put("distanceMeters", metros)
            .put("polyline", JSONObject().put("encodedPolyline", PolyUtil.encode(pontos)))
            .put("navigationInstruction", JSONObject().put("maneuver", manobra).put("instructions", texto))

        val rota = JSONObject()
            .put("distanceMeters", 213)
            .put("duration", "160s")
            .put("polyline", JSONObject().put("encodedPolyline", PolyUtil.encode(listOf(a, b, c))))
            .put(
                "legs", JSONArray().put(
                    JSONObject().put(
                        "steps", JSONArray()
                            .put(passo(listOf(a, b), 111, "DEPART", "Siga para o norte"))
                            .put(passo(listOf(b, c), 102, "TURN_RIGHT", "Vire à direita na R. Teste\nO destino estará à direita"))
                    )
                )
            )
        return JSONObject().put("routes", JSONArray().put(rota)).toString()
    }

    private fun rota() = lerRota(respostaRoutesApi())!!

    @Test
    fun lerRota_leDistanciaDuracaoEPassos() {
        val rota = rota()
        assertEquals(213, rota.distanciaMetros)
        assertEquals(160, rota.duracaoSegundos)
        assertEquals(3, rota.pontos.size)
        assertEquals(2, rota.passos.size)
        assertEquals("TURN_RIGHT", rota.passos[1].manobra)
        assertEquals("Vire à direita na R. Teste", rota.passos[1].instrucao)
    }

    @Test
    fun lerRota_respostaSemRotas_null() {
        assertNull(lerRota("{}"))
        assertNull(lerRota("""{"routes":[]}"""))
    }

    @Test
    fun progresso_noInicio_proximaManobraEhAVirada() {
        val progresso = ProgressoRota(rota()).atualizar(a)
        assertEquals("TURN_RIGHT", progresso.proximaManobra?.manobra)
        assertEquals(111.0, progresso.metrosAteManobra, 2.0)
        assertEquals(213.0, progresso.metrosRestantes, 3.0)
        assertEquals(160, progresso.segundosRestantes)
        assertEquals(0.0, progresso.distanciaDaRotaMetros, 0.5)
    }

    @Test
    fun progresso_noMeioDoPrimeiroTrecho() {
        val meio = LatLng(-23.9695, -46.3300)
        val progresso = ProgressoRota(rota()).atualizar(meio)
        assertEquals(0, progresso.indiceSegmento)
        assertEquals(55.6, progresso.metrosAteManobra, 2.0)
        assertEquals(157.0, progresso.metrosRestantes, 3.0)
    }

    @Test
    fun progresso_noUltimoTrecho_semManobraSoChegada() {
        val progressoRota = ProgressoRota(rota())
        progressoRota.atualizar(a)
        val progresso = progressoRota.atualizar(LatLng(-23.9690, -46.3295))
        assertEquals(1, progresso.indiceSegmento)
        assertNull(progresso.proximaManobra)
        assertEquals(progresso.metrosRestantes, progresso.metrosAteManobra, 0.001)
        assertEquals(51.0, progresso.metrosRestantes, 2.0)
    }

    @Test
    fun progresso_foraDaRota_medeDistancia() {
        // ~50 m a leste do meio do primeiro trecho
        val fora = LatLng(-23.9695, -46.32951)
        val progresso = ProgressoRota(rota()).atualizar(fora)
        assertEquals(0, progresso.indiceSegmento)
        assertEquals(50.0, progresso.distanciaDaRotaMetros, 3.0)
    }

    @Test
    fun pontosRestantes_comecaNaPosicaoEncaixada() {
        val progressoRota = ProgressoRota(rota())
        val progresso = progressoRota.atualizar(LatLng(-23.9695, -46.32999))
        val restantes = progressoRota.pontosRestantes(progresso)
        assertEquals(3, restantes.size)
        assertEquals(-23.9695, restantes[0].latitude, 1e-6)
        assertEquals(-46.3300, restantes[0].longitude, 1e-6)
        assertEquals(c, restantes.last())
    }

    @Test
    fun diferencaAngular_daAVoltaNoNorte() {
        assertEquals(20f, diferencaAngular(350f, 10f), 0.001f)
        assertEquals(180f, diferencaAngular(0f, 180f), 0.001f)
        assertEquals(5f, diferencaAngular(90f, 85f), 0.001f)
    }
}
