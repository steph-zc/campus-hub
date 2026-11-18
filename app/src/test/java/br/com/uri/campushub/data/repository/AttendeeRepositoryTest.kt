package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.awaitException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do AttendeeRepository (data/repository): a lista do organizador em events/{eventId}/attendees
// e o sync, que o app do aluno roda ao abrir o evento. Firestore simulado pelo MockK;
// o Robolectric dá a thread principal usada pelo continueWith
@RunWith(RobolectricTestRunner::class)
class AttendeeRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val attendees = mockk<CollectionReference>()
    private val anaRef = mockk<DocumentReference>()
    private val repository = AttendeeRepository(db)

    private val ana = User(name = "Ana", course = "Direito")

    @Before
    fun setUp() {
        every { db.collection("events").document("event-1").collection("attendees") } returns attendees
        every { attendees.document("uid-ana") } returns anaRef
        every { anaRef.set(any()) } returns Tasks.forResult(null)
        every { anaRef.delete() } returns Tasks.forResult(null)
    }

    // documento da Ana como está no banco (null = não existe)
    private fun savedInDatabase(attendee: Attendee?) {
        val snapshot = mockk<DocumentSnapshot>()
        every { anaRef.get() } returns Tasks.forResult(snapshot)
        every { snapshot.exists() } returns (attendee != null)
        every { snapshot.toObject(Attendee::class.java) } returns attendee
    }

    @Test
    fun `organizer reads every attendee of the event`() {
        val snapshot = mockk<QuerySnapshot>()
        val list = listOf(Attendee(userId = "uid-ana", name = "Ana"))
        every { attendees.get() } returns Tasks.forResult(snapshot)
        every { snapshot.toObjects(Attendee::class.java) } returns list

        assertEquals(list, repository.getAll("event-1").await())
    }

    // inscrição de antes da lista existir: o documento é criado com a hora do servidor
    @Test
    fun `sync creates the missing document`() {
        savedInDatabase(null)

        repository.sync("uid-ana", "event-1", ana, isEnrolled = true, waitlistPosition = 0).await()

        verify {
            anaRef.set(mapOf("name" to "Ana", "course" to "Direito", "status" to "enrolled", "since" to FieldValue.serverTimestamp()))
        }
    }

    // nome trocado no perfil, mesma situação: atualiza e mantém a hora em que se inscreveu
    @Test
    fun `sync keeps the time when only the profile changed`() {
        val since = Timestamp(NOW)
        savedInDatabase(Attendee(name = "Ana Antiga", course = "Direito", status = Attendee.ENROLLED, since = since))

        repository.sync("uid-ana", "event-1", ana, isEnrolled = true, waitlistPosition = 0).await()

        verify { anaRef.set(mapOf("name" to "Ana", "course" to "Direito", "status" to "enrolled", "since" to since)) }
    }

    // situação mudou (estava na fila, agora inscrita): a hora passa a ser a da inscrição
    @Test
    fun `sync updates the status with a new time`() {
        savedInDatabase(Attendee(name = "Ana", course = "Direito", status = Attendee.WAITING, since = Timestamp(NOW)))

        repository.sync("uid-ana", "event-1", ana, isEnrolled = true, waitlistPosition = 0).await()

        verify {
            anaRef.set(mapOf("name" to "Ana", "course" to "Direito", "status" to "enrolled", "since" to FieldValue.serverTimestamp()))
        }
    }

    @Test
    fun `sync leaves a matching document alone`() {
        savedInDatabase(Attendee(name = "Ana", course = "Direito", status = Attendee.WAITING))

        repository.sync("uid-ana", "event-1", ana, isEnrolled = false, waitlistPosition = 1).await()

        verify(exactly = 0) { anaRef.set(any()) }
        verify(exactly = 0) { anaRef.delete() }
    }

    // não está mais inscrita nem na fila: o documento que sobrou é apagado
    @Test
    fun `sync removes a leftover document`() {
        savedInDatabase(Attendee(name = "Ana", course = "Direito"))

        repository.sync("uid-ana", "event-1", ana, isEnrolled = false, waitlistPosition = 0).await()

        verify { anaRef.delete() }
    }

    // as regras recusaram a gravação: o erro chega a quem chamou
    @Test
    fun `sync reports a refused write`() {
        savedInDatabase(null)
        every { anaRef.set(any()) } returns Tasks.forException(IllegalStateException("PERMISSION_DENIED"))

        val error = repository.sync("uid-ana", "event-1", ana, isEnrolled = true, waitlistPosition = 0).awaitException()

        assertEquals("PERMISSION_DENIED", (error.cause ?: error).message)
    }
}
