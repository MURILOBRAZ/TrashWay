package com.example.trashway.ui.problemas

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.trashway.ui.Mapa.LixeiraViewModel
import com.example.trashway.databinding.FragmentNotificationsBinding
import com.google.firebase.firestore.FirebaseFirestore

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private lateinit var lixeiraViewModel: LixeiraViewModel
    private val db = FirebaseFirestore.getInstance() // Inicializa o Firestore

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root

        // Inicializa o ViewModel para lixeiras
        lixeiraViewModel = ViewModelProvider(requireActivity()).get(LixeiraViewModel::class.java)

        // Configura o Spinner
        val spinner: Spinner = binding.SpinnerLixeira

        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner, Observer { lixeiras ->
            Log.d("NotificationsFragment", "Lixeiras recebidas: $lixeiras")

            val lixeiraNames = lixeiras.map { it.nome }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, lixeiraNames)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            spinner.adapter = adapter
        })

        // Configura o botão de enviar
        val buttonEnviar: Button = binding.buttonEnviar
        buttonEnviar.setOnClickListener {
            enviarProblema()
        }

        return root
    }

    private fun enviarProblema() {
        val nomeLixeira = binding.SpinnerLixeira.selectedItem.toString()
        val problema1 = binding.radioGroup1.checkedRadioButtonId
        val problema2 = binding.radioGroup2.checkedRadioButtonId
        val outroProblema = binding.editTextProblem.text.toString()

        val problemaReportado = mapOf(
            "nomeLixeira" to nomeLixeira,
            "lixeiraPresente" to (if (problema1 == binding.radioGroup1.id) "Sim" else "Não"),
            "lixeiraQuebrada" to (if (problema2 == binding.radioGroup2.id) "Sim" else "Não"),
            "outroProblema" to outroProblema
        )

        // Envio dos dados para o Firestore
        db.collection("Problemas") // A coleção que você definiu nas regras
            .add(problemaReportado)
            .addOnSuccessListener { documentReference ->
                Log.d("NotificationsFragment", "Problema enviado com ID: ${documentReference.id}")

                // Limpa os campos após o envio
                limparCampos()

                // Exibe uma mensagem de confirmação
                Toast.makeText(requireContext(), "Problema enviado com sucesso!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.w("NotificationsFragment", "Erro ao enviar problema", e)
                Toast.makeText(requireContext(), "Falha ao enviar o problema.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun limparCampos() {
        binding.SpinnerLixeira.setSelection(0) // Reseta o Spinner para o primeiro item
        binding.radioGroup1.clearCheck() // Limpa a seleção do primeiro RadioGroup
        binding.radioGroup2.clearCheck() // Limpa a seleção do segundo RadioGroup
        binding.editTextProblem.text.clear() // Limpa o EditText
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
