package com.example.trashway.ui.Mapa

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trashway.databinding.FragmentDashboardBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.LatLngBounds

class DashboardFragment : Fragment(), OnMapReadyCallback {

    private lateinit var binding: FragmentDashboardBinding
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var recyclerView: RecyclerView
    private lateinit var lixeiraAdapter: LixeiraAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var userMarker: Marker? = null
    private var isMapCenteredOnUser = false // Flag para centralizar apenas uma vez

    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private val lixeiraViewModel: LixeiraViewModel by activityViewModels()
    private val markerLixeiraMap = mutableMapOf<Marker, Lixeira>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 2000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    val userLatLng = LatLng(location.latitude, location.longitude)

                    if (userMarker == null) {
                        userMarker = googleMap.addMarker(
                            MarkerOptions()
                                .position(userLatLng)
                                .title("Você está aqui")
                                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        )
                    } else {
                        userMarker?.position = userLatLng
                    }

                    // Centraliza o mapa apenas na primeira vez
                    if (!isMapCenteredOnUser) {
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                        isMapCenteredOnUser = true // Define como centralizado
                    }

                    lixeiraViewModel.userLocation = userLatLng
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentDashboardBinding.inflate(inflater, container, false)

        mapView = binding.mapView
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        recyclerView = binding.recyclerViewLixeiras
        recyclerView.layoutManager = LinearLayoutManager(context)

        lixeiraAdapter = LixeiraAdapter(
            lixeiras = emptyList(),
            onClick = { lixeira ->
                val gmmIntentUri = Uri.parse("google.navigation:q=${lixeira.latLng.latitude},${lixeira.latLng.longitude}&mode=w")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            },
            onLixeiraClick = { latLng ->
                val marker = markerLixeiraMap.entries.firstOrNull { it.value.latLng == latLng }?.key
                marker?.let {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    it.showInfoWindow()
                }
            }
        )
        recyclerView.adapter = lixeiraAdapter

        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner) { lixeiras ->
            lixeiraAdapter.updateLixeiras(lixeiras)
        }

        requestLocationPermissions()
        return binding.root
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            startLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissions()
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        googleMap.setOnMarkerClickListener { marker ->
            val lixeira = markerLixeiraMap[marker]
            lixeira?.let {
                val position = lixeiraViewModel.lixeiras.value?.indexOf(it) ?: -1
                if (position != -1) {
                    recyclerView.scrollToPosition(position)
                }
                marker.showInfoWindow()
            }
            true
        }

        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner) { lixeiras ->
            if (lixeiras.isNotEmpty()) {
                markerLixeiraMap.clear()
                lixeiras.forEach { lixeira ->
                    val marker = googleMap.addMarker(
                        MarkerOptions()
                            .position(lixeira.latLng)
                            .title(lixeira.nome)
                            .icon(BitmapDescriptorFactory.defaultMarker(157.68f))
                    )
                    marker?.let { markerLixeiraMap[marker] = lixeira }
                }

                val boundsBuilder = LatLngBounds.builder()
                lixeiras.forEach { lixeira ->
                    boundsBuilder.include(lixeira.latLng)
                }
            } else {
                Log.d("DashboardFragment", "Nenhuma lixeira encontrada.")
            }
        }

        getCurrentLocation()
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val userLatLng = LatLng(it.latitude, it.longitude)
                if (!isMapCenteredOnUser) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                    isMapCenteredOnUser = true
                }

                userMarker = googleMap.addMarker(
                    MarkerOptions()
                        .position(userLatLng)
                        .title("Você está aqui")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                )

                lixeiraViewModel.userLocation = userLatLng
            } ?: run {
                Toast.makeText(requireContext(), "Não foi possível obter a localização.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        stopLocationUpdates()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLocationUpdates()
            } else {
                Toast.makeText(requireContext(), "Permissão de localização negada.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
