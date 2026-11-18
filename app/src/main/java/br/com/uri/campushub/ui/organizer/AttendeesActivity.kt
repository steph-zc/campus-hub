package br.com.uri.campushub.ui.organizer

import android.graphics.Rect
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.repository.AttendeeRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.domain.AttendeeOverview
import br.com.uri.campushub.domain.CheckinRules
import br.com.uri.campushub.ui.checkin.CheckinResultDialog
import br.com.uri.campushub.ui.checkin.GmsQrScanner
import br.com.uri.campushub.ui.checkin.QrScanner
import br.com.uri.campushub.ui.common.applySystemBarsPadding
import br.com.uri.campushub.ui.common.setupToolbar
import com.google.android.gms.tasks.Tasks
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

// inscritos e lista de espera de um evento, para o organizador acompanhar vagas ocupadas, fila e check-ins.
// Aberta pelo botão "Ver inscritos" dos detalhes, que só aparece para o organizador dono.
// Lê o evento (EventRepository) e os participantes (AttendeeRepository, events/{eventId}/attendees no
// Firestore); as regras do Firestore só liberam a lista para o dono, então outra conta recebe erro.
// O AttendeeOverview (domain) ordena e conta; o AttendeeAdapter desenha.
// O botão "Fazer check-in" lê o QR do aluno (QrScanner), confere pelo CheckinRules e grava
// pelo AttendeeRepository.checkIn; o CheckinResultDialog mostra o resultado
class AttendeesActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_EVENT_ID = "event_id"

        // leitor de QR usado pela tela; os testes do aparelho trocam por uma leitura simulada
        @VisibleForTesting
        var scannerFactory: () -> QrScanner = { GmsQrScanner() }
    }

    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvAttendees: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var fabCheckin: ExtendedFloatingActionButton

    private val eventRepository = EventRepository()
    private val attendeeRepository = AttendeeRepository()
    private val scanner = scannerFactory()

    private lateinit var eventId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_attendees)

        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        rvAttendees = findViewById(R.id.rvAttendees)
        progressBar = findViewById(R.id.progressBar)
        fabCheckin = findViewById(R.id.fabCheckin)

        setupToolbar(toolbar)
        rvAttendees.layoutManager = LinearLayoutManager(this)
        // espaço depois do último aluno, para o botão de check-in não cobrir a etiqueta dele
        val fabSpace = resources.getDimensionPixelSize(R.dimen.fab_clearance)
        rvAttendees.addItemDecoration(object : RecyclerView.ItemDecoration() {
            override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                if (parent.getChildAdapterPosition(view) == state.itemCount - 1) outRect.bottom = fabSpace
            }
        })
        applySystemBarsPadding(findViewById(R.id.root), appBar, rvAttendees)

        eventId = intent.getStringExtra(EXTRA_EVENT_ID)!!
        fabCheckin.setOnClickListener { scan() }
    }

    // recarrega ao voltar para a tela: alunos se inscrevem e cancelam enquanto o organizador acompanha
    override fun onResume() {
        super.onResume()
        load()
    }

    private fun isGone() = isFinishing || isDestroyed

    // busca o evento e os participantes ao mesmo tempo e espera os dois
    private fun load() {
        val eventTask = eventRepository.get(eventId)
        val attendeesTask = attendeeRepository.getAll(eventId)
        Tasks.whenAllSuccess<Any>(eventTask, attendeesTask)
            .addOnSuccessListener {
                // a tela foi fechada enquanto o Firestore respondia
                if (isGone()) return@addOnSuccessListener
                val event = eventTask.result
                if (event == null) {
                    Toast.makeText(this, R.string.event_not_found, Toast.LENGTH_LONG).show()
                    finish()
                    return@addOnSuccessListener
                }
                rvAttendees.adapter = AttendeeAdapter(event, AttendeeOverview.of(event, attendeesTask.result))
                fabCheckin.visibility = if (CheckinRules.canCheckIn(event)) View.VISIBLE else View.GONE
                progressBar.visibility = View.GONE
                rvAttendees.visibility = View.VISIBLE
            }
            .addOnFailureListener { error ->
                if (isGone()) return@addOnFailureListener
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
                finish()
            }
    }

    // abre a câmera; o texto lido segue para a conferência
    private fun scan() {
        scanner.scan(
            this,
            onCode = { text -> checkIn(text) },
            // falha ao preparar ou abrir a câmera: janela em vez de Toast, que corta textos longos
            onFailure = { error ->
                if (!isGone()) {
                    AlertDialog.Builder(this)
                        .setTitle(R.string.scanner_unavailable_title)
                        .setMessage(error.message)
                        .setPositiveButton(R.string.close, null)
                        .show()
                }
            }
        )
    }

    // confere se é um QR deste evento (CheckinRules.read) e registra no Firestore
    // (AttendeeRepository.checkIn, numa transação); o resultado aparece numa janela
    private fun checkIn(text: String) {
        val (payload, refusal) = CheckinRules.read(text, eventId)
        if (payload == null) {
            showResult(refusal ?: CheckinRules.Outcome.NotACheckinCode)
            return
        }
        fabCheckin.isEnabled = false
        attendeeRepository.checkIn(eventId, payload)
            .addOnSuccessListener { outcome ->
                if (isGone()) return@addOnSuccessListener
                fabCheckin.isEnabled = true
                showResult(outcome)
                // a lista e os números mudam com o check-in novo
                if (outcome is CheckinRules.Outcome.Done) load()
            }
            .addOnFailureListener { error ->
                if (isGone()) return@addOnFailureListener
                fabCheckin.isEnabled = true
                // sem internet ou recusado pelas regras (por exemplo, o evento foi encerrado)
                Toast.makeText(this, error.message, Toast.LENGTH_LONG).show()
            }
    }

    private fun showResult(outcome: CheckinRules.Outcome) {
        CheckinResultDialog.show(this, outcome) { scan() }
    }
}
