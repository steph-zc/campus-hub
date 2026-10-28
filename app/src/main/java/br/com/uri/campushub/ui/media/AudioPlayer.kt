package br.com.uri.campushub.ui.media

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import java.io.File

// toca um áudio por vez na lista; tocar outro para o anterior
// avisa a lista (onChanged) ao tocar, pausar, terminar e a cada fração de segundo para a barra andar
class AudioPlayer {

    companion object {
        private const val TICK_MS = 200L
    }

    // id da publicação que mudou de estado
    var onChanged: ((postId: String) -> Unit)? = null

    private var player: MediaPlayer? = null
    private var currentPostId: String? = null
    private val handler = Handler(Looper.getMainLooper())

    private val tick = object : Runnable {
        override fun run() {
            currentPostId?.let { onChanged?.invoke(it) }
            if (player?.isPlaying == true) handler.postDelayed(this, TICK_MS)
        }
    }

    fun isPlaying(postId: String) = postId == currentPostId && player?.isPlaying == true

    // posição atual; 0 se esse áudio não é o que está carregado
    fun positionMs(postId: String): Long =
        if (postId == currentPostId) player?.currentPosition?.toLong() ?: 0L else 0L

    // play/pause do mesmo áudio, ou troca para outro
    fun toggle(postId: String, file: File) {
        val current = player
        when {
            postId == currentPostId && current != null && current.isPlaying -> {
                current.pause()
                notifyChanged(postId)
            }
            postId == currentPostId && current != null -> {
                current.start()
                startTicking()
            }
            else -> play(postId, file)
        }
    }

    private fun play(postId: String, file: File) {
        stop()
        val newPlayer = MediaPlayer()
        try {
            newPlayer.setDataSource(file.absolutePath)
            newPlayer.prepare()
        } catch (error: Exception) {
            newPlayer.release()
            throw error
        }
        // ao terminar volta para o começo, pronto para tocar de novo
        newPlayer.setOnCompletionListener { stop() }
        player = newPlayer
        currentPostId = postId
        newPlayer.start()
        startTicking()
    }

    // solta o áudio carregado; a tela chama ao ser destruída
    fun stop() {
        val postId = currentPostId
        handler.removeCallbacks(tick)
        player?.release()
        player = null
        currentPostId = null
        postId?.let { notifyChanged(it) }
    }

    // usado ao excluir: só para se for esse áudio que está tocando
    fun stopIfPlaying(postId: String) {
        if (postId == currentPostId) stop()
    }

    // quando a tela some (outro app, tela desligada) o áudio pausa e continua de onde parou
    fun pause() {
        val postId = currentPostId ?: return
        if (player?.isPlaying == true) {
            player?.pause()
            notifyChanged(postId)
        }
    }

    private fun startTicking() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    private fun notifyChanged(postId: String) {
        onChanged?.invoke(postId)
    }
}
