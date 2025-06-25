package com.leandro.manageriscos

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.leandro.manageriscos.databinding.ActivityFullScreenImageBinding

class FullScreenImageActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenImageBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //

        val imageUrl = intent.getStringExtra(ListaRiscosActivity.EXTRA_IMAGE_URL)

        if (!imageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(imageUrl)
                .error(R.drawable.ic_image_error) // reusar o drawable de erro
                .fitCenter()
                .into(binding.ivFullScreen)
        } else {
            Toast.makeText(this, "URL da imagem não encontrada.", Toast.LENGTH_LONG).show()
            finish() // fecha a actv se n tiver URL
        }

        binding.btnCloseFullScreen.setOnClickListener {
            finish() // fecha a activity
        }
        binding.ivFullScreen.setOnClickListener {
            finish()
        }
    }
}
