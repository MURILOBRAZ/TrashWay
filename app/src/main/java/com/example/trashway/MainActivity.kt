package com.example.trashway

import android.os.Bundle
import android.view.View
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.trashway.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Tela de abertura do sistema (Theme.TrashWay.Starting), antes de desenhar o app
        installSplashScreen()
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // A localização do usuário é tratada pelo DashboardFragment (tela do mapa)
        val navHost = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment_activity_main) as NavHostFragment
        binding.navView.setupWithNavController(navHost.navController)
    }

    // Esconde a barra de abas durante a navegação, para o mapa ocupar a tela toda
    fun mostrarBarraInferior(visivel: Boolean) {
        binding.navView.visibility = if (visivel) View.VISIBLE else View.GONE
    }

    // Troca de aba como se o usuário tocasse na barra inferior
    fun irParaAba(@IdRes destino: Int) {
        binding.navView.selectedItemId = destino
    }
}
