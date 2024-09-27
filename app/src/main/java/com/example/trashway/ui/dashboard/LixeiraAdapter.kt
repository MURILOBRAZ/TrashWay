package com.example.trashway.ui.dashboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.trashway.R

class LixeiraAdapter(
    private val lixeiras: List<Lixeira>,           // Lista de lixeiras
    private val onClick: (Lixeira) -> Unit         // Ação quando o botão "IR" for clicado
) : RecyclerView.Adapter<LixeiraAdapter.LixeiraViewHolder>() {

    // ViewHolder: contém as views para cada item (lixeira) da lista
    inner class LixeiraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nomeTextView: TextView = itemView.findViewById(R.id.textViewNomeLixeira)
        val localTextView: TextView = itemView.findViewById(R.id.textViewLocalLixeira)
        val distanciaTextView: TextView = itemView.findViewById(R.id.textViewDistancia)
        val irButton: Button = itemView.findViewById(R.id.buttonIr)

        // Método que "liga" os dados da lixeira com o layout
        fun bind(lixeira: Lixeira) {
            nomeTextView.text = lixeira.nome
            localTextView.text = lixeira.local
            distanciaTextView.text = lixeira.distancia
            irButton.setOnClickListener {
                onClick(lixeira)  // Executa a ação quando o botão "IR" é clicado
            }
        }
    }

    // Cria a ViewHolder quando o item da lista é mostrado pela primeira vez
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LixeiraViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lixeira, parent, false)
        return LixeiraViewHolder(view)
    }

    // "Liga" os dados da lixeira com a ViewHolder
    override fun onBindViewHolder(holder: LixeiraViewHolder, position: Int) {
        holder.bind(lixeiras[position])
    }

    // Retorna o número total de itens na lista
    override fun getItemCount() = lixeiras.size
}
