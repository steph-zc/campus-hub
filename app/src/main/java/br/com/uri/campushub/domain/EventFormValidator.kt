package br.com.uri.campushub.domain

import java.util.Date

// regras do formulário de evento do organizador (criar e editar), sem tela e sem Firebase.
// Os mesmos limites estão no firestore.rules (isValidEventForm), que confere tudo de novo no servidor
object EventFormValidator {

    const val MAX_TITLE = 100
    const val MAX_DESCRIPTION = 2000
    const val MAX_CATEGORY = 50
    const val MAX_LOCATION = 100
    const val MAX_ADDRESS = 200
    const val MAX_CAPACITY = 5000

    enum class Result {
        VALID,
        EMPTY_FIELDS,
        TOO_LONG,
        PAST_DATE,
        INVALID_CAPACITY,
        CAPACITY_BELOW_ENROLLED
    }

    // o que o organizador digitou; a capacidade chega como texto para o validador tratar letras e vazio
    data class Form(
        val title: String,
        val description: String,
        val category: String,
        val date: Date?,
        val location: String,
        val address: String,
        val capacity: String
    )

    // originalDate: data salva antes da edição (null ao criar). Um evento que já aconteceu
    // pode ter o texto corrigido, mas a data só pode mudar para o futuro.
    // enrolledCount: inscritos atuais; a capacidade nova não pode deixar ninguém sem vaga.
    // As verificações seguem a ordem em que o organizador vê os erros na tela
    fun validate(
        form: Form,
        originalDate: Date? = null,
        enrolledCount: Int = 0,
        now: Date = Date()
    ): Result {
        val capacity = form.capacity.trim().toIntOrNull()
        return when {
            form.title.isBlank() || form.description.isBlank() || form.category.isBlank() ||
                form.address.isBlank() || form.date == null || form.capacity.isBlank() ->
                Result.EMPTY_FIELDS
            form.title.trim().length > MAX_TITLE ||
                form.description.trim().length > MAX_DESCRIPTION ||
                form.category.trim().length > MAX_CATEGORY ||
                form.location.trim().length > MAX_LOCATION ||
                form.address.trim().length > MAX_ADDRESS -> Result.TOO_LONG
            form.date != originalDate && !form.date.after(now) -> Result.PAST_DATE
            capacity == null || capacity < 1 || capacity > MAX_CAPACITY -> Result.INVALID_CAPACITY
            capacity < enrolledCount -> Result.CAPACITY_BELOW_ENROLLED
            else -> Result.VALID
        }
    }
}
