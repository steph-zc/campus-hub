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
        EventChips.bind(holder.chipCourse, holder.chipAccess, event)

        holder.itemView.setOnClickListener { onClick(event) }
    }

    override fun getItemCount() = events.size
}
