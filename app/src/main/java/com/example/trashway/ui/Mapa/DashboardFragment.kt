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
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.trashway.MainActivity
import com.example.trashway.R
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
    private var icones: Map<Int, BitmapDescriptor> = emptyMap()

    private val voltarEncerraNavegacao = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() = navegacaoViewModel.encerrar()
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

        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner) { lixeiras ->
            lixeiraAdapter.submitList(lixeiras)
            sincronizarMarcadores(lixeiras)
            atualizarTitulo(lixeiras)
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

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, voltarEncerraNavegacao)

        if (!temPermissaoLocalizacao()) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        return binding.root
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
        binding.layoutNavegacao.visibility = View.VISIBLE
        binding.buttonRecentralizar.visibility = View.GONE
        binding.root.keepScreenOn = true
        voltarEncerraNavegacao.isEnabled = true
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
        binding.root.keepScreenOn = false
        voltarEncerraNavegacao.isEnabled = false

        marcadorAvatar?.remove()
        marcadorDestino?.remove()
        linhaRota?.remove()
        linhaRotaBorda?.remove()
        marcadorAvatar = null
        marcadorDestino = null
        linhaRota = null
        linhaRotaBorda = null

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

        googleMap.setOnCameraIdleListener(manager)
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
        _binding = null
    }

    private companion object {
        val SANTOS = LatLng(-23.9670, -46.3300)
        const val ZOOM_NAVEGACAO = 18.5f
        const val INCLINACAO_NAVEGACAO = 60f
        const val SALTO_MAXIMO_ANIMADO_METROS = 80.0
    }
}
