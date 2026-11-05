package br.com.uri.campushub.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.RatingSummary
import br.com.uri.campushub.testing.EmulatorData.createAdmin
import br.com.uri.campushub.testing.EmulatorData.createStudent
import br.com.uri.campushub.testing.EmulatorData.daysFromNow
import br.com.uri.campushub.testing.EmulatorData.moveEventDate
import br.com.uri.campushub.testing.EmulatorData.saveEvents
import br.com.uri.campushub.testing.EmulatorData.signIn
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.await
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// integração do RatingRepository com o emulador do Firestore: events/{eventId}/ratings/{uid}
// e a média calculada pelo próprio Firestore (agregação no servidor)
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class RatingRepositoryIntegrationTest {

    private companion object {
        const val EVENT = "palestra"
        const val ANA_EMAIL = "ana@campushub.test"
        const val BETO_EMAIL = "beto@campushub.test"
    }

    private val db = FirebaseEmulator.firestore
    private val ratings = RatingRepository(db)
    private val enrollments = EnrollmentRepository(db)

    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
        createAdmin()
        saveEvents(mapOf(EVENT to Event(title = "Palestra", date = daysFromNow(3), capacity = 10)))
    }

    // os dois se inscrevem, o evento acontece e cada um avalia (a Ana troca a nota uma vez)
    @Test
    fun eachStudentHasOneRatingAndTheServerCalculatesTheAverage() {
        val betoUid = createStudent(BETO_EMAIL, "Beto", "Direito")
        enrollments.enroll(betoUid, EVENT).await()
        val anaUid = createStudent(ANA_EMAIL, "Ana", "Direito")
        enrollments.enroll(anaUid, EVENT).await()
        moveEventDate(EVENT, daysFromNow(-1))

        signIn(ANA_EMAIL)
        assertEquals(0, ratings.getMyRating(anaUid, EVENT).await())
        ratings.save(anaUid, EVENT, 3).await()
        ratings.save(anaUid, EVENT, 4).await()
        signIn(BETO_EMAIL)
        ratings.save(betoUid, EVENT, 5).await()

        assertEquals(RatingSummary(4.5, 2), ratings.getSummary(EVENT).await())
        signIn(ANA_EMAIL)
        assertEquals(4, ratings.getMyRating(anaUid, EVENT).await())
    }
}
