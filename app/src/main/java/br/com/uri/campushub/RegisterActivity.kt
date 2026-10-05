package br.com.uri.campushub

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {

    private lateinit var edtName: EditText
    private lateinit var spnCourse: Spinner
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var edtConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var btnBack: Button

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        edtName = findViewById(R.id.edtName)
        spnCourse = findViewById(R.id.spnCourse)
        edtEmail = findViewById(R.id.edtEmail)
        edtPassword = findViewById(R.id.edtPassword)
        edtConfirmPassword = findViewById(R.id.edtConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        btnBack = findViewById(R.id.btnBack)

        btnRegister.setOnClickListener { register() }
        btnBack.setOnClickListener { finish() }
    }

    private fun register() {
        val name = edtName.text.toString().trim()
        val course = spnCourse.selectedItem.toString()
        val email = edtEmail.text.toString().trim()
        val password = edtPassword.text.toString()
        val confirmPassword = edtConfirmPassword.text.toString()

        // o Firebase fecha o app se receber email ou senha vazios
        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, R.string.fill_all_fields, Toast.LENGTH_SHORT).show()
            return
        }

        // a posição 0 é o texto "Selecione o curso"
        if (spnCourse.selectedItemPosition == 0) {
            Toast.makeText(this, R.string.select_course, Toast.LENGTH_SHORT).show()
            return
        }

        if (password.length < 6) {
            Toast.makeText(this, R.string.short_password, Toast.LENGTH_SHORT).show()
            return
        }

        if (password != confirmPassword) {
            Toast.makeText(this, R.string.passwords_dont_match, Toast.LENGTH_SHORT).show()
            return
        }

        // evita criar duas contas com toques repetidos
        btnRegister.isEnabled = false

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val uid = result.user!!.uid
                val user = hashMapOf(
                    "name" to name,
                    "course" to course,
                    "email" to email
                )

                // nome e curso ficam no Firestore, o Auth guarda só email e senha
                db.collection("users").document(uid).set(user)
                    .addOnSuccessListener {
                        Toast.makeText(this, R.string.account_created, Toast.LENGTH_SHORT).show()
                        openEvents()
                    }
                    .addOnFailureListener { error -> showError(error) }
            }
            .addOnFailureListener { error -> showError(error) }
    }

    // o Firebase já deixa a conta nova logada
    private fun openEvents() {
        val intent = Intent(this, EventsActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun showError(error: Exception) {
        btnRegister.isEnabled = true
        Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
    }
}
