package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.testing.TestEvents.TEN_YEARS_IN_DAYS
import br.com.uri.campushub.testing.TestEvents.event
import br.com.uri.campushub.testing.await
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.AggregateQuery
import com.google.firebase.firestore.AggregateQuerySnapshot
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import com.google.firebase.firestore.WriteBatch
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do WaitlistRepository (data/repository): fila com senhas em events/{eventId}/waitlist/{uid}
// e waitlistTickets/{senha}, o aluno na lista do organizador (attendees/{uid}), a posição pela
// agregação count e o settle, que inscreve o primeiro da fila.
// Firestore e transações simulados pelo MockK: a função passada ao runTransaction roda de verdade
// com uma Transaction falsa; o Robolectric dá a thread principal usada pelo continueWith
@RunWith(RobolectricTestRunner::class)
class WaitlistRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val eventRef = mockk<DocumentReference>()
    private val waitlist = mockk<CollectionReference>()
    private val tickets = mockk<CollectionReference>()
    private val myEntry = mockk<DocumentReference>()
    private val attendees = mockk<CollectionReference>()
    private val users = mockk<CollectionReference>()
    private val transaction = mockk<Transaction>(relaxed = true)
    private val eventSnapshot = mockk<DocumentSnapshot>()
    // segredo do QR fixo, no lugar do gerado ao acaso
    private val repository = WaitlistRepository(db) { "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" }

    // documentos das senhas e das inscrições criados sob demanda e guardados para o verify
    private val ticketRefs = mutableMapOf<String, DocumentReference>()
    private val entryRefs = mutableMapOf<String, DocumentReference>()
    private val attendeeRefs = mutableMapOf<String, DocumentReference>()
    private val userRefs = mutableMapOf<String, DocumentReference>()

    @Before
    fun setUp() {
        every { db.collection("events").document("event-1") } returns eventRef
        every { eventRef.collection("waitlist") } returns waitlist
        every { eventRef.collection("waitlistTickets") } returns tickets
        every { waitlist.document(any()) } answers { entryRefs.getOrPut(firstArg()) { mockk() } }
        every { tickets.document(any()) } answers { ticketRefs.getOrPut(firstArg()) { mockk() } }
        every { eventRef.collection("attendees") } returns attendees
        every { attendees.document(any()) } answers { attendeeRefs.getOrPut(firstArg()) { mockk() } }
        every { db.collection("users") } returns users
        every { users.document(any()) } answers { userRefs.getOrPut(firstArg()) { mockk() } }
        entryRefs["uid-ana"] = myEntry

        // perfil da Ana, lido na transação de entrar na fila
        val profile = mockk<DocumentSnapshot>()
        every { profile.toObject(User::class.java) } returns User(name = "Ana", course = "Direito")
        val anaProfile = users.document("uid-ana")
        every { transaction.get(anaProfile) } returns profile

        // executa a função da transação com a Transaction falsa e devolve o resultado como Task
        every { db.runTransaction(any<Transaction.Function<Any?>>()) } answers {
            try {
                Tasks.forResult(firstArg<Transaction.Function<Any?>>().apply(transaction))
            } catch (error: Exception) {
                Tasks.forException(error)
            }
        }
        every { transaction.get(eventRef) } returns eventSnapshot
    }

    // evento como está no banco no momento da transação
    private fun eventInDatabase(event: Event?) {
        every { eventSnapshot.toObject(Event::class.java) } returns event
    }

    // senha no banco: com o dono (uid) ou apagada (null = o aluno saiu da fila)
    private fun ticketInDatabase(ticket: Int, uid: String?) {
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.getString("userId") } returns uid
        every { transaction.get(tickets.document(ticket.toString())) } returns snapshot
    }

    // lugar de um aluno na fila, lido dentro da transação do settle
    private fun entryInDatabase(uid: String, hasAttendee: Boolean) {
        val snapshot = mockk<DocumentSnapshot>()
        every { snapshot.getBoolean("hasAttendee") } returns hasAttendee
        val entry = waitlist.document(uid)
        every { transaction.get(entry) } returns snapshot
    }

    // inscrição do Beto (users/uid-beto/enrollments/event-1); o perfil é lido fora do every
    private fun betoEnrollment(): DocumentReference {
        val enrollment = mockk<DocumentReference>()
        val beto = users.document("uid-beto")
        every { beto.collection("enrollments").document("event-1") } returns enrollment
        return enrollment
    }

    // documento do lugar da Ana na fila, lido fora da transação
    private fun myEntryInDatabase(exists: Boolean, ticket: Long?) {
        val snapshot = mockk<DocumentSnapshot>()
        every { myEntry.get() } returns Tasks.forResult(snapshot)
        every { snapshot.exists() } returns exists
        every { snapshot.contains("ticket") } returns (ticket != null)
        every { snapshot.getLong("ticket") } returns ticket
    }

    // o settle usa o relógio real: o evento fica daqui a 10 anos para estar sempre no futuro
    private fun queued(enrolled: Int, head: Int, next: Int, status: EventStatus = EventStatus.PUBLISHED) =
        event(daysFromNow = TEN_YEARS_IN_DAYS, capacity = 10, enrolledCount = enrolled, status = status)
            .copy(waitlistHead = head, waitlistNext = next)

    // ---------- entrar, sair e posição ----------

    // a Ana pega a senha waitlistNext (4): lugar na fila, senha apontando para ela e o contador em 5
    @Test
    fun `joining takes the next ticket`() {
        myEntryInDatabase(exists = false, ticket = null)
        eventInDatabase(queued(enrolled = 10, head = 2, next = 4))

        repository.join("uid-ana", "event-1").await()

        // a referência é lida antes do verify, que gravaria a chamada como esperada
        val ticket4 = tickets.document("4")
        val anaAttendee = attendees.document("uid-ana")
        verify {
            transaction.set(myEntry, mapOf("ticket" to 4, "joinedAt" to FieldValue.serverTimestamp(), "hasAttendee" to true))
        }
        verify { transaction.set(ticket4, mapOf("userId" to "uid-ana")) }
        verify { transaction.update(eventRef, "waitlistNext", 5) }
        // e aparece na fila da lista do organizador, com o nome e o curso do perfil
        verify {
            transaction.set(
                anaAttendee,
                mapOf("name" to "Ana", "course" to "Direito", "status" to "waiting", "since" to FieldValue.serverTimestamp(),
                    "checkinToken" to "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
            )
        }
    }

    // lugar da versão anterior da fila (sem senha): é apagado antes de pegar a senha nova
    @Test
    fun `old entry without ticket is removed before joining`() {
        myEntryInDatabase(exists = true, ticket = null)
        every { myEntry.delete() } returns Tasks.forResult(null)
        eventInDatabase(queued(enrolled = 10, head = 0, next = 0))

        repository.join("uid-ana", "event-1").await()

        verify { myEntry.delete() }
        verify { transaction.update(eventRef, "waitlistNext", 1) }
    }

    // sair apaga o lugar e a senha juntos
    @Test
    fun `leaving deletes the entry and the ticket`() {
        myEntryInDatabase(exists = true, ticket = 3)
        val batch = mockk<WriteBatch>(relaxed = true)
        every { db.batch() } returns batch
        every { batch.commit() } returns Tasks.forResult(null)

        repository.leave("uid-ana", "event-1").await()

        val ticket3 = tickets.document("3")
        val anaAttendee = attendees.document("uid-ana")
        verify { batch.delete(myEntry) }
        verify { batch.delete(ticket3) }
        verify { batch.delete(anaAttendee) }
    }

    @Test
    fun `student outside the queue has position zero`() {
        myEntryInDatabase(exists = false, ticket = null)

        assertEquals(0, repository.getPosition("uid-ana", "event-1").await())
    }

    // posição = quantas senhas até a dela ainda estão na fila, contadas no servidor
    @Test
    fun `position counts the tickets up to the student`() {
        myEntryInDatabase(exists = true, ticket = 7)
        val upToMine = mockk<Query>()
        val count = mockk<AggregateQuery>()
        val result = mockk<AggregateQuerySnapshot>()
        every { waitlist.whereLessThanOrEqualTo("ticket", 7L) } returns upToMine
        every { upToMine.count() } returns count
        every { count.get(AggregateSource.SERVER) } returns Tasks.forResult(result)
        every { result.count } returns 2L

        assertEquals(2, repository.getPosition("uid-ana", "event-1").await())
    }

    // ---------- settle: atender a fila ----------

    // vaga livre e o Beto com a senha da vez: ele é inscrito, a senha é atendida e ele sai da fila
    @Test
    fun `free spot goes to the first ticket`() {
        // primeiro passo: 9 de 10 com a senha 0 do Beto; segundo passo: lotou, para
        every { eventSnapshot.toObject(Event::class.java) } returnsMany listOf(
            queued(enrolled = 9, head = 0, next = 2),
            queued(enrolled = 10, head = 1, next = 2)
        )
        ticketInDatabase(0, "uid-beto")
        ticketInDatabase(1, "uid-caio")
        entryInDatabase("uid-beto", hasAttendee = true)
        val betoEnrollment = betoEnrollment()

        assertEquals(1, repository.settle("event-1").await())

        val ticket0 = tickets.document("0")
        val betoEntry = waitlist.document("uid-beto")
        val betoAttendee = attendees.document("uid-beto")

        verify {
            transaction.set(
                betoEnrollment,
                mapOf("eventId" to "event-1", "enrolledAt" to FieldValue.serverTimestamp(), "fromWaitlist" to true)
            )
        }
        verify { transaction.update(eventRef, mapOf("enrolledCount" to 10, "waitlistHead" to 1)) }
        verify { transaction.delete(ticket0) }
        verify { transaction.delete(betoEntry) }
        // na lista do organizador, o Beto sai da fila e passa para os inscritos
        verify {
            transaction.update(betoAttendee, mapOf("status" to "enrolled", "since" to FieldValue.serverTimestamp()))
        }
    }

    // lugar de uma versão anterior da fila (sem hasAttendee): inscreve sem mexer na lista do organizador,
    // que ganha o aluno quando ele abrir o evento (AttendeeRepository.sync)
    @Test
    fun `old queue entry is promoted without an attendee document`() {
        every { eventSnapshot.toObject(Event::class.java) } returnsMany listOf(
            queued(enrolled = 9, head = 0, next = 1),
            queued(enrolled = 10, head = 1, next = 1)
        )
        ticketInDatabase(0, "uid-beto")
        entryInDatabase("uid-beto", hasAttendee = false)
        val betoEnrollment = betoEnrollment()

        assertEquals(1, repository.settle("event-1").await())

        verify { transaction.set(betoEnrollment, any()) }
        verify(exactly = 0) { transaction.update(any<DocumentReference>(), mapOf("status" to "enrolled", "since" to FieldValue.serverTimestamp())) }
    }

    // senha de quem saiu (apagada): só avança, sem inscrever ninguém
    @Test
    fun `ticket of someone who left is skipped`() {
        every { eventSnapshot.toObject(Event::class.java) } returnsMany listOf(
            queued(enrolled = 10, head = 0, next = 2),
            queued(enrolled = 10, head = 1, next = 2)
        )
        ticketInDatabase(0, null)
        ticketInDatabase(1, "uid-caio")

        assertEquals(0, repository.settle("event-1").await())

        verify { transaction.update(eventRef, "waitlistHead", 1) }
        verify(exactly = 0) { transaction.set(any<DocumentReference>(), any()) }
    }

    // sem fila, evento sumido, inscrições encerradas ou já começado: não mexe em nada
    @Test
    fun `settle does nothing without a queue to serve`() {
        val started = queued(enrolled = 5, head = 0, next = 1).copy(date = event(daysFromNow = -TEN_YEARS_IN_DAYS).date)
        for (event in listOf(
            queued(enrolled = 5, head = 2, next = 2),
            null,
            queued(enrolled = 5, head = 0, next = 1, status = EventStatus.CLOSED),
            started
        )) {
            eventInDatabase(event)
            assertEquals(0, repository.settle("event-1").await())
        }
        verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<Map<String, Any>>()) }
        verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<String>(), any()) }
    }

    // fila enorme de senhas apagadas: o settle para depois do limite de passos, sem laço infinito
    @Test
    fun `settle stops after the step limit`() {
        // o evento falso não muda entre os passos: a senha 0 aparece apagada toda vez
        eventInDatabase(queued(enrolled = 10, head = 0, next = 1000))
        ticketInDatabase(0, null)

        assertEquals(0, repository.settle("event-1").await())

        verify(exactly = 50) { transaction.update(eventRef, "waitlistHead", 1) }
    }
}
