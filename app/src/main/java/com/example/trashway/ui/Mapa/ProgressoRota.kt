package com.example.trashway.ui.Mapa

import com.google.android.gms.maps.model.LatLng
import kotlin.math.cos
import kotlin.math.roundToInt

// Onde o usuário está em relação à rota
data class Progresso(
    // Posição do usuário "encaixada" na linha da rota
    val pontoNaRota: LatLng,
    // Índice do segmento (pontos[i] -> pontos[i + 1]) em que o usuário está
    val indiceSegmento: Int,
    val distanciaDaRotaMetros: Double,
    val metrosRestantes: Double,
    val segundosRestantes: Int,
    // Próxima manobra a fazer; null quando o próximo ponto já é a lixeira
    val proximaManobra: Passo?,
    val metrosAteManobra: Double
)

// Acompanha o avanço do usuário ao longo de uma rota.
// Só procura para a frente (com uma janela limitada) para não "pular" para
// trechos da rota que passam perto de onde o usuário já esteve.
class ProgressoRota(private val rota: Rota) {

    private val pontos = rota.pontos

    // Distância percorrida ao longo da rota até cada ponto
    private val acumulado = DoubleArray(pontos.size).also { acc ->
        for (i in 1 until pontos.size) acc[i] = acc[i - 1] + metrosEntre(pontos[i - 1], pontos[i])
    }
    private val comprimentoTotal = acumulado.last()

    // Onde cada passo termina, na mesma escala de `acumulado`
    private val fimDosPassos: DoubleArray = run {
        val comprimentos = rota.passos.map { passo ->
            (1 until passo.pontos.size).sumOf { metrosEntre(passo.pontos[it - 1], passo.pontos[it]) }
        }
        val soma = comprimentos.sum()
        val escala = if (soma > 0) comprimentoTotal / soma else 0.0
        var total = 0.0
        DoubleArray(comprimentos.size) { i -> total += comprimentos[i] * escala; total }
    }

    private var segmentoAtual = 0

    fun atualizar(posicao: LatLng): Progresso {
        val limite = acumulado[segmentoAtual] + JANELA_BUSCA_METROS
        var melhor = segmentoAtual
        var melhorProjecao = projetar(posicao, segmentoAtual)
        var i = segmentoAtual + 1
        while (i < pontos.size - 1 && acumulado[i] <= limite) {
            val projecao = projetar(posicao, i)
            if (projecao.distancia < melhorProjecao.distancia) {
                melhor = i
                melhorProjecao = projecao
            }
            i++
        }
        segmentoAtual = melhor

        val percorrido = acumulado[melhor] + metrosEntre(pontos[melhor], melhorProjecao.ponto)
        val restante = (comprimentoTotal - percorrido).coerceAtLeast(0.0)

        // Passo atual = o primeiro que ainda não terminou
        val passoAtual = fimDosPassos.indexOfFirst { it > percorrido }
        val proximaManobra: Passo?
        val metrosAteManobra: Double
        if (passoAtual == -1 || passoAtual >= rota.passos.size - 1) {
            proximaManobra = null
            metrosAteManobra = restante
        } else {
            proximaManobra = rota.passos[passoAtual + 1]
            metrosAteManobra = fimDosPassos[passoAtual] - percorrido
        }

        val segundos = if (rota.duracaoSegundos > 0 && comprimentoTotal > 0) {
            rota.duracaoSegundos * restante / comprimentoTotal
        } else {
            restante / VELOCIDADE_CAMINHADA
        }

        return Progresso(
            pontoNaRota = melhorProjecao.ponto,
            indiceSegmento = melhor,
            distanciaDaRotaMetros = melhorProjecao.distancia,
            metrosRestantes = restante,
            segundosRestantes = segundos.roundToInt(),
            proximaManobra = proximaManobra,
            metrosAteManobra = metrosAteManobra
        )
    }

    // Trecho que falta percorrer, para desenhar só ele no mapa
    fun pontosRestantes(progresso: Progresso): List<LatLng> =
        listOf(progresso.pontoNaRota) + pontos.subList(progresso.indiceSegmento + 1, pontos.size)

    private class Projecao(val ponto: LatLng, val distancia: Double)

    // Ponto mais próximo de `p` no segmento i, usando uma projeção plana local
    // (suficiente para as distâncias curtas de uma caminhada)
    private fun projetar(p: LatLng, i: Int): Projecao {
        val a = pontos[i]
        val b = pontos[i + 1]
        val escalaLng = cos(Math.toRadians(a.latitude))
        val bx = (b.longitude - a.longitude) * escalaLng
        val by = b.latitude - a.latitude
        val px = (p.longitude - a.longitude) * escalaLng
        val py = p.latitude - a.latitude
        val comprimento2 = bx * bx + by * by
        val t = if (comprimento2 == 0.0) 0.0 else ((px * bx + py * by) / comprimento2).coerceIn(0.0, 1.0)
        val ponto = LatLng(a.latitude + t * (b.latitude - a.latitude), a.longitude + t * (b.longitude - a.longitude))
        return Projecao(ponto, metrosEntre(p, ponto))
    }

    private companion object {
        const val JANELA_BUSCA_METROS = 120.0
        const val VELOCIDADE_CAMINHADA = 1.3 // m/s
    }
}

fun metrosEntre(a: LatLng, b: LatLng): Double =
    calcularDistanciaMetros(a.latitude, a.longitude, b.latitude, b.longitude)
