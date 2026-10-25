package br.com.uri.campushub.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// favoritos em users/{uid}/favorites/{eventId}
// fica separado das inscrições: favoritar não reserva vaga
class FavoriteRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun favorites(uid: String) =
        db.collection("users").document(uid).collection("favorites")

    fun isFavorite(uid: String, eventId: String): Task<Boolean> =
        favorites(uid).document(eventId).get().continueWith { task -> task.result.exists() }

    // o id do documento é o id do evento, então o mesmo evento nunca é favoritado duas vezes
    fun setFavorite(uid: String, eventId: String, favorite: Boolean): Task<Void> {
        val doc = favorites(uid).document(eventId)
        return if (favorite) {
            doc.set(hashMapOf(
                "eventId" to eventId,
                "favoritedAt" to FieldValue.serverTimestamp()
            ))
        } else {
            doc.delete()
        }
    }

    // ids dos eventos favoritados, para a tela Meus favoritos
    fun getEventIds(uid: String): Task<List<String>> =
        favorites(uid).get().continueWith { task -> task.result.documents.map { it.id } }
}
