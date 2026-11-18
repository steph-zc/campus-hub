package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventStatus
import java.util.Date

// quando o aluno pode se inscrever ou cancelar.
// Regras puras sobre o modelo Event (data/model); o firestore.rules confere o mesmo no servidor
object EnrollmentRules {

    // ALLOWED libera o botão; os outros dizem por que ele fica desativado.
    // IN_PROGRESS: já começou e o organizador ainda não encerrou;
    // NOT_PUBLISHED: rascunho (só o organizador vê); CLOSED: o organizador encerrou as inscrições
    enum class Status { ALLOWED, ENDED, IN_PROGRESS, NOT_PUBLISHED, CLOSED, OTHER_COURSE, SOLD_OUT }

    // a ordem importa: um evento que já acabou mostra "encerrado" mesmo se também estiver lotado,
    // e as inscrições encerradas pelo organizador valem antes do curso e das vagas
    fun check(event: Event, userCourse: String, now: Date = Date()): Status = when {
        event.hasEnded(now) -> Status.ENDED
        event.hasStarted(now) -> Status.IN_PROGRESS
        event.currentStatus() == EventStatus.DRAFT -> Status.NOT_PUBLISHED
        event.currentStatus() == EventStatus.CLOSED -> Status.CLOSED
        !event.acceptsCourse(userCourse) -> Status.OTHER_COURSE
        // com gente na lista de espera, a vaga que abrir é dela: para quem chega, o evento continua lotado
        event.remainingSpots() == 0 || event.hasQueue() -> Status.SOLD_OUT
        else -> Status.ALLOWED
    }

    // depois do horário de início não dá mais para cancelar; com as inscrições encerradas
    // o aluno ainda pode desistir (a vaga só não é oferecida a mais ninguém)
    fun canCancel(event: Event, isEnrolled: Boolean, now: Date = Date()) =
        isEnrolled && !event.hasStarted(now)
}
