package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.MediaPost
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

// testes do MediaRepository (data/repository): publicações em events/{eventId}/media
// só os dados vão para o Firestore; o arquivo fica no MediaStorage (testado à parte)
// Firestore simulado pelo MockK; o Robolectric dá a thread principal usada pelo continueWith
@RunWith(RobolectricTestRunner::class)
class MediaRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val media = mockk<CollectionReference>()
    private val repository = MediaRepository(db)

    @Before
    fun setUp() {
        every { db.collection("events").document("event-1").collection("media") } returns media
    }

    @Test
    fun `posts come newest first`() {
        val newestFirst = mockk<Query>()
        val snapshot = mockk<QuerySnapshot>()
        val list = listOf(MediaPost(id = "p2"), MediaPost(id = "p1"))
        every { media.orderBy("createdAt", Query.Direction.DESCENDING) } returns newestFirst
        every { newestFirst.get() } returns Tasks.forResult(snapshot)
        every { snapshot.toObjects(MediaPost::class.java) } returns list

        assertEquals(list, repository.getAll("event-1").await())
    }

    @Test
    fun `new video saves author, file, duration and server date`() {
        val data = slot<Any>()
        every { media.add(capture(data)) } returns Tasks.forResult(mockk())

        repository.add("event-1", "uid-ana", "Ana Souza", MediaPost.TYPE_VIDEO, "abc.mp4", 8_000).await()

        val saved = data.captured as Map<*, *>
        assertEquals("uid-ana", saved["userId"])
        assertEquals("Ana Souza", saved["authorName"])
        assertEquals("video", saved["type"])
        assertEquals("abc.mp4", saved["fileName"])
        assertEquals(8_000L, saved["durationMs"])
        assertEquals(FieldValue.serverTimestamp(), saved["createdAt"])
    }

    // foto não tem duração: o valor padrão é 0
    @Test
    fun `new photo is saved with zero duration`() {
        val data = slot<Any>()
        every { media.add(capture(data)) } returns Tasks.forResult(mockk())

        repository.add("event-1", "uid-ana", "Ana Souza", MediaPost.TYPE_PHOTO, "abc.jpg").await()

        assertEquals(0L, (data.captured as Map<*, *>)["durationMs"])
    }

    @Test
    fun `deleting removes the post document`() {
        val doc = mockk<DocumentReference>()
        every { media.document("p1") } returns doc
        every { doc.delete() } returns Tasks.forResult(null)

        repository.delete("event-1", "p1").await()
        verify { doc.delete() }
    }
}
