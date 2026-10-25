package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.RatingSummary
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// avaliações em events/{eventId}/ratings/{uid}
// o id da avaliação é o uid do aluno: cada aluno tem no máximo uma por evento
class RatingRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun ratings(eventId: String) =
        db.collection("events").document(eventId).collection("ratings")

    // 0 = o aluno ainda não avaliou
    fun getMyRating(uid: String, eventId: String): Task<Int> =
        ratings(eventId).document(uid).get()
            .continueWith { task -> task.result.getLong("value")?.toInt() ?: 0 }

    // set no mesmo documento: a primeira vez cria, as próximas trocam a nota
    fun save(uid: String, eventId: String, value: Int): Task<Void> =
        ratings(eventId).document(uid).set(hashMapOf(
            "userId" to uid,
            "value" to value,
            "updatedAt" to FieldValue.serverTimestamp()
        ))

    // o próprio Firestore calcula a média e a quantidade, sem baixar as avaliações uma a uma
    fun getSummary(eventId: String): Task<RatingSummary> {
        val average = AggregateField.average("value")
        val count = AggregateField.count()

        return ratings(eventId).aggregate(average, count).get(AggregateSource.SERVER)
            .continueWith { task ->
                val result = task.result
                RatingSummary(result.get(average) ?: 0.0, result.get(count))
            }
    }
}
