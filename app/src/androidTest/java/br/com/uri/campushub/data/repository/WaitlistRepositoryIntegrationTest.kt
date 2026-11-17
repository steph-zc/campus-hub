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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// integração do WaitlistRepository com o emulador do Firestore: a ordem da fila pela hora do servidor,
// a posição calculada pela agregação count e a inscrição automática do primeiro da fila (settle).
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class WaitlistRepositoryIntegrationTest {

    private companion object {
        const val EVENT = "lotado"
        const val ANA = "ana@campushub.test"
        const val BETO = "beto@campushub.test"
        const val CAIO = "caio@campushub.test"
    }

    private val db = FirebaseEmulator.firestore
    private val waitlist = WaitlistRepository(db)
    private val enrollments = EnrollmentRepository(db)

    private lateinit var anaUid: String
    private lateinit var betoUid: String
    private lateinit var caioUid: String

    // evento de 1 vaga já ocupada pelo Caio; Ana e Beto ficam de fora
    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
        createAdmin()
        saveEvents(mapOf(EVENT to Event(title = "Lotado", date = daysFromNow(5), capacity = 1)))
        anaUid = createStudent(ANA, "Ana", "Direito")
        betoUid = createStudent(BETO, "Beto", "Direito")
        caioUid = createStudent(CAIO, "Caio", "Direito")
        enrollments.enroll(caioUid, EVENT).await()
    }

    // quem entra antes fica na frente; quem sai libera a posição para os de trás
    @Test
    fun queueFollowsTheJoiningOrder() {
        signIn(ANA)
        assertEquals(0, waitlist.getPosition(anaUid, EVENT).await())
        waitlist.join(anaUid, EVENT).await()
        signIn(BETO)
        waitlist.join(betoUid, EVENT).await()

        assertEquals(2, waitlist.getPosition(betoUid, EVENT).await())
        signIn(ANA)
        assertEquals(1, waitlist.getPosition(anaUid, EVENT).await())

        waitlist.leave(anaUid, EVENT).await()
        assertEquals(0, waitlist.getPosition(anaUid, EVENT).await())
        signIn(BETO)
        assertEquals(1, waitlist.getPosition(betoUid, EVENT).await())
    }

    // a vaga do Caio abre: o settle inscreve a Ana, que estava na fila, e ela vê o aviso uma vez só
    @Test
    fun freedSpotEnrollsTheFirstInQueueWithANotice() {
        signIn(ANA)
        waitlist.join(anaUid, EVENT).await()
        signIn(CAIO)
        enrollments.cancel(caioUid, EVENT).await()
        assertEquals(1, waitlist.settle(EVENT).await())

        signIn(ANA)
        assertTrue(enrollments.isEnrolled(anaUid, EVENT).await())
        assertEquals(0, waitlist.getPosition(anaUid, EVENT).await())
        assertTrue(enrollments.takePromotionNotice(anaUid, EVENT).await())
        assertFalse(enrollments.takePromotionNotice(anaUid, EVENT).await())
    }

    // sem vaga, o settle não inscreve ninguém e a fila fica como está
    @Test
    fun settleWithoutSpotsChangesNothing() {
        signIn(ANA)
        waitlist.join(anaUid, EVENT).await()

        assertEquals(0, waitlist.settle(EVENT).await())
        assertEquals(1, waitlist.getPosition(anaUid, EVENT).await())
    }
}
