package br.com.uri.campushub

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import java.text.SimpleDateFormat
import java.util.Locale

// comentário guardado em events/{eventId}/comments
data class Comment(
    @DocumentId val id: String = "",
    // quem escreveu; usado depois para liberar editar e excluir só para o autor
    val userId: String = "",
    // o nome fica gravado no comentário para não precisar buscar o perfil de cada autor
    val authorName: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null,
    // preenchido na primeira edição; null = nunca foi editado
    val editedAt: Timestamp? = null
) {
    fun formattedDate(): String {
        val format = SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
        return createdAt?.let { format.format(it.toDate()) } ?: ""
    }
}
