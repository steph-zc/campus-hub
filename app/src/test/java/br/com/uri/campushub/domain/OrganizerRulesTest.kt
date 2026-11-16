package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Role
import br.com.uri.campushub.testing.TestEvents.event
import org.junit.Assert.assertFalse
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
}
