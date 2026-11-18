package br.com.uri.campushub.data.model

import br.com.uri.campushub.domain.CampusTime
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId

// participante de um evento, guardado em events/{eventId}/attendees/{uid} (Cloud Firestore):
// cópia do nome e do curso do aluno para o organizador acompanhar quem vai, sem acesso ao perfil
// (users/{uid} continua privado). Existe enquanto o aluno está inscrito ou na lista de espera;
// as regras do Firestore conferem que o nome e o curso são os do perfil e que a situação é verdadeira.
// Os valores padrão permitem o toObject() do Firestore; o id do documento é o uid do aluno
data class Attendee(
    @DocumentId val userId: String = "",
    val name: String = "",
    val course: String = "",
    // ENROLLED ou WAITING
    val status: String = ENROLLED,
    // quando entrou na situação atual (inscrição ou fila), pela hora do servidor
    val since: Timestamp? = null,
    // preenchido no check-in pelo organizador; null = ainda não chegou
    val checkedInAt: Timestamp? = null
) {
    companion object {
        const val ENROLLED = "enrolled"
        const val WAITING = "waiting"
    }

    // não pode começar com "is": o Firestore acharia que é um campo e gravaria no documento
    fun enrolled() = status == ENROLLED

    fun checkedIn() = checkedInAt != null

    // "17/11/2026 às 19:15", no fuso do campus, igual às outras datas do app
    fun formattedSince(): String =
        since?.let { CampusTime.formatter("dd/MM/yyyy 'às' HH:mm").format(it.toDate()) } ?: ""
}
