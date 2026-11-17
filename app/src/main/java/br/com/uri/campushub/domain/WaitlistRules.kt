package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Event
import java.util.Date

// quando o aluno pode entrar ou sair da lista de espera de um evento lotado.
// Regras puras sobre o modelo Event (data/model) e o EnrollmentRules; o firestore.rules confere o mesmo
object WaitlistRules {

    // entrar na fila: só quem não está inscrito e só quando o único motivo para não se inscrever
    // é a falta de vaga (evento publicado, futuro e aberto ao curso do aluno)
    fun canJoin(event: Event, userCourse: String, isEnrolled: Boolean, now: Date = Date()) =
        !isEnrolled && EnrollmentRules.check(event, userCourse, now) == EnrollmentRules.Status.SOLD_OUT

    // sair da fila: quem está nela, até o horário de início (depois a fila não serve mais para nada)
    fun canLeave(event: Event, position: Int, now: Date = Date()) =
        position > 0 && !event.hasStarted(now)
}
