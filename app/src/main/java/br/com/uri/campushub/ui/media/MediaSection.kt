package br.com.uri.campushub.ui.media

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.media.MediaStorage
import br.com.uri.campushub.data.model.MediaPost
import br.com.uri.campushub.data.repository.MediaRepository
import br.com.uri.campushub.domain.MediaRules
import com.google.android.material.button.MaterialButton
import java.io.File

// cartão "Publicações" dos detalhes: lista, publica e exclui fotos, áudios e vídeos
// fica fora da EventDetailsActivity para a tela não crescer ainda mais
// precisa ser criada no onCreate da tela, porque registra os retornos da câmera, da galeria e da permissão
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

    private val player = AudioPlayer()
    private val recordDialog = RecordAudioDialog(activity, storage) { fileName, durationMs ->
        publish(MediaPost.TYPE_AUDIO, fileName, durationMs)
    }
    private var adapter: MediaAdapter? = null

    // arquivo que a câmera está preenchendo; fica guardado caso a tela seja recriada nesse meio-tempo
    private var pendingFile: File? = null

    private val takePicture =
        activity.registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val file = takePendingFile() ?: return@registerForActivityResult
            if (saved) {
                publish(MediaPost.TYPE_PHOTO, file.name)
            } else {
                // o aluno voltou da câmera sem tirar a foto
                file.delete()
            }
        }

    private val takeVideo =
        activity.registerForActivityResult(CaptureShortVideo()) { saved ->
            val file = takePendingFile() ?: return@registerForActivityResult
            if (saved) {
                publishVideo(file.name)
            } else {
                // a câmera grava direto no arquivo; se o aluno descartou, o arquivo pode estar cheio
                file.delete()
            }
        }

    // seletor do Android: não precisa de permissão para ler a galeria
    private val pickPhoto =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) importAndPublish(uri, MediaPost.TYPE_PHOTO, "jpg")
        }

    private val pickVideo =
        activity.registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) importAndPublish(uri, MediaPost.TYPE_VIDEO, "mp4")
        }

    // o microfone é pedido só quando o aluno escolhe gravar
    private val requestMic =
        activity.registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                recordDialog.show()
            } else {
                Toast.makeText(activity, R.string.mic_permission_denied, Toast.LENGTH_LONG).show()
            }
        }

    init {
        rvMedia.layoutManager = LinearLayoutManager(activity)
        btnPublishMedia.setOnClickListener { showPublishOptions() }
        player.onChanged = { postId -> adapter?.notifyAudioChanged(postId) }

        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            // tela escondida: o áudio pausa e a gravação é descartada
            // (o Android não deixa usar o microfone com o app em segundo plano)
            override fun onStop(owner: LifecycleOwner) {
                player.pause()
                recordDialog.cancel()
            }

            override fun onDestroy(owner: LifecycleOwner) {
                player.stop()
            }
        })
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
                // a lista nova perderia o estado do áudio que está tocando
                player.stop()
                val newAdapter = MediaAdapter(
                    posts,
                    uid,
                    findFile = { post -> storage.find(post.fileName) },
                    onOpen = { post, file -> open(post, file) },
                    onDelete = { post -> confirmDelete(post) },
                    player = player,
                    onToggleAudio = { post, file -> toggleAudio(post, file) }
                )
                adapter = newAdapter
                rvMedia.adapter = newAdapter
                txtMediaTitle.text = activity.getString(R.string.media_count, posts.size)
                txtNoMedia.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
            }
            .addOnFailureListener { error -> showError(error) }
    }

    private fun showPublishOptions() {
        val options = arrayOf(
            activity.getString(R.string.take_photo),
            activity.getString(R.string.pick_photo),
            activity.getString(R.string.record_audio),
            activity.getString(R.string.record_video),
            activity.getString(R.string.pick_video)
        )
        AlertDialog.Builder(activity)
            .setTitle(R.string.publish_media_title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> launchCamera(takePicture, "jpg")
                    1 -> pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    2 -> requestMic.launch(Manifest.permission.RECORD_AUDIO)
                    3 -> launchCamera(takeVideo, "mp4")
                    4 -> pickVideo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
                }
            }
            .show()
    }

    // o app de câmera grava a foto ou o vídeo direto no arquivo do app, pelo endereço do FileProvider
    private fun launchCamera(launcher: ActivityResultLauncher<Uri>, extension: String) {
        val file = storage.newFile(extension)
        pendingFile = file
        try {
            launcher.launch(storage.shareableUri(file))
        } catch (_: ActivityNotFoundException) {
            pendingFile = null
            file.delete()
            Toast.makeText(activity, R.string.no_camera_app, Toast.LENGTH_LONG).show()
        }
    }

    // devolve o arquivo que a câmera preencheu e esquece dele
    private fun takePendingFile(): File? {
        val file = pendingFile
        pendingFile = null
        return file
    }

    // a galeria devolve um endereço de outro app; o arquivo é copiado para a pasta do app antes
    private fun importAndPublish(uri: Uri, type: String, defaultExtension: String) {
        setPublishing(true)
        storage.importFrom(uri, defaultExtension)
            .addOnSuccessListener { fileName ->
                if (type == MediaPost.TYPE_VIDEO) publishVideo(fileName) else publish(type, fileName)
            }
            .addOnFailureListener { error ->
                setPublishing(false)
                showError(error)
            }
    }

    // só vídeo curto: o da galeria pode ter qualquer tamanho, e nem toda câmera respeita o limite
    private fun publishVideo(fileName: String) {
        val duration = storage.durationOf(fileName)
        val error = when {
            duration <= 0 -> R.string.video_unreadable
            !MediaRules.isVideoShortEnough(duration) -> R.string.video_too_long
            else -> null
        }
        if (error != null) {
            storage.delete(fileName)
            setPublishing(false)
            Toast.makeText(activity, error, Toast.LENGTH_LONG).show()
            return
        }
        publish(MediaPost.TYPE_VIDEO, fileName, duration.coerceAtMost(MediaRules.MAX_VIDEO_MS))
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

    private fun toggleAudio(post: MediaPost, file: File) {
        try {
            player.toggle(post.id, file)
        } catch (_: Exception) {
            Toast.makeText(activity, R.string.audio_unplayable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun open(post: MediaPost, file: File) {
        val intent = Intent(activity, MediaViewerActivity::class.java)
        intent.putExtra(MediaViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
        intent.putExtra(MediaViewerActivity.EXTRA_IS_VIDEO, post.type == MediaPost.TYPE_VIDEO)
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
        player.stopIfPlaying(post.id)
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
