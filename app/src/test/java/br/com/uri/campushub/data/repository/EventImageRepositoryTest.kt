package br.com.uri.campushub.data.repository

import br.com.uri.campushub.testing.await
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do EventImageRepository (data/repository): leitura da coleção "eventImages".
// Firestore simulado pelo MockK; o Robolectric dá a thread principal usada pelo continueWith
@RunWith(RobolectricTestRunner::class)
class EventImageRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val images = mockk<CollectionReference>()
    private val doc = mockk<DocumentReference>()
    private val snapshot = mockk<DocumentSnapshot>()
    private val repository = EventImageRepository(db)

    private fun imageOf(eventId: String, blob: Blob?) {
        every { db.collection("eventImages") } returns images
        every { images.document(eventId) } returns doc
        every { doc.get() } returns Tasks.forResult(snapshot)
        every { snapshot.getBlob("jpeg") } returns blob
    }

    @Test
    fun `saved image comes back as jpeg bytes`() {
        imageOf("event-1", Blob.fromBytes(byteArrayOf(1, 2, 3)))

        assertArrayEquals(byteArrayOf(1, 2, 3), repository.read("event-1").await())
    }

    // documento inexistente (evento sem imagem): null, e o Glide mostra a view vazia
    @Test
    fun `missing image comes back as null`() {
        imageOf("event-2", null)

        assertNull(repository.read("event-2").await())
    }

    // campos gravados pelo EventRepository: os mesmos nomes conferidos no firestore.rules
    @Test
    fun `image fields hold the bytes and the version`() {
        val fields = EventImageRepository.fields(byteArrayOf(9), 42L)

        assertEquals(mapOf("jpeg" to Blob.fromBytes(byteArrayOf(9)), "version" to 42L), fields)
    }
}
