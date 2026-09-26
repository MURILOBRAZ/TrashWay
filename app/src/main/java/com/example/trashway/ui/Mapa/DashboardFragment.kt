package com.example.trashway.ui.Mapa

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
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
import com.google.android.gms.maps.model.LatLng
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.maps.android.clustering.ClusterManager

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

    // Itens já adicionados ao mapa, pelo id do documento no Firestore
    private val itensPorId = mutableMapOf<String, LixeiraClusterItem>()

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
        .setMinUpdateIntervalMillis(2000)
        .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                atualizarPosicaoUsuario(LatLng(location.latitude, location.longitude))
            }
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
            onRota = ::abrirRotaAPe,
            onReportar = { lixeira ->
                lixeiraViewModel.reportar(lixeira.id)
                (requireActivity() as MainActivity).irParaAba(R.id.navigation_notifications)
            },
            onLixeiraClick = ::mostrarNoMapa
        )
        binding.recyclerViewLixeiras.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewLixeiras.adapter = lixeiraAdapter

        binding.buttonTentarNovamente.setOnClickListener { lixeiraViewModel.obterLixeirasDoFirestore() }

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

        // Ponto azul e botão "minha localização" do próprio Google Maps
        mapa.isMyLocationEnabled = true

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let { atualizarPosicaoUsuario(LatLng(it.latitude, it.longitude)) }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
        atualizandoLocalizacao = false
    }

    private fun atualizarPosicaoUsuario(userLatLng: LatLng) {
        val mapa = googleMap ?: return

        // Centraliza o mapa apenas na primeira vez
        if (!isMapCenteredOnUser) {
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

    private fun abrirRotaAPe(lixeira: Lixeira) {
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
    }

    override fun onStart() {
        super.onStart()
        binding.mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        stopLocationUpdates()
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
        binding.mapView.onDestroy()
        googleMap = null
        clusterManager = null
        clusterRenderer = null
        isMapCenteredOnUser = false
        itensPorId.clear()
        _binding = null
    }

    private companion object {
        val SANTOS = LatLng(-23.9670, -46.3300)
    }
}
