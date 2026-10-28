package br.com.uri.campushub.ui.media

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.MediaPost
import br.com.uri.campushub.domain.MediaRules
import br.com.uri.campushub.ui.common.Avatar
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File

// a seção decide o que fazer ao abrir ou excluir; o adapter só avisa qual publicação foi escolhida
class MediaAdapter(
    private val posts: List<MediaPost>,
    private val currentUserId: String,
    // null quando o arquivo não está neste aparelho
    private val findFile: (MediaPost) -> File?,
    private val onOpen: (MediaPost, File) -> Unit,
    private val onDelete: (MediaPost) -> Unit,
    // o player é da seção; o adapter só lê o estado para desenhar o botão e a barra
    private val player: AudioPlayer,
    private val onToggleAudio: (MediaPost, File) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    companion object {
        // redesenha só o áudio, sem recarregar as fotos
        private const val PAYLOAD_AUDIO = "audio"
    }

    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtInitials: TextView = view.findViewById(R.id.txtInitials)
        val txtAuthor: TextView = view.findViewById(R.id.txtAuthor)
        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val btnMediaMenu: ImageButton = view.findViewById(R.id.btnMediaMenu)
        val imgPhoto: ImageView = view.findViewById(R.id.imgPhoto)
        val txtUnavailable: TextView = view.findViewById(R.id.txtUnavailable)
        val layoutAudio: View = view.findViewById(R.id.layoutAudio)
        val btnPlayAudio: MaterialButton = view.findViewById(R.id.btnPlayAudio)
        val progressAudio: LinearProgressIndicator = view.findViewById(R.id.progressAudio)
        val txtAudioTime: TextView = view.findViewById(R.id.txtAudioTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_media, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        val post = posts[position]
        val context = holder.itemView.context

        holder.txtInitials.text = Avatar.initials(post.authorName)
        holder.txtAuthor.text = post.authorName
        holder.txtDate.text = post.formattedDate()

        holder.btnMediaMenu.visibility =
            if (MediaRules.canDelete(post, currentUserId)) View.VISIBLE else View.GONE
        holder.btnMediaMenu.setOnClickListener { button -> showMenu(button, post) }

        val file = findFile(post)
        val isAudio = post.type == MediaPost.TYPE_AUDIO
        holder.txtUnavailable.visibility = if (file == null) View.VISIBLE else View.GONE
        holder.imgPhoto.visibility = if (file != null && !isAudio) View.VISIBLE else View.GONE
        holder.layoutAudio.visibility = if (file != null && isAudio) View.VISIBLE else View.GONE
        if (file == null) return

        if (isAudio) {
            holder.btnPlayAudio.setOnClickListener { onToggleAudio(post, file) }
            bindAudio(holder, post)
        } else {
            // o Glide reduz a foto para o tamanho da tela, gira conforme a câmera e guarda em cache
            Glide.with(holder.imgPhoto).load(file).into(holder.imgPhoto)
            holder.imgPhoto.contentDescription = context.getString(R.string.photo_by, post.authorName)
            holder.imgPhoto.setOnClickListener { onOpen(post, file) }
        }
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int, payloads: List<Any>) {
        if (PAYLOAD_AUDIO in payloads) {
            bindAudio(holder, posts[position])
        } else {
            onBindViewHolder(holder, position)
        }
    }

    // chamado pela seção a cada mudança do player
    fun notifyAudioChanged(postId: String) {
        val index = posts.indexOfFirst { it.id == postId }
        if (index >= 0) notifyItemChanged(index, PAYLOAD_AUDIO)
    }

    // parado mostra a duração; tocando ou pausado mostra quanto já tocou
    private fun bindAudio(holder: MediaViewHolder, post: MediaPost) {
        val context = holder.itemView.context
        val playing = player.isPlaying(post.id)
        val position = player.positionMs(post.id)
        val duration = post.durationMs.coerceAtLeast(1)

        holder.btnPlayAudio.setIconResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        holder.btnPlayAudio.contentDescription = context.getString(
            if (playing) R.string.pause_audio else R.string.play_audio,
            post.authorName
        )
        holder.progressAudio.max = duration.toInt()
        holder.progressAudio.setProgressCompat(position.coerceAtMost(duration).toInt(), false)
        holder.txtAudioTime.text =
            MediaRules.formatDuration(if (position > 0) position else post.durationMs)
    }

    private fun showMenu(anchor: View, post: MediaPost) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.menuInflater.inflate(R.menu.menu_media, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.menuDeleteMedia) onDelete(post)
            true
        }
        popup.show()
    }

    override fun getItemCount() = posts.size
}
