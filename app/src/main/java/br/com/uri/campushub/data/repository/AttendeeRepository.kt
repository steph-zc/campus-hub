package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.domain.AttendeeRules
import br.com.uri.campushub.domain.CheckinCode
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

// lista de participantes que o organizador acompanha: events/{eventId}/attendees/{uid} (Cloud Firestore).
// Os documentos são gravados junto com a inscrição (EnrollmentRepository) e com a lista de espera
// (WaitlistRepository), nas mesmas transações; aqui ficam a leitura do organizador e o sync,
// que o app do aluno roda ao abrir o evento para criar ou corrigir o próprio documento.
// Cada documento guarda o segredo do QR de check-in do aluno (checkinToken, gerado pelo CheckinCode).
// As regras do Firestore deixam só o organizador dono ler a lista e só o próprio aluno gravar o dele.
// newToken é injetável para os testes usarem um segredo fixo
class AttendeeRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val newToken: () -> String = { CheckinCode.newToken() }
) {

    companion object {
        const val COLLECTION = "attendees"

        fun doc(db: FirebaseFirestore, eventId: String, uid: String): DocumentReference =
            db.collection("events").document(eventId).collection(COLLECTION).document(uid)

        // dados gravados para o aluno: nome e curso do perfil users/{uid}, a situação, o segredo do QR
        // e since = hora do servidor, ou a já salva quando a situação não mudou
        fun data(
            user: User,
            status: String,
            token: String,
            since: Any = FieldValue.serverTimestamp()
        ): Map<String, Any> =
            mapOf("name" to user.name, "course" to user.course, "status" to status, "since" to since, "checkinToken" to token)
    }

    // todos os participantes do evento (inscritos e fila); a tela ordena pelo AttendeeOverview
    fun getAll(eventId: String): Task<List<Attendee>> =
        db.collection("events").document(eventId).collection(COLLECTION).get()
            .continueWith { task -> task.result.toObjects(Attendee::class.java) }

    // deixa o documento do aluno igual à situação real (inscrito, na fila ou nenhuma) e ao perfil atual,
    // e devolve como ele ficou (null = não existe). Cobre inscrições feitas antes da lista existir,
    // nomes trocados no perfil e documentos ainda sem o segredo do QR (AttendeeRules decide)
    fun sync(uid: String, eventId: String, user: User, isEnrolled: Boolean, waitlistPosition: Int): Task<Attendee?> {
        val ref = doc(db, eventId, uid)
        val expected = AttendeeRules.expectedStatus(isEnrolled, waitlistPosition)
        // continueWith lendo task.result: uma falha na gravação continua sendo falha
        return ref.get().onSuccessTask { snapshot ->
            val saved = if (snapshot.exists()) snapshot.toObject(Attendee::class.java) else null
            when {
                !AttendeeRules.needsSync(saved, user, expected) -> Tasks.forResult(saved)
                expected == null -> ref.delete().continueWith { task -> task.result; null }
                else -> {
                    // o segredo do QR não muda: um código já mostrado continua valendo
                    val token = saved?.checkinToken?.takeIf { CheckinCode.isValidToken(it) } ?: newToken()
                    // mesma situação (só o nome ou o curso mudou): mantém a hora em que entrou
                    val since = saved?.since?.takeIf { saved.status == expected }
                    val data = if (since != null) data(user, expected, token, since) else data(user, expected, token)
                    ref.set(data).continueWith { task ->
                        task.result
                        Attendee(uid, user.name, user.course, expected, since, checkinToken = token)
                    }
                }
            }
        }
    }
}
