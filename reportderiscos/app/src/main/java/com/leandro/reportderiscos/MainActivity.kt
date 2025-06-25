package com.leandro.reportderiscos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.location.Location
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import com.leandro.reportderiscos.databinding.ActivityMainBinding
import java.io.ByteArrayOutputStream

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val auth by lazy { FirebaseAuth.getInstance() }
    private val dbRef by lazy { FirebaseDatabase.getInstance().reference.child("reports") }
    private val storageRef by lazy { FirebaseStorage.getInstance().reference.child("images") }

    private var capturedBitmap: Bitmap? = null
    private var currentLocation: String = "Localização não disponível"

    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == RESULT_OK) {
            val bmp = res.data?.extras?.get("data") as? Bitmap
            bmp?.let {
                capturedBitmap = it
                binding.imageViewPhoto.setImageBitmap(it)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (auth.currentUser == null) {
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        FirebaseApp.initializeApp(this)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        requestLocation() // Solicita a localização ao criar a activity

        binding.buttonTakePhoto.setOnClickListener {
            checkCameraPermissionAndLaunch()
        }

        binding.buttonSave.setOnClickListener {
            saveReport()
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1002)
        } else {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            cameraLauncher.launch(intent)
        }
    }

    private fun requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                currentLocation = "Lat: ${location.latitude}, Long: ${location.longitude}"
                binding.textViewLocation.text = getString(R.string.location_text, location.latitude, location.longitude)
            } else {
                binding.textViewLocation.text = getString(R.string.location_unavailable)
            }
        }.addOnFailureListener {
            binding.textViewLocation.text = getString(R.string.location_error)
        }
    }

    private fun saveReport() {
        val title = binding.editTextTitle.text.toString().trim()
        val desc = binding.editTextDescription.text.toString().trim()

        if (title.isBlank() || desc.isBlank()) {
            Toast.makeText(this, getString(R.string.title_description_required), Toast.LENGTH_SHORT).show()
            return
        }

        binding.buttonSave.isEnabled = false
        binding.progressBar.visibility = android.view.View.VISIBLE

        val userId = auth.uid ?: ""
        val reportTimestamp = System.currentTimeMillis()

        val report = RiskReport(title, desc, currentLocation, "", userId, reportTimestamp)

        val onComplete = {
            clearForm()
            binding.buttonSave.isEnabled = true
            binding.progressBar.visibility = android.view.View.GONE
            Toast.makeText(this, getString(R.string.report_saved_success), Toast.LENGTH_LONG).show()
        }

        val onFailure = { message: String ->
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            binding.buttonSave.isEnabled = true
            binding.progressBar.visibility = android.view.View.GONE
        }

        if (capturedBitmap != null) {
            val baos = ByteArrayOutputStream()
            capturedBitmap!!.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val data = baos.toByteArray()

            val imgRef = storageRef.child("${System.currentTimeMillis()}.jpg")
            imgRef.putBytes(data).addOnSuccessListener {
                imgRef.downloadUrl.addOnSuccessListener { uri ->
                    val reportWithImage = report.copy(imageUrl = uri.toString())
                    dbRef.push().setValue(reportWithImage)
                        .addOnSuccessListener { onComplete() }
                        .addOnFailureListener { onFailure("Erro ao salvar no banco") }
                }.addOnFailureListener { onFailure(getString(R.string.image_upload_error)) } // Usando string do resources
            }.addOnFailureListener { onFailure(getString(R.string.image_upload_error)) } // Usando string do resources
        } else {
            dbRef.push().setValue(report)
                .addOnSuccessListener { onComplete() }
                .addOnFailureListener { onFailure("Erro ao salvar no banco") }
        }
    }

    private fun clearForm() {
        binding.editTextTitle.text?.clear()
        binding.editTextDescription.text?.clear()
        binding.imageViewPhoto.setImageResource(0)
        binding.textViewLocation.text = getString(R.string.location_reloading) // String corrigida
        capturedBitmap = null
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            1002 -> { // camera
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    cameraLauncher.launch(intent)
                } else {
                    Toast.makeText(this, "Permissão de câmera negada", Toast.LENGTH_SHORT).show()
                }
            }
            1001 -> { // loc
                if (grantResults.isNotEmpty() && grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                    requestLocation()
                } else {
                    Toast.makeText(this, "Permissão de localização negada", Toast.LENGTH_SHORT).show()
                    binding.textViewLocation.text = getString(R.string.location_permission_denied) // leandro - correcao:string corrigida
                }
            }
        }
    }
}