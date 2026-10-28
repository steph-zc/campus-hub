package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.MediaPost
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// publicações em events/{eventId}/media; o arquivo em si fica no MediaStorage
class MediaRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun media(eventId: String) =
        db.collection("events").document(eventId).collection("media")

    // mais novas primeiro
    fun getAll(eventId: String): Task<List<MediaPost>> =
        media(eventId).orderBy("createdAt", Query.Direction.DESCENDING).get()
            .continueWith { task -> task.result.toObjects(MediaPost::class.java) }

    fun add(
        eventId: String,
        userId: String,
        authorName: String,
        type: String,
        fileName: String,
        durationMs: Long = 0
    ): Task<DocumentReference> =
        media(eventId).add(hashMapOf(
            "userId" to userId,
            "authorName" to authorName,
            "type" to type,
            "fileName" to fileName,
            "durationMs" to durationMs,
            "createdAt" to FieldValue.serverTimestamp()
        ))

    fun delete(eventId: String, postId: String): Task<Void> =
        media(eventId).document(postId).delete()
}
