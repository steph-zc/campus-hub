package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.domain.EventFilter.Status
import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.TestEvents.TEN_YEARS_IN_DAYS
import br.com.uri.campushub.testing.TestEvents.event
import org.junit.Assert.assertEquals
import org.junit.Test

// testes da ordem da lista, da busca e dos filtros (EventFilter, em domain)
// regra pura: os eventos são montados em memória pelo TestEvents, sem Firestore
class EventFilterTest {

    // dois encerrados e dois próximos, já em ordem de data como o Firestore devolve
    private val oldEnded = event(id = "old", title = "Dia de Campo", daysFromNow = -20, category = "Visita técnica")
    private val recentEnded = event(id = "recent", title = "Seminário de Gestão", daysFromNow = -2, category = "Seminário")
    private val soon = event(id = "soon", title = "Palestra: Inteligência Artificial", daysFromNow = 1)
    private val later = event(id = "later", title = "Hackathon CampusHub", daysFromNow = 30, category = "Competição")
    private val events = listOf(oldEnded, recentEnded, soon, later)

    // compara pelos ids: a mensagem de erro fica curta e mostra a ordem
    private fun ids(list: List<Event>) = list.map { it.id }

    @Test
    fun `list shows upcoming events first and then the most recent ended ones`() {
        val sorted = EventFilter.sortForList(events, NOW)
        assertEquals(listOf("soon", "later", "recent", "old"), ids(sorted))
    }

    @Test
    fun `event without date stays with the upcoming ones`() {
        val noDate = event(id = "no-date", daysFromNow = null)
        val sorted = EventFilter.sortForList(listOf(oldEnded, noDate), NOW)
        assertEquals(listOf("no-date", "old"), ids(sorted))
    }

    @Test
    fun `normalize removes accents and uppercase letters`() {
        assertEquals("palestra inteligencia", EventFilter.normalize("Palestra INTELIGÊNCIA"))
        assertEquals("seminario acao", EventFilter.normalize("Seminário Ação"))
    }

    @Test
    fun `search ignores accents and case`() {
        val result = EventFilter.filter(events, "inteligencia", Status.ALL, "", NOW)
        assertEquals(listOf("soon"), ids(result))
    }

    @Test
    fun `search ignores spaces around the text`() {
        val result = EventFilter.filter(events, "  hackathon  ", Status.ALL, "", NOW)
        assertEquals(listOf("later"), ids(result))
    }

    @Test
    fun `empty search returns every event`() {
        assertEquals(ids(events), ids(EventFilter.filter(events, "", Status.ALL, "", NOW)))
    }

    @Test
    fun `search without matches returns an empty list`() {
        assertEquals(emptyList<String>(), ids(EventFilter.filter(events, "xadrez", Status.ALL, "", NOW)))
    }

    @Test
    fun `upcoming status keeps only events that did not happen yet`() {
        val result = EventFilter.filter(events, "", Status.UPCOMING, "", NOW)
        assertEquals(listOf("soon", "later"), ids(result))
    }

    @Test
    fun `ended status keeps only events that already happened`() {
        val result = EventFilter.filter(events, "", Status.ENDED, "", NOW)
        assertEquals(listOf("old", "recent"), ids(result))
    }

    @Test
    fun `category filter keeps only that category`() {
        val result = EventFilter.filter(events, "", Status.ALL, "Seminário", NOW)
        assertEquals(listOf("recent"), ids(result))
    }

    @Test
    fun `search, status and category work together`() {
        // "palestra" acha o evento próximo, mas o filtro de encerrados o descarta
        assertEquals(emptyList<String>(), ids(EventFilter.filter(events, "palestra", Status.ENDED, "", NOW)))
        assertEquals(listOf("soon"), ids(EventFilter.filter(events, "palestra", Status.UPCOMING, "Palestra", NOW)))
    }

    // sem o "now", a ordem e os filtros usam o relógio real, como na lista de eventos
    @Test
    fun `sorting and filters use the current time when no date is given`() {
        val past = event(id = "past", daysFromNow = -TEN_YEARS_IN_DAYS)
        val future = event(id = "future", daysFromNow = TEN_YEARS_IN_DAYS)
        val list = listOf(past, future)
        assertEquals(listOf("future", "past"), ids(EventFilter.sortForList(list)))
        assertEquals(listOf("future"), ids(EventFilter.filter(list, "", Status.UPCOMING, "")))
    }
}
