package br.com.uri.campushub.ui.events

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.repository.AuthRepository
import br.com.uri.campushub.data.repository.EnrollmentRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.ui.details.EventDetailsActivity
import com.google.android.gms.tasks.Task
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.firebase.firestore.FirebaseFirestore

// mostra os eventos de uma subcoleção do aluno: as inscrições (Meus eventos) ou os favoritos
class MyEventsActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_LIST = "list"
        const val LIST_ENROLLMENTS = "enrollments"
        const val LIST_FAVORITES = "favorites"
    }

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvEvents: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var imgEmpty: ImageView
    private lateinit var txtEmptyTitle: TextView
    private lateinit var txtEmptyHint: TextView
    private lateinit var btnBrowseEvents: Button
    private lateinit var progressBar: ProgressBar

    private val authRepository = AuthRepository()
    private val eventRepository = EventRepository()
    private val enrollmentRepository = EnrollmentRepository()
    private val db = FirebaseFirestore.getInstance()

    // qual lista mostrar; sem extra abre as inscrições
    private lateinit var list: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_events)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        rvEvents = findViewById(R.id.rvEvents)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        imgEmpty = findViewById(R.id.imgEmpty)
        txtEmptyTitle = findViewById(R.id.txtEmptyTitle)
        txtEmptyHint = findViewById(R.id.txtEmptyHint)
        btnBrowseEvents = findViewById(R.id.btnBrowseEvents)
        progressBar = findViewById(R.id.progressBar)

        list = intent.getStringExtra(EXTRA_LIST) ?: LIST_ENROLLMENTS
        if (list == LIST_FAVORITES) {
            toolbar.setTitle(R.string.my_favorites)
            imgEmpty.setImageResource(R.drawable.ic_favorite_border)
            txtEmptyTitle.setText(R.string.no_favorites)
            txtEmptyHint.setText(R.string.no_favorites_hint)
        }

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        rvEvents.layoutManager = LinearLayoutManager(this)
        applySystemBarsPadding()

        // a lista de eventos é a tela anterior, então basta fechar esta
        btnBrowseEvents.setOnClickListener { finish() }
    }

    // recarrega ao voltar dos detalhes, caso a inscrição ou o favorito tenha sido removido
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

    // 1º passo: os ids dos eventos inscritos ou favoritados; 2º passo: os eventos com esses ids
    private fun loadMyEvents() {
        val uid = authRepository.requireUserId()

        loadEventIds(uid)
            .onSuccessTask { ids -> eventRepository.getByIds(ids) }
            .addOnSuccessListener { events -> showEvents(events) }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun loadEventIds(uid: String): Task<List<String>> =
        if (list == LIST_FAVORITES) {
            db.collection("users").document(uid).collection("favorites").get()
                .continueWith { task -> task.result.documents.map { it.id } }
        } else {
            enrollmentRepository.getEventIds(uid)
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
