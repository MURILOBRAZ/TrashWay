package com.example.trashway.ui.Mapa

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.trashway.R
import com.example.trashway.databinding.ItemLixeiraBinding
import com.google.android.material.color.MaterialColors

class LixeiraAdapter(
    private val onRota: (Lixeira) -> Unit,
    private val onReportar: (Lixeira) -> Unit,
    private val onLixeiraClick: (Lixeira) -> Unit
) : ListAdapter<Lixeira, LixeiraAdapter.LixeiraViewHolder>(DIFF) {

    inner class LixeiraViewHolder(private val binding: ItemLixeiraBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(lixeira: Lixeira, maisProxima: Boolean) {
            binding.textViewNomeLixeira.text = lixeira.nome
            binding.textViewLocalLixeira.text = lixeira.local
            binding.textViewDistancia.text = lixeira.distanciaMetros?.let { formatarDistancia(it) }
                ?: itemView.context.getString(R.string.distancia_desconhecida)

            // A primeira da lista (já ordenada por distância) ganha destaque
            binding.textViewMaisProxima.visibility = if (maisProxima) View.VISIBLE else View.GONE
            val corBorda = if (maisProxima) {
                com.google.android.material.R.attr.colorPrimary
            } else {
                com.google.android.material.R.attr.colorOutlineVariant
            }
            binding.card.strokeColor = MaterialColors.getColor(binding.card, corBorda)
            binding.card.strokeWidth = itemView.resources.displayMetrics.density
                .times(if (maisProxima) 2 else 1).toInt()

            binding.buttonIr.setOnClickListener { onRota(lixeira) }
            binding.buttonReportar.setOnClickListener { onReportar(lixeira) }
            binding.card.setOnClickListener { onLixeiraClick(lixeira) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LixeiraViewHolder {
        val binding = ItemLixeiraBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return LixeiraViewHolder(binding)
    }

    override fun onBindViewHolder(holder: LixeiraViewHolder, position: Int) {
        val lixeira = getItem(position)
        holder.bind(lixeira, maisProxima = position == 0 && lixeira.distanciaMetros != null)
    }

    override fun onCurrentListChanged(previousList: List<Lixeira>, currentList: List<Lixeira>) {
        // O destaque depende da posição: atualiza quem saiu e quem entrou no topo
        if (previousList.firstOrNull()?.id != currentList.firstOrNull()?.id) {
            val antigaPrimeira = currentList.indexOfFirst { it.id == previousList.firstOrNull()?.id }
            if (antigaPrimeira >= 0) notifyItemChanged(antigaPrimeira)
            if (currentList.isNotEmpty()) notifyItemChanged(0)
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<Lixeira>() {
            override fun areItemsTheSame(oldItem: Lixeira, newItem: Lixeira) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Lixeira, newItem: Lixeira) = oldItem == newItem
        }
    }
}
