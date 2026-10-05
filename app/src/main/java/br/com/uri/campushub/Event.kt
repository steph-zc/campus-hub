package br.com.uri.campushub

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.text.SimpleDateFormat
import java.util.Locale

// os valores padrão permitem que o Firestore monte o objeto sozinho com toObject()
data class Event(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val date: Timestamp? = null,
    val capacity: Int = 0,
    // curso que organiza o evento, com os mesmos nomes da lista do cadastro
    val course: String = "",
    // false = só alunos do curso podem participar
    val openToAll: Boolean = true
) {
    fun formattedDate() = format("dd/MM/yyyy 'às' HH:mm")

    fun day() = format("dd")

    // "out." vira "OUT"
    fun shortMonth() = format("MMM").replace(".", "").uppercase()

    fun time() = format("HH:mm")

    private fun format(pattern: String): String {
        val format = SimpleDateFormat(pattern, Locale.forLanguageTag("pt-BR"))
        return date?.let { format.format(it.toDate()) } ?: ""
    }
}
