package br.com.uri.campushub.data.repository

import br.com.uri.campushub.testing.await
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do FavoriteRepository (data/repository): favoritos em users/{uid}/favorites/{eventId}
// Firestore simulado pelo MockK; o Robolectric dá a thread principal usada pelo continueWith
@RunWith(RobolectricTestRunner::class)
class FavoriteRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val favorites = mockk<CollectionReference>()
    private val favoriteDoc = mockk<DocumentReference>()
    private val repository = FavoriteRepository(db)

    @Before
    fun setUp() {
        every { db.collection("users").document("uid-ana").collection("favorites") } returns favorites
        every { favorites.document("event-1") } returns favoriteDoc
    }

    @Test
    fun `event is favorite when its document exists`() {
        val snapshot = mockk<DocumentSnapshot>()
        every { favoriteDoc.get() } returns Tasks.forResult(snapshot)
        every { snapshot.exists() } returns true

        assertTrue(repository.isFavorite("uid-ana", "event-1").await())
    }

    @Test
    fun `event is not favorite when its document is missing`() {
        val snapshot = mockk<DocumentSnapshot>()
        every { favoriteDoc.get() } returns Tasks.forResult(snapshot)
        every { snapshot.exists() } returns false

        assertFalse(repository.isFavorite("uid-ana", "event-1").await())
    }

    // o id do documento é o id do evento, então favoritar de novo só regrava o mesmo documento
    @Test
    fun `favoriting saves the event id with the server date`() {
        val data = slot<Any>()
        every { favoriteDoc.set(capture(data)) } returns Tasks.forResult(null)

        repository.setFavorite("uid-ana", "event-1", favorite = true).await()

        val saved = data.captured as Map<*, *>
        assertEquals("event-1", saved["eventId"])
        assertEquals(FieldValue.serverTimestamp(), saved["favoritedAt"])
    }

    @Test
    fun `unfavoriting deletes the document`() {
        every { favoriteDoc.delete() } returns Tasks.forResult(null)

        repository.setFavorite("uid-ana", "event-1", favorite = false).await()
        verify { favoriteDoc.delete() }
    }

    @Test
    fun `favorite ids are the document ids`() {
        val snapshot = mockk<QuerySnapshot>()
        val first = mockk<DocumentSnapshot> { every { id } returns "event-1" }
        val second = mockk<DocumentSnapshot> { every { id } returns "event-7" }
        every { favorites.get() } returns Tasks.forResult(snapshot)
        every { snapshot.documents } returns listOf(first, second)

        assertEquals(listOf("event-1", "event-7"), repository.getEventIds("uid-ana").await())
    }
}
