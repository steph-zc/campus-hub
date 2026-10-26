package br.com.uri.campushub.domain

// regras do formulário de cadastro, sem tela e sem Firebase
object RegisterValidator {

    // o Firebase não aceita senha menor que isso
    const val MIN_PASSWORD_LENGTH = 6

    enum class Result { VALID, EMPTY_FIELDS, NO_COURSE, SHORT_PASSWORD, PASSWORDS_DONT_MATCH }

    // as verificações seguem a ordem em que o aluno vê os erros na tela
    fun validate(
        name: String,
        courseSelected: Boolean,
        email: String,
        password: String,
        confirmPassword: String
    ): Result = when {
        // o Firebase fecha o app se receber email ou senha vazios
        name.isBlank() || email.isBlank() || password.isEmpty() || confirmPassword.isEmpty() ->
            Result.EMPTY_FIELDS
        !courseSelected -> Result.NO_COURSE
        password.length < MIN_PASSWORD_LENGTH -> Result.SHORT_PASSWORD
        password != confirmPassword -> Result.PASSWORDS_DONT_MATCH
        else -> Result.VALID
    }
}
