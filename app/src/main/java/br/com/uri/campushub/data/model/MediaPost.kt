package br.com.uri.campushub.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.text.SimpleDateFormat
import java.util.Locale

// publicação de foto, áudio ou vídeo guardada em events/{eventId}/media
// o Firestore guarda só os dados; o arquivo fica no aparelho de quem publicou
data class MediaPost(
    @DocumentId val id: String = "",
    // quem publicou; só o autor pode excluir
    val userId: String = "",
    val authorName: String = "",
    // TYPE_PHOTO, TYPE_AUDIO ou TYPE_VIDEO
    val type: String = "",
    // nome do arquivo na pasta media do app
    val fileName: String = "",
    // duração do áudio ou do vídeo; 0 nas fotos
    val durationMs: Long = 0,
    val createdAt: Timestamp? = null
) {
    companion object {
        const val TYPE_PHOTO = "photo"
        const val TYPE_AUDIO = "audio"
        const val TYPE_VIDEO = "video"
    }

    fun formattedDate(): String {
        val format = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
        return createdAt?.let { format.format(it.toDate()) } ?: ""
    }
}
