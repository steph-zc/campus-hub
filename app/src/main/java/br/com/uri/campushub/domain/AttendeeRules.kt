package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.User

// quando o documento do aluno na lista do organizador (events/{eventId}/attendees/{uid}) precisa ser
// criado, corrigido ou apagado. Usado pelo AttendeeRepository.sync, que o app do próprio aluno roda
// ao abrir o evento: assim inscrições antigas (de antes da lista existir) e nomes trocados no perfil
// aparecem certos para o organizador. Regra pura sobre Attendee e User (data/model)
object AttendeeRules {

    // situação que o documento deveria ter: inscrito, na fila ou nenhuma (o documento não deveria existir)
    fun expectedStatus(isEnrolled: Boolean, waitlistPosition: Int): String? = when {
        isEnrolled -> Attendee.ENROLLED
        waitlistPosition > 0 -> Attendee.WAITING
        else -> null
    }

    // o documento salvo (null = não existe) não bate com o perfil e a situação atuais
    fun needsSync(saved: Attendee?, user: User, expected: String?): Boolean = when {
        expected == null -> saved != null
        saved == null -> true
        else -> saved.status != expected || saved.name != user.name || saved.course != user.course
    }
}
