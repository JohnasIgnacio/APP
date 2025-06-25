package com.leandro.manageriscos

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportAdapter(
    private var reports: List<Report>,
    private val onItemClicked: (Report) -> Unit,
    private val onImageClicked: (String?) -> Unit
) : RecyclerView.Adapter<ReportAdapter.ReportViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReportViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_report, parent, false)
        return ReportViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReportViewHolder, position: Int) {
        val report = reports[position]
        holder.bind(report)
    }

    override fun getItemCount(): Int = reports.size

    fun updateData(newReports: List<Report>) {
        reports = newReports
        notifyDataSetChanged()
    }

    inner class ReportViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleTextView: TextView = itemView.findViewById(R.id.tvReportTitle)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.tvReportDescription)
        private val locationTextView: TextView = itemView.findViewById(R.id.tvReportLocation)
        private val dateTextView: TextView = itemView.findViewById(R.id.tvReportDate)
        private val imageView: ImageView = itemView.findViewById(R.id.ivReportImage)
        private val statusTextView: TextView = itemView.findViewById(R.id.tvReportStatus)

        fun bind(report: Report) {
            val context = itemView.context

            titleTextView.text = report.title ?: context.getString(R.string.sem_titulo)
            descriptionTextView.text = report.description ?: context.getString(R.string.sem_descricao)
            locationTextView.text = report.location ?: context.getString(R.string.localizacao_nao_informada)

            report.reportDate?.let { timestamp ->
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                dateTextView.text = context.getString(R.string.formato_data_hora, sdf.format(Date(timestamp)))
            } ?: run {
                dateTextView.text = context.getString(R.string.data_nao_informada)
            }

            val statusDesc = report.status ?: StatusRisco.ABERTO.descricao
            statusTextView.text = context.getString(R.string.status_label, statusDesc)

            when (StatusRisco.fromDescricao(report.status)) {
                StatusRisco.RESOLVIDO -> statusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_resolvido))
                StatusRisco.EM_ANDAMENTO -> statusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_em_andamento))
                StatusRisco.EM_ANALISE -> statusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_em_analise))
                StatusRisco.INVALIDO -> statusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_invalido))
                StatusRisco.ABERTO -> statusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_aberto))
                null -> statusTextView.setTextColor(ContextCompat.getColor(context, R.color.status_desconhecido))
            }

            Glide.with(context)
                .load(report.imageUrl)
                .placeholder(R.drawable.ic_image_placeholder)
                .error(R.drawable.ic_image_error)
                .centerCrop()
                .into(imageView)

            itemView.setOnClickListener {
                onItemClicked(report)
            }

            imageView.setOnClickListener {
                onImageClicked(report.imageUrl)
            }
        }
    }
}