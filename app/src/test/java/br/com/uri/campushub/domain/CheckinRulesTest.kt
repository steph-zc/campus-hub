package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.TestEvents.TEN_YEARS_IN_DAYS
import br.com.uri.campushub.testing.TestEvents.event
import br.com.uri.campushub.testing.TestEvents.startingInMinutes
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// testes do CheckinRules (domain): quando o aluno vê o QR Code de check-in.
// Regra pura: eventos do TestEvents e participantes em memória
class CheckinRulesTest {

    private val enrolled = Attendee(userId = "uid-ana", status = Attendee.ENROLLED,
        checkinToken = "0123456789abcdef0123456789abcdef")

    // inscrita, antes e durante o evento do organizador
    @Test
    fun `enrolled student sees the code before and during the event`() {
        assertTrue(CheckinRules.canShowQr(event(organizerId = "uid-caio"), enrolled, NOW))
        assertTrue(CheckinRules.canShowQr(startingInMinutes(-30), enrolled, NOW))
    }

    // na fila, sem documento, sem segredo ou com o evento encerrado: nada de QR
    @Test
    fun `no code without a valid enrollment or after the event`() {
        val upcoming = event(organizerId = "uid-caio")
        assertFalse(CheckinRules.canShowQr(upcoming, null, NOW))
        assertFalse(CheckinRules.canShowQr(upcoming, enrolled.copy(status = Attendee.WAITING), NOW))
        assertFalse(CheckinRules.canShowQr(upcoming, enrolled.copy(checkinToken = ""), NOW))
        assertFalse(CheckinRules.canShowQr(startingInMinutes(-120, status = EventStatus.FINISHED), enrolled, NOW))
    }

    // sem o "now", usa o relógio real, como a tela de detalhes
    @Test
    fun `rule uses the current time when no date is given`() {
        assertTrue(CheckinRules.canShowQr(event(daysFromNow = TEN_YEARS_IN_DAYS), enrolled))
    }
}
