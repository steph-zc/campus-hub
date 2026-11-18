package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.SampleEvents
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventDraft
import br.com.uri.campushub.data.model.EventImageChange
import br.com.uri.campushub.data.model.EventStatus
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException

// eventos na coleção "events": os alunos leem os publicados e encerrados; o organizador responsável
// lê também os rascunhos dele, cria, edita, publica, encerra as inscrições, encerra o evento e exclui
// (create/update/publish/closeEnrollments/finish/delete);
// a conta admin grava os eventos de exemplo.
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

    // eventos que os alunos enxergam (publicados e encerrados), do mais antigo para o mais novo.
    // O filtro por status é obrigatório: as regras do Firestore recusam uma consulta que possa
    // trazer rascunhos de outros organizadores. A ordem é feita no app para não precisar de índice composto
    fun getAll(): Task<List<Event>> =
        events.whereIn("status", EventStatus.VISIBLE).get()
            .continueWith { task -> task.result.toObjects(Event::class.java).sortedBy { it.date } }

    // null se o evento não existir mais
    fun get(eventId: String): Task<Event?> =
        events.document(eventId).get()
            .continueWith { task -> task.result.toObject(Event::class.java) }

    // usado em Meus eventos e Meus favoritos: lê cada evento pelo id, ao mesmo tempo, do mais antigo
    // para o mais novo. Cada leitura passa pela regra de leitura de um documento; evento apagado
    // ou que a conta não pode ver (rascunho de outro organizador) fica de fora em vez de derrubar a lista
    fun getByIds(ids: List<String>): Task<List<Event>> {
        if (ids.isEmpty()) return Tasks.forResult(emptyList())

        val reads = ids.distinct().map { get(it) }
        return Tasks.whenAllComplete(reads)
            .continueWith { _ ->
                reads.filter { it.isSuccessful }.mapNotNull { it.result }.sortedBy { it.date }
            }
    }

    // eventos sob a responsabilidade de um organizador (campo organizerId), do mais antigo para o mais novo;
    // a ordem é feita no app para a consulta não precisar de um índice composto no Firestore
    fun getByOrganizer(organizerId: String): Task<List<Event>> =
        events.whereEqualTo("organizerId", organizerId).get()
            .continueWith { task -> task.result.toObjects(Event::class.java).sortedBy { it.date } }

    // novo evento do organizador: os campos do formulário mais o dono, o contador zerado,
    // a versão da imagem (0 = sem imagem) e a situação de rascunho (só ele vê até publicar). Com imagem, o evento e eventImages/{id} vão no mesmo lote:
    // ou os dois são gravados, ou nenhum. O id é gerado pelo Firestore e devolvido para a tela.
    // As regras só aceitam se a conta tiver organizers/{uid} e o organizerId for o dela
    fun create(organizerId: String, draft: EventDraft, image: ByteArray? = null): Task<String> {
        val doc = events.document()
        val version = if (image != null) clock() else 0L
        val data = draft.toMap() + mapOf(
            "organizerId" to organizerId,
            "enrolledCount" to 0,
            "imageVersion" to version,
            "status" to EventStatus.DRAFT.value
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

    // rascunho → publicado: o evento passa a aparecer para os alunos e a aceitar inscrições.
    // As regras só aceitam do organizador dono, a partir de um rascunho e com a data no futuro
    fun publish(eventId: String): Task<Void> = setStatus(eventId, EventStatus.PUBLISHED)

    // publicado → inscrições encerradas: ninguém mais se inscreve; quem já estava continua inscrito.
    // As regras só aceitam do organizador dono, a partir de um evento publicado (não tem volta)
    fun closeEnrollments(eventId: String): Task<Void> = setStatus(eventId, EventStatus.CLOSED)

    // publicado ou com inscrições encerradas → evento encerrado: ele passa a constar como realizado
    // (libera as avaliações) e não muda mais. As regras só aceitam do dono, 1 hora depois do início
    fun finish(eventId: String): Task<Void> = setStatus(eventId, EventStatus.FINISHED)

    // exclui o evento e, no mesmo lote, a capa em eventImages. As regras só aceitam do organizador dono
    // e com o evento sem nenhum inscrito (enrolledCount 0), então nenhum aluno perde a inscrição
    fun delete(event: Event): Task<Void> {
        val batch = db.batch()
        batch.delete(events.document(event.id))
        if (event.withImage()) batch.delete(images.document(event.id))
        return batch.commit()
    }

    // grava só o campo status; as outras partes do evento ficam como estão
    private fun setStatus(eventId: String, status: EventStatus): Task<Void> =
        events.document(eventId).update("status", status.value)

    // grava os eventos de exemplo (já publicados); só a conta admin consegue
    fun saveSampleEvents(): Task<Void> = SampleEvents.save(db)

    // chamado pela lista quando não há nenhum evento visível. Só a conta admin consegue
    // (as regras recusam a consulta sem filtro dos outros, com PERMISSION_DENIED):
    // coleção vazia → grava os exemplos; eventos gravados antes do campo status → marca como publicados.
    // Devolve true se gravou algo, para a tela buscar a lista de novo
    fun prepareSampleData(): Task<Boolean> =
        events.get().onSuccessTask { snapshot ->
            if (snapshot.isEmpty) return@onSuccessTask saveSampleEvents().onSuccessTask { Tasks.forResult(true) }

            val legacy = snapshot.documents.filter { !it.contains("status") }
            if (legacy.isEmpty()) return@onSuccessTask Tasks.forResult(false)

            val batch = db.batch()
            legacy.forEach { batch.update(it.reference, "status", EventStatus.PUBLISHED.value) }
            batch.commit().onSuccessTask { Tasks.forResult(true) }
        }

    // eventos de exemplo já gravados recebem o local atual (sala, endereço e ponto do prédio);
    // só grava se algo mudou, e só a conta admin tem permissão
    fun syncLocations(events: List<Event>): Task<Void> =
        SampleEvents.syncLocations(db, events)
}
