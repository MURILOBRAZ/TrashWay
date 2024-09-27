package com.example.trashway.ui.notifications

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.trashway.LixeiraViewModel
import com.example.trashway.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var lixeiraViewModel: LixeiraViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val notificationsViewModel =
            ViewModelProvider(this).get(NotificationsViewModel::class.java)

        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Inicializa o ViewModel para lixeiras
        lixeiraViewModel = ViewModelProvider(requireActivity()).get(LixeiraViewModel::class.java)

        // Configura o AutoCompleteTextView
        val Spinner : Spinner = binding.SpinnerLixeira

        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner, Observer { lixeiras ->
            // Log para verificar se a lista está sendo recebida
            Log.d("NotificationsFragment", "Lixeiras recebidas: $lixeiras")

            // Mapeia os nomes das lixeiras para a lista do adaptador
            val lixeiraNames = lixeiras.map { it.nome }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lixeiraNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            Spinner.adapter = adapter
        })

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
