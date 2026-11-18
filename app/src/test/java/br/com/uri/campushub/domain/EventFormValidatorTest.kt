package br.com.uri.campushub.domain

import br.com.uri.campushub.domain.EventFormValidator.Result
import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.TestEvents.daysFromNow
import org.junit.Assert.assertEquals
import org.junit.Test

// testes do EventFormValidator (domain): formulário de evento do organizador.
// JUnit puro, com o "agora" fixo do TestEvents (15/10/2026 12:00)
class EventFormValidatorTest {

    // formulário válido; cada teste muda só o campo que quer conferir
    private val valid = EventFormValidator.Form(
        title = "Hackathon de Inovação",
        description = "Maratona de 24 horas para criar soluções para o campus.",
        category = "Competição",
        date = daysFromNow(10),
        location = "Laboratório 3",
        address = "Rua Batista Bonoto Sobrinho, 733 - Santiago - RS",
        capacity = "60"
    )

    private fun check(form: EventFormValidator.Form, originalDate: java.util.Date? = null, enrolled: Int = 0) =
        EventFormValidator.validate(form, originalDate, enrolled, NOW)

    @Test
    fun `complete form is accepted`() {
        assertEquals(Result.VALID, check(valid))
    }

    @Test
    fun `location inside the campus is optional`() {
        assertEquals(Result.VALID, check(valid.copy(location = "")))
    }

    @Test
    fun `required fields left blank are reported`() {
        assertEquals(Result.EMPTY_FIELDS, check(valid.copy(title = "   ")))
        assertEquals(Result.EMPTY_FIELDS, check(valid.copy(description = "")))
        assertEquals(Result.EMPTY_FIELDS, check(valid.copy(category = "")))
        assertEquals(Result.EMPTY_FIELDS, check(valid.copy(address = "")))
        assertEquals(Result.EMPTY_FIELDS, check(valid.copy(date = null)))
        assertEquals(Result.EMPTY_FIELDS, check(valid.copy(capacity = "")))
    }

    // os limites são os mesmos do firestore.rules; o limite exato ainda passa
    @Test
    fun `texts longer than the limits are rejected`() {
        assertEquals(Result.VALID, check(valid.copy(title = "a".repeat(EventFormValidator.MAX_TITLE))))
        assertEquals(Result.TOO_LONG, check(valid.copy(title = "a".repeat(EventFormValidator.MAX_TITLE + 1))))
        assertEquals(Result.TOO_LONG, check(valid.copy(description = "a".repeat(EventFormValidator.MAX_DESCRIPTION + 1))))
        assertEquals(Result.TOO_LONG, check(valid.copy(category = "a".repeat(EventFormValidator.MAX_CATEGORY + 1))))
        assertEquals(Result.TOO_LONG, check(valid.copy(location = "a".repeat(EventFormValidator.MAX_LOCATION + 1))))
        assertEquals(Result.TOO_LONG, check(valid.copy(address = "a".repeat(EventFormValidator.MAX_ADDRESS + 1))))
    }

    @Test
    fun `new event cannot be scheduled in the past or right now`() {
        assertEquals(Result.PAST_DATE, check(valid.copy(date = daysFromNow(-1))))
        assertEquals(Result.PAST_DATE, check(valid.copy(date = NOW)))
    }

    // evento que já aconteceu pode ter o texto corrigido sem trocar a data
    @Test
    fun `editing keeps a past date that did not change`() {
        val past = daysFromNow(-3)
        assertEquals(Result.VALID, check(valid.copy(date = past), originalDate = past))
        assertEquals(Result.PAST_DATE, check(valid.copy(date = daysFromNow(-1)), originalDate = past))
    }

    @Test
    fun `capacity must be a whole number inside the limit`() {
        assertEquals(Result.INVALID_CAPACITY, check(valid.copy(capacity = "abc")))
        assertEquals(Result.INVALID_CAPACITY, check(valid.copy(capacity = "0")))
        assertEquals(Result.INVALID_CAPACITY, check(valid.copy(capacity = "-5")))
        assertEquals(Result.INVALID_CAPACITY, check(valid.copy(capacity = "${EventFormValidator.MAX_CAPACITY + 1}")))
        assertEquals(Result.VALID, check(valid.copy(capacity = " ${EventFormValidator.MAX_CAPACITY} ")))
    }

    // diminuir a capacidade não pode tirar a vaga de quem já se inscreveu
    @Test
    fun `capacity cannot drop below the students already enrolled`() {
        assertEquals(Result.CAPACITY_BELOW_ENROLLED, check(valid.copy(capacity = "9"), enrolled = 10))
        assertEquals(Result.VALID, check(valid.copy(capacity = "10"), enrolled = 10))
    }

    // os erros aparecem na ordem do formulário: vazio antes de longo, longo antes da data...
    @Test
    fun `errors are reported in the order of the form`() {
        val everythingWrong = valid.copy(
            title = "",
            description = "a".repeat(EventFormValidator.MAX_DESCRIPTION + 1),
            date = daysFromNow(-1),
            capacity = "0"
        )
        assertEquals(Result.EMPTY_FIELDS, check(everythingWrong))
        assertEquals(Result.TOO_LONG, check(everythingWrong.copy(title = "Ok")))
        assertEquals(Result.PAST_DATE, check(everythingWrong.copy(title = "Ok", description = "Ok")))
        assertEquals(
            Result.INVALID_CAPACITY,
            check(everythingWrong.copy(title = "Ok", description = "Ok", date = daysFromNow(1)))
        )
    }

    // a tela chama sem o "now": a data de daqui a dez anos é futura no relógio real
    @Test
    fun `validation uses the current time when no date is given`() {
        val farFuture = java.util.Date(System.currentTimeMillis() + 3650L * 24 * 60 * 60 * 1000)
        assertEquals(Result.VALID, EventFormValidator.validate(valid.copy(date = farFuture)))
    }
}
