package br.com.uri.campushub.data.media

import android.content.Context
import android.media.MediaRecorder
import android.os.SystemClock
import java.io.File

// grava o microfone em um arquivo .m4a (AAC), o formato que o MediaPlayer toca em qualquer aparelho
// quem usa precisa ter pedido a permissão RECORD_AUDIO antes
class AudioRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var startedAt = 0L

    val isRecording get() = recorder != null

    // tempo gravado até agora
    val elapsedMs get() = if (isRecording) SystemClock.elapsedRealtime() - startedAt else 0L

    // onMaxReached é chamado quando o gravador para sozinho no limite
    fun start(file: File, maxDurationMs: Long, onMaxReached: () -> Unit) {
        val newRecorder = MediaRecorder(context)
        try {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setAudioEncodingBitRate(96_000)
            newRecorder.setAudioSamplingRate(44_100)
            newRecorder.setMaxDuration(maxDurationMs.toInt())
            newRecorder.setOutputFile(file)
            newRecorder.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) onMaxReached()
            }
            newRecorder.prepare()
            newRecorder.start()
        } catch (error: Exception) {
            newRecorder.release()
            throw error
        }
        recorder = newRecorder
        startedAt = SystemClock.elapsedRealtime()
    }

    // termina o arquivo e devolve a duração; null se não deu para salvar (gravação curta demais)
    fun stop(): Long? {
        val current = recorder ?: return null
        val duration = elapsedMs
        recorder = null
        return try {
            current.stop()
            duration
        } catch (_: RuntimeException) {
            // o MediaRecorder falha no stop quando ainda não gravou nada
            null
        } finally {
            current.release()
        }
    }
}
