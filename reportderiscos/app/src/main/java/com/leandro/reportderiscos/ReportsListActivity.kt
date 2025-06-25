package com.leandro.reportderiscos

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.leandro.reportderiscos.databinding.ActivityReportsListBinding

class ReportsListActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReportsListBinding
    private val dbRef by lazy { FirebaseDatabase.getInstance().reference.child("reports") }
    private val adapter by lazy { ReportsAdapter(listOf()) }
    private val auth by lazy { FirebaseAuth.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Verifica se o usuário está autenticado
        if (auth.currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        binding = ActivityReportsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        binding.recyclerViewReports.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewReports.adapter = adapter

        loadUserReports()
    }

    private fun loadUserReports() {
        val currentUserId = auth.uid ?: return

        binding.progressBar.visibility = View.VISIBLE
        binding.textViewEmpty.visibility = View.GONE
        binding.recyclerViewReports.visibility = View.GONE

        dbRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userReports = snapshot.children.mapNotNull { it.getValue(RiskReport::class.java) }
                    .filter { it.userId == currentUserId }

                binding.progressBar.visibility = View.GONE

                if (userReports.isEmpty()) {
                    binding.textViewEmpty.visibility = View.VISIBLE
                    binding.recyclerViewReports.visibility = View.GONE
                } else {
                    binding.textViewEmpty.visibility = View.GONE
                    binding.recyclerViewReports.visibility = View.VISIBLE
                    adapter.updateList(userReports)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                binding.progressBar.visibility = View.GONE
                binding.textViewEmpty.visibility = View.VISIBLE
                binding.recyclerViewReports.visibility = View.GONE
            }
        })
    }
}
