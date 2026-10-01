package com.example.trashway.ui.Mapa

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Estado do modo navegação (null = mapa normal)
data class EstadoNavegacao(
    val lixeira: Lixeira,
    val rota: Rota? = null,
    val progresso: Progresso? = null,
    // Trecho da rota que ainda falta, para desenhar no mapa
    val pontosRestantes: List<LatLng> = emptyList(),
    val calculando: Boolean = false,
    // A rota não pôde ser calculada: o app mostra a direção em linha reta
    val semRota: Boolean = false,
    val distanciaRetaMetros: Double? = null
)

class NavegacaoViewModel(application: Application) : AndroidViewModel(application) {

    private val routesApi = RoutesApi(application)

    private val _estado = MutableLiveData<EstadoNavegacao?>(null)
    val estado: LiveData<EstadoNavegacao?> get() = _estado

    // Lixeira alcançada; a tela mostra a comemoração e chama chegadaConsumida()
    private val _chegada = MutableLiveData<Lixeira?>(null)
    val chegada: LiveData<Lixeira?> get() = _chegada

    // Avisos rápidos para a tela (ex.: falha ao calcular a rota)
    private val _aviso = MutableLiveData<AvisoNavegacao?>(null)
    val aviso: LiveData<AvisoNavegacao?> get() = _aviso

    private var progressoRota: ProgressoRota? = null
    private var calculo: Job? = null
    private var ultimaPosicao: LatLng? = null
    private var ultimoCalculo = 0L
    private var leiturasForaDaRota = 0

    val navegando: Boolean get() = _estado.value != null

    fun iniciar(lixeira: Lixeira, posicao: LatLng?) {
        cancelarCalculo()
        progressoRota = null
        leiturasForaDaRota = 0
        _estado.value = EstadoNavegacao(lixeira = lixeira, calculando = posicao != null)
        // Sem posição ainda: a rota é calculada quando ela chegar
        posicao?.let { atualizarPosicao(it, precisaoMetros = 0f) }
    }

    fun encerrar() {
        cancelarCalculo()
        progressoRota = null
        _estado.value = null
    }

    fun atualizarPosicao(posicao: LatLng, precisaoMetros: Float) {
        ultimaPosicao = posicao
        val atual = _estado.value ?: return
        val lixeira = atual.lixeira
        val distanciaReta = metrosEntre(posicao, lixeira.latLng)

        if (distanciaReta <= RAIO_CHEGADA_METROS) {
            encerrar()
            _chegada.value = lixeira
            return
        }

        val progressoRota = progressoRota
        if (progressoRota == null) {
            _estado.value = atual.copy(distanciaRetaMetros = distanciaReta)
            // Primeira rota, ou nova tentativa depois de uma falha
            if (calculo == null && (atual.rota == null) && podeRecalcular(INTERVALO_NOVA_TENTATIVA_MS)) {
                calcularRota(posicao, lixeira)
            }
            return
        }

        val progresso = progressoRota.atualizar(posicao)
        _estado.value = atual.copy(
            progresso = progresso,
            pontosRestantes = progressoRota.pontosRestantes(progresso),
            distanciaRetaMetros = distanciaReta
        )

        // Saiu do caminho: recalcula, sem exagerar nas chamadas à API
        val tolerancia = TOLERANCIA_ROTA_METROS + precisaoMetros.coerceAtMost(30f)
        leiturasForaDaRota = if (progresso.distanciaDaRotaMetros > tolerancia) leiturasForaDaRota + 1 else 0
        if (leiturasForaDaRota >= LEITURAS_PARA_RECALCULAR && calculo == null &&
            podeRecalcular(INTERVALO_RECALCULO_MS)
        ) {
            leiturasForaDaRota = 0
            calcularRota(posicao, lixeira)
        }
    }

    private fun podeRecalcular(intervaloMs: Long) =
        ultimoCalculo == 0L || SystemClock.elapsedRealtime() - ultimoCalculo >= intervaloMs

    private fun calcularRota(origem: LatLng, lixeira: Lixeira) {
        ultimoCalculo = SystemClock.elapsedRealtime()
        _estado.value = _estado.value?.copy(calculando = true)
        calculo = viewModelScope.launch {
            val rota = try {
                routesApi.calcularRotaAPe(origem, lixeira.latLng)
            } catch (e: Exception) {
                Log.w("NavegacaoViewModel", "Falha ao calcular a rota", e)
                null
            }
            calculo = null
            val atual = _estado.value
            if (atual == null || atual.lixeira.id != lixeira.id) return@launch

            if (rota == null) {
                // Mantém a rota anterior, se houver; senão segue em linha reta
                _estado.value = atual.copy(calculando = false, semRota = atual.rota == null)
                if (atual.rota == null) _aviso.value = AvisoNavegacao.SEM_ROTA
                return@launch
            }

            progressoRota = ProgressoRota(rota)
            leiturasForaDaRota = 0
            if (atual.rota != null) _aviso.value = AvisoNavegacao.ROTA_RECALCULADA
            _estado.value = atual.copy(rota = rota, calculando = false, semRota = false)
            ultimaPosicao?.let { atualizarPosicao(it, precisaoMetros = 0f) }
        }
    }

    private fun cancelarCalculo() {
        calculo?.cancel()
        calculo = null
        ultimoCalculo = 0L
    }

    fun chegadaConsumida() {
        _chegada.value = null
    }

    fun avisoConsumido() {
        _aviso.value = null
    }

    private companion object {
        const val RAIO_CHEGADA_METROS = 20.0
        const val TOLERANCIA_ROTA_METROS = 25f
        const val LEITURAS_PARA_RECALCULAR = 2
        const val INTERVALO_RECALCULO_MS = 15_000L
        const val INTERVALO_NOVA_TENTATIVA_MS = 30_000L
    }
}

enum class AvisoNavegacao { SEM_ROTA, ROTA_RECALCULADA }
