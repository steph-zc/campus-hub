package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.data.model.Role
import java.util.Date
import java.util.concurrent.TimeUnit

// o que cada conta pode fazer com um evento, do ponto de vista do organizador.
// O app usa para mostrar ou esconder as ações; o firestore.rules confere o mesmo no servidor.
// Regras puras sobre o modelo Event (data/model) e o EventStatus; não acessa banco nem API
object OrganizerRules {

    // o evento só pode ser encerrado 1 hora depois do horário de início
    // (mesmo valor do duration.value(1, 'h') no firestore.rules)
    val FINISH_DELAY_MS = TimeUnit.HOURS.toMillis(1)

    // só o organizador responsável (organizerId) cuida do evento; eventos de exemplo
    // têm organizerId vazio e continuam sendo só da conta admin
    fun canManage(event: Event, uid: String, role: Role): Boolean =
        role == Role.ORGANIZER && uid.isNotEmpty() && event.organizerId == uid

    // até o evento ser encerrado ele pode ser editado (inclusive com as inscrições encerradas)
    fun canEdit(event: Event) = event.currentStatus() != EventStatus.FINISHED

    // publicar: só rascunho, e só antes do horário de início (depois ninguém conseguiria se inscrever)
    fun canPublish(event: Event, now: Date = Date()) =
        event.currentStatus() == EventStatus.DRAFT && !event.hasStarted(now)

    // encerrar as inscrições: só evento publicado que ainda não começou
    // (depois do início as inscrições já estão fechadas pelo horário)
    fun canCloseEnrollments(event: Event, now: Date = Date()) =
        event.currentStatus() == EventStatus.PUBLISHED && !event.hasStarted(now)

    // a partir de quando o evento pode ser encerrado; null se ele não tem data
    fun finishAvailableAt(event: Event): Date? =
        event.date?.let { Date(it.toDate().time + FINISH_DELAY_MS) }

    // encerrar o evento: publicado (com ou sem inscrições encerradas) e 1 hora depois do início
    fun canFinish(event: Event, now: Date = Date()): Boolean {
        val status = event.currentStatus()
        val availableAt = finishAvailableAt(event) ?: return false
        return (status == EventStatus.PUBLISHED || status == EventStatus.CLOSED) && !now.before(availableAt)
    }

    // excluir: só sem nenhum aluno inscrito; com inscritos o caminho é encerrar
    fun canDelete(event: Event) = event.enrolledCount == 0
}
