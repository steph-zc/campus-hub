package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Comment
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

// comentários em events/{eventId}/comments, visíveis para todos os alunos
// as datas vêm do servidor (serverTimestamp), não do relógio do celular
class CommentRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun comments(eventId: String) =
        db.collection("events").document(eventId).collection("comments")

    // mais novos primeiro
    fun getAll(eventId: String): Task<List<Comment>> =
        comments(eventId).orderBy("createdAt", Query.Direction.DESCENDING).get()
            .continueWith { task -> task.result.toObjects(Comment::class.java) }

    // o nome do autor fica gravado no comentário, para não buscar o perfil de cada um
    fun add(eventId: String, userId: String, authorName: String, text: String): Task<DocumentReference> =
        comments(eventId).add(hashMapOf(
            "userId" to userId,
            "authorName" to authorName,
            "text" to text,
            "createdAt" to FieldValue.serverTimestamp()
        ))

    // editedAt preenchido faz o comentário aparecer como "editado"
    fun update(eventId: String, commentId: String, text: String): Task<Void> =
        comments(eventId).document(commentId).update(mapOf(
            "text" to text,
            "editedAt" to FieldValue.serverTimestamp()
        ))

    fun delete(eventId: String, commentId: String): Task<Void> =
        comments(eventId).document(commentId).delete()
}
