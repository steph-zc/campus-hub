package br.com.uri.campushub.domain

import br.com.uri.campushub.domain.RegisterValidator.Result
import org.junit.Assert.assertEquals
import org.junit.Test

// testes da validação do cadastro (RegisterValidator, em domain)
// regra pura: só JUnit, sem Android e sem Firebase
class RegisterValidatorTest {

    // chama o validador com dados válidos, trocando só o campo que o teste quer quebrar
    private fun validate(
        name: String = "Ana Souza",
        courseSelected: Boolean = true,
        email: String = "ana@uri.edu.br",
        password: String = "segredo1",
        confirmPassword: String = password
    ) = RegisterValidator.validate(name, courseSelected, email, password, confirmPassword)

    @Test
    fun `valid form is accepted`() {
        assertEquals(Result.VALID, validate())
    }

    @Test
    fun `name with only spaces counts as empty`() {
        assertEquals(Result.EMPTY_FIELDS, validate(name = "   "))
    }

    @Test
    fun `empty email is rejected`() {
        assertEquals(Result.EMPTY_FIELDS, validate(email = ""))
    }

    @Test
    fun `empty password is rejected`() {
        assertEquals(Result.EMPTY_FIELDS, validate(password = "", confirmPassword = "segredo1"))
    }

    @Test
    fun `empty confirmation is rejected`() {
        assertEquals(Result.EMPTY_FIELDS, validate(confirmPassword = ""))
    }

    @Test
    fun `missing course is rejected`() {
        assertEquals(Result.NO_COURSE, validate(courseSelected = false))
    }

    @Test
    fun `password shorter than the Firebase minimum is rejected`() {
        assertEquals(Result.SHORT_PASSWORD, validate(password = "12345"))
    }

    @Test
    fun `password with exactly the minimum length is accepted`() {
        val password = "a".repeat(RegisterValidator.MIN_PASSWORD_LENGTH)
        assertEquals(Result.VALID, validate(password = password))
    }

    @Test
    fun `different confirmation is rejected`() {
        assertEquals(Result.PASSWORDS_DONT_MATCH, validate(confirmPassword = "outrasenha"))
    }

    // a ordem dos erros segue a tela: o aluno vê primeiro o problema mais básico
    @Test
    fun `empty fields are reported before the missing course`() {
        assertEquals(Result.EMPTY_FIELDS, validate(name = "", courseSelected = false))
    }

    @Test
    fun `missing course is reported before the short password`() {
        assertEquals(Result.NO_COURSE, validate(courseSelected = false, password = "123"))
    }

    @Test
    fun `short password is reported before the different confirmation`() {
        assertEquals(Result.SHORT_PASSWORD, validate(password = "123", confirmPassword = "456"))
    }
}
