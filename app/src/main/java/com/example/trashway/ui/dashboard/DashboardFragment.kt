package com.example.trashway.ui.dashboard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.trashway.R
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
import kotlin.io.path.Path
import kotlin.io.path.moveTo

class DashboardFragment : Fragment(), OnMapReadyCallback {

    private lateinit var binding: FragmentDashboardBinding
    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var recyclerView: RecyclerView
    private lateinit var lixeiraAdapter: LixeiraAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val LOCATION_PERMISSION_REQUEST_CODE = 1

    // Lista de Lixeiras
    private val lixeiras = listOf(
        Lixeira("Lixeira N°1", "Complexo esportivo Rebouças", "A 320m", LatLng(-23.9676, -46.3308)),
        Lixeira("Lixeira N°2", "Barca, Travessia Santos-Guarujá", "A 1Km", LatLng(-23.9685, -46.3348)),
        Lixeira("Lixeira N°3", "Av. Bartolomeu de Gusmão", "A 1.2Km", LatLng(-23.9635, -46.3285)),
        Lixeira("Lixeira N°4", "Av. Alm. Cochrane", "A 1.2Km", LatLng(-23.96829, -46.3078433)),
        Lixeira("Lixeira N°5", "Av. Dr. Epitácio Pessoa", "A 1.2Km", LatLng(-23.9786061, -46.3100418))
    )

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
        lixeiraAdapter = LixeiraAdapter(lixeiras, { lixeira ->
            // Ação quando o botão "IR" é clicado
            val gmmIntentUri = Uri.parse("google.navigation:q=${lixeira.latLng.latitude},${lixeira.latLng.longitude}")
            val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
            mapIntent.setPackage("com.google.android.apps.maps")
            startActivity(mapIntent)
        }, { latLng ->
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
        })
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = lixeiraAdapter

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

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val userLatLng = LatLng(it.latitude, it.longitude)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))
                googleMap.addMarker(MarkerOptions().position(userLatLng).title("Você está aqui"))
            } ?: run {
                // Caso não consiga obter a localização
                Toast.makeText(requireContext(), "Não foi possível obter a localização.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun createColoredMarker(): BitmapDescriptor {
        val paint = Paint().apply {
            color = Color.parseColor("#97E5AA") // Sua cor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        // Tamanho do bitmap
        val width = 100
        val height = 150
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Desenha o pino padrão
        val path = android.graphics.Path().apply {
            moveTo(width / 2f, 0f) // Topo do pino
            lineTo(0f, height.toFloat()) // Lado esquerdo
            lineTo(width.toFloat(), height.toFloat()) // Lado direito
            close()
        }
        canvas.drawPath(path, paint)

        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }


    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap

        // Adicionar marcadores ao mapa
        lixeiras.forEach { lixeira ->
            googleMap.addMarker(
                MarkerOptions()
                    .position(lixeira.latLng)
                    .title(lixeira.nome)
            )
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
