package br.com.uri.campushub.ui.organizer

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.repository.AttendeeRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.domain.AttendeeOverview
import br.com.uri.campushub.ui.common.applySystemBarsPadding
import br.com.uri.campushub.ui.common.setupToolbar
import com.google.android.gms.tasks.Tasks
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar

// inscritos e lista de espera de um evento, para o organizador acompanhar vagas ocupadas, fila e check-ins.
// Aberta pelo botão "Ver inscritos" dos detalhes, que só aparece para o organizador dono.
// Lê o evento (EventRepository) e os participantes (AttendeeRepository, events/{eventId}/attendees no
// Firestore); as regras do Firestore só liberam a lista para o dono, então outra conta recebe erro.
// O AttendeeOverview (domain) ordena e conta; o AttendeeAdapter desenha
class AttendeesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvAttendees: RecyclerView
    private lateinit var progressBar: ProgressBar

    private val eventRepository = EventRepository()
    private val attendeeRepository = AttendeeRepository()

    private lateinit var eventId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendees)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        rvAttendees = findViewById(R.id.rvAttendees)
        progressBar = findViewById(R.id.progressBar)

        setupToolbar(toolbar)
        rvAttendees.layoutManager = LinearLayoutManager(this)
        applySystemBarsPadding(findViewById(R.id.root), appBar, rvAttendees)

        eventId = intent.getStringExtra(EXTRA_EVENT_ID)!!
    }

    // recarrega ao voltar para a tela: alunos se inscrevem e cancelam enquanto o organizador acompanha
    override fun onResume() {
        super.onResume()
        load()
    }

    // busca o evento e os participantes ao mesmo tempo e espera os dois
    private fun load() {
        val eventTask = eventRepository.get(eventId)
        val attendeesTask = attendeeRepository.getAll(eventId)
        Tasks.whenAllSuccess<Any>(eventTask, attendeesTask)
            .addOnSuccessListener {
                // a tela foi fechada enquanto o Firestore respondia
                if (isFinishing || isDestroyed) return@addOnSuccessListener
                val event = eventTask.result
                if (event == null) {
                    Toast.makeText(this, R.string.event_not_found, Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                rvAttendees.adapter = AttendeeAdapter(event, AttendeeOverview.of(event, attendeesTask.result))
                progressBar.visibility = View.GONE
                rvAttendees.visibility = View.VISIBLE
            }
            .addOnFailureListener { error ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
                finish()
            }
    }
}
