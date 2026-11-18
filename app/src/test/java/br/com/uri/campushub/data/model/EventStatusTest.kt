package br.com.uri.campushub.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

// testes do EventStatus (data/model): texto gravado no campo "status" ↔ situação do evento. JUnit puro
class EventStatusTest {

    @Test
    fun `each saved value maps to its status`() {
        assertEquals(EventStatus.DRAFT, EventStatus.from("draft"))
        assertEquals(EventStatus.PUBLISHED, EventStatus.from("published"))
        assertEquals(EventStatus.CLOSED, EventStatus.from("closed"))
        assertEquals(EventStatus.FINISHED, EventStatus.from("finished"))
    }

    // eventos gravados antes do campo existir (ou com valor estranho) continuam publicados,
    // igual ao padrão do firestore.rules
    @Test
    fun `missing or unknown value counts as published`() {
        assertEquals(EventStatus.PUBLISHED, EventStatus.from(null))
        assertEquals(EventStatus.PUBLISHED, EventStatus.from(""))
        assertEquals(EventStatus.PUBLISHED, EventStatus.from("archived"))
    }

    // filtro da lista dos alunos: tudo menos rascunho
    @Test
    fun `students see every status except draft`() {
        assertEquals(listOf("published", "closed", "finished"), EventStatus.VISIBLE)
    }
}
