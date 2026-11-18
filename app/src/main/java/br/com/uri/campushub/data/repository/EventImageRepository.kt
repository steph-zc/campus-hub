package br.com.uri.campushub.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore

// imagem de capa dos eventos na coleção "eventImages" do Firestore: um documento por evento
// (o id é o mesmo do evento) com os bytes do JPEG (campo jpeg, tipo Blob) e a versão (campo version).
// Fica fora de events/{eventId} para a lista de eventos não baixar as imagens junto.
// Aqui só tem a leitura e os dados gravados; a gravação acontece no EventRepository,
// no mesmo lote (batch) que atualiza o imageVersion do evento, e o firestore.rules confere os dois
class EventImageRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    companion object {
        const val COLLECTION = "eventImages"

        // limite do JPEG gravado; o mesmo número está no firestore.rules.
        // O documento do Firestore aceita até 1 MiB, então sobra folga para os outros campos
        const val MAX_BYTES = 500_000

        // campos do documento, montados pelo EventRepository ao gravar
        fun fields(jpeg: ByteArray, version: Long): Map<String, Any> =
            mapOf("jpeg" to Blob.fromBytes(jpeg), "version" to version)
    }

    fun document(eventId: String): DocumentReference = db.collection(COLLECTION).document(eventId)

    // bytes do JPEG do evento; null se o evento não tem imagem
    fun read(eventId: String): Task<ByteArray?> =
        document(eventId).get().continueWith { task ->
            task.result.getBlob("jpeg")?.toBytes()
        }
}
