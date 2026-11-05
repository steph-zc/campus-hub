package br.com.uri.campushub.data

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.testing.await
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do SampleEvents (data): os 12 eventos gravados na primeira execução e a sincronização
// dos locais dos eventos já gravados. O Firestore e o lote de escrita (WriteBatch) são simulados
// pelo MockK; os eventos gravados são capturados para o teste conferir o conteúdo.
@RunWith(RobolectricTestRunner::class)
class SampleEventsTest {

    private val db = mockk<FirebaseFirestore>()
    private val events = mockk<CollectionReference>()
    private val batch = mockk<WriteBatch>()

    // documentos falsos guardados pelo id, para conferir qual evento foi atualizado
    private val docsById = mutableMapOf<String, DocumentReference>()
    private val savedEvents = mutableListOf<Event>()
    private val updates = mutableMapOf<DocumentReference, Map<String, Any>>()

    @Before
    fun setUp() {
        every { db.collection("events") } returns events
        every { db.batch() } returns batch
        every { events.document() } answers { mockk() }
        every { events.document(any<String>()) } answers { docsById.getOrPut(firstArg()) { mockk() } }
        every { batch.set(any<DocumentReference>(), any<Event>()) } answers {
            savedEvents += secondArg<Event>()
            batch
        }
        every { batch.update(any<DocumentReference>(), any<Map<String, Any>>()) } answers {
            updates[firstArg()] = secondArg()
            batch
        }
        every { batch.commit() } returns Tasks.forResult(null)
    }

    // grava os exemplos e devolve o que foi para o lote
    private fun samples(): List<Event> {
        SampleEvents.save(db).await()
        return savedEvents.toList()
    }

    @Test
    fun `twelve sample events are saved in one commit`() {
        assertEquals(12, samples().size)
        verify(exactly = 1) { batch.commit() }
    }

    // a sincronização encontra cada exemplo pelo título, então eles não podem se repetir
    @Test
    fun `sample titles are unique`() {
        val titles = samples().map { it.title }
        assertEquals(titles.size, titles.toSet().size)
    }

    @Test
    fun `every sample has address, map point and date`() {
        for (event in samples()) {
            assertTrue(event.title, event.address.isNotBlank())
            assertNotNull(event.title, event.coordinates)
            assertNotNull(event.title, event.date)
        }
    }

    // os auditórios ficam no Prédio 4: todos os eventos em auditório têm o mesmo ponto
    @Test
    fun `auditorium events share the same building`() {
        val points = samples().filter { it.location.startsWith("Auditório") }.map { it.coordinates }
        assertTrue(points.size > 1)
        assertEquals(1, points.toSet().size)
    }

    @Test
    fun `mock trial happens at the city courthouse`() {
        val trial = samples().single { it.title == "Júri Simulado" }
        assertEquals("Fórum de Santiago", trial.location)
        assertTrue(trial.address.startsWith("Fórum de Santiago"))
    }

    @Test
    fun `sync updates only saved events whose location changed`() {
        val sample = samples().first()
        val upToDate = sample.copy(id = "same")
        val moved = sample.copy(id = "moved", coordinates = GeoPoint(0.0, 0.0), location = "Sala antiga")
        val notASample = Event(id = "console", title = "Evento criado no console")

        SampleEvents.syncLocations(db, listOf(upToDate, moved, notASample)).await()

        assertEquals(setOf(docsById.getValue("moved")), updates.keys)
        assertEquals(
            mapOf(
                "location" to sample.location,
                "address" to sample.address,
                "coordinates" to sample.coordinates
            ),
            updates.getValue(docsById.getValue("moved"))
        )
        // um commit do save() e um da sincronização
        verify(exactly = 2) { batch.commit() }
    }

    // eventos de antes do mapa não tinham endereço nem ponto: a sincronização completa os dois
    @Test
    fun `sync fills events saved before the map existed`() {
        val sample = samples().last()
        val old = sample.copy(id = "old", address = "", coordinates = null)

        SampleEvents.syncLocations(db, listOf(old)).await()

        assertEquals(sample.coordinates, updates.getValue(docsById.getValue("old"))["coordinates"])
    }

    @Test
    fun `sync without changes does not write anything`() {
        val upToDate = samples().map { it.copy(id = "id-${it.title}") }

        SampleEvents.syncLocations(db, upToDate).await()

        assertTrue(updates.isEmpty())
        // só o commit do save()
        verify(exactly = 1) { batch.commit() }
    }
}
