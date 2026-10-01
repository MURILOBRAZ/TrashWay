package com.example.trashway.ui.Mapa

import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.PolyUtil
import org.json.JSONObject

// Rota a pé calculada pela Routes API
data class Rota(
    val pontos: List<LatLng>,
    val distanciaMetros: Int,
    val duracaoSegundos: Int,
    val passos: List<Passo>
)

// Um trecho da rota. A instrução descreve a manobra feita no INÍCIO do trecho.
data class Passo(
    val instrucao: String,
    val manobra: String,
    val distanciaMetros: Int,
    val pontos: List<LatLng>
)

// Lê a resposta do computeRoutes; null se não veio nenhuma rota
fun lerRota(json: String): Rota? {
    val rotas = JSONObject(json).optJSONArray("routes") ?: return null
    if (rotas.length() == 0) return null
    val rota = rotas.getJSONObject(0)

    val pontos = PolyUtil.decode(rota.getJSONObject("polyline").getString("encodedPolyline"))
    if (pontos.size < 2) return null

    val passos = mutableListOf<Passo>()
    val trechos = rota.optJSONArray("legs")
    for (i in 0 until (trechos?.length() ?: 0)) {
        val passosJson = trechos!!.getJSONObject(i).optJSONArray("steps") ?: continue
        for (j in 0 until passosJson.length()) {
            val passo = passosJson.getJSONObject(j)
            val navegacao = passo.optJSONObject("navigationInstruction")
            val pontosPasso = passo.optJSONObject("polyline")?.optString("encodedPolyline")
                ?.takeIf { it.isNotEmpty() }
                ?.let { PolyUtil.decode(it) }
                .orEmpty()
            if (pontosPasso.isEmpty()) continue
            passos += Passo(
                // Só a primeira linha: as seguintes são dicas ("Você verá…", "O destino estará…")
                instrucao = navegacao?.optString("instructions").orEmpty().lineSequence().first().trim(),
                manobra = navegacao?.optString("maneuver").orEmpty(),
                distanciaMetros = passo.optInt("distanceMeters"),
                pontos = pontosPasso
            )
        }
    }

    return Rota(
        pontos = pontos,
        distanciaMetros = rota.optInt("distanceMeters"),
        // A duração vem como texto: "123s"
        duracaoSegundos = rota.optString("duration").removeSuffix("s").toDoubleOrNull()?.toInt() ?: 0,
        passos = passos
    )
}
