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
import java.io.File

// a seção decide o que fazer ao abrir ou excluir; o adapter só avisa qual publicação foi escolhida
class MediaAdapter(
    private val posts: List<MediaPost>,
    private val currentUserId: String,
    // null quando o arquivo não está neste aparelho
    private val findFile: (MediaPost) -> File?,
    private val onOpen: (MediaPost, File) -> Unit,
    private val onDelete: (MediaPost) -> Unit
) : RecyclerView.Adapter<MediaAdapter.MediaViewHolder>() {

    class MediaViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtInitials: TextView = view.findViewById(R.id.txtInitials)
        val txtAuthor: TextView = view.findViewById(R.id.txtAuthor)
        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val btnMediaMenu: ImageButton = view.findViewById(R.id.btnMediaMenu)
        val imgPhoto: ImageView = view.findViewById(R.id.imgPhoto)
        val txtUnavailable: TextView = view.findViewById(R.id.txtUnavailable)
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
        holder.txtUnavailable.visibility = if (file == null) View.VISIBLE else View.GONE
        holder.imgPhoto.visibility = if (file != null) View.VISIBLE else View.GONE
        if (file != null) {
            // o Glide reduz a foto para o tamanho da tela, gira conforme a câmera e guarda em cache
            Glide.with(holder.imgPhoto).load(file).into(holder.imgPhoto)
            holder.imgPhoto.contentDescription = context.getString(R.string.photo_by, post.authorName)
            holder.imgPhoto.setOnClickListener { onOpen(post, file) }
        }
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
