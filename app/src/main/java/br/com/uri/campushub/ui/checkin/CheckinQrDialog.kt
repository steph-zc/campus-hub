package br.com.uri.campushub.ui.checkin

import android.app.Activity
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.domain.CheckinCode

// mostra o QR Code de check-in do aluno inscrito, aberto pelo botão dos detalhes do evento.
// O conteúdo vem do CheckinCode (evento + aluno + segredo salvo em attendees/{uid}) e a imagem
// é desenhada no aparelho pelo QrCodeImage (ZXing); não acessa banco nem API
object CheckinQrDialog {

    // tamanho do bitmap; a ImageView (240dp) reduz sem perder nitidez
    private const val QR_SIZE_PX = 720

    fun show(activity: Activity, event: Event, attendee: Attendee): AlertDialog {
        val view = activity.layoutInflater.inflate(R.layout.dialog_checkin_qr, null)
        val code = CheckinCode.encode(event.id, attendee.userId, attendee.checkinToken)
        view.findViewById<ImageView>(R.id.imgQr).setImageBitmap(QrCodeImage.render(code, QR_SIZE_PX))
        view.findViewById<TextView>(R.id.txtQrEvent).text = event.title
        view.findViewById<TextView>(R.id.txtQrDate).text = event.formattedDate()
        view.findViewById<TextView>(R.id.txtQrName).text = attendee.name

        val dialog = AlertDialog.Builder(activity)
            .setView(view)
            .setPositiveButton(R.string.close, null)
            .show()
        // tela no brilho máximo enquanto o QR está aberto, para a câmera do organizador ler fácil;
        // vale só para esta janela e volta ao normal quando ela fecha
        dialog.window?.let { window ->
            window.attributes = window.attributes.apply {
                screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
            }
        }
        return dialog
    }
}
