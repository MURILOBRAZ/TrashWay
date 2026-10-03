package com.example.trashway.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.trashway.MainActivity
import com.example.trashway.R
import com.example.trashway.databinding.FragmentHomeBinding
import com.example.trashway.ui.Mapa.NavegacaoViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val navegacaoViewModel: NavegacaoViewModel by activityViewModels()

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

        val main = requireActivity() as MainActivity
        binding.buttonIrMaisProxima.setOnClickListener {
            // O mapa começa a navegação assim que souber qual lixeira é a mais próxima
            navegacaoViewModel.irParaMaisProxima()
            main.irParaAba(R.id.navigation_dashboard)
        }
        binding.buttonProcurar.setOnClickListener { main.irParaAba(R.id.navigation_dashboard) }
        binding.buttonReportar.setOnClickListener { main.irParaAba(R.id.navigation_notifications) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
