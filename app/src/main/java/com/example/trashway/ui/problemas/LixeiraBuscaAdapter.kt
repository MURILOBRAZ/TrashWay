package com.example.trashway.ui.problemas

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Filter
import com.example.trashway.ui.Mapa.Lixeira
import java.text.Normalizer

// Opção da lista de busca: o texto exibido é "Lixeira N°01 · Av. Conselheiro Nébias 839"
class OpcaoLixeira(val lixeira: Lixeira) {
    override fun toString() =
        if (lixeira.local.isBlank()) lixeira.nome else "${lixeira.nome} · ${lixeira.local}"
}

// Filtra por qualquer trecho do nome ou do endereço, sem diferenciar maiúsculas e acentos.
// (O filtro padrão do ArrayAdapter só compara o começo das palavras: "01" não acharia "N°01".)
class LixeiraBuscaAdapter(
    context: Context,
    private val todas: List<OpcaoLixeira>
) : ArrayAdapter<OpcaoLixeira>(context, android.R.layout.simple_dropdown_item_1line, ArrayList(todas)) {

    private val filtro = object : Filter() {
        override fun performFiltering(texto: CharSequence?): FilterResults {
            val busca = normalizar(texto?.toString().orEmpty())
            val encontradas = if (busca.isBlank()) todas else todas.filter { normalizar(it.toString()).contains(busca) }
            return FilterResults().apply {
                values = encontradas
                count = encontradas.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(texto: CharSequence?, resultado: FilterResults) {
            clear()
            addAll(resultado.values as List<OpcaoLixeira>)
            notifyDataSetChanged()
        }

        override fun convertResultToString(resultado: Any?) = resultado.toString()
    }

    override fun getFilter(): Filter = filtro

    private fun normalizar(texto: String) =
        Normalizer.normalize(texto.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").trim()
}
