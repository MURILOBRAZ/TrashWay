package com.example.trashway.ui.Mapa

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trashway.R

class LixeiraAdapter(
    private val onClick: (Lixeira) -> Unit,
    private val onLixeiraClick: (Lixeira) -> Unit
) : ListAdapter<Lixeira, LixeiraAdapter.LixeiraViewHolder>(DIFF) {

    inner class LixeiraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nomeTextView: TextView = itemView.findViewById(R.id.textViewNomeLixeira)
        private val localTextView: TextView = itemView.findViewById(R.id.textViewLocalLixeira)
        private val distanciaTextView: TextView = itemView.findViewById(R.id.textViewDistancia)
        private val irButton: Button = itemView.findViewById(R.id.buttonIr)
        private val linearLayout: LinearLayout = itemView.findViewById(R.id.linear)

        fun bind(lixeira: Lixeira) {
            nomeTextView.text = lixeira.nome
            localTextView.text = lixeira.local
            distanciaTextView.text = lixeira.distanciaMetros?.let { formatarDistancia(it) }
                ?: itemView.context.getString(R.string.distancia_desconhecida)

            irButton.setOnClickListener { onClick(lixeira) }
            linearLayout.setOnClickListener { onLixeiraClick(lixeira) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LixeiraViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lixeira, parent, false)
        return LixeiraViewHolder(view)
    }

    override fun onBindViewHolder(holder: LixeiraViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Lixeira>() {
            override fun areItemsTheSame(oldItem: Lixeira, newItem: Lixeira) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Lixeira, newItem: Lixeira) = oldItem == newItem
        }
    }
}
