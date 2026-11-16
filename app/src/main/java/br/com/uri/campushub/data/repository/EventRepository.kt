package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.SampleEvents
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventDraft
import br.com.uri.campushub.data.model.EventImageChange
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.QuerySnapshot

// eventos na coleção "events": leitura para todos os logados; gravação pelo organizador
// responsável (create/update) e pela conta admin (eventos de exemplo).
// A imagem de capa vai junto, no mesmo lote (WriteBatch), para a coleção eventImages (EventImageRepository).
// clock gera a versão da imagem; os testes passam um relógio fixo
class EventRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val clock: () -> Long = System::currentTimeMillis
) {

    companion object {
        // as regras do Firestore (firestore.rules) só deixam a conta admin gravar os eventos de exemplo;
        // para os outros alunos a gravação dos exemplos volta com esse erro
        fun isPermissionDenied(error: Exception) =
            (error as? FirebaseFirestoreException)?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
    }

    private val events = db.collection("events")
    private val images get() = db.collection(EventImageRepository.COLLECTION)

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

    // novo evento do organizador: os campos do formulário mais o dono, o contador zerado
    // e a versão da imagem (0 = sem imagem). Com imagem, o evento e eventImages/{id} vão no mesmo lote:
    // ou os dois são gravados, ou nenhum. O id é gerado pelo Firestore e devolvido para a tela.
    // As regras só aceitam se a conta tiver organizers/{uid} e o organizerId for o dela
    fun create(organizerId: String, draft: EventDraft, image: ByteArray? = null): Task<String> {
        val doc = events.document()
        val version = if (image != null) clock() else 0L
        val data = draft.toMap() + mapOf(
            "organizerId" to organizerId,
            "enrolledCount" to 0,
            "imageVersion" to version
        )
        val batch = db.batch()
        batch.set(doc, data)
        if (image != null) batch.set(images.document(doc.id), EventImageRepository.fields(image, version))
        return batch.commit().continueWith { task ->
            task.result
            doc.id
        }
    }

    // edição pelo organizador responsável: troca os campos do formulário e, se pedido, a imagem;
    // dono e inscritos continuam como estão (as regras recusam se mudarem).
    // Imagem nova ganha outra versão (o cache do Glide usa a versão, então a capa antiga não reaparece)
    fun update(eventId: String, draft: EventDraft, image: EventImageChange = EventImageChange.Keep): Task<Void> {
        val doc = events.document(eventId)
        val batch = db.batch()
        when (image) {
            EventImageChange.Keep -> batch.update(doc, draft.toMap())
            is EventImageChange.Replace -> {
                val version = clock()
                batch.update(doc, draft.toMap() + ("imageVersion" to version))
                batch.set(images.document(eventId), EventImageRepository.fields(image.jpeg, version))
            }
            EventImageChange.Remove -> {
                batch.update(doc, draft.toMap() + ("imageVersion" to 0L))
                batch.delete(images.document(eventId))
            }
        }
        return batch.commit()
    }

    // grava os eventos de exemplo; a tela chama quando a coleção está vazia (só a conta admin consegue)
    fun saveSampleEvents(): Task<Void> = SampleEvents.save(db)

    // eventos de exemplo já gravados recebem o local atual (sala, endereço e ponto do prédio);
    // só grava se algo mudou, e só a conta admin tem permissão
    fun syncLocations(events: List<Event>): Task<Void> =
        SampleEvents.syncLocations(db, events)
}
