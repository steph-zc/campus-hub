package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventStatus
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

// inscrições em users/{uid}/enrollments/{eventId} e o contador enrolledCount do evento
// o id da inscrição é o id do evento, então cada aluno só tem uma por evento
class EnrollmentRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

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

        return db.runTransaction { transaction ->
            val event = transaction.get(eventRef).toObject(Event::class.java)!!
            val enrollment = transaction.get(enrollmentRef)

            if (enrollment.exists()) {
                return@runTransaction null
            }
            // rascunho ou inscrições encerradas: as regras recusariam; o erro próprio vira um aviso claro
            if (event.currentStatus() != EventStatus.PUBLISHED) {
                throw FirebaseFirestoreException(CLOSED, FirebaseFirestoreException.Code.ABORTED)
            }
            if (event.remainingSpots() == 0) {
                throw FirebaseFirestoreException(SOLD_OUT, FirebaseFirestoreException.Code.ABORTED)
            }

            val data = hashMapOf(
                "eventId" to eventId,
                "enrolledAt" to FieldValue.serverTimestamp()
            )
            transaction.set(enrollmentRef, data)
            transaction.update(eventRef, "enrolledCount", FieldValue.increment(1))
            null
        }
    }

    fun cancel(uid: String, eventId: String): Task<Void?> {
        val eventRef = eventDoc(eventId)
        val enrollmentRef = enrollmentDoc(uid, eventId)

        return db.runTransaction { transaction ->
            val enrollment = transaction.get(enrollmentRef)

            // só devolve a vaga se a inscrição existia mesmo
            if (enrollment.exists()) {
                transaction.delete(enrollmentRef)
                transaction.update(eventRef, "enrolledCount", FieldValue.increment(-1))
            }
            null
        }
    }
}
