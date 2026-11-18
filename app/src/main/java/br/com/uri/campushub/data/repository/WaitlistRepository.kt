package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.domain.CheckinCode
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Transaction
import java.util.Date

// lista de espera de um evento lotado, com senhas numeradas (Cloud Firestore):
// - events/{eventId}/waitlist/{uid}: o aluno na fila, com a senha (ticket) e a hora em que entrou.
//   O id é o uid, então cada aluno tem uma senha só;
// - events/{eventId}/waitlistTickets/{ticket}: a senha apontando para o aluno (userId),
//   para as regras do Firestore descobrirem quem é o primeiro da fila;
// - no evento, waitlistNext (próxima senha a entregar) e waitlistHead (próxima a atender);
// - events/{eventId}/attendees/{uid} (AttendeeRepository): o aluno aparece na fila da tela do organizador
//   e passa para os inscritos quando é atendido. Lugares com hasAttendee = true têm esse documento;
//   os de versões anteriores não, e o próprio aluno cria ao abrir o evento (AttendeeRepository.sync).
// Quando uma vaga abre, settle() inscreve automaticamente o dono da senha waitlistHead.
// Não há servidor próprio: quem roda o settle é o app de quem liberou a vaga (cancelamento ou aumento
// de capacidade) ou de quem abrir o evento, e o firestore.rules confere cada passo
// newToken gera o segredo do QR de check-in (CheckinCode), que o aluno usa se for inscrito pela fila;
// injetável para os testes
class WaitlistRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val newToken: () -> String = { CheckinCode.newToken() }
) {

    companion object {
        const val COLLECTION = "waitlist"
        const val TICKETS = "waitlistTickets"

        // limite de passos de um settle, para nunca ficar em laço (cada passo atende uma senha)
        private const val MAX_STEPS = 50
    }

    private fun eventDoc(eventId: String) = db.collection("events").document(eventId)
    private fun waitlist(eventId: String) = eventDoc(eventId).collection(COLLECTION)
    private fun ticketDoc(eventId: String, ticket: Int) = eventDoc(eventId).collection(TICKETS).document(ticket.toString())

    // entra no fim da fila: pega a senha waitlistNext e avança o contador, numa transação
    // (dois alunos entrando juntos recebem senhas diferentes; o Firestore repete uma das transações).
    // Um lugar antigo sem senha (da versão anterior da fila) é apagado antes
    fun join(uid: String, eventId: String): Task<Void?> {
        val entry = waitlist(eventId).document(uid)
        return entry.get()
            .onSuccessTask { snapshot ->
                if (snapshot.exists() && !snapshot.contains("ticket")) entry.delete() else Tasks.forResult(null)
            }
            .onSuccessTask { takeTicket(uid, eventId) }
    }

    // lê o evento (senha da vez) e o perfil do aluno (nome e curso para a tela do organizador) e grava junto:
    // o lugar, a senha, o contador e o aluno na fila da lista do organizador
    private fun takeTicket(uid: String, eventId: String): Task<Void?> =
        db.runTransaction { transaction ->
            val event = transaction.get(eventDoc(eventId)).toObject(Event::class.java)!!
            val user = transaction.get(db.collection("users").document(uid)).toObject(User::class.java) ?: User()
            val ticket = event.waitlistNext
            transaction.set(
                waitlist(eventId).document(uid),
                mapOf("ticket" to ticket, "joinedAt" to FieldValue.serverTimestamp(), "hasAttendee" to true)
            )
            transaction.set(ticketDoc(eventId, ticket), mapOf("userId" to uid))
            transaction.update(eventDoc(eventId), "waitlistNext", ticket + 1)
            transaction.set(AttendeeRepository.doc(db, eventId, uid), AttendeeRepository.data(user, Attendee.WAITING, newToken()))
            null
        }

    // sai da fila: apaga o lugar, a senha e o aluno da lista do organizador juntos;
    // a senha vira um buraco que o settle pula
    fun leave(uid: String, eventId: String): Task<Void> {
        val entry = waitlist(eventId).document(uid)
        return entry.get().onSuccessTask { snapshot ->
            val batch = db.batch()
            batch.delete(entry)
            snapshot.getLong("ticket")?.let { batch.delete(ticketDoc(eventId, it.toInt())) }
            batch.delete(AttendeeRepository.doc(db, eventId, uid))
            batch.commit()
        }
    }

    // posição do aluno na fila (1 = o próximo); 0 = não está na fila.
    // Conta no servidor (agregação count) quantas senhas até a dele ainda estão na fila
    fun getPosition(uid: String, eventId: String): Task<Int> =
        waitlist(eventId).document(uid).get().onSuccessTask { snapshot ->
            val ticket = snapshot.getLong("ticket") ?: return@onSuccessTask Tasks.forResult(0)
            waitlist(eventId).whereLessThanOrEqualTo("ticket", ticket).count()
                .get(AggregateSource.SERVER)
                .continueWith { task -> task.result.count.toInt() }
        }

    // atende a fila enquanto houver vaga: inscreve o dono da senha da vez ou pula senhas de quem saiu.
    // Devolve quantos alunos foram inscritos. Para sozinho quando a fila acaba, as vagas acabam,
    // o evento não está publicado ou já começou
    fun settle(eventId: String): Task<Int> = step(eventId, promoted = 0, steps = 0)

    private fun step(eventId: String, promoted: Int, steps: Int): Task<Int> {
        if (steps >= MAX_STEPS) return Tasks.forResult(promoted)
        return db.runTransaction { transaction -> settleOnce(transaction, eventId) }
            .onSuccessTask { result ->
                when (result) {
                    Step.PROMOTED -> step(eventId, promoted + 1, steps + 1)
                    Step.SKIPPED -> step(eventId, promoted, steps + 1)
                    else -> Tasks.forResult(promoted)
                }
            }
    }

    // resultado de um passo do settle
    private enum class Step { PROMOTED, SKIPPED, DONE }

    // um passo, dentro de uma transação: lê o evento e a senha da vez e decide
    private fun settleOnce(transaction: Transaction, eventId: String): Step {
        val eventRef = eventDoc(eventId)
        val event = transaction.get(eventRef).toObject(Event::class.java) ?: return Step.DONE
        val acceptsQueue = event.currentStatus() == EventStatus.PUBLISHED && !event.hasStarted(Date())
        if (!event.hasQueue() || !acceptsQueue) return Step.DONE

        val head = event.waitlistHead
        val ticketRef = ticketDoc(eventId, head)
        val ticket = transaction.get(ticketRef)
        val uid = ticket.getString("userId")

        // senha de quem saiu da fila: só avança para a próxima
        if (uid == null) {
            transaction.update(eventRef, "waitlistHead", head + 1)
            return Step.SKIPPED
        }
        if (event.remainingSpots() == 0) return Step.DONE

        // o lugar diz se o aluno tem documento na lista do organizador (lugares antigos não têm);
        // lido antes das gravações, como a transação exige
        val entryRef = waitlist(eventId).document(uid)
        val hasAttendee = transaction.get(entryRef).getBoolean("hasAttendee") == true

        // inscreve o primeiro da fila: inscrição marcada como vinda da fila (para o aviso),
        // vaga ocupada, senha atendida e o aluno fora da fila, tudo junto
        val enrollmentRef = db.collection("users").document(uid).collection("enrollments").document(eventId)
        transaction.set(
            enrollmentRef,
            mapOf("eventId" to eventId, "enrolledAt" to FieldValue.serverTimestamp(), "fromWaitlist" to true)
        )
        transaction.update(
            eventRef,
            mapOf("enrolledCount" to event.enrolledCount + 1, "waitlistHead" to head + 1)
        )
        transaction.delete(ticketRef)
        transaction.delete(entryRef)
        // na tela do organizador, o aluno sai da fila e passa para os inscritos
        if (hasAttendee) {
            transaction.update(
                AttendeeRepository.doc(db, eventId, uid),
                mapOf("status" to Attendee.ENROLLED, "since" to FieldValue.serverTimestamp())
            )
        }
        return Step.PROMOTED
    }
}
