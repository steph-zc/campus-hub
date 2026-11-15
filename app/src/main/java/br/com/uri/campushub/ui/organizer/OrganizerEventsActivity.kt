package br.com.uri.campushub.ui.organizer

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.repository.AuthRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.ui.common.applySystemBarsPadding
import br.com.uri.campushub.ui.common.setupToolbar
import br.com.uri.campushub.ui.details.EventDetailsActivity
import br.com.uri.campushub.ui.events.EventAdapter
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

// área do organizador: os eventos sob a responsabilidade da conta logada.
// Só é aberta pelo menu da lista de eventos quando o UserRepository diz que a conta é organizadora;
// mesmo que alguém abra a tela sendo aluno, a consulta não traz nada (nenhum evento tem o uid dele)
// e as regras do Firestore continuam barrando qualquer gravação.
// Usa o AuthRepository (uid logado), o EventRepository (consulta por organizerId) e o EventAdapter da lista
class OrganizerEventsActivity : AppCompatActivity() {

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvEvents: RecyclerView
    private lateinit var layoutEmpty: LinearLayout
    private lateinit var progressBar: ProgressBar

    private val authRepository = AuthRepository()
    private val eventRepository = EventRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_events)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        rvEvents = findViewById(R.id.rvEvents)
        layoutEmpty = findViewById(R.id.layoutEmpty)
        progressBar = findViewById(R.id.progressBar)

        setupToolbar(toolbar)
        rvEvents.layoutManager = LinearLayoutManager(this)
        applySystemBarsPadding(findViewById(R.id.root), appBar, rvEvents)
    }

    // recarrega ao voltar dos detalhes: as vagas ocupadas mudam com as inscrições
    override fun onResume() {
        super.onResume()
        loadEvents()
    }

    // consulta no Firestore os eventos com organizerId igual ao uid da conta logada
    private fun loadEvents() {
        eventRepository.getByOrganizer(authRepository.requireUserId())
            .addOnSuccessListener { events -> showEvents(events) }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun showEvents(events: List<Event>) {
        progressBar.visibility = View.GONE
        rvEvents.adapter = EventAdapter(events) { event -> openDetails(event) }
        layoutEmpty.visibility = if (events.isEmpty()) View.VISIBLE else View.GONE
    }

    // por enquanto abre os mesmos detalhes que o aluno vê; o painel do organizador vem depois
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
