package br.com.uri.campushub

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var txtForgotPassword: TextView
    private lateinit var btnLogin: Button
    private lateinit var btnBack: Button

    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        txtForgotPassword = findViewById(R.id.txtForgotPassword)
        btnLogin = findViewById(R.id.btnLogin)
        btnBack = findViewById(R.id.btnBack)

        txtForgotPassword.setOnClickListener { showForgotPasswordDialog() }
        btnLogin.setOnClickListener { login() }
        btnBack.setOnClickListener { finish() }
    }

    private fun showForgotPasswordDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
        val edtResetEmail = view.findViewById<EditText>(R.id.edtResetEmail)

        // aproveita o email que já foi digitado na tela de login
        edtResetEmail.setText(edtEmail.text)

        AlertDialog.Builder(this)
            .setTitle(R.string.forgot_password)
            .setMessage(R.string.reset_password_message)
            .setView(view)
            .setPositiveButton(R.string.send) { _, _ ->
                sendResetEmail(edtResetEmail.text.toString().trim())
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendResetEmail(email: String) {
        if (email.isEmpty()) {
            Toast.makeText(this, R.string.fill_email, Toast.LENGTH_SHORT).show()
            return
        }

        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                Toast.makeText(this, R.string.reset_email_sent, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
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
