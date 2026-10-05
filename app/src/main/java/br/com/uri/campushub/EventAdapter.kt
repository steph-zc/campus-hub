package br.com.uri.campushub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip

class EventAdapter(private val events: List<Event>) :
    RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    // guarda as views de um item para não precisar procurar de novo a cada rolagem
    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtDay: TextView = view.findViewById(R.id.txtDay)
        val txtMonth: TextView = view.findViewById(R.id.txtMonth)
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
        val txtTime: TextView = view.findViewById(R.id.txtTime)
        val txtLocation: TextView = view.findViewById(R.id.txtLocation)
        val txtCapacity: TextView = view.findViewById(R.id.txtCapacity)
        val chipCourse: Chip = view.findViewById(R.id.chipCourse)
        val chipAccess: Chip = view.findViewById(R.id.chipAccess)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = events[position]
        val context = holder.itemView.context

        holder.txtDay.text = event.day()
        holder.txtMonth.text = event.shortMonth()
        holder.txtTitle.text = event.title
        holder.txtTime.text = event.time()
        holder.txtLocation.text = event.location
        holder.txtCapacity.text = context.getString(R.string.capacity, event.capacity)

        holder.chipCourse.text = event.course
        holder.chipCourse.visibility = if (event.course.isEmpty()) View.GONE else View.VISIBLE

        // verde e cadeado aberto para todos, âmbar e cadeado fechado para exclusivos
        if (event.openToAll) {
            showAccessChip(holder.chipAccess, R.string.open_to_all, R.drawable.ic_lock_open,
                R.color.chip_open_bg, R.color.chip_open_text)
        } else {
            showAccessChip(holder.chipAccess, R.string.course_only, R.drawable.ic_lock,
                R.color.chip_closed_bg, R.color.chip_closed_text)
        }
    }

    private fun showAccessChip(chip: Chip, text: Int, icon: Int, background: Int, textColor: Int) {
        val context = chip.context
        val color = ContextCompat.getColorStateList(context, textColor)
        chip.setText(text)
        chip.setChipIconResource(icon)
        chip.chipIconTint = color
        chip.setTextColor(color)
        chip.chipBackgroundColor = ContextCompat.getColorStateList(context, background)
    }

    override fun getItemCount() = events.size
}
