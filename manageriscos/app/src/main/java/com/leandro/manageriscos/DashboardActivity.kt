package com.leandro.manageriscos

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.leandro.manageriscos.databinding.ActivityDashboardBinding

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // configurar título da actionbar

        // listener para o botão de abrir mapa
        binding.btnOpenMap.setOnClickListener {
            startActivity(Intent(this, MapaActivity::class.java))
        }


        binding.btnOpenReportList.setOnClickListener {
            startActivity(Intent(this, ListaRiscosActivity::class.java))
        }


        binding.btnOpenStatistics.setOnClickListener {
            // intent para iniciar a EstatisticasActivity
            val intent = Intent(this, EstatisticasActivity::class.java)
            startActivity(intent)
        }
    }
}