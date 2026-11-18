package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.domain.CheckinCode
import br.com.uri.campushub.domain.CheckinRules
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
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Transaction
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do AttendeeRepository (data/repository): a lista do organizador em events/{eventId}/attendees
// e o sync, que o app do aluno roda ao abrir o evento (com o segredo do QR de check-in).
// Firestore simulado pelo MockK; o Robolectric dá a thread principal usada pelo continueWith
@RunWith(RobolectricTestRunner::class)
class AttendeeRepositoryTest {

    private companion object {
        // segredo que o repositório geraria (fixo no teste) e um já salvo antes
        const val NEW_TOKEN = "0123456789abcdef0123456789abcdef"
        const val SAVED_TOKEN = "ffffffffffffffffffffffffffffffff"
    }

    private val db = mockk<FirebaseFirestore>()
    private val attendees = mockk<CollectionReference>()
    private val anaRef = mockk<DocumentReference>()
    private val repository = AttendeeRepository(db) { NEW_TOKEN }

    private val ana = User(name = "Ana", course = "Direito")

    @Before
    fun setUp() {
        every { db.collection("events").document("event-1").collection("attendees") } returns attendees
        every { attendees.document("uid-ana") } returns anaRef
        every { anaRef.set(any(), any<SetOptions>()) } returns Tasks.forResult(null)
        every { anaRef.delete() } returns Tasks.forResult(null)
    }

    // documento da Ana como está no banco (null = não existe)
    private fun savedInDatabase(attendee: Attendee?) {
        val snapshot = mockk<DocumentSnapshot>()
        every { anaRef.get() } returns Tasks.forResult(snapshot)
        every { snapshot.exists() } returns (attendee != null)
        every { snapshot.toObject(Attendee::class.java) } returns attendee
    }

    private fun fields(status: String, since: Any, token: String) = mapOf(
        "name" to "Ana", "course" to "Direito", "status" to status, "since" to since, "checkinToken" to token
    )

    @Test
    fun `organizer reads every attendee of the event`() {
        val snapshot = mockk<QuerySnapshot>()
        val list = listOf(Attendee(userId = "uid-ana", name = "Ana"))
        every { attendees.get() } returns Tasks.forResult(snapshot)
        every { snapshot.toObjects(Attendee::class.java) } returns list

        assertEquals(list, repository.getAll("event-1").await())
    }

    // inscrição de antes da lista existir: o documento é criado com a hora do servidor e um segredo novo
    @Test
    fun `sync creates the missing document with a new secret`() {
        savedInDatabase(null)

        val synced = repository.sync("uid-ana", "event-1", ana, isEnrolled = true, waitlistPosition = 0).await()

        verify { anaRef.set(fields("enrolled", FieldValue.serverTimestamp(), NEW_TOKEN), SetOptions.merge()) }
        assertEquals(Attendee("uid-ana", "Ana", "Direito", "enrolled", checkinToken = NEW_TOKEN), synced)
    }

    // nome trocado no perfil, mesma situação: atualiza, mantém a hora da inscrição e o segredo do QR
    @Test
    fun `sync keeps the time and the secret when only the profile changed`() {
        val since = Timestamp(NOW)
        savedInDatabase(Attendee(name = "Ana Antiga", course = "Direito", status = Attendee.ENROLLED, since = since,
            checkinToken = SAVED_TOKEN))

        repository.sync("uid-ana", "event-1", ana, isEnrolled = true, waitlistPosition = 0).await()

        verify { anaRef.set(fields("enrolled", since, SAVED_TOKEN), SetOptions.merge()) }
    }

    // documento de antes do QR existir: ganha um segredo e mantém a hora da inscrição
    @Test
    fun `sync adds the secret to an old document`() {
        val since = Timestamp(NOW)
        savedInDatabase(Attendee(name = "Ana", course = "Direito", status = Attendee.ENROLLED, since = since))

        repository.sync("uid-ana", "event-1", ana, isEnrolled = true, waitlistPosition = 0).await()

        verify { anaRef.set(fields("enrolled", since, NEW_TOKEN), SetOptions.merge()) }
    }

    // situação mudou (estava na fila, agora inscrita): a hora passa a ser a da inscrição
    @Test
    fun `sync updates the status with a new time`() {
        savedInDatabase(Attendee(name = "Ana", course = "Direito", status = Attendee.WAITING, since = Timestamp(NOW),
            checkinToken = SAVED_TOKEN))

        repository.sync("uid-ana", "event-1", ana, isEnrolled = true, waitlistPosition = 0).await()

        verify { anaRef.set(fields("enrolled", FieldValue.serverTimestamp(), SAVED_TOKEN), SetOptions.merge()) }
    }

    // nada mudou: não grava e devolve o documento salvo (com o segredo para o QR)
    @Test
    fun `sync leaves a matching document alone`() {
        val saved = Attendee(name = "Ana", course = "Direito", status = Attendee.WAITING, checkinToken = SAVED_TOKEN)
        savedInDatabase(saved)

        val synced = repository.sync("uid-ana", "event-1", ana, isEnrolled = false, waitlistPosition = 1).await()

        assertSame(saved, synced)
        verify(exactly = 0) { anaRef.set(any(), any<SetOptions>()) }
        verify(exactly = 0) { anaRef.delete() }
    }

    // não está mais inscrita nem na fila: o documento que sobrou é apagado
    @Test
    fun `sync removes a leftover document`() {
        savedInDatabase(Attendee(name = "Ana", course = "Direito", checkinToken = SAVED_TOKEN))

        assertNull(repository.sync("uid-ana", "event-1", ana, isEnrolled = false, waitlistPosition = 0).await())
        verify { anaRef.delete() }
    }

    // já tinha check-in: o sync devolve a hora salva (o merge não mexe nela)
    @Test
    fun `sync keeps the check-in`() {
        val at = Timestamp(NOW)
        savedInDatabase(Attendee(name = "Ana Antiga", course = "Direito", status = Attendee.ENROLLED,
            checkedInAt = at, checkinToken = SAVED_TOKEN))

        val synced = repository.sync("uid-ana", "event-1", ana, isEnrolled = true, waitlistPosition = 0).await()

        assertEquals(at, synced?.checkedInAt)
    }

    // ---------- check-in pelo organizador ----------

    // transação do check-in com o documento da Ana como está no banco (null = não existe)
    private fun checkIn(saved: Attendee?, token: String = SAVED_TOKEN): Pair<CheckinRules.Outcome, Transaction> {
        val transaction = mockk<Transaction>(relaxed = true)
        val snapshot = mockk<DocumentSnapshot>()
        every { transaction.get(anaRef) } returns snapshot
        every { snapshot.exists() } returns (saved != null)
        every { snapshot.toObject(Attendee::class.java) } returns saved
        every { db.runTransaction(any<Transaction.Function<CheckinRules.Outcome>>()) } answers {
            Tasks.forResult(firstArg<Transaction.Function<CheckinRules.Outcome>>().apply(transaction))
        }
        val outcome = repository.checkIn("event-1", CheckinCode.Payload("event-1", "uid-ana", token)).await()
        return outcome to transaction
    }

    private val enrolledAna = Attendee(name = "Ana", course = "Direito", status = Attendee.ENROLLED, checkinToken = SAVED_TOKEN)

    // QR certo e sem check-in ainda: grava a hora do servidor
    @Test
    fun `valid code checks the student in`() {
        val (outcome, transaction) = checkIn(enrolledAna)

        assertEquals(CheckinRules.Outcome.Done("Ana"), outcome)
        verify { transaction.update(anaRef, "checkedInAt", FieldValue.serverTimestamp()) }
    }

    // segunda leitura, segredo errado ou aluno fora da lista: nada é gravado
    @Test
    fun `refused readings write nothing`() {
        val at = Timestamp(NOW)
        val cases = listOf(
            checkIn(enrolledAna.copy(checkedInAt = at)) to CheckinRules.Outcome.AlreadyDone("Ana", at),
            checkIn(enrolledAna, token = NEW_TOKEN) to CheckinRules.Outcome.WrongCode,
            checkIn(null) to CheckinRules.Outcome.NotEnrolled
        )
        for ((result, expected) in cases) {
            val (outcome, transaction) = result
            assertEquals(expected, outcome)
            verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<String>(), any()) }
        }
    }

    // as regras recusaram a gravação: o erro chega a quem chamou
    @Test
    fun `sync reports a refused write`() {
        savedInDatabase(null)
        every { anaRef.set(any(), any<SetOptions>()) } returns Tasks.forException(IllegalStateException("PERMISSION_DENIED"))

        val error = repository.sync("uid-ana", "event-1", ana, isEnrolled = true, waitlistPosition = 0).awaitException()

        assertEquals("PERMISSION_DENIED", (error.cause ?: error).message)
    }
}
