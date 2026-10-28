package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.MediaPost

// regras das publicações de fotos, áudios e vídeos
object MediaRules {

    // áudio curto: o gravador para sozinho em 1 minuto
    const val MAX_AUDIO_MS = 60_000L

    // menos que isso costuma ser toque sem querer
    const val MIN_AUDIO_MS = 1_000L

    // vídeo curto: a câmera recebe esse limite, e o da galeria é conferido
    const val MAX_VIDEO_MS = 30_000L

    // a câmera costuma passar alguns décimos do limite ao fechar o arquivo
    private const val VIDEO_TOLERANCE_MS = 1_000L

    // qualquer aluno logado publica, mas só o autor exclui
    fun canDelete(post: MediaPost, userId: String) =
        userId.isNotEmpty() && post.userId == userId

    fun isAudioLongEnough(durationMs: Long) = durationMs >= MIN_AUDIO_MS

    fun isVideoShortEnough(durationMs: Long) = durationMs <= MAX_VIDEO_MS + VIDEO_TOLERANCE_MS

    // 0:07, 1:00, 12:34
    fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs.coerceAtLeast(0) / 1000
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }
}
