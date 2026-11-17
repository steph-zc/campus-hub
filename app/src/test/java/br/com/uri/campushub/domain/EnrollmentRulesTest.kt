package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.domain.EnrollmentRules.Status
import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.TestEvents.TEN_YEARS_IN_DAYS
import br.com.uri.campushub.testing.TestEvents.event
import br.com.uri.campushub.testing.TestEvents.startingInMinutes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// testes de quando o aluno pode se inscrever ou cancelar (EnrollmentRules, em domain)
// regra pura: eventos em memória pelo TestEvents
class EnrollmentRulesTest {

    private val course = "Direito"

    @Test
    fun `student can enroll in an upcoming open event with spots`() {
        assertEquals(Status.ALLOWED, EnrollmentRules.check(event(), course, NOW))
    }

    @Test
    fun `event that already happened is closed`() {
        assertEquals(Status.ENDED, EnrollmentRules.check(event(daysFromNow = -1), course, NOW))
    }

    @Test
    fun `ended event shows ended even when it is also sold out`() {
        val event = event(daysFromNow = -1, capacity = 10, enrolledCount = 10)
        assertEquals(Status.ENDED, EnrollmentRules.check(event, course, NOW))
    }

    @Test
    fun `exclusive event blocks students from other courses`() {
        val exclusive = event(course = "Medicina", openToAll = false)
        assertEquals(Status.OTHER_COURSE, EnrollmentRules.check(exclusive, course, NOW))
    }

    @Test
    fun `exclusive event accepts students from its own course`() {
        val exclusive = event(course = course, openToAll = false)
        assertEquals(Status.ALLOWED, EnrollmentRules.check(exclusive, course, NOW))
    }

    @Test
    fun `full event is sold out`() {
        val full = event(capacity = 30, enrolledCount = 30)
        assertEquals(Status.SOLD_OUT, EnrollmentRules.check(full, course, NOW))
    }

    @Test
    fun `event with more enrollments than capacity is still sold out`() {
        val overbooked = event(capacity = 30, enrolledCount = 31)
        assertEquals(Status.SOLD_OUT, EnrollmentRules.check(overbooked, course, NOW))
    }

    @Test
    fun `last spot is still available`() {
        val almostFull = event(capacity = 30, enrolledCount = 29)
        assertEquals(Status.ALLOWED, EnrollmentRules.check(almostFull, course, NOW))
    }

    // rascunho: só o organizador vê, e o botão diz que ainda não foi publicado
    @Test
    fun `draft event is not published yet`() {
        assertEquals(Status.NOT_PUBLISHED, EnrollmentRules.check(event(status = EventStatus.DRAFT), course, NOW))
    }

    @Test
    fun `event closed by the organizer refuses new enrollments`() {
        assertEquals(Status.CLOSED, EnrollmentRules.check(event(status = EventStatus.CLOSED), course, NOW))
    }

    // inscrições encerradas valem antes do curso e das vagas; o evento que já aconteceu vale antes de tudo
    @Test
    fun `closed wins over course and spots but not over ended`() {
        val closedFull = event(status = EventStatus.CLOSED, capacity = 10, enrolledCount = 10, course = "Medicina", openToAll = false)
        assertEquals(Status.CLOSED, EnrollmentRules.check(closedFull, course, NOW))
        assertEquals(Status.ENDED, EnrollmentRules.check(event(status = EventStatus.CLOSED, daysFromNow = -1), course, NOW))
    }

    // evento de organizador que já começou e não foi encerrado: em andamento, sem inscrição nem cancelamento
    @Test
    fun `organizer event that started is in progress until finished`() {
        val started = startingInMinutes(-10)
        assertEquals(Status.IN_PROGRESS, EnrollmentRules.check(started, course, NOW))
        assertFalse(EnrollmentRules.canCancel(started, isEnrolled = true, now = NOW))

        val finished = startingInMinutes(-90, status = EventStatus.FINISHED)
        assertEquals(Status.ENDED, EnrollmentRules.check(finished, course, NOW))
    }

    // com as inscrições encerradas o aluno ainda pode desistir antes do evento
    @Test
    fun `enrolled student can still cancel after enrollments close`() {
        assertTrue(EnrollmentRules.canCancel(event(status = EventStatus.CLOSED), isEnrolled = true, now = NOW))
    }

    @Test
    fun `enrolled student can cancel before the event`() {
        assertTrue(EnrollmentRules.canCancel(event(), isEnrolled = true, now = NOW))
    }

    @Test
    fun `student who is not enrolled has nothing to cancel`() {
        assertFalse(EnrollmentRules.canCancel(event(), isEnrolled = false, now = NOW))
    }

    @Test
    fun `enrollment cannot be cancelled after the event`() {
        assertFalse(EnrollmentRules.canCancel(event(daysFromNow = -1), isEnrolled = true, now = NOW))
    }

    // sem o "now", as regras usam o relógio real, como na tela de detalhes
    @Test
    fun `rules use the current time when no date is given`() {
        val past = event(daysFromNow = -TEN_YEARS_IN_DAYS)
        val future = event(daysFromNow = TEN_YEARS_IN_DAYS)
        assertEquals(Status.ENDED, EnrollmentRules.check(past, course))
        assertEquals(Status.ALLOWED, EnrollmentRules.check(future, course))
        assertFalse(EnrollmentRules.canCancel(past, isEnrolled = true))
        assertTrue(EnrollmentRules.canCancel(future, isEnrolled = true))
    }
}
