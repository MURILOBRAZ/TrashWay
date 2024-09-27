package com.example.trashway.ui.dashboard

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
    private val lixeiras: List<Lixeira>,           // Lista de lixeiras
    private val onClick: (Lixeira) -> Unit,        // Ação quando o botão "IR" for clicado
    private val onLixeiraClick: (LatLng) -> Unit    // Ação quando o LinearLayout for clicado
) : RecyclerView.Adapter<LixeiraAdapter.LixeiraViewHolder>() {

    inner class LixeiraViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nomeTextView: TextView = itemView.findViewById(R.id.textViewNomeLixeira)
        val localTextView: TextView = itemView.findViewById(R.id.textViewLocalLixeira)
        val distanciaTextView: TextView = itemView.findViewById(R.id.textViewDistancia)
        val irButton: Button = itemView.findViewById(R.id.buttonIr)
        val linearLayout: LinearLayout = itemView.findViewById(R.id.linear) // Certifique-se de que o ID está correto

        fun bind(lixeira: Lixeira) {
            nomeTextView.text = lixeira.nome
            localTextView.text = lixeira.local
            distanciaTextView.text = lixeira.distancia

            irButton.setOnClickListener {
                onClick(lixeira)  // Executa a ação quando o botão "IR" é clicado
            }

            linearLayout.setOnClickListener {
                onLixeiraClick(lixeira.latLng) // Centraliza o mapa ao clicar no LinearLayout
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
}