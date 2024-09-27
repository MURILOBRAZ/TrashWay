package com.example.trashway.ui.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
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
import com.example.trashway.LixeiraViewModel
import com.example.trashway.databinding.FragmentDashboardBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class DashboardFragment : Fragment(), OnMapReadyCallback {

    private lateinit var binding: FragmentDashboardBinding
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var recyclerView: RecyclerView
    private lateinit var lixeiraAdapter: LixeiraAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    private val lixeiraViewModel: LixeiraViewModel by activityViewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())
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

        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner) { lixeiras ->
            lixeiraAdapter = LixeiraAdapter(lixeiras, { lixeira ->
                // Ação quando o botão "IR" é clicado
                val gmmIntentUri = Uri.parse("google.navigation:q=${lixeira.latLng.latitude},${lixeira.latLng.longitude}&mode=w")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")
                startActivity(mapIntent)
            }, { latLng ->
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            })
            recyclerView.layoutManager = LinearLayoutManager(context)
            recyclerView.adapter = lixeiraAdapter
        }

        requestLocationPermissions() // Solicitar permissões de localização

        return binding.root
    }

    private fun requestLocationPermissions() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            getCurrentLocation()
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        val color = Color.rgb(255, 201, 14)
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val userLatLng = LatLng(it.latitude, it.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                googleMap.addMarker(
                    MarkerOptions()
                        .position(userLatLng)
                        .title("Você está aqui")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)) // Marca o usuário com a cor padrão azul
                )
            } ?: run {
                // Caso não consiga obter a localização
                Toast.makeText(requireContext(), "Não foi possível obter a localização.", Toast.LENGTH_SHORT).show()
            }
        }
    }


    private fun createColoredMarker(): BitmapDescriptor {
        return BitmapDescriptorFactory.defaultMarker(157.68f) // Define HUE de 135
    }


    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        // Adicionar marcadores ao mapa
        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner) { lixeiras ->
            lixeiras.forEach { lixeira ->
                googleMap.addMarker(
                    MarkerOptions()
                        .position(lixeira.latLng)
                        .title(lixeira.nome)
                        .icon(createColoredMarker())
                )
            }
        }

        // Tente obter a localização atual do usuário
        getCurrentLocation()
    }

    // Outros métodos para o ciclo de vida do MapView
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Toast.makeText(requireContext(), "Permissão de localização negada.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
