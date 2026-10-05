package br.com.uri.campushub

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var btnBack: Button

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnBack = findViewById(R.id.btnBack)

        btnLogin.setOnClickListener { login() }
        btnBack.setOnClickListener { finish() }
    }

    private fun login() {
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString()

        // o Firebase fecha o app se receber email ou senha vazios
        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show()
            return
        }

        btnLogin.isEnabled = false

        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener {
                openEvents()
            }
            .addOnFailureListener {
                btnLogin.isEnabled = true
                Toast.makeText(this, R.string.login_failed, Toast.LENGTH_LONG).show()
            }
    }

    private fun openEvents() {
        val intent = Intent(this, EventsActivity::class.java)
        // limpa a pilha para o voltar não retornar ao login
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
