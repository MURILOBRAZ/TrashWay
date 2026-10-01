package com.example.trashway.ui.Mapa

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// Direção para onde o celular aponta, em graus a partir do norte (0..360).
// Funciona com o aparelho deitado ou em pé, como num jogo de realidade aumentada.
class Bussola(context: Context, private val aoMudar: (Float) -> Unit) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val matriz = FloatArray(9)

    // Direção suavizada, guardada como vetor para não "dar a volta" entre 359° e 0°
    private var seno = 0.0
    private var cosseno = 0.0
    private var iniciada = false
    private var ultimoEnvio = 0L
    private var ultimaDirecao = Float.NaN

    val disponivel: Boolean get() = sensor != null

    fun ligar() {
        sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun desligar() {
        sensorManager.unregisterListener(this)
        iniciada = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        SensorManager.getRotationMatrixFromVector(matriz, event.values)

        // Eixo Y do aparelho (topo da tela) somado ao -Z (para trás da tela), projetados
        // no plano horizontal: deitado vale o Y, em pé vale o -Z, inclinado os dois concordam
        val leste = matriz[1] - matriz[2]
        val norte = matriz[4] - matriz[5]
        if (abs(leste) + abs(norte) < 1e-3) return
        val angulo = atan2(leste.toDouble(), norte.toDouble())

        if (!iniciada) {
            seno = sin(angulo)
            cosseno = cos(angulo)
            iniciada = true
        } else {
            seno += SUAVIZACAO * (sin(angulo) - seno)
            cosseno += SUAVIZACAO * (cos(angulo) - cosseno)
        }
        val direcao = ((Math.toDegrees(atan2(seno, cosseno)) + 360) % 360).toFloat()

        // Evita redesenhar o mapa por variações mínimas
        val agora = SystemClock.elapsedRealtime()
        if (!ultimaDirecao.isNaN() &&
            (diferencaAngular(direcao, ultimaDirecao) < 1f || agora - ultimoEnvio < 33)
        ) return
        ultimaDirecao = direcao
        ultimoEnvio = agora
        aoMudar(direcao)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private companion object {
        const val SUAVIZACAO = 0.12
    }
}

// Menor diferença entre dois ângulos, em graus (0..180)
fun diferencaAngular(a: Float, b: Float): Float {
    val d = abs(a - b) % 360
    return if (d > 180) 360 - d else d
}
