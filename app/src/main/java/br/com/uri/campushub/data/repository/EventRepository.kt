package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.SampleEvents
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventDraft
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot

// eventos na coleção "events": leitura para todos os logados; gravação pelo organizador
// responsável (create/update) e pela conta admin (eventos de exemplo)
class EventRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    companion object {
        // as regras do Firestore (firestore.rules) só deixam a conta admin gravar os eventos de exemplo;
        // para os outros alunos a gravação dos exemplos volta com esse erro
        fun isPermissionDenied(error: Exception) =
            (error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    private val events = db.collection("events")

    // todos os eventos, do mais antigo para o mais novo
    fun getAll(): Task<List<Event>> =
        events.orderBy("date").get()
            .continueWith { task -> task.result.toObjects(Event::class.java) }

    // null se o evento não existir mais
    fun get(eventId: String): Task<Event?> =
        events.document(eventId).get()
            .continueWith { task -> task.result.toObject(Event::class.java) }

    // usado em Meus eventos e Meus favoritos: busca só os eventos com esses ids
    fun getByIds(ids: List<String>): Task<List<Event>> {
        if (ids.isEmpty()) return Tasks.forResult(emptyList())

        // o whereIn aceita até 30 ids por consulta, então a lista é dividida em partes
        val queries = ids.chunked(30).map { part ->
            events.whereIn(FieldPath.documentId(), part).get()
        }
        return Tasks.whenAllSuccess<QuerySnapshot>(queries)
            .continueWith { task ->
                task.result.flatMap { it.toObjects(Event::class.java) }.sortedBy { it.date }
            }
    }

    // eventos sob a responsabilidade de um organizador (campo organizerId), do mais antigo para o mais novo;
    // a ordem é feita no app para a consulta não precisar de um índice composto no Firestore
    fun getByOrganizer(organizerId: String): Task<List<Event>> =
        events.whereEqualTo("organizerId", organizerId).get()
            .continueWith { task -> task.result.toObjects(Event::class.java).sortedBy { it.date } }

    // novo evento do organizador: os campos do formulário mais o dono e o contador zerado.
    // O id é gerado pelo Firestore (document() sem nome) e devolvido para a tela.
    // As regras só aceitam se a conta tiver organizers/{uid} e o organizerId for o dela
    fun create(organizerId: String, draft: EventDraft): Task<String> {
        val doc = events.document()
        val data = draft.toMap() + mapOf("organizerId" to organizerId, "enrolledCount" to 0)
        return doc.set(data).continueWith { task ->
            task.result
            doc.id
        }
    }

    // edição pelo organizador responsável: troca só os campos do formulário;
    // dono e inscritos continuam como estão (as regras recusam se mudarem)
    fun update(eventId: String, draft: EventDraft): Task<Void> =
        events.document(eventId).update(draft.toMap())

    // grava os eventos de exemplo; a tela chama quando a coleção está vazia (só a conta admin consegue)
    fun saveSampleEvents(): Task<Void> = SampleEvents.save(db)

    // eventos de exemplo já gravados recebem o local atual (sala, endereço e ponto do prédio);
    // só grava se algo mudou, e só a conta admin tem permissão
    fun syncLocations(events: List<Event>): Task<Void> =
        SampleEvents.syncLocations(db, events)
}
