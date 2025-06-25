package com.leandro.manageriscos

import android.app.DatePickerDialog
import android.content.Intent
// import android.graphics.Color // nao usado diretamente
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.GridLayout // importacao para o gridlayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.leandro.manageriscos.databinding.ActivityEstatisticasBinding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class EstatisticasActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEstatisticasBinding
    private lateinit var database: FirebaseDatabase
    private val allReportsList = mutableListOf<Report>() // lista com todos os reports do firebase
    private var filteredReportsList = mutableListOf<Report>() // lista de reports apos filtros

    // variaveis para os filtros selecionados
    private var selectedStartDate: Calendar? = null
    private var selectedEndDate: Calendar? = null
    private var selectedStatus: String? = null
    private var selectedUserId: String? = null

    private lateinit var lastReportsAdapter: LastReportsAdapter
    private val distinctUserIds = mutableSetOf<String>() // armazena ids unicos de usuarios

    companion object {
        private const val TAG = "EstatisticasActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEstatisticasBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // configura a actionbar
        setSupportActionBar(binding.toolbarEstatisticas)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.titulo_estatisticas_relatorios)

        database = FirebaseDatabase.getInstance("https://report-de-riscos-default-rtdb.firebaseio.com/")

        setupFiltersUI()
        setupLastReportsRecyclerView()
        fetchAndProcessReports() // busca e processa os dados iniciais
    }

    // trata o clique no botao voltar da actionbar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // configura os componentes de ui para os filtros
    private fun setupFiltersUI() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        // listeners para os datepickers de data inicio e fim
        binding.etStatsStartDate.setOnClickListener {
            val calendar = selectedStartDate ?: Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                selectedStartDate = Calendar.getInstance().apply { set(year, month, dayOfMonth, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                binding.etStatsStartDate.setText(sdf.format(selectedStartDate!!.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        binding.etStatsEndDate.setOnClickListener {
            val calendar = selectedEndDate ?: Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, dayOfMonth ->
                selectedEndDate = Calendar.getInstance().apply { set(year, month, dayOfMonth, 23, 59, 59); set(Calendar.MILLISECOND, 999) }
                binding.etStatsEndDate.setText(sdf.format(selectedEndDate!!.time))
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        // configura dropdown de status
        val statusOptions = mutableListOf(getString(R.string.todos_placeholder))
        statusOptions.addAll(StatusRisco.getAllDescriptions())
        val statusAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusOptions)
        binding.actvStatsStatus.setAdapter(statusAdapter)
        binding.actvStatsStatus.setOnItemClickListener { _, _, position, _ ->
            selectedStatus = if (position == 0) null else statusOptions[position] // null se "todos" for selecionado
        }
        binding.actvStatsStatus.setText(getString(R.string.todos_placeholder), false)


        // configura dropdown de usuario (populado dinamicamente)
        val initialUserOptions = mutableListOf(getString(R.string.todos_placeholder))
        val userAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, initialUserOptions)
        binding.actvStatsUser.setAdapter(userAdapter)
        binding.actvStatsUser.setOnItemClickListener { _, _, position, _ ->
            val selectedItem = binding.actvStatsUser.adapter.getItem(position).toString()
            selectedUserId = if (selectedItem == getString(R.string.todos_placeholder)) null else selectedItem
        }
        binding.actvStatsUser.setText(getString(R.string.todos_placeholder), false)


        // listeners para os botoes de aplicar e limpar filtros
        binding.btnStatsApplyFilter.setOnClickListener {
            applyFiltersAndRefreshData()
        }
        binding.btnStatsClearFilter.setOnClickListener {
            clearFilters()
            applyFiltersAndRefreshData()
        }
    }

    // configura o recyclerview para os ultimos reports
    private fun setupLastReportsRecyclerView() {
        lastReportsAdapter = LastReportsAdapter(emptyList()) { report ->
            // ao clicar, abre a tela de detalhes do report
            if (report.id != null) {
                val intent = Intent(this, DetalheRiscoActivity::class.java).apply {
                    putExtra(DetalheRiscoActivity.EXTRA_REPORT_ID, report.id)
                }
                startActivity(intent)
            }
        }
        binding.rvLastReports.layoutManager = LinearLayoutManager(this)
        binding.rvLastReports.adapter = lastReportsAdapter
        binding.rvLastReports.isNestedScrollingEnabled = false // para rv dentro de nestedscrollview
    }


    // busca todos os reports do firebase e inicia o processamento
    private fun fetchAndProcessReports() {
        binding.progressBarEstatisticas.visibility = View.VISIBLE
        val reportsRef = database.getReference("reports")

        reportsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allReportsList.clear()
                distinctUserIds.clear()

                if (!snapshot.exists()) {
                    Log.d(TAG, "nenhum relatorio encontrado no firebase.")
                    allReportsList.clear()
                } else {
                    for (reportSnapshot in snapshot.children) {
                        val report = reportSnapshot.getValue(Report::class.java)
                        report?.let {
                            it.id = reportSnapshot.key
                            allReportsList.add(it)
                            it.userId?.let { userId -> if (userId.isNotBlank()) distinctUserIds.add(userId) }
                        }
                    }
                }
                allReportsList.sortByDescending { it.reportDate } // ordena por data, mais recentes primeiro
                populateUserFilter() // atualiza o dropdown de usuarios
                applyFiltersAndRefreshData() // aplica filtros e atualiza ui
                binding.progressBarEstatisticas.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "falha ao ler relatorios.", error.toException())
                Toast.makeText(this@EstatisticasActivity, getString(R.string.erro_carregar_dados), Toast.LENGTH_SHORT).show()
                binding.progressBarEstatisticas.visibility = View.GONE
                updateUIWithFilteredData(emptyList()) // limpa a ui em caso de erro
            }
        })
    }

    // popula o dropdown de filtro de usuario com os ids carregados
    private fun populateUserFilter() {
        val userOptions = mutableListOf(getString(R.string.todos_placeholder))
        userOptions.addAll(distinctUserIds.sorted())
        val userAdapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, userOptions)
        binding.actvStatsUser.setAdapter(userAdapter)

        // mantem a selecao atual do filtro de usuario se possivel
        val currentFilterText = selectedUserId ?: getString(R.string.todos_placeholder)
        binding.actvStatsUser.setText(currentFilterText, false)
    }


    // limpa todos os filtros aplicados
    private fun clearFilters() {
        selectedStartDate = null
        selectedEndDate = null
        selectedStatus = null
        selectedUserId = null

        binding.etStatsStartDate.text?.clear()
        binding.etStatsEndDate.text?.clear()
        binding.actvStatsStatus.setText(getString(R.string.todos_placeholder), false)
        binding.actvStatsUser.setText(getString(R.string.todos_placeholder), false)
        Toast.makeText(this, getString(R.string.filtros_limpos), Toast.LENGTH_SHORT).show()
    }

    // aplica os filtros selecionados a lista de todos os reports e atualiza a ui
    private fun applyFiltersAndRefreshData() {
        filteredReportsList.clear()

        val tempFilteredList = allReportsList.filter { report ->
            val afterStartDate = selectedStartDate?.let { start ->
                report.reportDate?.let { it >= start.timeInMillis } ?: false
            } ?: true

            val beforeEndDate = selectedEndDate?.let { end ->
                report.reportDate?.let { it <= end.timeInMillis } ?: false
            } ?: true

            val statusMatch = selectedStatus?.let { status ->
                report.status == status
            } ?: true

            val userMatch = selectedUserId?.let { userId ->
                report.userId == userId
            } ?: true

            afterStartDate && beforeEndDate && statusMatch && userMatch
        }
        filteredReportsList.addAll(tempFilteredList)
        updateUIWithFilteredData(filteredReportsList) // atualiza todos os componentes da ui
    }

    // atualiza todos os componentes da ui com base nos dados filtrados
    private fun updateUIWithFilteredData(reports: List<Report>) {
        updateStatusCounters(reports)
        updateBarChart(reports)

        val lastThreeReports = reports.take(3)
        lastReportsAdapter.updateData(lastThreeReports)
        // logica de visibilidade para a lista de ultimos reports
        binding.rvLastReports.visibility = if (lastThreeReports.isEmpty() && reports.isNotEmpty()) View.GONE else if (lastThreeReports.isEmpty()) View.GONE else View.VISIBLE

        // logica de visibilidade para o placeholder de "nenhum dado"
        if (reports.isEmpty() && (selectedStartDate != null || selectedEndDate != null || selectedStatus != null || selectedUserId != null)) {
            binding.tvStatsPlaceholder.text = getString(R.string.nenhum_dado_filtros)
            binding.tvStatsPlaceholder.visibility = View.VISIBLE
        } else if (reports.isEmpty()) {
            binding.tvStatsPlaceholder.text = getString(R.string.nenhum_dado_disponivel)
            binding.tvStatsPlaceholder.visibility = View.VISIBLE
        }
        else {
            binding.tvStatsPlaceholder.visibility = View.GONE
        }
    }

    // atualiza os contadores de status no gridlayout
    private fun updateStatusCounters(reports: List<Report>) {
        val statusCounts = reports.groupingBy { it.status ?: StatusRisco.ABERTO.descricao }.eachCount()
        Log.d(TAG, "contagem de status para ui: $statusCounts")
        binding.gridLayoutStatusCounters.removeAllViews() // limpa views antigas

        // adiciona um card para cada status definido no enum
        StatusRisco.entries.forEach { statusEntry ->
            val count = statusCounts[statusEntry.descricao] ?: 0
            addCounterView(statusEntry.descricao, count.toString())
        }

        // trata status nao mapeados no enum (ex: "desconhecido" ou nulos)
        val unknownKey = "Desconhecido"
        val reportsWithNullOrUnmappedStatus = reports.filter { StatusRisco.fromDescricao(it.status) == null }
        val unknownCountMap = reportsWithNullOrUnmappedStatus.groupingBy { it.status ?: unknownKey }.eachCount()

        unknownCountMap.forEach { (statusName, count) ->
            if (count > 0 && StatusRisco.entries.none { it.descricao == statusName }) {
                addCounterView(statusName, count.toString())
            }
        }
    }

    // helper para adicionar um card de contador ao gridlayout
    private fun addCounterView(statusName: String, count: String) {
        val inflater = LayoutInflater.from(this)
        // infla o layout do card de contador
        val cardView = inflater.inflate(R.layout.item_status_counter_card, binding.gridLayoutStatusCounters, false) as com.google.android.material.card.MaterialCardView

        val tvCount = cardView.findViewById<TextView>(R.id.tvCounterValue)
        val tvName = cardView.findViewById<TextView>(R.id.tvCounterName)

        tvCount.text = count
        tvName.text = statusName

        // define os parametros de layout para o gridlayout
        val params = GridLayout.LayoutParams().apply {
            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f) // distribui espaco igualmente
            rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            width = 0 // necessario para columnweight
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
        }
        cardView.layoutParams = params
        binding.gridLayoutStatusCounters.addView(cardView)
    }


    // atualiza o grafico de barras simples com base nos reports filtrados
    private fun updateBarChart(reports: List<Report>) {
        binding.layoutBarChart.removeAllViews() // limpa barras antigas

        if (reports.isEmpty()) {
            binding.layoutBarChart.visibility = View.GONE // esconde grafico se nao ha dados
            return
        }
        binding.layoutBarChart.visibility = View.VISIBLE // mostra grafico se ha dados

        val statusCounts = reports.groupingBy { it.status ?: StatusRisco.ABERTO.descricao }.eachCount()
        val maxValue = statusCounts.values.maxOrNull()?.toFloat() ?: 1f // maximo para normalizar altura das barras

        // mapa de cores para cada status
        val barColors = mapOf(
            StatusRisco.ABERTO.descricao to ContextCompat.getColor(this, R.color.status_aberto),
            StatusRisco.EM_ANALISE.descricao to ContextCompat.getColor(this, R.color.status_em_analise),
            StatusRisco.EM_ANDAMENTO.descricao to ContextCompat.getColor(this, R.color.status_em_andamento),
            StatusRisco.RESOLVIDO.descricao to ContextCompat.getColor(this, R.color.status_resolvido),
            StatusRisco.INVALIDO.descricao to ContextCompat.getColor(this, R.color.status_invalido)
        )
        val defaultColor = ContextCompat.getColor(this, R.color.status_desconhecido)

        val maxBarHeightPx = dpToPx(180) // altura maxima do grafico em pixels

        // cria uma view para cada barra do grafico
        statusCounts.filter { it.value > 0 }.forEach { (status, count) ->
            val barView = View(this)
            val barHeight = if (maxValue > 0) (count.toFloat() / maxValue * maxBarHeightPx) else 0f // altura proporcional

            val params = LinearLayout.LayoutParams(dpToPx(35), barHeight.toInt().coerceAtLeast(dpToPx(5))) // largura e altura minima
            params.setMargins(dpToPx(6), 0, dpToPx(6), 0)
            barView.layoutParams = params
            barView.setBackgroundColor(barColors[status] ?: defaultColor)

            // tooltip simples ao pressionar longamente a barra
            barView.setOnLongClickListener {
                Toast.makeText(this, "$status: $count", Toast.LENGTH_SHORT).show()
                true
            }
            binding.layoutBarChart.addView(barView)
        }
    }

    // converte dp para pixels
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    // adapter interno para a lista dos ultimos reports
    private class LastReportsAdapter(
        private var reports: List<Report>,
        private val onItemClicked: (Report) -> Unit
    ) : RecyclerView.Adapter<LastReportsAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val title: TextView = itemView.findViewById(R.id.tvResumidoTitle)
            val status: TextView = itemView.findViewById(R.id.tvResumidoStatus)
            val date: TextView = itemView.findViewById(R.id.tvResumidoDate)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_report_resumido, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val report = reports[position]
            val context = holder.itemView.context
            holder.title.text = report.title ?: context.getString(R.string.sem_titulo)
            holder.status.text = context.getString(R.string.status_label, report.status ?: StatusRisco.ABERTO.descricao)

            report.reportDate?.let {
                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                holder.date.text = sdf.format(Date(it))
            } ?: run {
                holder.date.text = context.getString(R.string.data_nao_informada)
            }

            // aplica cor
            when (StatusRisco.fromDescricao(report.status)) {
                StatusRisco.RESOLVIDO -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_resolvido))
                StatusRisco.EM_ANDAMENTO -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_em_andamento))
                StatusRisco.EM_ANALISE -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_em_analise))
                StatusRisco.INVALIDO -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_invalido))
                StatusRisco.ABERTO -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_aberto))
                null -> holder.status.setTextColor(ContextCompat.getColor(context, R.color.status_desconhecido))
            }
            holder.itemView.setOnClickListener { onItemClicked(report) }
        }
        override fun getItemCount(): Int = reports.size

        fun updateData(newReports: List<Report>) {
            reports = newReports
            notifyDataSetChanged() // para listas pequenas; diffutil para listas grandes
        }
    }
}