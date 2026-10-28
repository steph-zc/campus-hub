package br.com.uri.campushub.ui.media

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.media.MediaStorage
import br.com.uri.campushub.data.model.MediaPost
import br.com.uri.campushub.data.repository.MediaRepository
import com.google.android.material.button.MaterialButton
import java.io.File

// cartão "Publicações" dos detalhes: lista, publica e exclui fotos, áudios e vídeos
// fica fora da EventDetailsActivity para a tela não crescer ainda mais
// precisa ser criada no onCreate da tela, porque registra os retornos da câmera e da galeria
class MediaSection(
    private val activity: AppCompatActivity,
    private val eventId: String,
    private val uid: String
) {

    companion object {
        private const val STATE_PENDING_FILE = "pending_media_file"
    }

    // nome gravado como autor; a tela preenche quando o perfil carrega
    var userName = ""

    private val repository = MediaRepository()
    private val storage = MediaStorage(activity)

    private val txtMediaTitle: TextView = activity.findViewById(R.id.txtMediaTitle)
    private val btnPublishMedia: MaterialButton = activity.findViewById(R.id.btnPublishMedia)
    private val txtNoMedia: TextView = activity.findViewById(R.id.txtNoMedia)
    private val rvMedia: RecyclerView = activity.findViewById(R.id.rvMedia)

    // arquivo que a câmera está preenchendo; fica guardado caso a tela seja recriada nesse meio-tempo
    private var pendingFile: File? = null

    private val takePicture =
        activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val file = pendingFile ?: return@registerForActivityResult
            pendingFile = null
            if (saved) {
                publish(MediaPost.TYPE_PHOTO, file.name)
            } else {
                // o aluno voltou da câmera sem tirar a foto
                file.delete()
            }
        }

    // seletor de fotos do Android: não precisa de permissão para ler a galeria
    private val pickPhoto =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) importAndPublish(uri, MediaPost.TYPE_PHOTO, "jpg")
        }

    init {
        rvMedia.layoutManager = LinearLayoutManager(activity)
        btnPublishMedia.setOnClickListener { showPublishOptions() }
    }

    fun saveState(outState: Bundle) {
        pendingFile?.let { outState.putString(STATE_PENDING_FILE, it.absolutePath) }
    }

    fun restoreState(savedState: Bundle?) {
        savedState?.getString(STATE_PENDING_FILE)?.let { pendingFile = File(it) }
    }

    // mais novas primeiro
    fun load() {
        repository.getAll(eventId)
            .addOnSuccessListener { posts ->
                rvMedia.adapter = MediaAdapter(
                    posts,
                    uid,
                    findFile = { post -> storage.find(post.fileName) },
                    onOpen = { post, file -> open(post, file) },
                    onDelete = { post -> confirmDelete(post) }
                )
                txtMediaTitle.text = activity.getString(R.string.media_count, posts.size)
                txtNoMedia.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun showPublishOptions() {
        val options = arrayOf(
            activity.getString(R.string.take_photo),
            activity.getString(R.string.pick_photo)
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.publish_media_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePhoto()
                    1 -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            .show()
    }

    // o app de câmera grava direto no arquivo do app, pelo endereço do FileProvider
    private fun takePhoto() {
        val file = storage.newFile("jpg")
        pendingFile = file
        try {
            takePicture.launch(storage.shareableUri(file))
        } catch (_: ActivityNotFoundException) {
            pendingFile = null
            file.delete()
            Toast.makeText(activity, R.string.no_camera_app, Toast.LENGTH_LONG).show()
        }
    }

    // a galeria devolve um endereço de outro app; o arquivo é copiado para a pasta do app antes
    private fun importAndPublish(uri: Uri, type: String, defaultExtension: String) {
        setPublishing(true)
        storage.importFrom(uri, defaultExtension)
            .addOnSuccessListener { fileName -> publish(type, fileName) }
            .addOnFailureListener { error ->
                setPublishing(false)
                showError(error)
            }
    }

    private fun publish(type: String, fileName: String, durationMs: Long = 0) {
        setPublishing(true)
        repository.add(eventId, uid, userName, type, fileName, durationMs)
            .addOnSuccessListener {
                setPublishing(false)
                Toast.makeText(activity, R.string.media_published, Toast.LENGTH_SHORT).show()
                load()
            }
            .addOnFailureListener { error ->
                // sem a publicação no Firestore o arquivo não aparece em lugar nenhum
                storage.delete(fileName)
                setPublishing(false)
                showError(error)
            }
    }

    // evita publicar duas vezes com toques repetidos
    private fun setPublishing(publishing: Boolean) {
        btnPublishMedia.isEnabled = !publishing
    }

    private fun open(post: MediaPost, file: File) {
        val intent = Intent(activity, MediaViewerActivity::class.java)
        intent.putExtra(MediaViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
        intent.putExtra(MediaViewerActivity.EXTRA_AUTHOR, post.authorName)
        intent.putExtra(MediaViewerActivity.EXTRA_DATE, post.formattedDate())
        activity.startActivity(intent)
    }

    private fun confirmDelete(post: MediaPost) {
        AlertDialog.Builder(activity)
            .setMessage(R.string.delete_media_question)
            .setPositiveButton(R.string.delete) { _, _ -> delete(post) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // apaga a publicação no Firestore e o arquivo, se ele estiver neste aparelho
    private fun delete(post: MediaPost) {
        repository.delete(eventId, post.id)
            .addOnSuccessListener {
                storage.delete(post.fileName)
                Toast.makeText(activity, R.string.media_deleted, Toast.LENGTH_SHORT).show()
                load()
            }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun showError(error: Exception) {
        Toast.makeText(activity, error.message, Toast.LENGTH_LONG).show()
    }
}
