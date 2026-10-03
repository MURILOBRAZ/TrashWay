package com.example.trashway.ui.Mapa

import android.Manifest
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.inputmethod.InputMethodManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trashway.MainActivity
import com.example.trashway.R
import com.example.trashway.conta.ContaViewModel
import com.example.trashway.databinding.FragmentDashboardBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Dot
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.maps.android.SphericalUtil
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.collections.MarkerManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

class DashboardFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private var clusterManager: ClusterManager<LixeiraClusterItem>? = null
    private var clusterRenderer: LixeiraClusterRenderer? = null
    private lateinit var lixeiraAdapter: LixeiraAdapter
    private lateinit var bottomSheet: BottomSheetBehavior<View>
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var isMapCenteredOnUser = false // Flag para centralizar apenas uma vez
    private var atualizandoLocalizacao = false

    private val lixeiraViewModel: LixeiraViewModel by activityViewModels()
    private val navegacaoViewModel: NavegacaoViewModel by activityViewModels()
    private val contaViewModel: ContaViewModel by activityViewModels()
    private val solicitacaoViewModel: SolicitacaoViewModel by activityViewModels()
    private val pedidoViewModel: PedidoViewModel by activityViewModels()

    // Itens já adicionados ao mapa, pelo id do documento no Firestore
    private val itensPorId = mutableMapOf<String, LixeiraClusterItem>()

    // --- Modo navegação ---
    private var modoNavegacao = false
    private lateinit var bussola: Bussola
    // Para onde o usuário está virado (graus a partir do norte)
    private var direcao = 0f
    // Posição desenhada do avatar, que desliza entre as leituras do GPS
    private var posicaoExibida: LatLng? = null
    private var animadorPosicao: ValueAnimator? = null
    // A câmera acompanha o usuário até ele arrastar o mapa
    private var seguindo = true
    private var animandoCamera = false
    private var marcadorAvatar: Marker? = null
    private var marcadorDestino: Marker? = null
    private var linhaRota: Polyline? = null
    private var linhaRotaBorda: Polyline? = null
    // Tracejado do avatar até a rota, quando o usuário está fora da rua (ex.: meio do quarteirão)
    private var linhaLigacao: Polyline? = null
    private var inicioRota: LatLng? = null
    private var icones: Map<Int, BitmapDescriptor> = emptyMap()

    // --- Sugestão de lixeiras ---
    // Marcadores das solicitações pendentes (fora do agrupamento das lixeiras)
    private var colecaoSolicitacoes: MarkerManager.Collection? = null
    private var iconesSolicitacao: Map<Int, BitmapDescriptor> = emptyMap()
    // Marcadores dos pedidos de lixeira nova
    private var colecaoPedidos: MarkerManager.Collection? = null
    private var iconesPedido: Map<Int, BitmapDescriptor> = emptyMap()
    private var escolhendoLocal = false
    // Mapear uma lixeira que já existe na rua ou pedir uma para um lugar que não tem
    private var pedindoLixeiraNova = false
    // O usuário digitou a referência: o endereço automático não a sobrescreve mais
    private var referenciaEditada = false
    private var preenchendoReferencia = false

    private val voltar = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (escolhendoLocal) sairEscolhaDeLocal() else navegacaoViewModel.encerrar()
        }
    }

    private fun atualizarVoltar() {
        voltar.isEnabled = modoNavegacao || escolhendoLocal
    }

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
        .setMinUpdateIntervalMillis(2000)
        .build()

    // Durante a navegação a posição é lida com mais frequência
    private val locationRequestNavegacao = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
        .setMinUpdateIntervalMillis(500)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let(::aoReceberLocalizacao)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { resultado ->
        if (resultado.values.any { it }) {
            startLocationUpdates()
        } else {
            navegacaoViewModel.cancelarMaisProxima()
            Toast.makeText(requireContext(), R.string.permissao_negada, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
        bussola = Bussola(requireContext()) { graus ->
            direcao = graus
            renderizarNavegacao()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        bottomSheet = BottomSheetBehavior.from(binding.bottomSheet)

        lixeiraAdapter = LixeiraAdapter(
            onRota = ::iniciarNavegacao,
            onReportar = ::reportar,
            onLixeiraClick = ::mostrarNoMapa
        )
        binding.recyclerViewLixeiras.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewLixeiras.adapter = lixeiraAdapter

        binding.buttonTentarNovamente.setOnClickListener { lixeiraViewModel.obterLixeirasDoFirestore() }

        binding.buttonEncerrar.setOnClickListener { navegacaoViewModel.encerrar() }
        binding.buttonAbrirMaps.setOnClickListener {
            navegacaoViewModel.estado.value?.let { abrirRotaNoGoogleMaps(it.lixeira) }
        }
        binding.buttonRecentralizar.setOnClickListener { voltarASeguir() }

        binding.buttonSugerir.setOnClickListener { iniciarSugestao() }
        binding.buttonCancelarSugestao.setOnClickListener { sairEscolhaDeLocal() }
        binding.buttonEnviarSugestao.setOnClickListener { enviarSugestao() }
        binding.buttonSairConta.setOnClickListener {
            sairEscolhaDeLocal()
            viewLifecycleOwner.lifecycleScope.launch { contaViewModel.sair(requireContext()) }
        }
        binding.editReferencia.doAfterTextChanged {
            if (!preenchendoReferencia) referenciaEditada = true
            binding.inputReferencia.error = null
        }
        solicitacaoViewModel.solicitacoes.observe(viewLifecycleOwner, ::desenharSolicitacoes)
        pedidoViewModel.pedidos.observe(viewLifecycleOwner, ::desenharPedidos)
        contaViewModel.usuario.observe(viewLifecycleOwner) { atualizarCartaoSugestao() }
        contaViewModel.admin.observe(viewLifecycleOwner) { atualizarCartaoSugestao() }

        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner) { lixeiras ->
            lixeiraAdapter.submitList(lixeiras)
            sincronizarMarcadores(lixeiras)
            atualizarTitulo(lixeiras)
            irParaMaisProximaSePendente(lixeiras)
        }
        lixeiraViewModel.carregando.observe(viewLifecycleOwner) { carregando ->
            binding.progressCarregando.visibility = if (carregando) View.VISIBLE else View.GONE
        }
        lixeiraViewModel.erroCarregamento.observe(viewLifecycleOwner) { erro ->
            binding.layoutErro.visibility = if (erro) View.VISIBLE else View.GONE
        }

        navegacaoViewModel.estado.observe(viewLifecycleOwner, ::aplicarEstadoNavegacao)
        navegacaoViewModel.chegada.observe(viewLifecycleOwner) { lixeira ->
            lixeira?.let {
                navegacaoViewModel.chegadaConsumida()
                comemorarChegada(it)
            }
        }
        navegacaoViewModel.aviso.observe(viewLifecycleOwner) { aviso ->
            val texto = when (aviso) {
                AvisoNavegacao.SEM_ROTA -> R.string.sem_rota
                AvisoNavegacao.ROTA_RECALCULADA -> R.string.rota_recalculada
                null -> return@observe
            }
            navegacaoViewModel.avisoConsumido()
            Toast.makeText(requireContext(), texto, Toast.LENGTH_SHORT).show()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, voltar)

        if (!temPermissaoLocalizacao()) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        if (navegacaoViewModel.maisProximaPendente && !navegacaoViewModel.navegando) {
            Toast.makeText(requireContext(), R.string.procurando_mais_proxima, Toast.LENGTH_SHORT).show()
        }
        return binding.root
    }

    // A lista já vem ordenada por distância: a primeira é a mais próxima
    private fun irParaMaisProximaSePendente(lixeiras: List<Lixeira>) {
        if (!navegacaoViewModel.maisProximaPendente) return
        val maisProxima = lixeiras.firstOrNull()?.takeIf { it.distanciaMetros != null } ?: return
        iniciarNavegacao(maisProxima)
    }

    private fun atualizarTitulo(lixeiras: List<Lixeira>) {
        binding.textViewTituloLista.text = when {
            lixeiras.isEmpty() -> getString(R.string.lixeiras_proximas)
            lixeiras.first().distanciaMetros != null ->
                getString(R.string.titulo_lista_com_distancia, lixeiras.size)
            else -> getString(R.string.titulo_lista_sem_distancia, lixeiras.size)
        }
    }

    private fun temPermissaoLocalizacao(): Boolean {
        val contexto = requireContext()
        return ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(contexto, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    // Só começa a rastrear quando o mapa está pronto e a permissão foi concedida
    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val mapa = googleMap ?: return
        if (atualizandoLocalizacao || !temPermissaoLocalizacao()) return
        atualizandoLocalizacao = true

        // Ponto azul e botão "minha localização" do próprio Google Maps (fora da navegação,
        // que desenha o próprio avatar)
        mapa.isMyLocationEnabled = !modoNavegacao

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let(::aoReceberLocalizacao)
        }
        val requisicao = if (modoNavegacao) locationRequestNavegacao else locationRequest
        fusedLocationClient.requestLocationUpdates(requisicao, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        atualizandoLocalizacao = false
    }

    private fun aoReceberLocalizacao(location: Location) {
        val posicao = LatLng(location.latitude, location.longitude)
        atualizarPosicaoUsuario(posicao)
        if (escolhendoLocal) atualizarDistanciaEscolha()

        if (!modoNavegacao) return
        // Sem bússola no aparelho: usa a direção do movimento
        if (!bussola.disponivel && location.hasBearing() && location.speed > 0.7f) {
            direcao = location.bearing
        }
        navegacaoViewModel.atualizarPosicao(posicao, location.accuracy)
        moverAvatar(posicao)
    }

    private fun atualizarPosicaoUsuario(userLatLng: LatLng) {
        val mapa = googleMap ?: return

        // Centraliza o mapa apenas na primeira vez
        if (!isMapCenteredOnUser && !modoNavegacao) {
            mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
            isMapCenteredOnUser = true
        }

        lixeiraViewModel.userLocation = userLatLng
    }

    // Adiciona só as lixeiras novas e remove as que saíram da lista.
    // A lista é reemitida a cada mudança de distância, então não dá para recriar tudo.
    private fun sincronizarMarcadores(lixeiras: List<Lixeira>) {
        val manager = clusterManager ?: return
        val ids = lixeiras.mapTo(HashSet()) { it.id }
        var mudou = false

        itensPorId.entries.removeAll { (id, item) ->
            (id !in ids).also { removido -> if (removido) { manager.removeItem(item); mudou = true } }
        }
        lixeiras.filter { it.id !in itensPorId }.forEach { lixeira ->
            val item = LixeiraClusterItem(lixeira)
            itensPorId[lixeira.id] = item
            manager.addItem(item)
            mudou = true
        }
        if (mudou) manager.cluster()
    }

    // Recolhe o painel, aproxima o mapa na lixeira e abre o balão com o nome
    private fun mostrarNoMapa(lixeira: Lixeira) {
        val mapa = googleMap ?: return
        bottomSheet.state = BottomSheetBehavior.STATE_COLLAPSED
        mapa.animateCamera(
            CameraUpdateFactory.newLatLngZoom(lixeira.latLng, 18f),
            object : GoogleMap.CancelableCallback {
                override fun onFinish() {
                    // O marcador só existe depois que o agrupamento é redesenhado
                    _binding?.mapView?.postDelayed({
                        itensPorId[lixeira.id]?.let { clusterRenderer?.getMarker(it)?.showInfoWindow() }
                    }, 400)
                }

                override fun onCancel() {}
            }
        )
    }

    private fun reportar(lixeira: Lixeira) {
        lixeiraViewModel.reportar(lixeira.id)
        (requireActivity() as MainActivity).irParaAba(R.id.navigation_notifications)
    }

    // Alternativa à navegação do app: abre a rota a pé no Google Maps
    private fun abrirRotaNoGoogleMaps(lixeira: Lixeira) {
        val lat = lixeira.latLng.latitude
        val lng = lixeira.latLng.longitude
        val navegacao = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=w"))
            .setPackage("com.google.android.apps.maps")
        try {
            startActivity(navegacao)
        } catch (e: ActivityNotFoundException) {
            // Sem o app do Google Maps: abre a rota no navegador
            val web = Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lng&travelmode=walking")
            try {
                startActivity(Intent(Intent.ACTION_VIEW, web))
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.sem_app_mapas, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------------------------------------------------------------------
    // Modo navegação: câmera 3D que segue o usuário, rota desenhada e chegada à lixeira
    // ---------------------------------------------------------------------------------

    private fun iniciarNavegacao(lixeira: Lixeira) {
        if (!temPermissaoLocalizacao()) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        navegacaoViewModel.iniciar(lixeira, lixeiraViewModel.userLocation)
    }

    private fun aplicarEstadoNavegacao(estado: EstadoNavegacao?) {
        if (estado != null && !modoNavegacao) entrarModoNavegacao()
        if (estado == null && modoNavegacao) sairModoNavegacao()
        if (estado != null) {
            atualizarPainelNavegacao(estado)
            desenharRota(estado)
        }
    }

    private fun entrarModoNavegacao() {
        modoNavegacao = true
        seguindo = true
        posicaoExibida = lixeiraViewModel.userLocation ?: posicaoExibida

        binding.bottomSheet.visibility = View.GONE
        binding.buttonSugerir.visibility = View.GONE
        binding.layoutNavegacao.visibility = View.VISIBLE
        definirTelaCheia(true)
        binding.buttonRecentralizar.visibility = View.GONE
        binding.root.keepScreenOn = true
        atualizarVoltar()
        bussola.ligar()

        // Troca para a leitura de posição mais frequente
        stopLocationUpdates()
        startLocationUpdates()
        configurarMapaNavegacao()
    }

    // Parte que depende do mapa; também roda no onMapReady se a navegação já estava ativa
    @SuppressLint("MissingPermission")
    private fun configurarMapaNavegacao() {
        val mapa = googleMap ?: return
        mapa.setMapStyle(MapStyleOptions.loadRawResourceStyle(requireContext(), R.raw.estilo_navegacao))
        mapa.uiSettings.isCompassEnabled = false
        // O avatar substitui o ponto azul
        if (temPermissaoLocalizacao()) mapa.isMyLocationEnabled = false

        // O avatar fica na parte de baixo da tela, com mais mapa à frente (como nos jogos)
        binding.layoutNavegacao.post {
            // A navegação pode ter acabado antes disso (ex.: já estava na lixeira)
            if (!modoNavegacao) return@post
            val b = _binding ?: return@post
            val altura = b.mapView.height
            googleMap?.setPadding(0, (altura * 0.45f).toInt(), 0, b.layoutBarraNavegacao.height)
            animarCameraParaUsuario()
            renderizarNavegacao()
        }
    }

    private fun sairModoNavegacao() {
        modoNavegacao = false
        animadorPosicao?.cancel()
        bussola.desligar()

        binding.layoutNavegacao.visibility = View.GONE
        binding.bottomSheet.visibility = View.VISIBLE
        binding.buttonSugerir.visibility = View.VISIBLE
        definirTelaCheia(false)
        binding.root.keepScreenOn = false
        atualizarVoltar()

        marcadorAvatar?.remove()
        marcadorDestino?.remove()
        linhaRota?.remove()
        linhaRotaBorda?.remove()
        linhaLigacao?.remove()
        marcadorAvatar = null
        marcadorDestino = null
        linhaRota = null
        linhaRotaBorda = null
        linhaLigacao = null
        inicioRota = null

        googleMap?.let { mapa ->
            mapa.setMapStyle(null)
            mapa.uiSettings.isCompassEnabled = true
            mapa.setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.peek_lista))
            val centro = lixeiraViewModel.userLocation ?: mapa.cameraPosition.target
            mapa.animateCamera(
                CameraUpdateFactory.newCameraPosition(CameraPosition(centro, 16f, 0f, 0f))
            )
        }
        stopLocationUpdates()
        startLocationUpdates()
    }

    // Navegação em tela cheia: some o cabeçalho e a barra de abas
    private fun definirTelaCheia(ativa: Boolean) {
        binding.header.root.visibility = if (ativa) View.GONE else View.VISIBLE
        (activity as? MainActivity)?.mostrarBarraInferior(!ativa)
    }

    private fun voltarASeguir() {
        seguindo = true
        binding.buttonRecentralizar.visibility = View.GONE
        animarCameraParaUsuario()
    }

    private fun posicaoCameraNavegacao(alvo: LatLng) = CameraPosition.Builder()
        .target(alvo)
        .zoom(ZOOM_NAVEGACAO)
        .tilt(INCLINACAO_NAVEGACAO)
        .bearing(direcao)
        .build()

    // Transição suave para a visão 3D; durante ela a câmera não é movida quadro a quadro
    private fun animarCameraParaUsuario() {
        val mapa = googleMap ?: return
        val alvo = posicaoExibida ?: return
        animandoCamera = true
        mapa.animateCamera(
            CameraUpdateFactory.newCameraPosition(posicaoCameraNavegacao(alvo)),
            1200,
            object : GoogleMap.CancelableCallback {
                override fun onFinish() { animandoCamera = false }
                override fun onCancel() { animandoCamera = false }
            }
        )
    }

    // Desliza o avatar até a nova posição em vez de "teleportar" a cada leitura do GPS
    private fun moverAvatar(destino: LatLng) {
        val inicio = posicaoExibida
        animadorPosicao?.cancel()
        if (inicio == null || metrosEntre(inicio, destino) > SALTO_MAXIMO_ANIMADO_METROS) {
            posicaoExibida = destino
            if (inicio == null) animarCameraParaUsuario()
            renderizarNavegacao()
            return
        }
        animadorPosicao = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            interpolator = LinearInterpolator()
            addUpdateListener { animacao ->
                val t = animacao.animatedFraction.toDouble()
                posicaoExibida = LatLng(
                    inicio.latitude + (destino.latitude - inicio.latitude) * t,
                    inicio.longitude + (destino.longitude - inicio.longitude) * t
                )
                renderizarNavegacao()
            }
            start()
        }
    }

    // Atualiza avatar, câmera e seta; chamado a cada quadro da animação e a cada leitura da bússola
    private fun renderizarNavegacao() {
        if (!modoNavegacao) return
        val mapa = googleMap ?: return
        val posicao = posicaoExibida ?: return

        val avatar = marcadorAvatar
        if (avatar == null) {
            marcadorAvatar = mapa.addMarker(
                MarkerOptions()
                    .position(posicao)
                    .icon(icone(R.drawable.ic_avatar_navegacao))
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .rotation(direcao)
                    .zIndex(10f)
            )
        } else {
            avatar.position = posicao
            avatar.rotation = direcao
        }

        if (seguindo && !animandoCamera) {
            mapa.moveCamera(CameraUpdateFactory.newCameraPosition(posicaoCameraNavegacao(posicao)))
        }
        atualizarLigacao(posicao)

        // Sem rota calculada: a seta do cartão aponta para a lixeira
        navegacaoViewModel.estado.value?.takeIf { it.semRota }?.let { estado ->
            val rumo = SphericalUtil.computeHeading(posicao, estado.lixeira.latLng).toFloat()
            binding.imageManobra.rotation = rumo - direcao
        }
    }

    private fun atualizarPainelNavegacao(estado: EstadoNavegacao) {
        val b = binding
        val progresso = estado.progresso
        val aguardando = estado.rota == null && !estado.semRota

        b.progressRota.visibility = if (aguardando) View.VISIBLE else View.GONE
        b.imageManobra.visibility = if (aguardando) View.GONE else View.VISIBLE
        b.textDistanciaManobra.visibility = if (aguardando) View.GONE else View.VISIBLE
        if (!estado.semRota) b.imageManobra.rotation = 0f

        when {
            aguardando -> b.textInstrucao.setText(
                if (estado.calculando) R.string.calculando_rota else R.string.procurando_localizacao
            )
            progresso == null -> {
                // Sem rota: seta apontando para a lixeira (girada em renderizarNavegacao)
                b.imageManobra.setImageResource(R.drawable.ic_manobra_reto)
                b.textDistanciaManobra.text = estado.distanciaRetaMetros?.let { formatarDistancia(it) }.orEmpty()
                b.textInstrucao.setText(R.string.siga_direcao_lixeira)
            }
            progresso.proximaManobra == null -> {
                b.imageManobra.setImageResource(R.drawable.ic_bandeira)
                b.textDistanciaManobra.text = distanciaArredondada(progresso.metrosAteManobra)
                b.textInstrucao.setText(R.string.lixeira_a_frente)
            }
            else -> {
                val manobra = progresso.proximaManobra
                b.imageManobra.setImageResource(iconeDaManobra(manobra.manobra))
                b.textDistanciaManobra.text = distanciaArredondada(progresso.metrosAteManobra)
                b.textInstrucao.text = manobra.instrucao.ifBlank { getString(R.string.siga_em_frente) }
            }
        }

        b.textResumoRota.text = when {
            progresso != null -> getString(
                R.string.resumo_rota,
                formatarTempo(progresso.segundosRestantes),
                formatarDistancia(progresso.metrosRestantes)
            )
            estado.distanciaRetaMetros != null -> getString(
                R.string.resumo_rota,
                formatarDistancia(estado.distanciaRetaMetros),
                getString(R.string.em_linha_reta)
            )
            else -> ""
        }
        b.textDestino.text = getString(R.string.destino_resumo, estado.lixeira.nome, estado.lixeira.local)
    }

    private fun desenharRota(estado: EstadoNavegacao) {
        val mapa = googleMap ?: return

        if (marcadorDestino?.tag != estado.lixeira.id) {
            marcadorDestino?.remove()
            marcadorDestino = mapa.addMarker(
                MarkerOptions()
                    .position(estado.lixeira.latLng)
                    .icon(icone(R.drawable.ic_destino_lixeira))
                    .anchor(0.5f, 1f)
                    .zIndex(5f)
            )?.also { it.tag = estado.lixeira.id }
        }

        // Com rota: o trecho que falta. Sem rota: linha tracejada até a lixeira.
        val semRota = estado.rota == null
        val pontos = when {
            !semRota -> estado.pontosRestantes.ifEmpty { estado.rota!!.pontos }
            else -> listOfNotNull(posicaoExibida ?: lixeiraViewModel.userLocation, estado.lixeira.latLng)
        }
        inicioRota = if (semRota) null else pontos.firstOrNull()
        posicaoExibida?.let(::atualizarLigacao)
        if (pontos.size < 2) {
            linhaRota?.isVisible = false
            linhaRotaBorda?.isVisible = false
            return
        }

        val densidade = resources.displayMetrics.density
        val borda = linhaRotaBorda ?: mapa.addPolyline(
            PolylineOptions()
                .color(ContextCompat.getColor(requireContext(), R.color.borda_rota))
                .width(14 * densidade)
                .jointType(JointType.ROUND)
                .startCap(RoundCap())
                .endCap(RoundCap())
                .zIndex(1f)
        ).also { linhaRotaBorda = it }
        val linha = linhaRota ?: mapa.addPolyline(
            PolylineOptions()
                .color(ContextCompat.getColor(requireContext(), R.color.linha_rota))
                .width(9 * densidade)
                .jointType(JointType.ROUND)
                .startCap(RoundCap())
                .endCap(RoundCap())
                .zIndex(2f)
        ).also { linhaRota = it }

        val padrao = if (semRota) listOf(Dash(12 * densidade), Gap(8 * densidade)) else null
        for (polyline in listOf(borda, linha)) {
            polyline.points = pontos
            polyline.pattern = padrao
            polyline.isVisible = true
        }
    }

    private fun atualizarLigacao(posicao: LatLng) {
        val mapa = googleMap ?: return
        val inicio = inicioRota
        if (inicio == null || metrosEntre(posicao, inicio) < DISTANCIA_MINIMA_LIGACAO_METROS) {
            linhaLigacao?.isVisible = false
            return
        }
        val densidade = resources.displayMetrics.density
        val linha = linhaLigacao ?: mapa.addPolyline(
            PolylineOptions()
                .color(ContextCompat.getColor(requireContext(), R.color.borda_rota))
                .width(6 * densidade)
                .pattern(listOf(Dot(), Gap(6 * densidade)))
                .zIndex(1f)
        ).also { linhaLigacao = it }
        linha.points = listOf(posicao, inicio)
        linha.isVisible = true
    }

    private fun comemorarChegada(lixeira: Lixeira) {
        vibrarChegada()
        MaterialAlertDialogBuilder(requireContext())
            .setIcon(R.drawable.ic_marcador_lixeira)
            .setTitle(R.string.chegada_titulo)
            .setMessage(getString(R.string.chegada_mensagem, lixeira.nome))
            .setPositiveButton(R.string.chegada_tudo_certo) { _, _ ->
                Toast.makeText(requireContext(), R.string.chegada_obrigado, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.chegada_reportar) { _, _ -> reportar(lixeira) }
            .show()
    }

    private fun vibrarChegada() {
        val contexto = requireContext()
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (contexto.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            contexto.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 90, 80, 90, 220), -1))
    }

    @DrawableRes
    private fun iconeDaManobra(manobra: String): Int = when (manobra) {
        "TURN_LEFT", "TURN_SHARP_LEFT", "ROUNDABOUT_LEFT", "RAMP_LEFT" -> R.drawable.ic_manobra_esquerda
        "TURN_RIGHT", "TURN_SHARP_RIGHT", "ROUNDABOUT_RIGHT", "RAMP_RIGHT" -> R.drawable.ic_manobra_direita
        "TURN_SLIGHT_LEFT", "FORK_LEFT" -> R.drawable.ic_manobra_leve_esquerda
        "TURN_SLIGHT_RIGHT", "FORK_RIGHT" -> R.drawable.ic_manobra_leve_direita
        "UTURN_LEFT", "UTURN_RIGHT" -> R.drawable.ic_manobra_retorno
        else -> R.drawable.ic_manobra_reto
    }

    // Distância até a manobra em passos de 10 m, para o número não ficar "piscando"
    private fun distanciaArredondada(metros: Double): String =
        formatarDistancia(if (metros < 1000) (metros / 10).roundToInt() * 10.0 else metros)

    private fun formatarTempo(segundos: Int): String =
        if (segundos < 60) getString(R.string.menos_de_1_min)
        else getString(R.string.minutos, ceil(segundos / 60.0).toInt())

    private fun icone(@DrawableRes resId: Int): BitmapDescriptor = icones[resId] ?: run {
        val drawable = ContextCompat.getDrawable(requireContext(), resId)!!
        val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        BitmapDescriptorFactory.fromBitmap(bitmap).also { icones = icones + (resId to it) }
    }

    // ---------------------------------------------------------------------------------
    // Sugestão de lixeiras: o usuário marca o local, a comunidade confirma (5 votos)
    // ---------------------------------------------------------------------------------

    private fun iniciarSugestao() {
        if (!temPermissaoLocalizacao()) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
            return
        }
        val opcoes = arrayOf(getString(R.string.opcao_adicionar_lixeira), getString(R.string.opcao_pedir_lixeira))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sugerir_titulo)
            .setItems(opcoes) { _, escolha -> exigirLogin { entrarEscolhaDeLocal(pedirNova = escolha == 1) } }
            .setNegativeButton(R.string.cancelar, null)
            .show()
    }

    private fun entrarEscolhaDeLocal(pedirNova: Boolean) {
        val mapa = googleMap ?: return
        if (modoNavegacao) return
        escolhendoLocal = true
        pedindoLixeiraNova = pedirNova
        atualizarVoltar()
        referenciaEditada = false
        definirReferencia("")

        binding.bottomSheet.visibility = View.GONE
        binding.buttonSugerir.visibility = View.GONE
        binding.layoutEscolherLocal.visibility = View.VISIBLE
        atualizarCartaoSugestao()

        // Sem margens: o centro da câmera coincide com o pino fixo no meio da tela
        mapa.setPadding(0, 0, 0, 0)
        val alvo = lixeiraViewModel.userLocation ?: mapa.cameraPosition.target
        mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(alvo, 19f))
        atualizarDistanciaEscolha()
    }

    private fun sairEscolhaDeLocal() {
        if (!escolhendoLocal) return
        escolhendoLocal = false
        atualizarVoltar()
        val b = _binding ?: return
        esconderTeclado()
        b.layoutEscolherLocal.visibility = View.GONE
        b.bottomSheet.visibility = View.VISIBLE
        b.buttonSugerir.visibility = View.VISIBLE
        googleMap?.setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.peek_lista))
    }

    private fun atualizarCartaoSugestao() {
        val b = _binding ?: return
        val admin = contaViewModel.admin.value == true
        if (pedindoLixeiraNova) {
            b.textTituloEscolherLocal.setText(R.string.escolher_local_titulo_pedido)
            b.inputReferencia.setHint(R.string.referencia_hint_pedido)
            b.buttonEnviarSugestao.setText(R.string.pedir)
            b.textExplicacaoSugestao.setText(R.string.explicacao_pedido)
        } else {
            b.textTituloEscolherLocal.setText(R.string.escolher_local_titulo)
            b.inputReferencia.setHint(R.string.referencia_hint)
            b.buttonEnviarSugestao.setText(if (admin) R.string.cadastrar else R.string.solicitar)
            b.textExplicacaoSugestao.setText(if (admin) R.string.explicacao_admin else R.string.explicacao_votos)
        }
        val usuario = contaViewModel.usuario.value
        b.buttonSairConta.visibility = if (usuario != null) View.VISIBLE else View.INVISIBLE
        b.buttonSairConta.text = getString(R.string.sair_conta, usuario?.email.orEmpty())
    }

    // Distância do usuário até o pino; acima de 50 m não deixa enviar
    private fun atualizarDistanciaEscolha() {
        val b = _binding ?: return
        val mapa = googleMap ?: return
        val usuario = lixeiraViewModel.userLocation
        if (usuario == null) {
            b.textDistanciaLocal.setText(R.string.escolher_local_sem_gps)
            b.buttonEnviarSugestao.isEnabled = false
            return
        }
        val distancia = metrosEntre(usuario, mapa.cameraPosition.target)
        val perto = distancia <= RAIO_PRESENCA_METROS
        b.textDistanciaLocal.text = getString(
            if (perto) R.string.escolher_local_distancia else R.string.escolher_local_longe,
            formatarDistancia(distancia)
        )
        b.buttonEnviarSugestao.isEnabled = perto
    }

    // Preenche a referência com o endereço do ponto, enquanto o usuário não digitar a sua
    private fun aoPararMapaNaEscolha() {
        atualizarDistanciaEscolha()
        if (referenciaEditada) return
        val alvo = googleMap?.cameraPosition?.target ?: return
        val contexto = requireContext().applicationContext
        viewLifecycleOwner.lifecycleScope.launch {
            val endereco = withContext(Dispatchers.IO) { enderecoDe(contexto, alvo) } ?: return@launch
            if (escolhendoLocal && !referenciaEditada && _binding != null) definirReferencia(endereco)
        }
    }

    @Suppress("DEPRECATION") // a versão assíncrona só existe a partir do Android 13
    private fun enderecoDe(contexto: Context, ponto: LatLng): String? = try {
        if (!Geocoder.isPresent()) null
        else Geocoder(contexto, Locale("pt", "BR"))
            .getFromLocation(ponto.latitude, ponto.longitude, 1)
            ?.firstOrNull()
            ?.let { listOfNotNull(it.thoroughfare, it.subThoroughfare).joinToString(", ") }
            ?.takeIf { it.isNotBlank() }
    } catch (e: IOException) {
        null
    }

    private fun definirReferencia(texto: String) {
        preenchendoReferencia = true
        binding.editReferencia.setText(texto)
        preenchendoReferencia = false
    }

    private fun enviarSugestao() {
        val mapa = googleMap ?: return
        val b = binding
        val usuario = lixeiraViewModel.userLocation ?: return aviso(getString(R.string.sem_localizacao))
        val ponto = mapa.cameraPosition.target
        if (metrosEntre(usuario, ponto) > RAIO_PRESENCA_METROS) return

        val referencia = b.editReferencia.text?.toString()?.trim().orEmpty()
        if (referencia.isEmpty()) {
            b.inputReferencia.error =
                getString(if (pedindoLixeiraNova) R.string.erro_referencia_pedido else R.string.erro_referencia)
            return
        }
        if (pedindoLixeiraNova) return enviarPedido(ponto, referencia)

        // Já está no mapa?
        val lixeiras = lixeiraViewModel.lixeiras.value.orEmpty()
        maisProximoDentroDe(ponto, lixeiras, RAIO_DUPLICATA_METROS) { it.latLng }?.let { existente ->
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.ja_existe_lixeira, existente.nome,
                    formatarDistancia(metrosEntre(ponto, existente.latLng))))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        val admin = contaViewModel.admin.value == true
        // Já pediram aqui? Melhor somar o voto do que dividir a votação
        if (!admin) {
            val pendentes = solicitacaoViewModel.solicitacoes.value.orEmpty()
            maisProximoDentroDe(ponto, pendentes, RAIO_DUPLICATA_METROS) { it.latLng }?.let { existente ->
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.ja_existe_solicitacao_titulo)
                    .setMessage(getString(R.string.ja_existe_solicitacao,
                        formatarDistancia(metrosEntre(ponto, existente.latLng)), existente.votos))
                    .setPositiveButton(R.string.confirmar) { _, _ ->
                        sairEscolhaDeLocal()
                        confirmarSolicitacao(existente)
                    }
                    .setNegativeButton(R.string.cancelar, null)
                    .show()
                return
            }
        }

        val uid = contaViewModel.uid ?: return
        b.buttonEnviarSugestao.isEnabled = false
        executar(depois = { atualizarDistanciaEscolha() }) {
            if (admin) {
                val nome = proximoNome()
                solicitacaoViewModel.cadastrarDireto(ponto, referencia, nome)
                aviso(getString(R.string.lixeira_cadastrada, nome))
                lixeiraViewModel.obterLixeirasDoFirestore()
            } else {
                solicitacaoViewModel.solicitar(ponto, referencia, uid)
                aviso(getString(R.string.solicitacao_enviada))
            }
            sairEscolhaDeLocal()
        }
    }

    private fun desenharSolicitacoes(solicitacoes: List<Solicitacao>) {
        val colecao = colecaoSolicitacoes ?: return
        colecao.clear()
        for (solicitacao in solicitacoes) {
            colecao.addMarker(
                MarkerOptions()
                    .position(solicitacao.latLng)
                    .icon(iconeSolicitacao(solicitacao.votos))
                    .anchor(0.5f, 0.5f)
                    .zIndex(3f)
            ).tag = solicitacao
        }
    }

    private fun abrirSolicitacao(solicitacao: Solicitacao) {
        val distancia = lixeiraViewModel.userLocation?.let { metrosEntre(it, solicitacao.latLng) }
        val mensagem = if (distancia != null) {
            getString(R.string.solicitacao_mensagem, solicitacao.referencia, solicitacao.votos,
                formatarDistancia(distancia))
        } else {
            getString(R.string.solicitacao_mensagem_sem_distancia, solicitacao.referencia, solicitacao.votos)
        }
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.solicitacao_titulo)
            .setMessage(mensagem)
            .setPositiveButton(R.string.confirmar) { _, _ -> confirmarSolicitacao(solicitacao) }
            .setNegativeButton(R.string.fechar, null)
        if (contaViewModel.admin.value == true) {
            dialogo.setNeutralButton(R.string.moderar) { _, _ -> moderar(solicitacao) }
        }
        dialogo.show()
    }

    private fun confirmarSolicitacao(solicitacao: Solicitacao) = exigirLogin {
        val posicao = lixeiraViewModel.userLocation ?: return@exigirLogin aviso(getString(R.string.sem_localizacao))
        val distancia = metrosEntre(posicao, solicitacao.latLng)
        if (distancia > RAIO_PRESENCA_METROS) {
            return@exigirLogin aviso(getString(R.string.longe_para_confirmar, formatarDistancia(distancia)))
        }
        val uid = contaViewModel.uid ?: return@exigirLogin
        val nome = proximoNome()
        executar {
            when (solicitacaoViewModel.confirmar(solicitacao.id, uid, nome)) {
                ResultadoConfirmacao.CONFIRMADA ->
                    aviso(getString(R.string.confirmacao_registrada, solicitacao.votos + 1))
                ResultadoConfirmacao.APROVADA -> {
                    aviso(getString(R.string.lixeira_aprovada, nome))
                    lixeiraViewModel.obterLixeirasDoFirestore()
                }
                ResultadoConfirmacao.JA_CONFIRMOU -> aviso(getString(R.string.ja_confirmou))
                ResultadoConfirmacao.ENCERRADA -> aviso(getString(R.string.solicitacao_encerrada))
            }
        }
    }

    // Administrador: aprova na hora ou recusa o pedido
    private fun moderar(solicitacao: Solicitacao) {
        val opcoes = arrayOf(getString(R.string.aprovar_agora), getString(R.string.recusar))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(solicitacao.referencia)
            .setItems(opcoes) { _, escolha ->
                executar {
                    if (escolha == 0) {
                        val nome = proximoNome()
                        solicitacaoViewModel.aprovar(solicitacao, nome)
                        aviso(getString(R.string.lixeira_cadastrada, nome))
                        lixeiraViewModel.obterLixeirasDoFirestore()
                    } else {
                        solicitacaoViewModel.recusar(solicitacao)
                        aviso(getString(R.string.solicitacao_recusada))
                    }
                }
            }
            .show()
    }

    // ---------------------------------------------------------------------------------
    // Pedidos de lixeira nova: a população aponta onde falta lixeira e apoia os pedidos
    // ---------------------------------------------------------------------------------

    private fun enviarPedido(ponto: LatLng, referencia: String) {
        // Já tem lixeira por perto: não precisa de outra
        val lixeiras = lixeiraViewModel.lixeiras.value.orEmpty()
        maisProximoDentroDe(ponto, lixeiras, RAIO_LIXEIRA_PROXIMA_METROS) { it.latLng }?.let { existente ->
            MaterialAlertDialogBuilder(requireContext())
                .setMessage(getString(R.string.pedido_lixeira_perto, existente.nome,
                    formatarDistancia(metrosEntre(ponto, existente.latLng))))
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        // Já pediram aqui? Melhor somar o apoio do que dividir
        val abertos = pedidoViewModel.pedidos.value.orEmpty()
        maisProximoDentroDe(ponto, abertos, RAIO_DUPLICATA_METROS) { it.latLng }?.let { existente ->
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.ja_existe_pedido_titulo)
                .setMessage(getString(R.string.ja_existe_pedido,
                    formatarDistancia(metrosEntre(ponto, existente.latLng)), pessoasPedem(existente.apoios)))
                .setPositiveButton(R.string.apoiar) { _, _ ->
                    sairEscolhaDeLocal()
                    apoiarPedido(existente)
                }
                .setNegativeButton(R.string.cancelar, null)
                .show()
            return
        }

        val uid = contaViewModel.uid ?: return
        binding.buttonEnviarSugestao.isEnabled = false
        executar(depois = { atualizarDistanciaEscolha() }) {
            pedidoViewModel.pedir(ponto, referencia, uid)
            aviso(getString(R.string.pedido_enviado))
            sairEscolhaDeLocal()
        }
    }

    private fun pessoasPedem(apoios: Int) = resources.getQuantityString(R.plurals.pessoas_pedem, apoios, apoios)

    private fun desenharPedidos(pedidos: List<Pedido>) {
        val colecao = colecaoPedidos ?: return
        colecao.clear()
        for (pedido in pedidos) {
            colecao.addMarker(
                MarkerOptions()
                    .position(pedido.latLng)
                    .icon(iconePedido(pedido.apoios))
                    .anchor(0.5f, 0.5f)
                    .zIndex(2f)
            ).tag = pedido
        }
    }

    private fun abrirPedido(pedido: Pedido) {
        val dialogo = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.pedido_titulo)
            .setMessage(getString(R.string.pedido_mensagem, pedido.referencia, pessoasPedem(pedido.apoios)))
            .setPositiveButton(R.string.apoiar) { _, _ -> apoiarPedido(pedido) }
            .setNegativeButton(R.string.fechar, null)
        if (contaViewModel.admin.value == true) {
            dialogo.setNeutralButton(R.string.moderar) { _, _ -> moderarPedido(pedido) }
        }
        dialogo.show()
    }

    // Apoiar não exige estar no local: funciona como um abaixo-assinado
    private fun apoiarPedido(pedido: Pedido) = exigirLogin {
        val uid = contaViewModel.uid ?: return@exigirLogin
        executar {
            when (pedidoViewModel.apoiar(pedido.id, uid)) {
                ResultadoApoio.APOIADO -> aviso(getString(R.string.apoio_registrado))
                ResultadoApoio.JA_APOIOU -> aviso(getString(R.string.ja_apoiou))
                ResultadoApoio.ENCERRADO -> aviso(getString(R.string.pedido_encerrado))
            }
        }
    }

    // Administrador: a lixeira foi instalada (entra no mapa) ou o pedido é recusado
    private fun moderarPedido(pedido: Pedido) {
        val opcoes = arrayOf(getString(R.string.lixeira_instalada), getString(R.string.recusar))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(pedido.referencia)
            .setItems(opcoes) { _, escolha ->
                executar {
                    if (escolha == 0) {
                        val nome = proximoNome()
                        pedidoViewModel.marcarAtendido(pedido, nome)
                        aviso(getString(R.string.lixeira_cadastrada, nome))
                        lixeiraViewModel.obterLixeirasDoFirestore()
                    } else {
                        pedidoViewModel.recusar(pedido)
                        aviso(getString(R.string.solicitacao_recusada))
                    }
                }
            }
            .show()
    }

    // Marcador do pedido: círculo laranja com o número de apoios
    private fun iconePedido(apoios: Int): BitmapDescriptor = iconesPedido[apoios] ?: run {
        val densidade = resources.displayMetrics.density
        val tamanho = (40 * densidade).toInt()
        val bitmap = Bitmap.createBitmap(tamanho, tamanho, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centro = tamanho / 2f

        val laranja = ContextCompat.getColor(requireContext(), R.color.laranja_pedido)
        canvas.drawCircle(centro, centro, centro - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        canvas.drawCircle(centro, centro, centro - 3.5f * densidade, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = laranja })

        val rotulo = if (apoios < 1000) "$apoios" else "999+"
        val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFFFFFFFF.toInt()
            textSize = (if (rotulo.length <= 2) 14f else 11f) * densidade
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(rotulo, centro, centro - (texto.descent() + texto.ascent()) / 2, texto)

        BitmapDescriptorFactory.fromBitmap(bitmap).also { iconesPedido = iconesPedido + (apoios to it) }
    }

    // Login só quando a ação precisa dele; o resto do app funciona sem conta
    private fun exigirLogin(depois: () -> Unit) {
        if (contaViewModel.logado) {
            depois()
            return
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.login_titulo)
            .setMessage(R.string.login_mensagem)
            .setPositiveButton(R.string.entrar) { _, _ ->
                val activity = requireActivity()
                viewLifecycleOwner.lifecycleScope.launch {
                    val entrou = try {
                        contaViewModel.entrarComGoogle(activity)
                    } catch (e: Exception) {
                        Log.w("DashboardFragment", "Falha no login com Google", e)
                        aviso(getString(R.string.erro_login))
                        false
                    }
                    if (entrou && _binding != null) depois()
                }
            }
            .setNegativeButton(R.string.agora_nao, null)
            .show()
    }

    // Roda uma operação no Firestore e mostra um aviso genérico se ela falhar
    private fun executar(depois: () -> Unit = {}, bloco: suspend () -> Unit) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                bloco()
            } catch (e: Exception) {
                Log.w("DashboardFragment", "Falha na operação", e)
                aviso(getString(R.string.erro_operacao))
            } finally {
                if (_binding != null) depois()
            }
        }
    }

    private fun aviso(texto: String) {
        context?.let { Toast.makeText(it, texto, Toast.LENGTH_LONG).show() }
    }

    private fun proximoNome() = proximoNomeDeLixeira(lixeiraViewModel.lixeiras.value.orEmpty().map { it.nome })

    private fun esconderTeclado() {
        val b = _binding ?: return
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(b.editReferencia.windowToken, 0)
        b.editReferencia.clearFocus()
    }

    // Marcador da solicitação: anel que enche conforme os votos, com a contagem no meio
    private fun iconeSolicitacao(votos: Int): BitmapDescriptor = iconesSolicitacao[votos] ?: run {
        val densidade = resources.displayMetrics.density
        val tamanho = (46 * densidade).toInt()
        val bitmap = Bitmap.createBitmap(tamanho, tamanho, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val centro = tamanho / 2f
        val espessura = 4.5f * densidade
        val raio = centro - espessura

        val verde = ContextCompat.getColor(requireContext(), R.color.verde_marca)
        canvas.drawCircle(centro, centro, centro - 1f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFFFFF.toInt() })
        val anel = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = espessura
            color = 0x33007A51
        }
        canvas.drawCircle(centro, centro, raio, anel)
        anel.color = verde
        anel.strokeCap = Paint.Cap.ROUND
        val oval = RectF(centro - raio, centro - raio, centro + raio, centro + raio)
        canvas.drawArc(oval, -90f, 360f * votos.coerceAtMost(VOTOS_PARA_APROVAR) / VOTOS_PARA_APROVAR, false, anel)

        val texto = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = verde
            textSize = 12.5f * densidade
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("$votos/$VOTOS_PARA_APROVAR", centro, centro - (texto.descent() + texto.ascent()) / 2, texto)

        BitmapDescriptorFactory.fromBitmap(bitmap).also { iconesSolicitacao = iconesSolicitacao + (votos to it) }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        // Deixa o logo do Google e o centro do mapa acima do painel recolhido
        googleMap.setPadding(0, 0, 0, resources.getDimensionPixelSize(R.dimen.peek_lista))
        googleMap.uiSettings.isMapToolbarEnabled = false

        // Começa em Santos enquanto a localização do usuário não chega
        if (!isMapCenteredOnUser) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(SANTOS, 13f))
        }

        val manager = ClusterManager<LixeiraClusterItem>(requireContext(), googleMap)
        val renderer = LixeiraClusterRenderer(requireContext(), googleMap, manager)
        manager.renderer = renderer
        clusterManager = manager
        clusterRenderer = renderer

        googleMap.setOnCameraIdleListener {
            manager.onCameraIdle()
            if (escolhendoLocal) aoPararMapaNaEscolha()
        }
        googleMap.setOnCameraMoveListener { if (escolhendoLocal) atualizarDistanciaEscolha() }

        // Solicitações numa coleção própria do MarkerManager, para o clique não passar pelo agrupamento
        colecaoSolicitacoes = manager.markerManager.newCollection().also { colecao ->
            colecao.setOnMarkerClickListener { marcador ->
                (marcador.tag as? Solicitacao)?.let(::abrirSolicitacao)
                true
            }
        }
        colecaoPedidos = manager.markerManager.newCollection().also { colecao ->
            colecao.setOnMarkerClickListener { marcador ->
                (marcador.tag as? Pedido)?.let(::abrirPedido)
                true
            }
        }
        googleMap.setOnMarkerClickListener(manager)

        // Arrastar o mapa durante a navegação para de seguir o usuário
        googleMap.setOnCameraMoveStartedListener { motivo ->
            if (modoNavegacao && motivo == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                seguindo = false
                animandoCamera = false
                _binding?.buttonRecentralizar?.visibility = View.VISIBLE
            }
        }

        manager.setOnClusterClickListener { cluster ->
            // Toque num grupo: aproxima para separar as lixeiras
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(cluster.position, googleMap.cameraPosition.zoom + 2f)
            )
            true
        }
        manager.setOnClusterItemClickListener { item ->
            val position = lixeiraAdapter.currentList.indexOfFirst { it.id == item.lixeira.id }
            if (position != -1) {
                binding.recyclerViewLixeiras.scrollToPosition(position)
            }
            false // mantém o comportamento padrão: abre o balão e centraliza
        }

        sincronizarMarcadores(lixeiraViewModel.lixeiras.value.orEmpty())
        desenharSolicitacoes(solicitacaoViewModel.solicitacoes.value.orEmpty())
        desenharPedidos(pedidoViewModel.pedidos.value.orEmpty())
        startLocationUpdates()

        // Volta para a aba com uma navegação em andamento
        navegacaoViewModel.estado.value?.let { estado ->
            configurarMapaNavegacao()
            desenharRota(estado)
        }
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        startLocationUpdates()
        if (modoNavegacao) bussola.ligar()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        stopLocationUpdates()
        bussola.desligar()
    }

    override fun onStop() {
        super.onStop()
        binding.mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        _binding?.mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        _binding?.mapView?.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // A barra de abas é da Activity: não pode ficar escondida para as outras telas
        if (modoNavegacao) (activity as? MainActivity)?.mostrarBarraInferior(true)
        animadorPosicao?.cancel()
        bussola.desligar()
        binding.mapView.onDestroy()
        googleMap = null
        clusterManager = null
        clusterRenderer = null
        isMapCenteredOnUser = false
        itensPorId.clear()
        modoNavegacao = false
        marcadorAvatar = null
        marcadorDestino = null
        linhaRota = null
        linhaRotaBorda = null
        linhaLigacao = null
        inicioRota = null
        colecaoSolicitacoes = null
        colecaoPedidos = null
        escolhendoLocal = false
        _binding = null
    }

    private companion object {
        val SANTOS = LatLng(-23.9670, -46.3300)
        const val ZOOM_NAVEGACAO = 18.5f
        const val INCLINACAO_NAVEGACAO = 60f
        const val SALTO_MAXIMO_ANIMADO_METROS = 80.0
        const val DISTANCIA_MINIMA_LIGACAO_METROS = 6.0
    }
}
