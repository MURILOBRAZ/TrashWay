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
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

class DashboardFragment : Fragment(), OnMapReadyCallback {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private var googleMap: GoogleMap? = null
    private lateinit var lixeiraAdapter: LixeiraAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userMarker: Marker? = null
    private var isMapCenteredOnUser = false // Flag para centralizar apenas uma vez
    private var atualizandoLocalizacao = false

    private val lixeiraViewModel: LixeiraViewModel by activityViewModels()

    // Marcadores das lixeiras, pelo id do documento no Firestore
    private val marcadoresPorId = mutableMapOf<String, Marker>()

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
            Toast.makeText(requireContext(), R.string.permissao_negada, Toast.LENGTH_SHORT).show()
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

        lixeiraAdapter = LixeiraAdapter(
            onClick = ::abrirRotaAPe,
            onLixeiraClick = { lixeira ->
                marcadoresPorId[lixeira.id]?.let {
                    googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(lixeira.latLng, 15f))
                    it.showInfoWindow()
                }
            }
        )
        binding.recyclerViewLixeiras.layoutManager = LinearLayoutManager(context)
        binding.recyclerViewLixeiras.adapter = lixeiraAdapter

        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner) { lixeiras ->
            lixeiraAdapter.submitList(lixeiras)
            sincronizarMarcadores(lixeiras)
        }
        lixeiraViewModel.erroCarregamento.observe(viewLifecycleOwner) { erro ->
            if (erro) {
                Toast.makeText(requireContext(), R.string.erro_carregar_lixeiras, Toast.LENGTH_LONG).show()
            }
        }

        if (!temPermissaoLocalizacao()) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
        return binding.root
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
        if (googleMap == null || atualizandoLocalizacao || !temPermissaoLocalizacao()) return
        atualizandoLocalizacao = true

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

        if (userMarker == null) {
            userMarker = mapa.addMarker(
                MarkerOptions()
                    .position(userLatLng)
                    .title(getString(R.string.voce_esta_aqui))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        } else {
            userMarker?.position = userLatLng
        }

        // Centraliza o mapa apenas na primeira vez
        if (!isMapCenteredOnUser) {
            mapa.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
            isMapCenteredOnUser = true
        }

        lixeiraViewModel.userLocation = userLatLng
    }

    // Adiciona só os marcadores que ainda não existem e remove os que saíram da lista.
    // A lista é reemitida a cada mudança de distância, então não dá para recriar tudo.
    private fun sincronizarMarcadores(lixeiras: List<Lixeira>) {
        val mapa = googleMap ?: return
        val ids = lixeiras.mapTo(HashSet()) { it.id }

        marcadoresPorId.entries.removeAll { (id, marker) ->
            (id !in ids).also { removido -> if (removido) marker.remove() }
        }

        lixeiras.filter { it.id !in marcadoresPorId }.forEach { lixeira ->
            mapa.addMarker(
                MarkerOptions()
                    .position(lixeira.latLng)
                    .title(lixeira.nome)
                    .icon(BitmapDescriptorFactory.defaultMarker(157.68f))
            )?.let { marker ->
                marker.tag = lixeira.id
                marcadoresPorId[lixeira.id] = marker
            }
        }
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

        googleMap.setOnMarkerClickListener { marker ->
            val id = marker.tag as? String
            if (id != null) {
                val position = lixeiraAdapter.currentList.indexOfFirst { it.id == id }
                if (position != -1) {
                    binding.recyclerViewLixeiras.scrollToPosition(position)
                }
            }
            marker.showInfoWindow()
            true
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
        userMarker = null
        isMapCenteredOnUser = false
        marcadoresPorId.clear()
        _binding = null
    }
}
