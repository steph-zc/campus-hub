package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.domain.CheckinCode
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

// inscrições em users/{uid}/enrollments/{eventId} e o contador enrolledCount do evento
// o id da inscrição é o id do evento, então cada aluno só tem uma por evento.
// Junto vai o documento do aluno na lista do organizador (events/{eventId}/attendees/{uid},
// AttendeeRepository), com o nome e o curso lidos do perfil users/{uid}.
// Inscrições feitas pela lista de espera (WaitlistRepository.settle) vêm com fromWaitlist = true
// até o aluno ver o aviso (takePromotionNotice)
// newToken gera o segredo do QR de check-in (CheckinCode); injetável para os testes
class EnrollmentRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val newToken: () -> String = { CheckinCode.newToken() }
) {

    companion object {
        private const val SOLD_OUT = "sold_out"
        private const val CLOSED = "enrollments_closed"

        // a tela usa para mostrar "Vagas esgotadas" em vez da mensagem técnica
        fun isSoldOut(error: Exception) = hasMessage(error, SOLD_OUT)

        // o organizador encerrou as inscrições enquanto a tela do aluno estava aberta
        fun isClosed(error: Exception) = hasMessage(error, CLOSED)

        // o Firestore às vezes embrulha o erro da transação em outro
        private fun hasMessage(error: Exception, message: String) =
            error.message == message || error.cause?.message == message
    }

    private fun eventDoc(eventId: String) = db.collection("events").document(eventId)

    private fun enrollmentDoc(uid: String, eventId: String) =
        db.collection("users").document(uid).collection("enrollments").document(eventId)

    fun isEnrolled(uid: String, eventId: String): Task<Boolean> =
        enrollmentDoc(uid, eventId).get().continueWith { task -> task.result.exists() }

    // ids dos eventos em que o aluno está inscrito
    fun getEventIds(uid: String): Task<List<String>> =
        db.collection("users").document(uid).collection("enrollments").get()
            .continueWith { task -> task.result.documents.map { it.id } }

    // a transação lê e grava junto: se dois alunos pegarem a última vaga
    // ao mesmo tempo, o Firestore repete uma delas e ela encontra o evento lotado
    fun enroll(uid: String, eventId: String): Task<Void?> {
        val eventRef = eventDoc(eventId)
        val enrollmentRef = enrollmentDoc(uid, eventId)
        val userRef = db.collection("users").document(uid)
        val attendeeRef = AttendeeRepository.doc(db, eventId, uid)

        return db.runTransaction { transaction ->
            // a transação lê tudo antes de gravar: evento, inscrição e o perfil (nome e curso para o organizador)
            val event = transaction.get(eventRef).toObject(Event::class.java)!!
            val enrollment = transaction.get(enrollmentRef)
            val user = transaction.get(userRef).toObject(User::class.java) ?: User()

            if (enrollment.exists()) {
                return@runTransaction null
            }
            // rascunho ou inscrições encerradas: as regras recusariam; o erro próprio vira um aviso claro
            if (event.currentStatus() != EventStatus.PUBLISHED) {
                throw FirebaseFirestoreException(CLOSED, FirebaseFirestoreException.Code.ABORTED)
            }
            // sem vaga, ou com fila: a vaga que abrir é do primeiro da lista de espera
            if (event.remainingSpots() == 0 || event.hasQueue()) {
                throw FirebaseFirestoreException(SOLD_OUT, FirebaseFirestoreException.Code.ABORTED)
            }

            val data = hashMapOf(
                "eventId" to eventId,
                "enrolledAt" to FieldValue.serverTimestamp()
            )
            transaction.set(enrollmentRef, data)
            transaction.update(eventRef, "enrolledCount", FieldValue.increment(1))
            transaction.set(attendeeRef, AttendeeRepository.data(user, Attendee.ENROLLED, newToken()))
            null
        }
    }

    // true uma vez só, quando o aluno foi inscrito automaticamente pela lista de espera:
    // lê a inscrição e apaga a marca fromWaitlist, para o aviso não aparecer de novo
    fun takePromotionNotice(uid: String, eventId: String): Task<Boolean> {
        val ref = enrollmentDoc(uid, eventId)
        return ref.get().onSuccessTask { snapshot ->
            if (snapshot.getBoolean("fromWaitlist") != true) return@onSuccessTask Tasks.forResult(false)
            ref.update("fromWaitlist", FieldValue.delete()).continueWith { task ->
                task.result
                true
            }
        }
    }

    // a vaga liberada vai para a lista de espera: depois de cancelar, a tela chama o WaitlistRepository.settle.
    // O aluno sai da lista do organizador na mesma transação
    fun cancel(uid: String, eventId: String): Task<Void?> {
        val eventRef = eventDoc(eventId)
        val enrollmentRef = enrollmentDoc(uid, eventId)
        val attendeeRef = AttendeeRepository.doc(db, eventId, uid)

        return db.runTransaction { transaction ->
            val enrollment = transaction.get(enrollmentRef)

            // só devolve a vaga se a inscrição existia mesmo
            if (enrollment.exists()) {
                transaction.delete(enrollmentRef)
                transaction.update(eventRef, "enrolledCount", FieldValue.increment(-1))
                transaction.delete(attendeeRef)
            }
            null
        }
    }
}
