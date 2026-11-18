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

    // a lista dos alunos filtra por status (as regras exigem) e ordena por data no app
    @Test
    fun `students list brings every visible event ordered by date`() {
        val later = event(id = "b", daysFromNow = 2)
        val sooner = event(id = "a", daysFromNow = 1)
        every { events.whereIn("status", listOf("published", "closed", "finished")) } returns queryReturning(listOf(later, sooner))

        assertEquals(listOf("a", "b"), repository.getAll().await().map { it.id })
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

    // documento events/{id} simulado: devolve o evento, null (apagado) ou falha (sem permissão)
    private fun documentReturning(id: String, found: Event?, error: Exception? = null) {
        val doc = mockk<DocumentReference>()
        every { events.document(id) } returns doc
        if (error != null) {
            every { doc.get() } returns Tasks.forException(error)
            return
        }
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.toObject(Event::class.java) } returns found
        every { doc.get() } returns Tasks.forResult(snapshot)
    }

    // Meus eventos vazio: nem chega a consultar o Firestore
    @Test
    fun `empty id list returns empty without querying`() {
        assertTrue(repository.getByIds(emptyList()).await().isEmpty())
        verify(exactly = 0) { events.document(any()) }
    }

    // cada id é lido sozinho; apagado ou sem permissão (rascunho de outro) fica de fora,
    // id repetido é lido uma vez só, e o resultado sai por data
    @Test
    fun `events are read one by one skipping missing and forbidden ones`() {
        documentReturning("later", event(id = "later", daysFromNow = 9))
        documentReturning("sooner", event(id = "sooner", daysFromNow = 2))
        documentReturning("gone", null)
        documentReturning(
            "draft", null,
            FirebaseFirestoreException("sem permissão", FirebaseFirestoreException.Code.PERMISSION_DENIED)
        )

        val result = repository.getByIds(listOf("later", "gone", "draft", "sooner", "later")).await()

        assertEquals(listOf("sooner", "later"), result.map { it.id })
        verify(exactly = 1) { events.document("later") }
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

    // o Firestore gera o id; o documento leva o formulário, o dono, o contador zerado, imageVersion 0
    // e a situação de rascunho
    @Test
    fun `new event saves the form with its organizer and no enrollments`() {
        val doc = mockk<DocumentReference>()
        every { events.document() } returns doc
        every { doc.id } returns "novo-evento"
        val batch = recordingBatch()

        assertEquals("novo-evento", repository.create("uid-caio", draft).await())

        val expected = draft.toMap() + mapOf(
            "organizerId" to "uid-caio", "enrolledCount" to 0, "imageVersion" to 0L, "status" to "draft"
        )
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

        val expected = draft.toMap() + mapOf(
            "organizerId" to "uid-caio", "enrolledCount" to 0, "imageVersion" to VERSION, "status" to "draft"
        )
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
        assertFalse(draft.toMap().containsKey("status"))
    }

    // publicar, encerrar as inscrições e encerrar o evento gravam só o campo status
    @Test
    fun `status changes write only the status`() {
        val doc = mockk<DocumentReference>()
        every { events.document("event-1") } returns doc
        every { doc.update("status", any()) } returns Tasks.forResult(null)

        repository.publish("event-1").await()
        repository.closeEnrollments("event-1").await()
        repository.finish("event-1").await()

        verify { doc.update("status", "published") }
        verify { doc.update("status", "closed") }
        verify { doc.update("status", "finished") }
    }

    // excluir apaga o evento e, se houver, a capa, no mesmo lote
    @Test
    fun `deleting removes the event and its cover together`() {
        val doc = mockk<DocumentReference>()
        val imageDoc = mockk<DocumentReference>()
        every { events.document("event-1") } returns doc
        every { images.document("event-1") } returns imageDoc
        val batch = recordingBatch()

        repository.delete(event(id = "event-1").copy(imageVersion = VERSION)).await()

        verify { batch.delete(doc) }
        verify { batch.delete(imageDoc) }
        verify(exactly = 1) { batch.commit() }
    }

    @Test
    fun `deleting an event without cover leaves eventImages alone`() {
        val doc = mockk<DocumentReference>()
        every { events.document("event-1") } returns doc
        val batch = recordingBatch()

        repository.delete(event(id = "event-1")).await()

        verify { batch.delete(doc) }
        verify(exactly = 0) { images.document(any()) }
    }

    // consulta sem filtro da conta admin, com os documentos dados
    private fun allEventsReturning(docs: List<DocumentSnapshot>) {
        val snapshot = mockk<QuerySnapshot>()
        every { snapshot.isEmpty } returns docs.isEmpty()
        every { snapshot.documents } returns docs
        every { events.get() } returns Tasks.forResult(snapshot)
    }

    // documento já gravado, com ou sem o campo status
    private fun savedDoc(hasStatus: Boolean): DocumentSnapshot {
        val doc = mockk<DocumentSnapshot>()
        val ref = mockk<DocumentReference>()
        every { doc.contains("status") } returns hasStatus
        every { doc.reference } returns ref
        return doc
    }

    // coleção vazia: grava os exemplos e avisa que mudou algo
    @Test
    fun `empty collection gets the sample events`() {
        allEventsReturning(emptyList())
        val batch = recordingBatch()
        every { events.document() } answers { mockk() }

        assertTrue(repository.prepareSampleData().await())
        verify(exactly = 1) { batch.commit() }
    }

    // eventos antigos sem o campo status passam a ser publicados; os que já têm ficam como estão
    @Test
    fun `old events without status are published`() {
        val old = savedDoc(hasStatus = false)
        val current = savedDoc(hasStatus = true)
        allEventsReturning(listOf(old, current))
        val batch = recordingBatch()

        // as referências são lidas antes do verify, que gravaria a leitura como chamada esperada
        val oldRef = old.reference
        val currentRef = current.reference

        assertTrue(repository.prepareSampleData().await())
        verify { batch.update(oldRef, "status", "published") }
        verify(exactly = 0) { batch.update(currentRef, any<String>(), any()) }
    }

    // nada a fazer (só rascunhos de organizadores, por exemplo): não grava e devolve false
    @Test
    fun `nothing to prepare writes nothing`() {
        allEventsReturning(listOf(savedDoc(hasStatus = true)))

        assertFalse(repository.prepareSampleData().await())
        verify(exactly = 0) { db.batch() }
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
