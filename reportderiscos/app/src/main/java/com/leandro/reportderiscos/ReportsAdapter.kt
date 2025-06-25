package com.leandro.reportderiscos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class ReportsAdapter(private var items: List<RiskReport>)
    : RecyclerView.Adapter<ReportsAdapter.ViewHolder>() {

    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgReport)
        val title: TextView = view.findViewById(R.id.txtTitle)
        val desc: TextView = view.findViewById(R.id.txtDesc)
        val location: TextView = view.findViewById(R.id.txtLocation)
        val status: TextView = view.findViewById(R.id.txtStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_report, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
        val r = items[pos]
        val context = holder.itemView.context

        holder.title.text = r.title
        holder.desc.text = r.description
        holder.location.text = r.location


        // danilo - correção: r.status eh nao nulo, ent o operador Elvis n é necessario.
        val statusText = r.status
        holder.status.text = context.getString(R.string.status_label_adapter, statusText)


        when (statusText) {
            "Resolvido" -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_resolvido_app1))
            "Em Andamento", "Em Análise" -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_em_progresso_app1))
            "Inválido" -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_invalido_app1))
            "Aberto" -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_aberto_app1))
            else -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_desconhecido_app1)) // Para "Desconhecido" ou outros valores
        }

        Glide.with(holder.img.context)
            .load(r.imageUrl)
            .placeholder(R.drawable.ic_image_placeholder_app1)
            .error(R.drawable.ic_image_error_app1)
            .into(holder.img)
    }

    override fun getItemCount() = items.size

    fun updateList(newItems: List<RiskReport>) {
        items = newItems
        notifyDataSetChanged()
    }
}