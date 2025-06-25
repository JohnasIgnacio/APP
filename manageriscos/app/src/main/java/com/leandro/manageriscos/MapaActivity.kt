package com.leandro.manageriscos

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MapaActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var database: FirebaseDatabase
    private val reportsList = mutableListOf<Report>() // lista de reports
    private var focusedReportIdFromIntent: String? = null // id do report para foco via intent

    companion object {
        private const val TAG = "MapaActivity"
        // chaves para extras do intent
        const val EXTRA_FOCUS_LAT = "FOCUS_LAT"
        const val EXTRA_FOCUS_LNG = "FOCUS_LNG"
        const val EXTRA_FOCUS_TITLE = "FOCUS_TITLE"
        const val EXTRA_FOCUS_REPORT_ID = "FOCUS_REPORT_ID" // id para abrir infowindow
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mapa)

        // configura toolbar
        supportActionBar?.title = "Mapa de Riscos"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        database = FirebaseDatabase.getInstance("https://report-de-riscos-default-rtdb.firebaseio.com/")

        // verifica id para foco
        focusedReportIdFromIntent = intent.getStringExtra(EXTRA_FOCUS_REPORT_ID)

        // inicializa fragmento do mapa
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapContainer) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                supportFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, it)
                    .commit()
            }
        mapFragment.getMapAsync(this) // carrega mapa assincrono
    }

    // trata clique no botao voltar
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        googleMap.uiSettings.isZoomControlsEnabled = true // habilita controles de zoom

        // listener clique marcador
        googleMap.setOnMarkerClickListener { marker ->
            val clickedReportId = marker.tag as? String
            if (clickedReportId != null) {
                val intent = Intent(this, DetalheRiscoActivity::class.java).apply {
                    putExtra(DetalheRiscoActivity.EXTRA_REPORT_ID, clickedReportId)
                }
                startActivity(intent)
            } else {
                Log.w(TAG, "ID do risco nao encontrado na tag do marcador.")
            }
            true // evento consumido
        }

        // listener clique infowindow
        googleMap.setOnInfoWindowClickListener { marker ->
            val clickedReportId = marker.tag as? String
            if (clickedReportId != null) {
                val intent = Intent(this, DetalheRiscoActivity::class.java).apply {
                    putExtra(DetalheRiscoActivity.EXTRA_REPORT_ID, clickedReportId)
                }
                startActivity(intent)
            }
        }

        // verifica coords de foco
        val focusLat = intent.getDoubleExtra(EXTRA_FOCUS_LAT, -999.0)
        val focusLng = intent.getDoubleExtra(EXTRA_FOCUS_LNG, -999.0)

        if (focusLat != -999.0 && focusLng != -999.0) {
            // centraliza se veio com foco
            val focusPosition = LatLng(focusLat, focusLng)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(focusPosition, 15f)) // zoom para foco
        }
        // sempre carrega reports
        fetchReportsFromFirebase()
    }

    // busca reports do firebase
    private fun fetchReportsFromFirebase() {
        val reportsRef = database.getReference("reports")
        reportsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                reportsList.clear()
                googleMap.clear() // limpa marcadores antigos

                if (!snapshot.exists()) {
                    Log.d(TAG, "Nenhum relatorio encontrado no Firebase.")
                    // centraliza em sp se sem foco e sem dados
                    if (intent.getDoubleExtra(EXTRA_FOCUS_LAT, -999.0) == -999.0) {
                        val defaultLocation = LatLng(-23.5505, -46.6333) // localizacao padrao
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))
                    }
                    return
                }

                val boundsBuilder = LatLngBounds.Builder() // para ajustar zoom
                var hasValidLocations = false
                var markerToOpenInfoWindow: Marker? = null // marcador para abrir infowindow

                for (reportSnapshot in snapshot.children) {
                    val report = reportSnapshot.getValue(Report::class.java)
                    report?.let { currentReport ->
                        currentReport.id = reportSnapshot.key // popula id do firebase
                        reportsList.add(currentReport)

                        currentReport.getLatLng()?.let { (lat, lng) ->
                            val position = LatLng(lat, lng)
                            val markerOptions = MarkerOptions()
                                .position(position)
                                .title(currentReport.title ?: "Risco")
                                .snippet(currentReport.description ?: "Sem descricao")

                            // muda cor do marcador por status
                            when (StatusRisco.fromDescricao(currentReport.status)) {
                                StatusRisco.RESOLVIDO -> markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
                                StatusRisco.EM_ANDAMENTO, StatusRisco.EM_ANALISE -> markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
                                StatusRisco.INVALIDO -> markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN)) // cyan para invalido
                                StatusRisco.ABERTO -> markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                                null -> markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)) // status desconhecido/nulo
                            }

                            val marker = googleMap.addMarker(markerOptions)
                            marker?.tag = currentReport.id // associa id ao marcador

                            // verifica se e report para foco
                            if(currentReport.id == focusedReportIdFromIntent) {
                                markerToOpenInfoWindow = marker
                            }

                            boundsBuilder.include(position)
                            hasValidLocations = true
                        }
                    }
                }

                // logica da camera
                val focusLatFromIntent = intent.getDoubleExtra(EXTRA_FOCUS_LAT, -999.0)
                val focusLngFromIntent = intent.getDoubleExtra(EXTRA_FOCUS_LNG, -999.0)

                if (markerToOpenInfoWindow != null) {
                    // foco em marcador especifico
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(markerToOpenInfoWindow!!.position, 15f))
                    markerToOpenInfoWindow?.showInfoWindow()
                } else if (focusLatFromIntent != -999.0 && focusLngFromIntent != -999.0) {
                    // foco via coordenadas
                } else if (hasValidLocations) {
                    // sem foco, ajusta para todos
                    try {
                        val bounds = boundsBuilder.build()
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150)) // padding
                    } catch (e: IllegalStateException) {
                        // trata erro para um marcador
                        if (reportsList.size == 1 && reportsList[0].getLatLng() != null) {
                            reportsList[0].getLatLng()?.let { (lat, lng) ->
                                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 15f))
                            }
                        } else {
                            Log.e(TAG, "Erro ao construir bounds para multiplos marcadores: ${e.message}")
                        }
                    }
                } else if (reportsList.isNotEmpty()) {
                    // reports sem localizacao valida
                    Log.d(TAG, "Relatorios encontrados, mas nenhum com localizacao valida (sem foco).")
                    val defaultLocation = LatLng(-23.5505, -46.6333) // localizacao padrao
                    googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f))
                }

                Log.d(TAG, "Total de ${reportsList.size} relatorios carregados e processados no mapa.")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Falha ao ler os relatorios do Firebase.", error.toException())
                Toast.makeText(this@MapaActivity, "Erro ao carregar dados do mapa.", Toast.LENGTH_SHORT).show()
            }
        })
    }
}