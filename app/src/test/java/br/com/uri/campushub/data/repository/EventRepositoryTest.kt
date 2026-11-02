package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.testing.TestEvents.event
import br.com.uri.campushub.testing.await
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
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
    private lateinit var repository: EventRepository

    @Before
    fun setUp() {
        every { db.collection("events") } returns events
        // o repositório guarda a coleção ao ser criado, então é criado depois de simulá-la
        repository = EventRepository(db)
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
}
