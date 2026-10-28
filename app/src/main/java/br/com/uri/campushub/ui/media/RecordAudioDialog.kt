package br.com.uri.campushub.ui.media

import android.os.Handler
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import br.com.uri.campushub.R
import br.com.uri.campushub.data.media.AudioRecorder
import br.com.uri.campushub.data.media.MediaStorage
import br.com.uri.campushub.domain.MediaRules
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File

// janela "Gravando áudio": começa a gravar ao abrir e mostra o tempo até o limite de 1 minuto
// onRecorded recebe o arquivo pronto para publicar
class RecordAudioDialog(
    private val activity: AppCompatActivity,
    private val storage: MediaStorage,
    private val onRecorded: (fileName: String, durationMs: Long) -> Unit
) {

    companion object {
        private const val TICK_MS = 200L
    }

    private val recorder = AudioRecorder(activity)
    private val handler = Handler(Looper.getMainLooper())
    private var dialog: AlertDialog? = null
    private var file: File? = null

    private lateinit var txtRecordTime: TextView
    private lateinit var progressRecord: LinearProgressIndicator

    private val tick = object : Runnable {
        override fun run() {
            showElapsed()
            handler.postDelayed(this, TICK_MS)
        }
    }

    fun show() {
        val newFile = storage.newFile("m4a")
        try {
            // no limite o gravador para sozinho e o áudio é publicado como se o aluno tivesse tocado em parar
            recorder.start(newFile, MediaRules.MAX_AUDIO_MS) { finish() }
        } catch (_: Exception) {
            newFile.delete()
            Toast.makeText(activity, R.string.record_failed, Toast.LENGTH_LONG).show()
            return
        }
        file = newFile

        val view = activity.layoutInflater.inflate(R.layout.dialog_record_audio, null)
        txtRecordTime = view.findViewById(R.id.txtRecordTime)
        progressRecord = view.findViewById(R.id.progressRecord)
        progressRecord.max = MediaRules.MAX_AUDIO_MS.toInt()

        // fora da janela não fecha: o aluno escolhe publicar ou descartar
        dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.recording_audio)
            .setView(view)
            .setCancelable(false)
            .setPositiveButton(R.string.stop_and_publish) { _, _ -> finish() }
            .setNegativeButton(R.string.cancel) { _, _ -> cancel() }
            .show()
        handler.post(tick)
    }

    // descarta a gravação; a seção também chama quando a tela some no meio
    fun cancel() {
        val current = file ?: return
        close()
        recorder.stop()
        current.delete()
    }

    private fun finish() {
        val current = file ?: return
        close()
        val duration = recorder.stop()
        if (duration == null || !MediaRules.isAudioLongEnough(duration)) {
            current.delete()
            Toast.makeText(activity, R.string.audio_too_short, Toast.LENGTH_SHORT).show()
            return
        }
        // o relógio passa alguns milissegundos do limite enquanto o gravador fecha o arquivo
        onRecorded(current.name, duration.coerceAtMost(MediaRules.MAX_AUDIO_MS))
    }

    private fun close() {
        file = null
        handler.removeCallbacks(tick)
        dialog?.dismiss()
        dialog = null
    }

    private fun showElapsed() {
        val elapsed = recorder.elapsedMs.coerceAtMost(MediaRules.MAX_AUDIO_MS)
        txtRecordTime.text = activity.getString(
            R.string.record_time,
            MediaRules.formatDuration(elapsed),
            MediaRules.formatDuration(MediaRules.MAX_AUDIO_MS)
        )
        progressRecord.setProgressCompat(elapsed.toInt(), false)
    }
}
