package br.com.uri.campushub.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import br.com.uri.campushub.domain.CampusTime

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
        // no fuso do campus (CampusTime), igual às datas dos eventos
        return createdAt?.let { CampusTime.formatter("dd/MM/yyyy 'às' HH:mm").format(it.toDate()) } ?: ""
    }
}
