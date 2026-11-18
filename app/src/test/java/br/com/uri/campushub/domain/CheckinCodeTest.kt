package br.com.uri.campushub.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom

// testes do CheckinCode (domain): segredo do aluno, texto gravado no QR e a leitura de volta.
// Regra pura, sem Android nem Firebase
class CheckinCodeTest {

    private val token = "0123456789abcdef0123456789abcdef"

    // 32 dígitos hexadecimais, diferentes a cada chamada
    @Test
    fun `new secrets have the expected format and differ`() {
        val first = CheckinCode.newToken()
        val second = CheckinCode.newToken(SecureRandom())

        assertTrue(CheckinCode.isValidToken(first))
        assertTrue(CheckinCode.isValidToken(second))
        assertNotEquals(first, second)
    }

    @Test
    fun `only 32 lowercase hex digits are a secret`() {
        assertTrue(CheckinCode.isValidToken(token))
        assertFalse(CheckinCode.isValidToken(""))
        assertFalse(CheckinCode.isValidToken(token.uppercase()))
        assertFalse(CheckinCode.isValidToken(token.dropLast(1)))
        assertFalse(CheckinCode.isValidToken(token.dropLast(1) + "g"))
    }

    // o texto do QR volta exatamente aos mesmos dados
    @Test
    fun `encoded code is read back`() {
        val text = CheckinCode.encode("event-1", "uid_Ana9", token)

        assertEquals("campushub-checkin:v1:event-1:uid_Ana9:$token", text)
        assertEquals(CheckinCode.Payload("event-1", "uid_Ana9", token), CheckinCode.parse(" $text\n"))
    }

    // dado que quebraria o formato não vira QR
    @Test
    fun `invalid data is not encoded`() {
        assertThrows(IllegalArgumentException::class.java) { CheckinCode.encode("evento:1", "uid", token) }
        assertThrows(IllegalArgumentException::class.java) { CheckinCode.encode("event-1", "", token) }
        assertThrows(IllegalArgumentException::class.java) { CheckinCode.encode("event-1", "uid", "segredo") }
    }

    // QR de outro app, versão desconhecida ou campos estranhos: não é um check-in do CampusHub
    @Test
    fun `foreign or broken codes are refused`() {
        for (text in listOf(
            "https://campushub.example/evento",
            "campushub-checkin:v2:event-1:uid:$token",
            "outro-app:v1:event-1:uid:$token",
            "campushub-checkin:v1:event-1:uid",
            "campushub-checkin:v1:event 1:uid:$token",
            "campushub-checkin:v1:event-1:uid/x:$token",
            "campushub-checkin:v1:event-1:uid:XYZ"
        )) {
            assertNull(text, CheckinCode.parse(text))
        }
    }
}
