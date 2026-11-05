package br.com.uri.campushub.data.model

import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.TestEvents.event
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// testes do modelo Event (data/model): vagas, encerramento, curso e formatos de data
// classe de dados comum, montada em memória pelo TestEvents; não usa Firestore nem Android
// (o Timestamp do Firebase é uma classe Java simples)
class EventTest {

    @Test
    fun `remaining spots are capacity minus enrollments`() {
        assertEquals(12, event(capacity = 40, enrolledCount = 28).remainingSpots())
    }

    @Test
    fun `remaining spots never go below zero`() {
        assertEquals(0, event(capacity = 10, enrolledCount = 12).remainingSpots())
    }

    @Test
    fun `event ended only when its date is before now`() {
        assertTrue(event(daysFromNow = -1).hasEnded(NOW))
        assertFalse(event(daysFromNow = 1).hasEnded(NOW))
    }

    @Test
    fun `event without date never counts as ended`() {
        assertFalse(event(daysFromNow = null).hasEnded(NOW))
    }

    @Test
    fun `open event accepts any course and exclusive event only its own`() {
        assertTrue(event(course = "Direito", openToAll = true).acceptsCourse("Medicina"))
        assertFalse(event(course = "Direito", openToAll = false).acceptsCourse("Medicina"))
        assertTrue(event(course = "Direito", openToAll = false).acceptsCourse("Direito"))
    }

    // 15/10/2026 (a data fixa do TestEvents) é uma quinta-feira
    @Test
    fun `dates are formatted in Brazilian Portuguese`() {
        val event = Event(date = Timestamp(NOW))
        assertEquals("15/10/2026 às 12:00", event.formattedDate())
        assertEquals("Quinta-feira, 15 de outubro de 2026", event.fullDate())
        assertEquals("15", event.day())
        assertEquals("OUT", event.shortMonth())
        assertEquals("12:00", event.time())
    }

    @Test
    fun `event without date shows empty texts`() {
        val event = Event()
        assertEquals("", event.formattedDate())
        assertEquals("", event.fullDate())
        assertEquals("", event.day())
        assertEquals("", event.shortMonth())
        assertEquals("", event.time())
    }

    // valores padrão: o Firestore precisa deles para montar o objeto com toObject()
    @Test
    fun `event can be created empty for Firestore`() {
        val event = Event()
        assertEquals("", event.id)
        assertTrue(event.openToAll)
        assertEquals(0, event.enrolledCount)
        assertNull(event.coordinates)
    }
}
