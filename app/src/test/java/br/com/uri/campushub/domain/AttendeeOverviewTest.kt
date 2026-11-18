package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.TestEvents.event
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Date

// testes do AttendeeOverview (domain): ordem das seções e números do resumo da tela de inscritos.
// Regra pura: eventos do TestEvents e participantes em memória
class AttendeeOverviewTest {

    private fun minutesAfterNow(minutes: Int) = Timestamp(Date(NOW.time + minutes * 60_000L))

    private fun enrolled(name: String, checkedIn: Boolean = false) = Attendee(
        userId = "uid-$name", name = name, status = Attendee.ENROLLED,
        checkedInAt = if (checkedIn) Timestamp(NOW) else null
    )

    private fun waiting(name: String, minutes: Int?) =
        Attendee(userId = "uid-$name", name = name, status = Attendee.WAITING, since = minutes?.let { minutesAfterNow(it) })

    // inscritos em ordem alfabética do português (acento não muda a posição); fila por ordem de chegada
    @Test
    fun `enrolled are alphabetical and the queue follows the arrival order`() {
        val overview = AttendeeOverview.of(
            event(capacity = 10, enrolledCount = 3),
            listOf(enrolled("bruno"), waiting("Caio", 20), enrolled("Ágata"), waiting("Davi", 5), enrolled("Carla"))
        )

        assertEquals(listOf("Ágata", "bruno", "Carla"), overview.enrolled.map { it.name })
        assertEquals(listOf("Davi", "Caio"), overview.waiting.map { it.name })
    }

    // vagas ocupadas vêm do enrolledCount do evento; check-ins contam só os inscritos presentes
    @Test
    fun `summary counts spots and check-ins`() {
        val overview = AttendeeOverview.of(
            event(capacity = 40, enrolledCount = 3),
            listOf(enrolled("Ana", checkedIn = true), enrolled("Beto"), enrolled("Caio", checkedIn = true))
        )

        assertEquals(3, overview.occupied)
        assertEquals(40, overview.capacity)
        assertEquals(2, overview.checkedIn)
        assertEquals(0, overview.unlisted)
    }

    // inscrições antigas sem documento: o resumo avisa quantas faltam na lista (nunca negativo)
    @Test
    fun `enrollments missing from the list are counted`() {
        assertEquals(2, AttendeeOverview.of(event(enrolledCount = 3), listOf(enrolled("Ana"))).unlisted)
        assertEquals(0, AttendeeOverview.of(event(enrolledCount = 0), listOf(enrolled("Ana"))).unlisted)
    }

    // um lugar sem hora ainda (gravação pendente) fica no fim da fila
    @Test
    fun `waiting student without time goes last`() {
        val overview = AttendeeOverview.of(event(), listOf(waiting("Sem hora", null), waiting("Ana", 1)))

        assertEquals(listOf("Ana", "Sem hora"), overview.waiting.map { it.name })
    }
}
