package br.com.uri.campushub.ui.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.Comment
import br.com.uri.campushub.ui.common.Avatar

// a tela decide o que fazer ao editar ou excluir; o adapter só avisa qual comentário foi escolhido
class CommentAdapter(
    private val comments: List<Comment>,
    private val currentUserId: String,
    private val onEdit: (Comment) -> Unit,
    private val onDelete: (Comment) -> Unit
) : RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtInitials: TextView = view.findViewById(R.id.txtInitials)
        val txtAuthor: TextView = view.findViewById(R.id.txtAuthor)
        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val txtText: TextView = view.findViewById(R.id.txtText)
        val btnCommentMenu: ImageButton = view.findViewById(R.id.btnCommentMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        val context = holder.itemView.context

        holder.txtInitials.text = Avatar.initials(comment.authorName)
        holder.txtAuthor.text = comment.authorName
        holder.txtText.text = comment.text
        holder.txtDate.text = if (comment.editedAt != null) {
            context.getString(R.string.comment_edited, comment.formattedDate())
        } else {
            comment.formattedDate()
        }

        // só o autor vê o menu de editar e excluir
        val isMine = comment.userId == currentUserId
        holder.btnCommentMenu.visibility = if (isMine) View.VISIBLE else View.GONE
        holder.btnCommentMenu.setOnClickListener { button -> showMenu(button, comment) }
    }

    private fun showMenu(anchor: View, comment: Comment) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.menuInflater.inflate(R.menu.menu_comment, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menuEditComment -> onEdit(comment)
                R.id.menuDeleteComment -> onDelete(comment)
            }
            true
        }
        popup.show()
    }

    override fun getItemCount() = comments.size
}
