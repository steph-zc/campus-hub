package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Event
import java.util.Date

// quando o aluno pode se inscrever ou cancelar
object EnrollmentRules {

    // ALLOWED libera o botão; os outros dizem por que ele fica desativado
    enum class Status { ALLOWED, ENDED, OTHER_COURSE, SOLD_OUT }

    // a ordem importa: um evento encerrado mostra "encerrado" mesmo se também estiver lotado
    fun check(event: Event, userCourse: String, now: Date = Date()): Status = when {
        event.hasEnded(now) -> Status.ENDED
        !event.acceptsCourse(userCourse) -> Status.OTHER_COURSE
        event.remainingSpots() == 0 -> Status.SOLD_OUT
        else -> Status.ALLOWED
    }

    // depois que o evento acontece não dá mais para cancelar
    fun canCancel(event: Event, isEnrolled: Boolean, now: Date = Date()) =
        isEnrolled && !event.hasEnded(now)
}
