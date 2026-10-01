package com.example.trashway.ui.Mapa

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.example.trashway.BuildConfig
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

// Calcula rotas a pé com a Routes API do Google (computeRoutes)
class RoutesApi(context: Context) {

    private val pacote = context.packageName

    // A chave pode estar restrita a apps Android: nesse caso o Google confere
    // o pacote e a impressão digital (SHA-1) do certificado enviados nos cabeçalhos
    private val certificadoSha1 = sha1DoCertificado(context)

    suspend fun calcularRotaAPe(origem: LatLng, destino: LatLng): Rota = withContext(Dispatchers.IO) {
        val corpo = JSONObject()
            .put("origin", ponto(origem))
            .put("destination", ponto(destino))
            .put("travelMode", "WALK")
            .put("languageCode", "pt-BR")
            .put("units", "METRIC")

        val conexao = URL(URL_COMPUTE_ROUTES).openConnection() as HttpURLConnection
        try {
            conexao.requestMethod = "POST"
            conexao.connectTimeout = 10_000
            conexao.readTimeout = 10_000
            conexao.doOutput = true
            conexao.setRequestProperty("Content-Type", "application/json")
            conexao.setRequestProperty("X-Goog-Api-Key", BuildConfig.ROUTES_API_KEY)
            conexao.setRequestProperty("X-Goog-FieldMask", CAMPOS)
            conexao.setRequestProperty("X-Android-Package", pacote)
            certificadoSha1?.let { conexao.setRequestProperty("X-Android-Cert", it) }

            conexao.outputStream.use { it.write(corpo.toString().toByteArray()) }

            val codigo = conexao.responseCode
            if (codigo !in 200..299) {
                val erro = conexao.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                throw IOException("Routes API respondeu $codigo: $erro")
            }
            val resposta = conexao.inputStream.bufferedReader().use { it.readText() }
            lerRota(resposta) ?: throw IOException("Nenhuma rota a pé encontrada")
        } finally {
            conexao.disconnect()
        }
    }

    private fun ponto(latLng: LatLng) = JSONObject().put(
        "location", JSONObject().put(
            "latLng", JSONObject().put("latitude", latLng.latitude).put("longitude", latLng.longitude)
        )
    )

    private companion object {
        const val URL_COMPUTE_ROUTES = "https://routes.googleapis.com/directions/v2:computeRoutes"

        // Só os campos usados pelo app (a API cobra e responde conforme o pedido)
        const val CAMPOS = "routes.distanceMeters,routes.duration,routes.polyline.encodedPolyline," +
                "routes.legs.steps.distanceMeters,routes.legs.steps.polyline.encodedPolyline," +
                "routes.legs.steps.navigationInstruction"

        @Suppress("DEPRECATION")
        fun sha1DoCertificado(context: Context): String? = try {
            val pm = context.packageManager
            val assinaturas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners
            } else {
                pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES).signatures
            }
            assinaturas?.firstOrNull()?.toByteArray()?.let { bytes ->
                MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02X".format(it) }
            }
        } catch (e: Exception) {
            null
        }
    }
}
