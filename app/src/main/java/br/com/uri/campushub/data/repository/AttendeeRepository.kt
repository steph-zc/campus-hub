package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.domain.AttendeeRules
import br.com.uri.campushub.domain.CheckinCode
import br.com.uri.campushub.domain.CheckinRules
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

// lista de participantes que o organizador acompanha: events/{eventId}/attendees/{uid} (Cloud Firestore).
// Os documentos são gravados junto com a inscrição (EnrollmentRepository) e com a lista de espera
// (WaitlistRepository), nas mesmas transações; aqui ficam a leitura do organizador e o sync,
// que o app do aluno roda ao abrir o evento para criar ou corrigir o próprio documento.
// Cada documento guarda o segredo do QR de check-in do aluno (checkinToken, gerado pelo CheckinCode)
// e a hora do check-in (checkedInAt), registrada pelo organizador em checkIn.
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

    // check-in pelo organizador dono, depois de ler o QR do aluno (CheckinRules.read já conferiu o evento).
    // Numa transação: lê o documento do aluno, decide pelo CheckinRules.check e, se valer,
    // grava checkedInAt com a hora do servidor. Duas leituras do mesmo QR ao mesmo tempo
    // não registram dois check-ins: o Firestore repete uma delas e ela encontra o check-in feito
    fun checkIn(eventId: String, payload: CheckinCode.Payload): Task<CheckinRules.Outcome> {
        val ref = doc(db, eventId, payload.userId)
        return db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            val attendee = if (snapshot.exists()) snapshot.toObject(Attendee::class.java) else null
            val outcome = CheckinRules.check(attendee, payload.token)
            if (outcome is CheckinRules.Outcome.Done) {
                transaction.update(ref, "checkedInAt", FieldValue.serverTimestamp())
            }
            outcome
        }
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
                    // merge: o que não é enviado (checkedInAt, gravado pelo organizador) continua salvo
                    ref.set(data, SetOptions.merge()).continueWith { task ->
                        task.result
                        Attendee(uid, user.name, user.course, expected, since, saved?.checkedInAt, token)
                    }
                }
            }
        }
    }
}
