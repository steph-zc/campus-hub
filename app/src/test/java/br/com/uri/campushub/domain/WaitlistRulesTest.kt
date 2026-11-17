package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.TestEvents.TEN_YEARS_IN_DAYS
import br.com.uri.campushub.testing.TestEvents.event
import br.com.uri.campushub.testing.TestEvents.startingInMinutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// testes do WaitlistRules (domain): quando o aluno entra e sai da lista de espera.
// Regra pura: eventos em memória pelo TestEvents
class WaitlistRulesTest {

    private val course = "Direito"
    private val full = event(capacity = 10, enrolledCount = 10)

    @Test
    fun `student joins the waitlist of a full event`() {
        assertTrue(WaitlistRules.canJoin(full, course, isEnrolled = false, now = NOW))
    }

    // com vaga, o caminho é se inscrever; inscrito não precisa esperar
    @Test
    fun `no waitlist with spots left or when already enrolled`() {
        assertFalse(WaitlistRules.canJoin(event(capacity = 10, enrolledCount = 9), course, false, NOW))
        assertFalse(WaitlistRules.canJoin(full, course, isEnrolled = true, now = NOW))
    }

    // lotado mas por outro motivo fechado: a fila não ajudaria
    @Test
    fun `no waitlist when the event is closed for another reason`() {
        val otherCourse = event(capacity = 10, enrolledCount = 10, course = "Medicina", openToAll = false)
        val closed = event(capacity = 10, enrolledCount = 10, status = EventStatus.CLOSED)
        val draft = event(capacity = 10, enrolledCount = 10, status = EventStatus.DRAFT)
        val started = startingInMinutes(-10).copy(capacity = 10, enrolledCount = 10)

        for (event in listOf(otherCourse, closed, draft, started)) {
            assertFalse(WaitlistRules.canJoin(event, course, isEnrolled = false, now = NOW))
        }
    }

    @Test
    fun `student in the queue leaves before the event starts`() {
        assertTrue(WaitlistRules.canLeave(full, position = 3, now = NOW))
        assertFalse(WaitlistRules.canLeave(full, position = 0, now = NOW))
        assertFalse(WaitlistRules.canLeave(startingInMinutes(-10), position = 1, now = NOW))
    }

    // sem o "now", usa o relógio real, como a tela de detalhes
    @Test
    fun `rules use the current time when no date is given`() {
        val future = event(daysFromNow = TEN_YEARS_IN_DAYS, capacity = 1, enrolledCount = 1)
        assertTrue(WaitlistRules.canJoin(future, course, isEnrolled = false))
        assertTrue(WaitlistRules.canLeave(future, position = 1))
    }
}
