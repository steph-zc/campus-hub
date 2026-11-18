package br.com.uri.campushub.data.model

import br.com.uri.campushub.testing.TestEvents
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

    // imageVersion 0 = sem capa; qualquer versão gravada = tem capa em eventImages
    @Test
    fun `event has an image only with a saved version`() {
        assertFalse(Event().withImage())
        assertTrue(Event(imageVersion = 1_700_000_000_000L).withImage())
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

    // evento de organizador: começa no horário, mas só acaba quando ele encerra
    @Test
    fun `organizer event ends only when finished`() {
        val started = Event(organizerId = "uid-caio", date = Timestamp(TestEvents.daysFromNow(-1)))
        assertTrue(started.hasStarted(NOW))
        assertFalse(started.hasEnded(NOW))
        assertTrue(started.inProgress(NOW))

        val finished = started.copy(status = EventStatus.FINISHED.value)
        assertTrue(finished.hasEnded(NOW))
        assertFalse(finished.inProgress(NOW))
    }

    // sem o "now", usa o relógio real, como as telas
    @Test
    fun `start and end use the current time when no date is given`() {
        val past = Event(date = Timestamp(TestEvents.daysFromNow(-TestEvents.TEN_YEARS_IN_DAYS)))
        assertTrue(past.hasStarted())
        assertTrue(past.hasEnded())
        assertFalse(past.copy(organizerId = "uid-caio").hasEnded())
        assertTrue(past.copy(organizerId = "uid-caio").inProgress())
    }

    // fila com senhas: vazia quando a próxima a atender é a próxima a entregar
    @Test
    fun `queue exists while some ticket waits`() {
        assertFalse(Event().hasQueue())
        assertTrue(Event(waitlistNext = 3, waitlistHead = 1).hasQueue())
        assertFalse(Event(waitlistNext = 3, waitlistHead = 3).hasQueue())
    }

    // evento antigo, sem o campo status: o padrão do modelo é publicado
    @Test
    fun `event without status is published`() {
        assertEquals(EventStatus.PUBLISHED, Event().currentStatus())
        assertEquals(EventStatus.CLOSED, Event(status = "closed").currentStatus())
    }
}
