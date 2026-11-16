package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventDraft
import br.com.uri.campushub.data.model.EventImageChange
import br.com.uri.campushub.testing.TestEvents
import br.com.uri.campushub.testing.TestEvents.event
import br.com.uri.campushub.testing.await
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do EventRepository (data/repository): leitura da coleção "events"
// Firestore simulado pelo MockK; o Robolectric dá a thread principal usada pelo continueWith
@RunWith(RobolectricTestRunner::class)
class EventRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val events = mockk<CollectionReference>()
    private val images = mockk<CollectionReference>()
    private lateinit var repository: EventRepository

    private companion object {
        // relógio fixo: a versão da imagem nova é sempre esta
        const val VERSION = 1_700_000_000_000L
    }

    @Before
    fun setUp() {
        every { db.collection("events") } returns events
        every { db.collection("eventImages") } returns images
        // o repositório guarda a coleção ao ser criado, então é criado depois de simulá-la
        repository = EventRepository(db) { VERSION }
    }

    // lote (WriteBatch) simulado que guarda as operações; o commit dá certo
    private fun recordingBatch(): WriteBatch {
        val batch = mockk<WriteBatch>(relaxed = true)
        every { db.batch() } returns batch
        every { batch.commit() } returns Tasks.forResult(null)
        return batch
    }

    // consulta falsa que devolve esses eventos
    private fun queryReturning(list: List<Event>): Query {
        val snapshot = mockk<QuerySnapshot>()
        every { snapshot.toObjects(Event::class.java) } returns list
        return mockk { every { get() } returns Tasks.forResult(snapshot) }
    }

    @Test
    fun `all events come ordered by date`() {
        val list = listOf(event(id = "a", daysFromNow = 1), event(id = "b", daysFromNow = 2))
        val ordered = queryReturning(list)
        every { events.orderBy("date") } returns ordered

        assertEquals(list, repository.getAll().await())
        verify { events.orderBy("date") }
    }

    @Test
    fun `one event is read by its id`() {
        val doc = mockk<DocumentReference>()
        val snapshot = mockk<DocumentSnapshot>()
        every { events.document("event-1") } returns doc
        every { doc.get() } returns Tasks.forResult(snapshot)
        every { snapshot.toObject(Event::class.java) } returns event(id = "event-1")

        assertEquals("event-1", repository.get("event-1").await()?.id)
    }

    @Test
    fun `deleted event comes back as null`() {
        val doc = mockk<DocumentReference>()
        val snapshot = mockk<DocumentSnapshot>()
        every { events.document("gone") } returns doc
        every { doc.get() } returns Tasks.forResult(snapshot)
        every { snapshot.toObject(Event::class.java) } returns null

        assertNull(repository.get("gone").await())
    }

    // Meus eventos vazio: nem chega a consultar o Firestore
    @Test
    fun `empty id list returns empty without querying`() {
        assertTrue(repository.getByIds(emptyList()).await().isEmpty())
        verify(exactly = 0) { events.whereIn(any<FieldPath>(), any()) }
    }

    // o whereIn do Firestore aceita até 30 ids, então 31 ids viram duas consultas
    @Test
    fun `many ids are split into queries of 30 and sorted by date`() {
        val ids = (1..31).map { "event-$it" }
        val askedParts = mutableListOf<List<*>>()
        every { events.whereIn(FieldPath.documentId(), any()) } answers {
            val part = secondArg<List<*>>()
            askedParts += part
            // cada evento i acontece daqui a (40 - i) dias: o Firestore devolve fora de ordem
            queryReturning(part.map { id ->
                val number = (id as String).removePrefix("event-").toInt()
                event(id = id, daysFromNow = 40 - number)
            })
        }

        val result = repository.getByIds(ids).await()

        assertEquals(listOf(30, 1), askedParts.map { it.size })
        assertEquals(31, result.size)
        // ordenados por data: o evento 31 (daqui a 9 dias) vem primeiro
        assertEquals("event-31", result.first().id)
        assertEquals("event-1", result.last().id)
    }

    // formulário preenchido pelo organizador, igual ao que a EventFormActivity monta
    private val draft = EventDraft(
        title = "Hackathon",
        description = "24 horas de programação",
        category = "Competição",
        date = Timestamp(TestEvents.daysFromNow(10)),
        location = "Laboratório 3",
        address = "Rua Batista Bonoto Sobrinho, 733",
        coordinates = GeoPoint(-29.19, -54.88),
        capacity = 60,
        course = "Ciência da Computação",
        openToAll = false
    )

    private val jpeg = byteArrayOf(1, 2, 3)

    // o Firestore gera o id; o documento leva o formulário, o dono, o contador zerado e imageVersion 0
    @Test
    fun `new event saves the form with its organizer and no enrollments`() {
        val doc = mockk<DocumentReference>()
        every { events.document() } returns doc
        every { doc.id } returns "novo-evento"
        val batch = recordingBatch()

        assertEquals("novo-evento", repository.create("uid-caio", draft).await())

        val expected = draft.toMap() + mapOf("organizerId" to "uid-caio", "enrolledCount" to 0, "imageVersion" to 0L)
        verify { batch.set(doc, expected) }
        // sem imagem, nada é gravado em eventImages
        verify(exactly = 0) { images.document(any()) }
    }

    // com imagem: o evento aponta para a versão e eventImages/{id} recebe o JPEG, no mesmo lote
    @Test
    fun `new event with image saves both in one batch`() {
        val doc = mockk<DocumentReference>()
        val imageDoc = mockk<DocumentReference>()
        every { events.document() } returns doc
        every { doc.id } returns "novo-evento"
        every { images.document("novo-evento") } returns imageDoc
        val batch = recordingBatch()

        repository.create("uid-caio", draft, jpeg).await()

        val expected = draft.toMap() + mapOf("organizerId" to "uid-caio", "enrolledCount" to 0, "imageVersion" to VERSION)
        verify { batch.set(doc, expected) }
        verify { batch.set(imageDoc, mapOf("jpeg" to Blob.fromBytes(jpeg), "version" to VERSION)) }
        verify(exactly = 1) { batch.commit() }
    }

    // a edição manda só os campos do formulário: organizerId, enrolledCount e imageVersion ficam de fora
    @Test
    fun `editing updates only the form fields`() {
        val doc = mockk<DocumentReference>()
        every { events.document("event-1") } returns doc
        val batch = recordingBatch()

        repository.update("event-1", draft).await()

        verify { batch.update(doc, draft.toMap()) }
        verify(exactly = 0) { images.document(any()) }
        assertFalse(draft.toMap().containsKey("organizerId"))
        assertFalse(draft.toMap().containsKey("enrolledCount"))
        assertFalse(draft.toMap().containsKey("imageVersion"))
    }

    // imagem trocada: versão nova no evento e o JPEG novo em eventImages, no mesmo lote
    @Test
    fun `replacing the image bumps the version with the new bytes`() {
        val doc = mockk<DocumentReference>()
        val imageDoc = mockk<DocumentReference>()
        every { events.document("event-1") } returns doc
        every { images.document("event-1") } returns imageDoc
        val batch = recordingBatch()

        repository.update("event-1", draft, EventImageChange.Replace(jpeg)).await()

        verify { batch.update(doc, draft.toMap() + ("imageVersion" to VERSION)) }
        verify { batch.set(imageDoc, mapOf("jpeg" to Blob.fromBytes(jpeg), "version" to VERSION)) }
    }

    // imagem removida: o evento volta para a versão 0 e o documento da imagem é apagado
    @Test
    fun `removing the image resets the version and deletes the bytes`() {
        val doc = mockk<DocumentReference>()
        val imageDoc = mockk<DocumentReference>()
        every { events.document("event-1") } returns doc
        every { images.document("event-1") } returns imageDoc
        val batch = recordingBatch()

        repository.update("event-1", draft, EventImageChange.Remove).await()

        verify { batch.update(doc, draft.toMap() + ("imageVersion" to 0L)) }
        verify { batch.delete(imageDoc) }
    }

    // a consulta filtra pelo dono; a ordem por data é feita no app (sem índice composto)
    @Test
    fun `organizer events are filtered by owner and sorted by date`() {
        val later = event(id = "later", daysFromNow = 5)
        val sooner = event(id = "sooner", daysFromNow = 2)
        every { events.whereEqualTo("organizerId", "uid-org") } returns queryReturning(listOf(later, sooner))

        val result = repository.getByOrganizer("uid-org").await()

        assertEquals(listOf("sooner", "later"), result.map { it.id })
        verify { events.whereEqualTo("organizerId", "uid-org") }
    }

    @Test
    fun `sample events are saved in a single batch`() {
        val batch = mockk<WriteBatch>(relaxed = true)
        every { db.batch() } returns batch
        every { events.document() } answers { mockk() }
        every { batch.commit() } returns Tasks.forResult(null)

        repository.saveSampleEvents().await()
        verify(exactly = 1) { batch.commit() }
    }

    @Test
    fun `sync with nothing to update finishes without writing`() {
        val batch = mockk<WriteBatch>(relaxed = true)
        every { db.batch() } returns batch

        repository.syncLocations(emptyList()).await()
        verify(exactly = 0) { batch.commit() }
    }

    // aluno que não é admin: as regras do Firestore negam a gravação dos eventos de exemplo
    @Test
    fun `permission denied is recognized only for that Firestore code`() {
        val denied = FirebaseFirestoreException("sem permissão", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        val offline = FirebaseFirestoreException("sem internet", FirebaseFirestoreException.Code.UNAVAILABLE)

        assertTrue(EventRepository.isPermissionDenied(denied))
        assertFalse(EventRepository.isPermissionDenied(offline))
        assertFalse(EventRepository.isPermissionDenied(Exception("outro erro")))
    }
}
