package br.com.uri.campushub.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import java.text.SimpleDateFormat
import java.util.Date
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
    val openToAll: Boolean = true,
    // quantos alunos já se inscreveram; muda a cada inscrição ou cancelamento
    val enrolledCount: Int = 0,
    // tipo do evento: Palestra, Workshop, Seminário...
    val category: String = "",
    // endereço da rua; "location" continua sendo o lugar dentro do campus (sala, auditório...)
    val address: String = "",
    // ponto do evento no mapa; null nos eventos ainda sem localização
    val coordinates: GeoPoint? = null
) {
    fun remainingSpots() = (capacity - enrolledCount).coerceAtLeast(0)

    // não pode começar com "is": o Firestore acharia que é um campo e gravaria no documento
    // "now" tem valor padrão; os testes podem passar outra data para simular o tempo
    fun hasEnded(now: Date = Date()) = date != null && date.toDate().before(now)

    // exclusivos só aceitam alunos do mesmo curso
    fun acceptsCourse(userCourse: String) = openToAll || course == userCourse

    fun formattedDate() = format("dd/MM/yyyy 'às' HH:mm")

    // "terça-feira, 20 de outubro de 2026" com a primeira letra maiúscula
    fun fullDate() = format("EEEE, dd 'de' MMMM 'de' yyyy").replaceFirstChar { it.uppercase() }

    fun day() = format("dd")

    // "out." vira "OUT"
    fun shortMonth() = format("MMM").replace(".", "").uppercase()

    fun time() = format("HH:mm")

    private fun format(pattern: String): String {
        val format = SimpleDateFormat(pattern, Locale.forLanguageTag("pt-BR"))
        return date?.let { format.format(it.toDate()) } ?: ""
    }
}
