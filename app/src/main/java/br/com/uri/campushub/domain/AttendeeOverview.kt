package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.Event
import java.text.Collator

// resumo que o organizador vê na tela de inscritos: quem está inscrito (em ordem alfabética),
// quem está na fila (na ordem de chegada) e os números de vagas ocupadas e check-ins.
// Regra pura sobre os modelos Event e Attendee (data/model); não acessa banco nem API
data class AttendeeOverview(
    val enrolled: List<Attendee>,
    val waiting: List<Attendee>,
    // vagas ocupadas: o enrolledCount do evento, que é o número oficial
    val occupied: Int,
    val capacity: Int,
    val checkedIn: Int
) {
    // inscrições feitas antes da lista existir e que ainda não aparecem: o aluno entra
    // na lista na próxima vez que abrir o evento (AttendeeRepository.sync)
    val unlisted: Int get() = (occupied - enrolled.size).coerceAtLeast(0)

    companion object {

        // ordem alfabética do português: acentos e maiúsculas não mudam a posição ("Ágata" antes de "Bruno")
        private val alphabetical = Collator.getInstance(CampusTime.LOCALE).apply { strength = Collator.PRIMARY }

        fun of(event: Event, attendees: List<Attendee>): AttendeeOverview {
            val (enrolled, waiting) = attendees.partition { it.enrolled() }
            return AttendeeOverview(
                enrolled = enrolled.sortedWith(compareBy(alphabetical) { it.name }),
                // quem entrou antes na fila vem primeiro (since = hora em que entrou)
                waiting = waiting.sortedWith(compareBy(nullsLast()) { it.since }),
                occupied = event.enrolledCount,
                capacity = event.capacity,
                checkedIn = enrolled.count { it.checkedIn() }
            )
        }
    }
}
