package com.leandro.manageriscos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.ArrayAdapter // importação ok
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.leandro.manageriscos.databinding.ActivityDetalheRiscoBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DetalheRiscoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDetalheRiscoBinding
    private lateinit var database: FirebaseDatabase
    private var currentReport: Report? = null // armazena o relatório carregado
    private var reportId: String? = null // id do relatório vindo do intent

    companion object {
        const val EXTRA_REPORT_ID = "extra_report_id" // chave para o intent extra
        private const val TAG = "DetalheRiscoActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDetalheRiscoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // config toolbar
        setSupportActionBar(binding.toolbarDetalheRisco)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.titulo_detalhes_risco)

        database = FirebaseDatabase.getInstance("https://report-de-riscos-default-rtdb.firebaseio.com/") // url do db
        reportId = intent.getStringExtra(EXTRA_REPORT_ID)

        // validação reportId
        if (reportId == null) {
            Toast.makeText(this, getString(R.string.id_risco_nao_encontrado_detalhe), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupStatusDropdown()
        loadReportDetails()

        // listener para tela cheia dps do clique
        binding.ivDetalheFoto.setOnClickListener {
            currentReport?.imageUrl?.let { url ->
                if (url.isNotEmpty()) {
                    val intent = Intent(this, FullScreenImageActivity::class.java).apply {
                        putExtra(ListaRiscosActivity.EXTRA_IMAGE_URL, url)
                    }
                    startActivity(intent)
                } else {
                    Toast.makeText(this, getString(R.string.imagem_nao_disponivel), Toast.LENGTH_SHORT).show()
                }
            }
        }

        // listener para salvar alteracoes
        binding.btnSalvarDetalhes.setOnClickListener {
            saveReportChanges()
        }

        // listener para ver o risco no mapa principal
        binding.btnVerNoMapa.setOnClickListener {
            currentReport?.getLatLng()?.let { (lat, lng) ->
                val intent = Intent(this, MapaActivity::class.java).apply {
                    putExtra(MapaActivity.EXTRA_FOCUS_LAT, lat)
                    putExtra(MapaActivity.EXTRA_FOCUS_LNG, lng)
                    putExtra(MapaActivity.EXTRA_FOCUS_TITLE, currentReport?.title ?: getString(R.string.localizacao_nao_informada))
                    putExtra(MapaActivity.EXTRA_FOCUS_REPORT_ID, currentReport?.id)
                }
                startActivity(intent)
            } ?: Toast.makeText(this, getString(R.string.localizacao_nao_disponivel), Toast.LENGTH_SHORT).show()
        }
    }

    // configura o dropdown de seleção de status
    private fun setupStatusDropdown() {
        val statusOptions = StatusRisco.getAllDescriptions()
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, statusOptions)
        binding.actvDetalheStatus.setAdapter(adapter)
    }

    // carrega os detalhes do relatorio do firebase
    private fun loadReportDetails() {
        // todo: adicionar progressbar enquanto carrega
        val reportRef = database.getReference("reports").child(reportId!!) // usa o id validado
        reportRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(this@DetalheRiscoActivity, getString(R.string.risco_nao_encontrado), Toast.LENGTH_LONG).show()
                    finish()
                    return
                }
                currentReport = snapshot.getValue(Report::class.java)
                currentReport?.id = snapshot.key // garante que o id do firebase está no objeto

                // popula a ui com os dados do relatório
                currentReport?.let { report ->
                    binding.tvDetalheTitulo.text = report.title ?: getString(R.string.sem_titulo)
                    binding.tvDetalheDescricao.text = report.description ?: getString(R.string.sem_descricao)

                    report.reportDate?.let { timestamp ->
                        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        binding.tvDetalheData.text = getString(R.string.label_reportado_em, sdf.format(Date(timestamp)))
                    } ?: run {
                        binding.tvDetalheData.text = getString(R.string.data_nao_informada)
                    }
                    binding.tvDetalheLocalizacao.text = report.location ?: getString(R.string.localizacao_nao_informada)

                    Glide.with(this@DetalheRiscoActivity)
                        .load(report.imageUrl)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_error)
                        .centerCrop()
                        .into(binding.ivDetalheFoto)


                    // o 'false' no setText evita que o dropdown abra automaticamente
                    binding.actvDetalheStatus.setText(report.status ?: StatusRisco.ABERTO.descricao, false)
                    binding.etDetalheObservacoes.setText(report.observacoes ?: "")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "falha ao carregar detalhes do risco: ${error.message}", error.toException())
                Toast.makeText(this@DetalheRiscoActivity, getString(R.string.erro_carregar_detalhes), Toast.LENGTH_SHORT).show()
            }
        })
    }

    // salva as alterações de status e observações no firebase
    private fun saveReportChanges() {
        if (reportId == null || currentReport == null) {
            Toast.makeText(this, getString(R.string.dados_risco_ausentes_salvar), Toast.LENGTH_SHORT).show()
            return
        }

        val newStatus = binding.actvDetalheStatus.text.toString()
        val newObservacoes = binding.etDetalheObservacoes.text.toString().trim()

        // verifica se houve alguma alteração real antes de salvar
        if (newStatus == currentReport?.status && newObservacoes == (currentReport?.observacoes ?: "")) {
            Toast.makeText(this, "nenhuma alteração detectada.", Toast.LENGTH_SHORT).show()
            return
        }

        val updates = mapOf<String, Any?>(
            "status" to newStatus,
            "observacoes" to newObservacoes
        )

        // todo: adicionar progressbar enquanto salva
        database.getReference("reports").child(reportId!!)
            .updateChildren(updates)
            .addOnSuccessListener {
                Toast.makeText(this, getString(R.string.alteracoes_salvas_sucesso), Toast.LENGTH_SHORT).show()
                // atualiza o objeto local para consistência imediata na ui
                currentReport?.status = newStatus
                currentReport?.observacoes = newObservacoes

            }
            .addOnFailureListener { e ->
                Log.e(TAG, "erro ao salvar alterações: ${e.message}", e)
                Toast.makeText(this, getString(R.string.erro_salvar_alteracoes, e.message), Toast.LENGTH_LONG).show()
            }
    }


    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed() // comportamento padrão de voltar
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}