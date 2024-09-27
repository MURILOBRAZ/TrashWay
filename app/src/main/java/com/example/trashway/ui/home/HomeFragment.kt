package com.example.trashway.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.example.trashway.R
import com.example.trashway.databinding.FragmentHomeBinding


class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val homeViewModel: HomeViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Aqui você pode usar o ViewModel para atualizar a UI, se necessário
        binding.textViewWelcome.text = homeViewModel.text.value

        binding.buttonProcurarLixeiras.setOnClickListener {
            findNavController().navigate(R.id.navigation_dashboard) // Navega para a tela do dashboard
        }

        binding.buttonReportarProblema.setOnClickListener {
            findNavController().navigate(R.id.navigation_notifications) // Navega para a tela de notificações
        }

    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
