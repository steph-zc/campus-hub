package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.Event
import java.util.Date

// quando o QR Code de check-in aparece para o aluno.
// Regra pura sobre Event e Attendee (data/model); não acessa banco nem API
object CheckinRules {

    // inscrito (não na fila), com o segredo do QR já salvo e com o evento ainda não encerrado:
    // antes e durante o evento o aluno pode mostrar o código na entrada
    fun canShowQr(event: Event, attendee: Attendee?, now: Date = Date()): Boolean =
        attendee != null
            && attendee.enrolled()
            && CheckinCode.isValidToken(attendee.checkinToken)
            && !event.hasEnded(now)
}
