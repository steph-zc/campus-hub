package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.data.model.Role
import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.TestEvents.startingInMinutes
import br.com.uri.campushub.testing.TestEvents.event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// testes do OrganizerRules (domain): quem pode gerenciar um evento. JUnit puro
class OrganizerRulesTest {

    private val caioEvent = event(organizerId = "uid-caio")

    @Test
    fun `responsible organizer manages the event`() {
        assertTrue(OrganizerRules.canManage(caioEvent, "uid-caio", Role.ORGANIZER))
    }

    @Test
    fun `another organizer cannot manage it`() {
        assertFalse(OrganizerRules.canManage(caioEvent, "uid-dani", Role.ORGANIZER))
    }

    // o uid bate, mas o organizers/{uid} foi apagado no console: perde o acesso
    @Test
    fun `account that is no longer an organizer cannot manage it`() {
        assertFalse(OrganizerRules.canManage(caioEvent, "uid-caio", Role.STUDENT))
    }

    // eventos de exemplo têm organizerId vazio; uid vazio nunca pode "combinar" com eles
    @Test
    fun `sample events without organizer are never managed`() {
        assertFalse(OrganizerRules.canManage(event(organizerId = ""), "", Role.ORGANIZER))
        assertFalse(OrganizerRules.canManage(event(organizerId = ""), "uid-caio", Role.ORGANIZER))
    }

    // até o evento ser encerrado ele é editado, inclusive com as inscrições encerradas
    @Test
    fun `only a finished event can no longer be edited`() {
        assertTrue(OrganizerRules.canEdit(event(status = EventStatus.DRAFT)))
        assertTrue(OrganizerRules.canEdit(event(status = EventStatus.PUBLISHED)))
        assertTrue(OrganizerRules.canEdit(event(status = EventStatus.CLOSED)))
        assertFalse(OrganizerRules.canEdit(event(status = EventStatus.FINISHED)))
    }

    @Test
    fun `only an upcoming draft can be published`() {
        assertTrue(OrganizerRules.canPublish(event(status = EventStatus.DRAFT), NOW))
        assertFalse(OrganizerRules.canPublish(startingInMinutes(-1, status = EventStatus.DRAFT), NOW))
        assertFalse(OrganizerRules.canPublish(event(status = EventStatus.PUBLISHED), NOW))
        assertFalse(OrganizerRules.canPublish(event(status = EventStatus.CLOSED), NOW))
    }

    // sem o "now" usa o relógio real, como a tela de detalhes
    @Test
    fun `rules use the current time when no date is given`() {
        assertTrue(OrganizerRules.canPublish(event(status = EventStatus.DRAFT, daysFromNow = 3650)))
        assertTrue(OrganizerRules.canCloseEnrollments(event(daysFromNow = 3650)))
        assertTrue(OrganizerRules.canFinish(event(daysFromNow = -3650)))
    }

    // encerrar as inscrições: só publicado e antes do início
    @Test
    fun `enrollments close only on a published event that did not start`() {
        assertTrue(OrganizerRules.canCloseEnrollments(event(status = EventStatus.PUBLISHED), NOW))
        assertFalse(OrganizerRules.canCloseEnrollments(startingInMinutes(-1), NOW))
        assertFalse(OrganizerRules.canCloseEnrollments(event(status = EventStatus.DRAFT), NOW))
        assertFalse(OrganizerRules.canCloseEnrollments(event(status = EventStatus.CLOSED), NOW))
        assertFalse(OrganizerRules.canCloseEnrollments(event(status = EventStatus.FINISHED), NOW))
    }

    // encerrar o evento: a partir de 1 hora depois do início, publicado ou com inscrições encerradas
    @Test
    fun `event can be finished from one hour after it starts`() {
        assertFalse(OrganizerRules.canFinish(startingInMinutes(30), NOW))
        assertFalse(OrganizerRules.canFinish(startingInMinutes(-59), NOW))
        assertTrue(OrganizerRules.canFinish(startingInMinutes(-60), NOW))
        assertTrue(OrganizerRules.canFinish(startingInMinutes(-90, status = EventStatus.CLOSED), NOW))
    }

    @Test
    fun `draft finished or undated events cannot be finished`() {
        assertFalse(OrganizerRules.canFinish(startingInMinutes(-90, status = EventStatus.DRAFT), NOW))
        assertFalse(OrganizerRules.canFinish(startingInMinutes(-90, status = EventStatus.FINISHED), NOW))
        assertFalse(OrganizerRules.canFinish(event(daysFromNow = null), NOW))
    }

    @Test
    fun `finishing becomes available one hour after the start`() {
        val event = startingInMinutes(0)
        assertEquals(NOW.time + OrganizerRules.FINISH_DELAY_MS, OrganizerRules.finishAvailableAt(event)?.time)
        assertNull(OrganizerRules.finishAvailableAt(event(daysFromNow = null)))
    }

    // excluir: só sem inscritos
    @Test
    fun `only an event without enrollments can be deleted`() {
        assertTrue(OrganizerRules.canDelete(event(enrolledCount = 0)))
        assertFalse(OrganizerRules.canDelete(event(enrolledCount = 1)))
    }
}
