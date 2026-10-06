package br.com.uri.campushub

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EventsActivity : AppCompatActivity() {

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var txtHello: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var rvEvents: RecyclerView

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_events)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        txtHello = findViewById(R.id.txtHello)
        progressBar = findViewById(R.id.progressBar)
        rvEvents = findViewById(R.id.rvEvents)

        // a Toolbar do layout passa a ser a barra da tela, com o menu de Sair
        setSupportActionBar(toolbar)
        rvEvents.layoutManager = LinearLayoutManager(this)
        applySystemBarsPadding()

        loadUserName()
    }

    // recarrega ao voltar dos detalhes, para as vagas aparecerem atualizadas
    override fun onResume() {
        super.onResume()
        loadEvents()
    }

    // no Android 15+ o app desenha atrás das barras do sistema, então
    // o topo desce até sair da barra de status e a lista termina acima da barra de navegação
    private fun applySystemBarsPadding() {
        val space = resources.getDimensionPixelSize(R.dimen.list_padding)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root)) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            appBar.setPadding(0, bars.top, 0, 0)
            rvEvents.setPadding(space, space, space, space + bars.bottom)
            insets
        }
    }

    private fun loadUserName() {
        val uid = auth.currentUser!!.uid

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val name = document.getString("name")
                txtHello.text = getString(R.string.hello_user, name)
            }
    }

    private fun loadEvents() {
        db.collection("events").orderBy("date").get()
            .addOnSuccessListener { result ->
                if (result.isEmpty) {
                    // primeira execução: cria os eventos de exemplo e busca de novo
                    SampleEvents.save(db)
                        .addOnSuccessListener { loadEvents() }
                        .addOnFailureListener { error -> showError(error) }
                    return@addOnSuccessListener
                }

                val events = result.toObjects(Event::class.java)
                rvEvents.adapter = EventAdapter(events) { event -> openDetails(event) }
                progressBar.visibility = View.GONE
            }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun openDetails(event: Event) {
        // manda só o id; a tela de detalhes busca o evento atualizado no Firestore
        val intent = Intent(this, EventDetailsActivity::class.java)
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.id)
        startActivity(intent)
    }

    private fun showError(error: Exception) {
        progressBar.visibility = View.GONE
        Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_events, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuMyEvents -> {
                startActivity(Intent(this, MyEventsActivity::class.java))
                return true
            }
            R.id.menuLogout -> {
                logout()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun logout() {
        auth.signOut()
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }
}
