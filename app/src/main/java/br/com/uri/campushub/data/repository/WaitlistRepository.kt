package br.com.uri.campushub.data.repository

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// lista de espera de um evento lotado em events/{eventId}/waitlist/{uid} (Cloud Firestore).
// O id do documento é o uid do aluno (cada um entra uma vez só) e o único campo é joinedAt,
// a hora do servidor em que ele entrou: a ordem da fila é a ordem desse campo.
// As regras (firestore.rules) só aceitam a entrada com o evento lotado e o aluno sem inscrição
class WaitlistRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    companion object {
        const val COLLECTION = "waitlist"
    }

    private fun waitlist(eventId: String) =
        db.collection("events").document(eventId).collection(COLLECTION)

    // entra no fim da fila; a hora vem do servidor, então ninguém fura a fila mudando o relógio
    fun join(uid: String, eventId: String): Task<Void> =
        waitlist(eventId).document(uid).set(mapOf("joinedAt" to FieldValue.serverTimestamp()))

    // sai da fila; se voltar depois, entra de novo no fim
    fun leave(uid: String, eventId: String): Task<Void> =
        waitlist(eventId).document(uid).delete()

    // posição do aluno na fila (1 = o próximo); 0 = não está na fila.
    // Lê a hora de entrada dele e conta no servidor (agregação count) quantos entraram até ela,
    // sem baixar os documentos da fila
    fun getPosition(uid: String, eventId: String): Task<Int> =
        waitlist(eventId).document(uid).get().onSuccessTask { snapshot ->
            val joinedAt = snapshot.getTimestamp("joinedAt")
                ?: return@onSuccessTask Tasks.forResult(0)
            waitlist(eventId).whereLessThanOrEqualTo("joinedAt", joinedAt).count()
                .get(AggregateSource.SERVER)
                .continueWith { task -> task.result.count.toInt() }
        }
}
