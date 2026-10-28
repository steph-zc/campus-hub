package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.SampleEvents
import br.com.uri.campushub.data.model.Event
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot

// leitura dos eventos na coleção "events"
class EventRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

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

    // grava os eventos de exemplo; a tela chama quando a coleção está vazia
    fun saveSampleEvents(): Task<Void> = SampleEvents.save(db)

    // eventos de exemplo já gravados recebem o local atual (sala, endereço e ponto do prédio)
    fun syncLocations(events: List<Event>): Task<Void> =
        SampleEvents.syncLocations(db, events)
}
