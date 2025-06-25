package com.leandro.manageriscos

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract // necessario para action_create_document
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.leandro.manageriscos.databinding.ActivityListaRiscosBinding
import java.io.IOException
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class ListaRiscosActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListaRiscosBinding
    private lateinit var database: FirebaseDatabase
    private lateinit var reportAdapter: ReportAdapter
    private val allReportsList = mutableListOf<Report>() // lista original, todos os reports
    private val displayedReportsList = mutableListOf<Report>() // lista para exibir, apos filtros

    private var selectedStartDate: Calendar? = null // data de inicio do filtro
    private var selectedEndDate: Calendar? = null // data de fim do filtro

    // launcher para o seletor de arquivos (saf) para criar o csv
    private lateinit var createFileLauncher: ActivityResultLauncher<Intent>

    companion object {
        private const val TAG = "ListaRiscosActivity"
        const val EXTRA_IMAGE_URL = "extra_image_url" // chave para intent da imagem
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListaRiscosBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // configura actionbar
        supportActionBar?.title = getString(R.string.titulo_relatorio_riscos)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        database = FirebaseDatabase.getInstance("https://report-de-riscos-default-rtdb.firebaseio.com/")

        setupRecyclerView()
        setupFilterListeners()
        setupExportButton() // configura botao de exportar
        fetchReportsFromFirebase()

        // inicializa o launcher para criar arquivos csv via saf
        createFileLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.data?.let { uri ->
                    // uri do arquivo selecionado pelo usuario
                    writeCsvToFile(uri, generateCsvContent(displayedReportsList))
                }
            }
        }
    }

    // trata clique no botao voltar da actionbar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // configura o recyclerview e seu adapter
    private fun setupRecyclerView() {
        reportAdapter = ReportAdapter(displayedReportsList,
            onItemClicked = { report ->
                // clique no item: abre detalhes do risco
                if (report.id != null) {
                    val intent = Intent(this, DetalheRiscoActivity::class.java).apply {
                        putExtra(DetalheRiscoActivity.EXTRA_REPORT_ID, report.id)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, getString(R.string.id_risco_invalido), Toast.LENGTH_SHORT).show()
                    Log.e(TAG, "tentativa de abrir detalhes para report sem id: ${report.title}")
                }
            },
            onImageClicked = { imageUrl ->
                // clique na imagem: abre em tela cheia
                if (!imageUrl.isNullOrEmpty()) {
                    val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                        putExtra(EXTRA_IMAGE_URL, imageUrl)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, getString(R.string.imagem_nao_disponivel), Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.recyclerViewReports.apply {
            layoutManager = LinearLayoutManager(this@ListaRiscosActivity)
            adapter = reportAdapter
        }
    }

    // configura listeners para os campos de filtro de data
    private fun setupFilterListeners() {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

        binding.etStartDate.setOnClickListener {
            val calendar = selectedStartDate ?: Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedStartDate = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth, 0, 0, 0) // inicio do dia
                        set(Calendar.MILLISECOND, 0)
                    }
                    binding.etStartDate.setText(sdf.format(selectedStartDate!!.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.etEndDate.setOnClickListener {
            val calendar = selectedEndDate ?: Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    selectedEndDate = Calendar.getInstance().apply {
                        set(year, month, dayOfMonth, 23, 59, 59) // fim do dia
                        set(Calendar.MILLISECOND, 999)
                    }
                    binding.etEndDate.setText(sdf.format(selectedEndDate!!.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        binding.btnApplyFilter.setOnClickListener {
            applyDateFilter()
        }
        binding.btnClearFilter.setOnClickListener {
            clearDateFilter()
        }
    }

    // configura listener para o botao de exportar csv
    private fun setupExportButton() {
        binding.btnExportCsv.setOnClickListener {
            if (displayedReportsList.isEmpty()) {
                Toast.makeText(this, getString(R.string.nenhum_dado_exportar), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            createCsvFile() // chama a criacao do arquivo
        }
    }

    // inicia o processo de criacao de arquivo csv usando saf
    private fun createCsvFile() {
        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        val timestamp = sdf.format(Date())
        val fileName = "relatorio_riscos_$timestamp.csv" // nome do arquivo com timestamp

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv" // mime type
            putExtra(Intent.EXTRA_TITLE, fileName) // nome sugerido
            // opcional: diretorio inicial (api 26+)
            // putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri)
        }
        createFileLauncher.launch(intent) // lanca o seletor de arquivos
    }

    // gera o conteudo da string csv a partir da lista de reports
    private fun generateCsvContent(reports: List<Report>): String {
        val csvBuilder = StringBuilder()
        // cabecalho do csv
        csvBuilder.append("ID,Titulo,Descricao,Localizacao,DataReporteUnix,DataReporteFormatada,Status,Observacoes,UserID,ImageURL\n")

        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

        // linhas de dados do csv
        reports.forEach { report ->
            csvBuilder.append("\"${report.id ?: ""}\",")
            csvBuilder.append("\"${(report.title ?: "").replace("\"", "\"\"")}\",") // trata aspas no texto
            csvBuilder.append("\"${(report.description ?: "").replace("\"", "\"\"")}\",")
            csvBuilder.append("\"${(report.location ?: "").replace("\"", "\"\"")}\",")
            csvBuilder.append("${report.reportDate ?: 0L},") // timestamp unix
            csvBuilder.append("\"${report.reportDate?.let { sdf.format(Date(it)) } ?: ""}\",") // data formatada
            csvBuilder.append("\"${(report.status ?: "").replace("\"", "\"\"")}\",")
            csvBuilder.append("\"${(report.observacoes ?: "").replace("\"", "\"\"")}\",")
            csvBuilder.append("\"${(report.userId ?: "").replace("\"", "\"\"")}\",")
            csvBuilder.append("\"${(report.imageUrl ?: "").replace("\"", "\"\"")}\"\n")
        }
        return csvBuilder.toString()
    }

    // escreve os dados csv no arquivo selecionado pelo usuario (uri)
    private fun writeCsvToFile(uri: Uri, csvData: String) {
        try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(csvData)
                }
            }
            Toast.makeText(this, getString(R.string.relatorio_csv_salvo_sucesso), Toast.LENGTH_LONG).show()
        } catch (e: IOException) {
            Log.e(TAG, "erro ao salvar arquivo csv: ${e.message}", e)
            Toast.makeText(this, getString(R.string.erro_salvar_csv, e.message), Toast.LENGTH_LONG).show()
        }
    }


    // busca os reports do firebase
    private fun fetchReportsFromFirebase() {
        binding.tvNoReports.visibility = View.GONE
        // todo: adicionar progressbar aqui

        val reportsRef = database.getReference("reports")
        reportsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // todo: esconder progressbar aqui
                allReportsList.clear()
                if (!snapshot.exists()) {
                    Log.d(TAG, "nenhum relatorio encontrado no firebase.")
                    updateDisplayedList(emptyList()) // atualiza ui para lista vazia
                    return
                }

                for (reportSnapshot in snapshot.children) {
                    val report = reportSnapshot.getValue(Report::class.java)
                    report?.let {
                        it.id = reportSnapshot.key // pega o id do firebase
                        allReportsList.add(it)
                    }
                }
                allReportsList.sortByDescending { it.reportDate } // ordena por data
                Log.d(TAG, "total de ${allReportsList.size} relatorios carregados.")
                applyDateFilter() // aplica filtros (ou mostra todos)
            }

            override fun onCancelled(error: DatabaseError) {
                // todo: esconder progressbar aqui
                Log.e(TAG, "falha ao ler relatorios.", error.toException())
                Toast.makeText(this@ListaRiscosActivity, getString(R.string.erro_carregar_dados), Toast.LENGTH_SHORT).show()
                updateDisplayedList(emptyList())
                binding.tvNoReports.text = getString(R.string.erro_carregar_dados)
            }
        })
    }

    // aplica o filtro de data a lista de reports
    private fun applyDateFilter() {
        val filteredList = allReportsList.filter { report ->
            report.reportDate?.let { reportTime ->
                val reportCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = reportTime }
                // logica do filtro de data
                val afterStartDate = selectedStartDate?.let { start ->
                    reportCalendar.timeInMillis >= start.timeInMillis
                } ?: true // true se nao ha data de inicio
                val beforeEndDate = selectedEndDate?.let { end ->
                    reportCalendar.timeInMillis <= end.timeInMillis
                } ?: true // true se nao ha data de fim
                return@filter afterStartDate && beforeEndDate
            } ?: (selectedStartDate == null && selectedEndDate == null) // inclui reports sem data apenas se nenhum filtro de data estiver ativo
        }
        updateDisplayedList(filteredList)
    }

    // limpa os filtros de data selecionados
    private fun clearDateFilter() {
        selectedStartDate = null
        selectedEndDate = null
        binding.etStartDate.text.clear()
        binding.etEndDate.text.clear()
        applyDateFilter() // reaplica para mostrar todos
        Toast.makeText(this, getString(R.string.filtro_data_limpo), Toast.LENGTH_SHORT).show()
    }

    // atualiza a lista exibida no recyclerview e a visibilidade dos textviews
    private fun updateDisplayedList(newList: List<Report>) {
        displayedReportsList.clear()
        displayedReportsList.addAll(newList)
        reportAdapter.updateData(displayedReportsList)

        // controla visibilidade do textview "nenhum risco"
        if (displayedReportsList.isEmpty()) {
            binding.recyclerViewReports.visibility = View.GONE
            binding.tvNoReports.visibility = View.VISIBLE
            if (selectedStartDate != null || selectedEndDate != null) { // se estava filtrando
                binding.tvNoReports.text = getString(R.string.nenhum_risco_filtro)
            } else {
                binding.tvNoReports.text = getString(R.string.nenhum_risco_cadastrado)
            }
        } else {
            binding.recyclerViewReports.visibility = View.VISIBLE
            binding.tvNoReports.visibility = View.GONE
        }
    }
}