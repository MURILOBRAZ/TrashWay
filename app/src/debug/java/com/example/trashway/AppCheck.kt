package com.example.trashway

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

// Builds de debug: o token de debug aparece no Logcat (tag DebugAppCheckProvider)
// e precisa ser cadastrado no console do Firebase > App Check.
fun instalarAppCheck() {
    Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
