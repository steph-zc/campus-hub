package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.TestEvents.TEN_YEARS_IN_DAYS
import br.com.uri.campushub.testing.TestEvents.event
import br.com.uri.campushub.testing.TestEvents.startingInMinutes
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// testes do CheckinRules (domain): quando o aluno vê o QR Code de check-in e o resultado
// de cada leitura do organizador.
// Regra pura: eventos do TestEvents e participantes em memória
class CheckinRulesTest {

    private companion object {
        const val TOKEN = "0123456789abcdef0123456789abcdef"
    }

    private val enrolled = Attendee(userId = "uid-ana", name = "Ana", status = Attendee.ENROLLED, checkinToken = TOKEN)

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
        // depois do check-in o QR não aparece mais
        assertFalse(CheckinRules.canShowQr(upcoming, enrolled.copy(checkedInAt = Timestamp(NOW)), NOW))
    }

    // ---------- leitura pelo organizador ----------

    @Test
    fun `organizer checks in while the event is published or closed`() {
        assertTrue(CheckinRules.canCheckIn(event(status = EventStatus.PUBLISHED)))
        assertTrue(CheckinRules.canCheckIn(event(status = EventStatus.CLOSED)))
        assertFalse(CheckinRules.canCheckIn(event(status = EventStatus.DRAFT)))
        assertFalse(CheckinRules.canCheckIn(event(status = EventStatus.FINISHED)))
    }

    // texto lido: QR de outro app, de outro evento ou deste evento
    @Test
    fun `scanned text is checked against the event`() {
        val code = CheckinCode.encode("event-1", "uid-ana", TOKEN)

        assertEquals(null to CheckinRules.Outcome.NotACheckinCode, CheckinRules.read("https://exemplo.com", "event-1"))
        assertEquals(null to CheckinRules.Outcome.OtherEvent, CheckinRules.read(code, "event-2"))
        assertEquals(CheckinCode.Payload("event-1", "uid-ana", TOKEN) to null, CheckinRules.read(code, "event-1"))
    }

    // documento do aluno e segredo lido: confirma, avisa que já foi feito ou recusa
    @Test
    fun `reading outcome follows the saved attendee`() {
        val at = Timestamp(NOW)
        assertEquals(CheckinRules.Outcome.Done("Ana"), CheckinRules.check(enrolled, TOKEN))
        assertEquals(CheckinRules.Outcome.AlreadyDone("Ana", at), CheckinRules.check(enrolled.copy(checkedInAt = at), TOKEN))
        assertEquals(CheckinRules.Outcome.WrongCode, CheckinRules.check(enrolled, "f".repeat(32)))
        assertEquals(CheckinRules.Outcome.WrongCode, CheckinRules.check(enrolled.copy(checkinToken = ""), ""))
        assertEquals(CheckinRules.Outcome.NotEnrolled, CheckinRules.check(null, TOKEN))
        assertEquals(CheckinRules.Outcome.NotEnrolled, CheckinRules.check(enrolled.copy(status = Attendee.WAITING), TOKEN))
    }

    // sem o "now", usa o relógio real, como a tela de detalhes
    @Test
    fun `rule uses the current time when no date is given`() {
        assertTrue(CheckinRules.canShowQr(event(daysFromNow = TEN_YEARS_IN_DAYS), enrolled))
    }
}
