package com.example.trashway.ui.Mapa

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.trashway.R
import com.google.android.gms.maps.model.LatLng

class LixeiraAdapter(
    private var lixeiras: List<Lixeira>,
    private val onClick: (Lixeira) -> Unit,
    private val onLixeiraClick: (LatLng) -> Unit
) : RecyclerView.Adapter<LixeiraAdapter.LixeiraViewHolder>() {

    inner class LixeiraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nomeTextView: TextView = itemView.findViewById(R.id.textViewNomeLixeira)
        val localTextView: TextView = itemView.findViewById(R.id.textViewLocalLixeira)
        val distanciaTextView: TextView = itemView.findViewById(R.id.textViewDistancia)
        val irButton: Button = itemView.findViewById(R.id.buttonIr)
        val linearLayout: LinearLayout = itemView.findViewById(R.id.linear)

        fun bind(lixeira: Lixeira) {
            nomeTextView.text = lixeira.nome
            localTextView.text = lixeira.local
            distanciaTextView.text = lixeira.distancia

            irButton.setOnClickListener {
                onClick(lixeira)
            }

            linearLayout.setOnClickListener {
                onLixeiraClick(lixeira.latLng)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LixeiraViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_lixeira, parent, false)
        return LixeiraViewHolder(view)
    }

    override fun onBindViewHolder(holder: LixeiraViewHolder, position: Int) {
        holder.bind(lixeiras[position])
    }

    override fun getItemCount() = lixeiras.size

    // Método para atualizar apenas a lista de dados
    fun updateLixeiras(newLixeiras: List<Lixeira>) {
        lixeiras = newLixeiras
        notifyDataSetChanged() // Notifica o adapter para atualizar a visualização
    }
}

