package br.com.uri.campushub.ui.checkin

import android.app.Activity
import androidx.appcompat.app.AlertDialog
import br.com.uri.campushub.R
import br.com.uri.campushub.domain.CampusTime
import br.com.uri.campushub.domain.CheckinRules.Outcome

// resultado de uma leitura de QR para o organizador: confirmado, já feito ou recusado (e por quê).
// "Ler próximo" abre a câmera de novo, para a fila da entrada andar rápido.
// Só mostra o Outcome do CheckinRules (domain); não acessa banco nem API
object CheckinResultDialog {

    fun show(activity: Activity, outcome: Outcome, onScanNext: () -> Unit): AlertDialog {
        val (title, message) = texts(activity, outcome)
        return AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.scan_next) { _, _ -> onScanNext() }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun texts(activity: Activity, outcome: Outcome): Pair<String, String> = when (outcome) {
        is Outcome.Done ->
            activity.getString(R.string.checkin_done_title) to activity.getString(R.string.checkin_done, outcome.name)
        is Outcome.AlreadyDone -> {
            val time = outcome.at?.let { CampusTime.formatter("HH:mm").format(it.toDate()) } ?: "--:--"
            activity.getString(R.string.checkin_already_title) to
                activity.getString(R.string.checkin_already, outcome.name, time)
        }
        Outcome.NotEnrolled ->
            activity.getString(R.string.checkin_refused_title) to activity.getString(R.string.checkin_not_enrolled)
        Outcome.WrongCode ->
            activity.getString(R.string.checkin_refused_title) to activity.getString(R.string.checkin_wrong_code)
        Outcome.OtherEvent ->
            activity.getString(R.string.checkin_refused_title) to activity.getString(R.string.checkin_other_event)
        Outcome.NotACheckinCode ->
            activity.getString(R.string.checkin_refused_title) to activity.getString(R.string.checkin_not_a_code)
    }
}
