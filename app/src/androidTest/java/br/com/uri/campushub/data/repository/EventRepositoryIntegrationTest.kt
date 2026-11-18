package br.com.uri.campushub.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.testing.EmulatorData.ADMIN_EMAIL
import br.com.uri.campushub.testing.EmulatorData.createAdmin
import br.com.uri.campushub.testing.EmulatorData.createOrganizer
import br.com.uri.campushub.testing.EmulatorData.createStudent
import br.com.uri.campushub.testing.EmulatorData.daysFromNow
import br.com.uri.campushub.testing.EmulatorData.eventDraft
import br.com.uri.campushub.testing.EmulatorData.saveEvents
import br.com.uri.campushub.testing.EmulatorData.signIn
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// integração do EventRepository com o emulador do Firestore: leitura da coleção "events"
// e gravação dos eventos de exemplo pela conta admin
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class EventRepositoryIntegrationTest {

    private val db = FirebaseEmulator.firestore
    private val events = EventRepository(db)

    // a conta admin fica logada; os testes de leitura trocam para uma aluna
    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
        createAdmin()
    }

    // a lista dos alunos traz publicados e encerrados por data; a ordem não depende da gravação
    @Test
    fun eventsComeOrderedByDate() {
        saveEvents(mapOf(
            "c" to Event(title = "Daqui a um mês", date = daysFromNow(30)),
            "a" to Event(title = "Ontem", date = daysFromNow(-1)),
            "b" to Event(title = "Amanhã", date = daysFromNow(1))
        ))
        createStudent("ana@campushub.test", "Ana", "Direito")

        assertEquals(listOf("Ontem", "Amanhã", "Daqui a um mês"), events.getAll().await().map { it.title })
    }

    @Test
    fun singleEventIsReadByIdOrNullWhenMissing() {
        saveEvents(mapOf("palestra" to Event(title = "Palestra", date = daysFromNow(2), capacity = 40)))
        createStudent("ana@campushub.test", "Ana", "Direito")

        val event = events.get("palestra").await()
        assertEquals("palestra", event?.id)
        assertEquals(40, event?.capacity)
        assertNull(events.get("apagado").await())
    }

    // cada id é lido sozinho (35 leituras ao mesmo tempo) e o resultado sai por data
    @Test
    fun eventsAreReadByIdInDateOrder() {
        val ids = (1..35).map { "evento-%02d".format(it) }
        saveEvents(ids.mapIndexed { day, id -> id to Event(title = id, date = daysFromNow(day + 1)) }.toMap())
        createStudent("ana@campushub.test", "Ana", "Direito")

        assertEquals(ids, events.getByIds(ids.reversed()).await().map { it.id })
        assertTrue(events.getByIds(emptyList()).await().isEmpty())
    }

    // a área do organizador traz só os eventos com o uid dele em organizerId, ordenados por data
    @Test
    fun organizerGetsOnlyTheirOwnEvents() {
        val caioUid = createOrganizer("caio@campushub.test", "Caio", "Direito")
        signIn(ADMIN_EMAIL)
        saveEvents(mapOf(
            "depois" to Event(title = "Depois", date = daysFromNow(9), organizerId = caioUid),
            "antes" to Event(title = "Antes", date = daysFromNow(3), organizerId = caioUid),
            "de-outro" to Event(title = "De outro", date = daysFromNow(5), organizerId = "outro-uid")
        ))
        signIn("caio@campushub.test")

        assertEquals(listOf("Antes", "Depois"), events.getByOrganizer(caioUid).await().map { it.title })
    }

    // o formulário do organizador vira um evento dele, sem inscritos; a edição troca só o formulário
    @Test
    fun organizerCreatesAndEditsTheirEvent() {
        val caioUid = createOrganizer("caio@campushub.test", "Caio", "Direito")

        val id = events.create(caioUid, eventDraft(title = "Hackathon", capacity = 30, openToAll = false)).await()
        val created = events.get(id).await()!!
        assertEquals(caioUid, created.organizerId)
        assertEquals(0, created.enrolledCount)
        assertEquals(EventStatus.DRAFT, created.currentStatus())
        assertEquals("Direito", created.course)
        assertEquals(false, created.openToAll)

        events.update(id, eventDraft(title = "Hackathon 2026", capacity = 45)).await()
        val edited = events.get(id).await()!!
        assertEquals("Hackathon 2026", edited.title)
        assertEquals(45, edited.capacity)
        assertEquals(caioUid, edited.organizerId)
        assertEquals(listOf(id), events.getByOrganizer(caioUid).await().map { it.id })
    }

    // primeira execução na conta admin: grava os 12 exemplos; a sincronização devolve
    // o local certo a um evento que foi alterado
    @Test
    fun adminSavesSamplesAndSyncRestoresTheirLocation() {
        events.saveSampleEvents().await()
        val saved = events.getAll().await()
        assertEquals(12, saved.size)

        val sample = saved.first()
        db.collection("events").document(sample.id).update("location", "Sala antiga").await()

        events.syncLocations(events.getAll().await()).await()
        assertEquals(sample.location, events.get(sample.id).await()?.location)
    }

    // banco de antes deste campo: o admin publica os eventos sem status, e a lista dos alunos volta a trazê-los
    @Test
    fun adminPublishesOldEventsWithoutStatus() {
        db.collection("events").document("antigo")
            .set(mapOf("title" to "Antigo", "date" to daysFromNow(4), "capacity" to 20)).await()
        assertTrue(events.getAll().await().isEmpty())

        assertTrue(events.prepareSampleData().await())
        assertEquals(listOf("Antigo"), events.getAll().await().map { it.title })
        // com tudo em dia, não grava mais nada
        assertEquals(false, events.prepareSampleData().await())
    }

    // o organizador publica (aparece para os alunos) e depois encerra as inscrições (continua na lista)
    @Test
    fun organizerPublishesAndClosesTheirEvent() {
        val caioUid = createOrganizer("caio@campushub.test", "Caio", "Direito")
        val id = events.create(caioUid, eventDraft(title = "Hackathon")).await()

        events.publish(id).await()
        assertEquals(EventStatus.PUBLISHED, events.get(id).await()?.currentStatus())
        events.closeEnrollments(id).await()

        createStudent("ana@campushub.test", "Ana", "Direito")
        val listed = events.getAll().await().single()
        assertEquals(id, listed.id)
        assertEquals(EventStatus.CLOSED, listed.currentStatus())
    }
}
