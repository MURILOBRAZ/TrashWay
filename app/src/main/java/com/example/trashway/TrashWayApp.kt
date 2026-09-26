package com.example.trashway

import android.app.Application
import com.google.firebase.FirebaseApp

class TrashWayApp : Application() {

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        // Play Integrity no release; provedor de debug nos builds de debug (ver src/debug e src/release)
        instalarAppCheck()
    }
}
