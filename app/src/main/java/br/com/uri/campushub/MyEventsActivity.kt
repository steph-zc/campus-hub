package br.com.uri.campushub

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Tasks
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

class MyEventsActivity : AppCompatActivity() {

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvEvents: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var btnBrowseEvents: Button
    private lateinit var progressBar: ProgressBar

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_events)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        rvEvents = findViewById(R.id.rvEvents)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        btnBrowseEvents = findViewById(R.id.btnBrowseEvents)
        progressBar = findViewById(R.id.progressBar)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        rvEvents.layoutManager = LinearLayoutManager(this)
        applySystemBarsPadding()

        // a lista de eventos é a tela anterior, então basta fechar esta
        btnBrowseEvents.setOnClickListener { finish() }
    }

    // recarrega ao voltar dos detalhes, caso a inscrição tenha sido cancelada
    override fun onResume() {
        super.onResume()
        loadMyEvents()
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
            rvEvents.setPadding(space, space, space, space + bars.bottom)
            insets
        }
    }

    private fun loadMyEvents() {
        val uid = auth.currentUser!!.uid

        // 1º passo: os ids dos eventos em que o aluno se inscreveu
        db.collection("users").document(uid).collection("enrollments").get()
            .addOnSuccessListener { result ->
                val eventIds = result.documents.map { it.id }
                if (eventIds.isEmpty()) {
                    showEvents(emptyList())
                } else {
                    loadEvents(eventIds)
                }
            }
            .addOnFailureListener { error -> showError(error) }
    }

    // 2º passo: os eventos com esses ids; o whereIn aceita até 30 ids por consulta
    private fun loadEvents(eventIds: List<String>) {
        val queries = eventIds.chunked(30).map { ids ->
            db.collection("events").whereIn(FieldPath.documentId(), ids).get()
        }

        Tasks.whenAllSuccess<QuerySnapshot>(queries)
            .addOnSuccessListener { results ->
                val events = results
                    .flatMap { it.toObjects(Event::class.java) }
                    .sortedBy { it.date }
                showEvents(events)
            }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun showEvents(events: List<Event>) {
        progressBar.visibility = View.GONE
        rvEvents.adapter = EventAdapter(events) { event -> openDetails(event) }
        layoutEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun openDetails(event: Event) {
        val intent = Intent(this, EventDetailsActivity::class.java)
        intent.putExtra(EventDetailsActivity.EXTRA_EVENT_ID, event.id)
        startActivity(intent)
    }

    private fun showError(error: Exception) {
        progressBar.visibility = View.GONE
        Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
    }
}
