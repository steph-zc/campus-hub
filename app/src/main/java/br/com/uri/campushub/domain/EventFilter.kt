package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Event
import java.text.Normalizer
import java.util.Date

// busca, filtros e ordem da lista de eventos
object EventFilter {

    enum class Status { UPCOMING, ENDED, ALL }

    // próximos primeiro, do mais perto para o mais longe;
    // depois os encerrados, do mais recente para o mais antigo
    // (espera a lista já em ordem de data, como vem do Firestore)
    fun sortForList(events: List<Event>, now: Date = Date()): List<Event> {
        val (ended, upcoming) = events.partition { it.hasEnded(now) }
        return upcoming + ended.reversed()
    }

    // os três filtros valem juntos; categoria vazia = todas
    fun filter(
        events: List<Event>,
        query: String,
        status: Status,
        category: String,
        now: Date = Date()
    ): List<Event> {
        val normalizedQuery = normalize(query.trim())
        return events.filter { event ->
            val matchesTitle = normalize(event.title).contains(normalizedQuery)
            val matchesStatus = when (status) {
                Status.UPCOMING -> !event.hasEnded(now)
                Status.ENDED -> event.hasEnded(now)
                Status.ALL -> true
            }
            val matchesCategory = category.isEmpty() || event.category == category
            matchesTitle && matchesStatus && matchesCategory
        }
    }

    // "Palestra Inteligência" e "palestra inteligencia" ficam iguais:
    // tira os acentos e deixa tudo minúsculo antes de comparar
    fun normalize(text: String): String {
        val withoutAccents = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return withoutAccents.lowercase()
    }
}
