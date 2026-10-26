package br.com.uri.campushub.ui.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.data.repository.AuthRepository
import br.com.uri.campushub.data.repository.UserRepository
import br.com.uri.campushub.domain.RegisterValidator
import br.com.uri.campushub.ui.events.EventsActivity

class RegisterActivity : AppCompatActivity() {

    private lateinit var edtName: EditText
    private lateinit var spnCourse: Spinner
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var edtConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var btnBack: Button

    private val authRepository = AuthRepository()
    private val userRepository = UserRepository()

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

        // a posição 0 do Spinner é o texto "Selecione o curso"
        val result = RegisterValidator.validate(
            name, spnCourse.selectedItemPosition != 0, email, password, confirmPassword
        )
        val error = when (result) {
            RegisterValidator.Result.EMPTY_FIELDS -> R.string.fill_all_fields
            RegisterValidator.Result.NO_COURSE -> R.string.select_course
            RegisterValidator.Result.SHORT_PASSWORD -> R.string.short_password
            RegisterValidator.Result.PASSWORDS_DONT_MATCH -> R.string.passwords_dont_match
            RegisterValidator.Result.VALID -> null
        }
        if (error != null) {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
            return
        }

        // evita criar duas contas com toques repetidos
        btnRegister.isEnabled = false

        // primeiro cria a conta no Auth; com o uid dela, cria o perfil no Firestore
        // (nome e curso ficam no Firestore, o Auth guarda só email e senha)
        authRepository.register(email, password)
            .onSuccessTask { uid -> userRepository.create(uid, User(name, course, email)) }
            .addOnSuccessListener {
                Toast.makeText(this, R.string.account_created, Toast.LENGTH_SHORT).show()
                openEvents()
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
