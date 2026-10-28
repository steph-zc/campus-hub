package br.com.uri.campushub.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors

// arquivos das publicações na pasta privada do app (files/media), sem permissão de armazenamento
// o enunciado permite guardar só no aparelho; outros aparelhos veem a publicação sem o arquivo
class MediaStorage(private val context: Context) {

    companion object {
        // precisa bater com o android:authorities do FileProvider no manifest
        private const val AUTHORITY_SUFFIX = ".fileprovider"

        // copiar um vídeo grande na thread da tela travaria o app
        private val executor = Executors.newSingleThreadExecutor()
    }

    private val dir = File(context.filesDir, "media")

    // arquivo vazio com nome único, para a câmera ou o gravador preencherem
    fun newFile(extension: String): File {
        dir.mkdirs()
        return File(dir, "${UUID.randomUUID()}.$extension")
    }

    // null se o arquivo não está neste aparelho
    fun find(fileName: String): File? =
        File(dir, fileName).takeIf { fileName.isNotEmpty() && it.exists() }

    fun delete(fileName: String) {
        if (fileName.isNotEmpty()) File(dir, fileName).delete()
    }

    // duração de um áudio ou vídeo da pasta; 0 se o arquivo não é uma mídia válida
    fun durationOf(fileName: String): Long {
        val file = find(fileName) ?: return 0L
        return try {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            }
        } catch (_: Exception) {
            0L
        }
    }

    // endereço content:// que outro app (a câmera) pode usar para gravar no arquivo
    fun shareableUri(file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, file)

    // copia o arquivo escolhido na galeria para a pasta do app; devolve o nome da cópia
    fun importFrom(uri: Uri, defaultExtension: String): Task<String> =
        Tasks.call(executor) {
            val mimeType = context.contentResolver.getType(uri)
            val extension = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
                ?: defaultExtension
            val file = newFile(extension)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Não foi possível abrir o arquivo" }
                file.outputStream().use { output -> input.copyTo(output) }
            }
            file.name
        }
}
