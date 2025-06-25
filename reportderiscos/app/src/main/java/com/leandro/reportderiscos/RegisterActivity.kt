package com.leandro.reportderiscos

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class RegisterActivity : AppCompatActivity() {

    private lateinit var editTextNewEmail: EditText
    private lateinit var editTextNewPassword: EditText
    private lateinit var buttonRegister: Button
    private lateinit var textBackToLogin: TextView
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        editTextNewEmail = findViewById(R.id.editTextNewUsername)
        editTextNewPassword = findViewById(R.id.editTextNewPassword)
        buttonRegister = findViewById(R.id.buttonRegister)
        textBackToLogin = findViewById(R.id.textBackToLogin)

        auth = FirebaseAuth.getInstance()

        buttonRegister.setOnClickListener {
            val email = editTextNewEmail.text.toString().trim()
            val password = editTextNewPassword.text.toString()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this) { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "Cadastro realizado com sucesso!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, LoginActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this, "Falha no cadastro: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                Toast.makeText(this, "Por favor, preencha todos os campos.", Toast.LENGTH_SHORT).show()
            }
        }

        textBackToLogin.setOnClickListener {
            finish()
        }
    }

    override fun onBackPressed() {
        finish()
    }
}
