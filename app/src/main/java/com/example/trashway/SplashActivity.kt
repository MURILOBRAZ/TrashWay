package com.example.trashway

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val handler = Handler(Looper.getMainLooper())
    private val abrirMain = Runnable {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)
        supportActionBar?.hide()

        // Exibe a tela de abertura por 1,5 segundo antes de iniciar a MainActivity
        handler.postDelayed(abrirMain, 1500)
    }

    override fun onDestroy() {
        // Se o usuário sair durante a abertura, não abre a MainActivity depois
        handler.removeCallbacks(abrirMain)
        super.onDestroy()
    }
}
