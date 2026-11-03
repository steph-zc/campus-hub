package br.com.uri.campushub.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.testing.EmulatorData.createAdmin
import br.com.uri.campushub.testing.EmulatorData.createStudent
import br.com.uri.campushub.testing.EmulatorData.daysFromNow
import br.com.uri.campushub.testing.EmulatorData.saveEvents
import br.com.uri.campushub.testing.EmulatorData.signIn
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.awaitError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// integração do EnrollmentRepository com o emulador do Firestore:
// transação que grava users/{uid}/enrollments/{eventId} e muda o enrolledCount do evento
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class EnrollmentRepositoryIntegrationTest {

    private companion object {
        const val OPEN = "aberto"
        const val LAST_SPOT = "ultima-vaga"
        const val BETO_EMAIL = "beto@campushub.test"
    }

    private val db = FirebaseEmulator.firestore
    private val enrollments = EnrollmentRepository(db)
    private val events = EventRepository(db)

    private lateinit var anaUid: String
    private lateinit var betoUid: String

    // dois eventos futuros (um com uma vaga só) e dois alunos; a Ana fica logada
    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
        createAdmin()
        saveEvents(mapOf(
            OPEN to Event(title = "Aberto", date = daysFromNow(7), capacity = 10),
            LAST_SPOT to Event(title = "Última vaga", date = daysFromNow(7), capacity = 1)
        ))
        betoUid = createStudent(BETO_EMAIL, "Beto", "Direito")
        anaUid = createStudent("ana@campushub.test", "Ana", "Direito")
    }

    // inscrever de novo não ocupa outra vaga: o id da inscrição é o id do evento
    @Test
    fun enrollingIsSavedOnceAndTakesOneSpot() {
        enrollments.enroll(anaUid, OPEN).await()
        enrollments.enroll(anaUid, OPEN).await()

        assertTrue(enrollments.isEnrolled(anaUid, OPEN).await())
        assertEquals(listOf(OPEN), enrollments.getEventIds(anaUid).await())
        assertEquals(1, events.get(OPEN).await()?.enrolledCount)
    }

    // a transação relê o evento: quem chega depois da última vaga recebe "esgotado"
    @Test
    fun lastSpotGoesToOnlyOneStudent() {
        enrollments.enroll(anaUid, LAST_SPOT).await()

        signIn(BETO_EMAIL)
        val error = enrollments.enroll(betoUid, LAST_SPOT).awaitError()

        assertTrue(EnrollmentRepository.isSoldOut(error))
        assertFalse(enrollments.isEnrolled(betoUid, LAST_SPOT).await())
        assertEquals(1, events.get(LAST_SPOT).await()?.enrolledCount)
    }

    // cancelar sem estar inscrito não devolve vaga a mais
    @Test
    fun cancellingGivesTheSpotBackOnlyOnce() {
        enrollments.enroll(anaUid, OPEN).await()

        enrollments.cancel(anaUid, OPEN).await()
        enrollments.cancel(anaUid, OPEN).await()

        assertFalse(enrollments.isEnrolled(anaUid, OPEN).await())
        assertEquals(0, events.get(OPEN).await()?.enrolledCount)
    }
}
