package br.com.uri.campushub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CommentAdapter(private val comments: List<Comment>) :
    RecyclerView.Adapter<CommentAdapter.CommentViewHolder>() {

    class CommentViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtInitials: TextView = view.findViewById(R.id.txtInitials)
        val txtAuthor: TextView = view.findViewById(R.id.txtAuthor)
        val txtDate: TextView = view.findViewById(R.id.txtDate)
        val txtText: TextView = view.findViewById(R.id.txtText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        val comment = comments[position]
        holder.txtInitials.text = Avatar.initials(comment.authorName)
        holder.txtAuthor.text = comment.authorName
        holder.txtDate.text = comment.formattedDate()
        holder.txtText.text = comment.text
    }

    override fun getItemCount() = comments.size
}
