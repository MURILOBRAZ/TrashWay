package com.example.trashway

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.trashway.ui.dashboard.Lixeira
import com.google.android.gms.maps.model.LatLng

class LixeiraViewModel : ViewModel(){

    // Lista de Lixeiras
    val _lixeiras = MutableLiveData<List<Lixeira>>()
    val lixeiras: LiveData<List<Lixeira>> get() = _lixeiras

    init{
        _lixeiras.value = listOf(
            Lixeira("Lixeira N°1", "Complexo esportivo Rebouças", "A 320m", LatLng(-23.9676, -46.3308)),
            Lixeira("Lixeira N°2", "Barca, Travessia Santos-Guarujá", "A 1Km", LatLng(-23.9685, -46.3348)),
            Lixeira("Lixeira N°3", "Av. Bartolomeu de Gusmão", "A 1.2Km", LatLng(-23.9635, -46.3285)),
            Lixeira("Lixeira N°4", "Av. Alm. Cochrane", "A 1.2Km", LatLng(-23.96829, -46.3078433)),
            Lixeira("Lixeira N°5", "Av. Dr. Epitácio Pessoa", "A 1.2Km", LatLng(-23.9786061, -46.3100418))

        )
    }

}
