package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventStatus
import com.google.firebase.Timestamp
import java.security.MessageDigest
import java.util.Date

// regras do check-in por QR Code: quando o aluno vê o código, quando o organizador pode ler
// e o que acontece com cada leitura. O firestore.rules confere o mesmo no servidor.
// Regra pura sobre Event e Attendee (data/model) e o CheckinCode; não acessa banco nem API
object CheckinRules {

    // resultado de uma leitura, do ponto de vista do organizador
    sealed class Outcome {
        // check-in registrado agora
        data class Done(val name: String) : Outcome()
        // o aluno já tinha feito check-in (o QR vale uma vez só)
        data class AlreadyDone(val name: String, val at: Timestamp?) : Outcome()
        // QR de outro app ou estragado
        object NotACheckinCode : Outcome()
        // QR do CampusHub, mas de outro evento
        object OtherEvent : Outcome()
        // o aluno não está inscrito (cancelou, está só na fila ou nunca se inscreveu)
        object NotEnrolled : Outcome()
        // o segredo não bate com o salvo: QR inventado ou copiado
        object WrongCode : Outcome()
    }

    // inscrito (não na fila), com o segredo do QR já salvo, sem check-in feito e com o evento
    // ainda não encerrado: antes e durante o evento o aluno pode mostrar o código na entrada
    fun canShowQr(event: Event, attendee: Attendee?, now: Date = Date()): Boolean =
        attendee != null
            && attendee.enrolled()
            && !attendee.checkedIn()
            && CheckinCode.isValidToken(attendee.checkinToken)
            && !event.hasEnded(now)

    // o organizador lê QR enquanto o evento está publicado ou com as inscrições encerradas
    // (antes e durante; rascunho não tem inscritos e evento encerrado não recebe mais ninguém)
    fun canCheckIn(event: Event): Boolean =
        event.currentStatus() == EventStatus.PUBLISHED || event.currentStatus() == EventStatus.CLOSED

    // primeiro passo: o texto lido pela câmera é um QR de check-in deste evento?
    // Devolve os dados do QR ou o motivo da recusa
    fun read(text: String, eventId: String): Pair<CheckinCode.Payload?, Outcome?> {
        val payload = CheckinCode.parse(text) ?: return null to Outcome.NotACheckinCode
        if (payload.eventId != eventId) return null to Outcome.OtherEvent
        return payload to null
    }

    // segundo passo: o documento do aluno na lista (null = não existe) e o segredo lido no QR
    fun check(attendee: Attendee?, token: String): Outcome = when {
        attendee == null || !attendee.enrolled() -> Outcome.NotEnrolled
        !sameSecret(attendee.checkinToken, token) -> Outcome.WrongCode
        attendee.checkedIn() -> Outcome.AlreadyDone(attendee.name, attendee.checkedInAt)
        else -> Outcome.Done(attendee.name)
    }

    // comparação em tempo constante (MessageDigest.isEqual): o tempo da resposta não revela
    // quantos caracteres do segredo estavam certos
    private fun sameSecret(saved: String, read: String): Boolean =
        saved.isNotEmpty() && MessageDigest.isEqual(saved.toByteArray(), read.toByteArray())
}
