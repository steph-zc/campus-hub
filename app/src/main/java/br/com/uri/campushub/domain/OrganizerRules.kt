package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.Role

// o que cada conta pode fazer com um evento, do ponto de vista do organizador.
// O app usa para mostrar ou esconder as ações; o firestore.rules confere o mesmo no servidor
object OrganizerRules {

    // só o organizador responsável (organizerId) cuida do evento; eventos de exemplo
    // têm organizerId vazio e continuam sendo só da conta admin
    fun canManage(event: Event, uid: String, role: Role): Boolean =
        role == Role.ORGANIZER && uid.isNotEmpty() && event.organizerId == uid
}
