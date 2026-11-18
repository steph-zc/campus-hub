package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.User
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// testes do AttendeeRules (domain): quando o documento do aluno na lista do organizador
// precisa ser criado, corrigido ou apagado. Regra pura, sem Firestore
class AttendeeRulesTest {

    private val ana = User(name = "Ana", course = "Direito")
    private val enrolledAna = Attendee(name = "Ana", course = "Direito", status = Attendee.ENROLLED)

    @Test
    fun `expected status follows the enrollment and the queue`() {
        assertEquals(Attendee.ENROLLED, AttendeeRules.expectedStatus(isEnrolled = true, waitlistPosition = 0))
        assertEquals(Attendee.WAITING, AttendeeRules.expectedStatus(isEnrolled = false, waitlistPosition = 2))
        assertNull(AttendeeRules.expectedStatus(isEnrolled = false, waitlistPosition = 0))
    }

    // inscrição de antes da lista existir: o documento ainda não existe
    @Test
    fun `missing document is created`() {
        assertTrue(AttendeeRules.needsSync(null, ana, Attendee.ENROLLED))
    }

    @Test
    fun `document matching the profile and the status stays`() {
        assertFalse(AttendeeRules.needsSync(enrolledAna, ana, Attendee.ENROLLED))
        assertFalse(AttendeeRules.needsSync(null, ana, expected = null))
    }

    // situação errada, nome ou curso trocados no perfil, ou documento sobrando
    @Test
    fun `outdated document is fixed or removed`() {
        assertTrue(AttendeeRules.needsSync(enrolledAna, ana, Attendee.WAITING))
        assertTrue(AttendeeRules.needsSync(enrolledAna, ana.copy(name = "Ana Souza"), Attendee.ENROLLED))
        assertTrue(AttendeeRules.needsSync(enrolledAna, ana.copy(course = "Medicina"), Attendee.ENROLLED))
        assertTrue(AttendeeRules.needsSync(enrolledAna, ana, expected = null))
    }
}
