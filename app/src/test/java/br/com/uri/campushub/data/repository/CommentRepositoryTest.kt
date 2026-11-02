package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Comment
import br.com.uri.campushub.testing.await
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do CommentRepository (data/repository): comentários em events/{eventId}/comments
// Firestore simulado pelo MockK; o Robolectric dá a thread principal usada pelo continueWith
@RunWith(RobolectricTestRunner::class)
class CommentRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val comments = mockk<CollectionReference>()
    private val commentDoc = mockk<DocumentReference>()
    private val repository = CommentRepository(db)

    @Before
    fun setUp() {
        every { db.collection("events").document("event-1").collection("comments") } returns comments
        every { comments.document("comment-1") } returns commentDoc
    }

    @Test
    fun `comments come newest first`() {
        val newestFirst = mockk<Query>()
        val snapshot = mockk<QuerySnapshot>()
        val list = listOf(Comment(id = "c2", text = "Segundo"), Comment(id = "c1", text = "Primeiro"))
        every { comments.orderBy("createdAt", Query.Direction.DESCENDING) } returns newestFirst
        every { newestFirst.get() } returns Tasks.forResult(snapshot)
        every { snapshot.toObjects(Comment::class.java) } returns list

        assertEquals(list, repository.getAll("event-1").await())
    }

    // o nome do autor vai junto, e a data é a do servidor, não a do celular
    @Test
    fun `new comment saves author, text and server date`() {
        val data = slot<Any>()
        every { comments.add(capture(data)) } returns Tasks.forResult(mockk())

        repository.add("event-1", "uid-ana", "Ana Souza", "Ótimo evento").await()

        val saved = data.captured as Map<*, *>
        assertEquals("uid-ana", saved["userId"])
        assertEquals("Ana Souza", saved["authorName"])
        assertEquals("Ótimo evento", saved["text"])
        assertEquals(FieldValue.serverTimestamp(), saved["createdAt"])
    }

    // editedAt preenchido é o que faz o comentário aparecer como "editado"
    @Test
    fun `editing changes the text and marks it as edited`() {
        val changes = mapOf("text" to "Texto corrigido", "editedAt" to FieldValue.serverTimestamp())
        every { commentDoc.update(changes) } returns Tasks.forResult(null)

        repository.update("event-1", "comment-1", "Texto corrigido").await()
        verify { commentDoc.update(changes) }
    }

    @Test
    fun `deleting removes the comment document`() {
        every { commentDoc.delete() } returns Tasks.forResult(null)

        repository.delete("event-1", "comment-1").await()
        verify { commentDoc.delete() }
    }
}
