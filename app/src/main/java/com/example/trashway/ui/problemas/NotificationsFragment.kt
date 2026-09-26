package com.example.trashway.ui.problemas

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.trashway.R
import com.example.trashway.databinding.FragmentNotificationsBinding
import com.example.trashway.ui.Mapa.Lixeira
import com.example.trashway.ui.Mapa.LixeiraViewModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private val binding get() = _binding!!

    private val lixeiraViewModel: LixeiraViewModel by activityViewModels()
    private val db = FirebaseFirestore.getInstance() // Inicializa o Firestore

    // Ids das lixeiras no campo de busca, para só recriar o adapter quando o conjunto muda
    private var idsNaBusca: List<String> = emptyList()
    private var selecionada: Lixeira? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)

        binding.autoCompleteLixeira.setOnItemClickListener { parent, _, position, _ ->
            val opcao = parent.getItemAtPosition(position) as OpcaoLixeira
            selecionar(opcao.lixeira)
        }
        // Se o usuário editar o texto depois de escolher, a escolha deixa de valer
        binding.autoCompleteLixeira.doAfterTextChanged { texto ->
            val atual = selecionada
            if (atual != null && texto?.toString() != OpcaoLixeira(atual).toString()) {
                selecionada = null
            }
        }

        lixeiraViewModel.lixeiras.observe(viewLifecycleOwner) { lixeiras ->
            atualizarOpcoes(lixeiras)
            preSelecionar(lixeiras)
        }
        lixeiraViewModel.lixeiraParaReportar.observe(viewLifecycleOwner) { id ->
            val lixeira = lixeiraViewModel.lixeiras.value?.firstOrNull { it.id == id } ?: return@observe
            selecionar(lixeira)
            lixeiraViewModel.reporteConsumido()
        }

        binding.buttonEnviar.setOnClickListener { enviarProblema() }

        return binding.root
    }

    private fun atualizarOpcoes(lixeiras: List<Lixeira>) {
        // Ordem alfabética fixa: a lista do ViewModel muda de ordem conforme a distância
        val ordenadas = lixeiras.sortedBy { it.nome }
        val ids = ordenadas.map { it.id }
        if (ids == idsNaBusca) return
        idsNaBusca = ids
        binding.autoCompleteLixeira.setAdapter(
            LixeiraBuscaAdapter(requireContext(), ordenadas.map { OpcaoLixeira(it) })
        )
    }

    // Sem escolha do usuário, já sugere a lixeira mais próxima
    private fun preSelecionar(lixeiras: List<Lixeira>) {
        if (selecionada != null || !binding.autoCompleteLixeira.text.isNullOrEmpty()) return
        val maisProxima = lixeiras.firstOrNull()?.takeIf { it.distanciaMetros != null } ?: return
        selecionar(maisProxima)
    }

    private fun selecionar(lixeira: Lixeira) {
        selecionada = lixeira
        // filter = false: preenche sem abrir a lista de sugestões
        binding.autoCompleteLixeira.setText(OpcaoLixeira(lixeira).toString(), false)
        binding.inputLixeira.error = null
    }

    private fun enviarProblema() {
        val lixeira = selecionada
        if (lixeira == null) {
            binding.inputLixeira.error = getString(R.string.erro_selecione_lixeira)
            return
        }

        val presente = binding.togglePresente.checkedButtonId
        val quebrada = binding.toggleQuebrada.checkedButtonId
        if (presente == View.NO_ID || quebrada == View.NO_ID) {
            avisar(R.string.erro_responda_perguntas)
            return
        }

        val problemaReportado = mapOf(
            "lixeiraId" to lixeira.id,
            "nomeLixeira" to lixeira.nome,
            "lixeiraPresente" to (if (presente == R.id.buttonPresenteSim) "Sim" else "Não"),
            "lixeiraQuebrada" to (if (quebrada == R.id.buttonQuebradaSim) "Sim" else "Não"),
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
                avisar(R.string.problema_enviado)
            }
            .addOnFailureListener { e ->
                Log.w("NotificationsFragment", "Erro ao enviar problema", e)
                val b = _binding ?: return@addOnFailureListener
                b.buttonEnviar.isEnabled = true
                avisar(R.string.problema_falhou)
            }
    }

    private fun avisar(@StringRes mensagem: Int) {
        val b = _binding ?: return
        Snackbar.make(b.root, mensagem, Snackbar.LENGTH_LONG)
            .setAnchorView(requireActivity().findViewById(R.id.nav_view))
            .show()
    }

    private fun limparCampos() {
        selecionada = null
        binding.autoCompleteLixeira.setText("", false)
        binding.togglePresente.clearChecked()
        binding.toggleQuebrada.clearChecked()
        binding.editTextProblem.text?.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        idsNaBusca = emptyList()
        selecionada = null
    }
}
