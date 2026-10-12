package br.com.uri.campushub

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip

// onClick recebe o evento tocado, quem decide o que fazer é a tela
class EventAdapter(
    private val events: List<Event>,
    private val onClick: (Event) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    // guarda as views de um item para não precisar procurar de novo a cada rolagem
    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtDay: TextView = view.findViewById(R.id.txtDay)
        val txtMonth: TextView = view.findViewById(R.id.txtMonth)
        val dateBadge: View = view.findViewById(R.id.dateBadge)
        val txtCategory: TextView = view.findViewById(R.id.txtCategory)
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
        holder.txtCategory.text = event.category
        holder.txtCategory.visibility = if (event.category.isEmpty()) View.GONE else View.VISIBLE
        holder.txtTitle.text = event.title
        holder.txtTime.text = event.time()
        holder.txtLocation.text = event.location
        holder.txtCapacity.text = when {
            event.hasEnded() -> context.getString(R.string.event_ended)
            event.remainingSpots() > 0 -> context.getString(R.string.remaining_spots, event.remainingSpots())
            else -> context.getString(R.string.sold_out)
        }

        // eventos encerrados ficam com o bloco de data apagado
        holder.dateBadge.alpha = if (event.hasEnded()) 0.4f else 1f
        EventChips.bind(holder.chipCourse, holder.chipAccess, event)

        holder.itemView.setOnClickListener { onClick(event) }
    }

    override fun getItemCount() = events.size
}
