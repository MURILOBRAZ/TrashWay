package com.example.trashway.ui.problemas

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.trashway.R
import com.example.trashway.databinding.FragmentNotificationsBinding
import com.example.trashway.ui.Mapa.Lixeira
import com.example.trashway.ui.Mapa.LixeiraViewModel
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val lixeiraViewModel: LixeiraViewModel by activityViewModels()
    private val db = FirebaseFirestore.getInstance() // Inicializa o Firestore

    // Lixeiras exibidas no Spinner, na mesma ordem (a posição 0 é o "Selecione...")
    private var lixeirasNoSpinner: List<Lixeira> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)

        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner) { lixeiras ->
            // Ordem alfabética fixa: a lista do ViewModel muda de ordem conforme a distância
            val ordenadas = lixeiras.sortedBy { it.nome }
            // Só recria o adapter se o conjunto mudou, para não perder a seleção do usuário
            if (ordenadas.map { it.id } == lixeirasNoSpinner.map { it.id }) return@observe
            lixeirasNoSpinner = ordenadas

            val nomes = listOf(getString(R.string.selecione_lixeira)) + ordenadas.map { it.nome }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, nomes)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.SpinnerLixeira.adapter = adapter
        }

        binding.buttonEnviar.setOnClickListener { enviarProblema() }

        return binding.root
    }

    private fun enviarProblema() {
        val posicao = binding.SpinnerLixeira.selectedItemPosition
        val lixeira = lixeirasNoSpinner.getOrNull(posicao - 1)
        if (lixeira == null) {
            Toast.makeText(requireContext(), R.string.erro_selecione_lixeira, Toast.LENGTH_SHORT).show()
            return
        }

        val presente = binding.radioGroup1.checkedRadioButtonId
        val quebrada = binding.radioGroup2.checkedRadioButtonId
        if (presente == View.NO_ID || quebrada == View.NO_ID) {
            Toast.makeText(requireContext(), R.string.erro_responda_perguntas, Toast.LENGTH_SHORT).show()
            return
        }

        val problemaReportado = mapOf(
            "lixeiraId" to lixeira.id,
            "nomeLixeira" to lixeira.nome,
            "lixeiraPresente" to (if (presente == R.id.radioButton1_1) "Sim" else "Não"),
            "lixeiraQuebrada" to (if (quebrada == R.id.radioButton2_1) "Sim" else "Não"),
            "outroProblema" to binding.editTextProblem.text.toString().trim(),
            "criadoEm" to FieldValue.serverTimestamp()
        )

        // Evita envios duplicados enquanto o primeiro não termina
        binding.buttonEnviar.isEnabled = false

        db.collection("Problemas")
            .add(problemaReportado)
            .addOnSuccessListener { documentReference ->
                Log.d("NotificationsFragment", "Problema enviado com ID: ${documentReference.id}")
                val b = _binding ?: return@addOnSuccessListener
                b.buttonEnviar.isEnabled = true
                limparCampos()
                Toast.makeText(b.root.context, R.string.problema_enviado, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.w("NotificationsFragment", "Erro ao enviar problema", e)
                val b = _binding ?: return@addOnFailureListener
                b.buttonEnviar.isEnabled = true
                Toast.makeText(b.root.context, R.string.problema_falhou, Toast.LENGTH_SHORT).show()
            }
    }

    private fun limparCampos() {
        binding.SpinnerLixeira.setSelection(0)
        binding.radioGroup1.clearCheck()
        binding.radioGroup2.clearCheck()
        binding.editTextProblem.text.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        lixeirasNoSpinner = emptyList()
    }
}
