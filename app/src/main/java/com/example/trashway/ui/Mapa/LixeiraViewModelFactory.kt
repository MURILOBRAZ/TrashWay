package com.example.trashway

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.trashway.ui.Mapa.LixeiraViewModel
import com.google.firebase.firestore.FirebaseFirestore

class LixeiraViewModelFactory(private val db: FirebaseFirestore) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") // Suprimir aviso de cast inseguro
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LixeiraViewModel::class.java)) {
            return LixeiraViewModel(db) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
