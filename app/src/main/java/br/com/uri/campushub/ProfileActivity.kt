package br.com.uri.campushub

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var scrollContent: NestedScrollView
    private lateinit var progressBar: ProgressBar
    private lateinit var txtInitials: TextView
    private lateinit var txtName: TextView
    private lateinit var txtEmail: TextView
    private lateinit var edtName: TextInputEditText
    private lateinit var dropCourse: AutoCompleteTextView
    private lateinit var btnSave: MaterialButton
    private lateinit var btnLogout: MaterialButton

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    // mesma lista do cadastro, sem o "Selecione o curso" da posição 0
    private lateinit var courses: List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        scrollContent = findViewById(R.id.scrollContent)
        progressBar = findViewById(R.id.progressBar)
        txtInitials = findViewById(R.id.txtInitials)
        txtName = findViewById(R.id.txtName)
        txtEmail = findViewById(R.id.txtEmail)
        edtName = findViewById(R.id.edtName)
        dropCourse = findViewById(R.id.dropCourse)
        btnSave = findViewById(R.id.btnSave)
        btnLogout = findViewById(R.id.btnLogout)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        applySystemBarsPadding()

        courses = resources.getStringArray(R.array.courses).drop(1)
        dropCourse.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, courses))

        btnSave.setOnClickListener { saveProfile() }
        btnLogout.setOnClickListener { logout() }

        loadProfile()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun applySystemBarsPadding() {
        val space = resources.getDimensionPixelSize(R.dimen.list_padding)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.setPadding(0, bars.top, 0, 0)
            scrollContent.setPadding(space, space, space, space + bars.bottom)
            insets
        }
    }

    private fun loadProfile() {
        val user = auth.currentUser!!

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                val name = document.getString("name") ?: ""
                val course = document.getString("course") ?: ""

                showHeader(name)
                txtEmail.text = user.email
                edtName.setText(name)
                // false para não abrir a lista nem filtrar as opções ao preencher
                dropCourse.setText(course, false)

                progressBar.visibility = View.GONE
                scrollContent.visibility = View.VISIBLE
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
                finish()
            }
    }

    private fun showHeader(name: String) {
        txtName.text = name
        txtInitials.text = initials(name)
    }

    // "Stefan Zanella" vira "SZ"; um nome só vira uma letra
    private fun initials(name: String): String {
        val words = name.trim().split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val first = words.first().first()
        val last = if (words.size > 1) words.last().first().toString() else ""
        return (first + last).uppercase()
    }

    private fun saveProfile() {
        val name = edtName.text.toString().trim()
        val course = dropCourse.text.toString()

        if (name.isEmpty()) {
            Toast.makeText(this, R.string.fill_name, Toast.LENGTH_SHORT).show()
            return
        }
        if (course !in courses) {
            Toast.makeText(this, R.string.select_course, Toast.LENGTH_SHORT).show()
            return
        }

        btnSave.isEnabled = false
        val uid = auth.currentUser!!.uid
        val changes = mapOf("name" to name, "course" to course)

        // update altera só esses campos e mantém o email salvo no documento
        db.collection("users").document(uid).update(changes)
            .addOnSuccessListener {
                btnSave.isEnabled = true
                showHeader(name)
                Toast.makeText(this, R.string.profile_saved, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                btnSave.isEnabled = true
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun logout() {
        auth.signOut()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
