package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.testing.TestEvents.event
import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.awaitException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Transaction
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

// testes do EnrollmentRepository (data/repository): inscrições em users/{uid}/enrollments/{eventId}
// e o contador enrolledCount do evento, alterados juntos numa transação do Firestore.
// O MockK simula o Firestore e a transação: a função passada ao runTransaction roda de verdade
// com uma Transaction falsa, e o teste confere o que ela leu e gravou.
@RunWith(RobolectricTestRunner::class)
class EnrollmentRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val eventRef = mockk<DocumentReference>()
    private val enrollmentRef = mockk<DocumentReference>()
    private val transaction = mockk<Transaction>(relaxed = true)
    private val eventSnapshot = mockk<DocumentSnapshot>()
    private val enrollmentSnapshot = mockk<DocumentSnapshot>()
    private val repository = EnrollmentRepository(db)

    @Before
    fun setUp() {
        every { db.collection("events").document("event-1") } returns eventRef
        every { db.collection("users").document("uid-ana").collection("enrollments").document("event-1") } returns enrollmentRef

        // executa a função da transação com a Transaction falsa e devolve o resultado como Task
        every { db.runTransaction(any<Transaction.Function<Void?>>()) } answers {
            try {
                Tasks.forResult(firstArg<Transaction.Function<Void?>>().apply(transaction))
            } catch (error: Exception) {
                Tasks.forException(error)
            }
        }
        every { transaction.get(eventRef) } returns eventSnapshot
        every { transaction.get(enrollmentRef) } returns enrollmentSnapshot
    }

    // evento como está no banco no momento da transação
    private fun eventInDatabase(event: Event) {
        every { eventSnapshot.toObject(Event::class.java) } returns event
    }

    private fun alreadyEnrolled(enrolled: Boolean) {
        every { enrollmentSnapshot.exists() } returns enrolled
    }

    @Test
    fun `student is enrolled when the enrollment document exists`() {
        every { enrollmentRef.get() } returns Tasks.forResult(enrollmentSnapshot)
        alreadyEnrolled(true)

        assertTrue(repository.isEnrolled("uid-ana", "event-1").await())
    }

    @Test
    fun `enrolled event ids are the document ids`() {
        val snapshot = mockk<QuerySnapshot>()
        val doc = mockk<DocumentSnapshot> { every { id } returns "event-1" }
        every { db.collection("users").document("uid-ana").collection("enrollments").get() } returns Tasks.forResult(snapshot)
        every { snapshot.documents } returns listOf(doc)

        assertEquals(listOf("event-1"), repository.getEventIds("uid-ana").await())
    }

    @Test
    fun `enrolling creates the enrollment and takes one spot`() {
        eventInDatabase(event(capacity = 10, enrolledCount = 3))
        alreadyEnrolled(false)
        val data = slot<Any>()
        every { transaction.set(enrollmentRef, capture(data)) } returns transaction

        repository.enroll("uid-ana", "event-1").await()

        val saved = data.captured as Map<*, *>
        assertEquals("event-1", saved["eventId"])
        assertEquals(FieldValue.serverTimestamp(), saved["enrolledAt"])
        verify(exactly = 1) { transaction.update(eventRef, "enrolledCount", any()) }
    }

    // tocar duas vezes no botão não pode gastar duas vagas
    @Test
    fun `enrolling twice changes nothing`() {
        eventInDatabase(event(capacity = 10, enrolledCount = 3))
        alreadyEnrolled(true)

        repository.enroll("uid-ana", "event-1").await()

        verify(exactly = 0) { transaction.set(any<DocumentReference>(), any()) }
        verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<String>(), any()) }
    }

    // a transação lê o evento no banco: se a última vaga acabou de ser pega, a inscrição é recusada
    @Test
    fun `full event refuses the enrollment with the sold out error`() {
        eventInDatabase(event(capacity = 10, enrolledCount = 10))
        alreadyEnrolled(false)

        val error = repository.enroll("uid-ana", "event-1").awaitException()

        assertTrue(EnrollmentRepository.isSoldOut(error))
        verify(exactly = 0) { transaction.set(any<DocumentReference>(), any()) }
    }

    @Test
    fun `cancelling deletes the enrollment and gives the spot back`() {
        alreadyEnrolled(true)

        repository.cancel("uid-ana", "event-1").await()

        verify { transaction.delete(enrollmentRef) }
        verify(exactly = 1) { transaction.update(eventRef, "enrolledCount", any()) }
    }

    // sem inscrição não há vaga para devolver; o contador não pode ficar menor que o real
    @Test
    fun `cancelling without enrollment changes nothing`() {
        alreadyEnrolled(false)

        repository.cancel("uid-ana", "event-1").await()

        verify(exactly = 0) { transaction.delete(any()) }
        verify(exactly = 0) { transaction.update(any<DocumentReference>(), any<String>(), any()) }
    }

    // o Firestore às vezes embrulha o erro da transação em outro; a tela precisa reconhecer os dois
    @Test
    fun `sold out error is recognized directly or wrapped`() {
        val soldOut = FirebaseFirestoreException("sold_out", FirebaseFirestoreException.Code.ABORTED)
        assertTrue(EnrollmentRepository.isSoldOut(soldOut))
        assertTrue(EnrollmentRepository.isSoldOut(RuntimeException("falha na transação", soldOut)))
        assertFalse(EnrollmentRepository.isSoldOut(Exception("sem internet")))
    }
}
