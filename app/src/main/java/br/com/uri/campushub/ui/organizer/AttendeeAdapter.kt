package br.com.uri.campushub.ui.organizer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.Attendee
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.domain.AttendeeOverview
import br.com.uri.campushub.ui.common.Avatar
import com.google.android.material.chip.Chip

// lista da tela de inscritos (AttendeesActivity): o resumo no topo, a seção dos inscritos
// e a seção da lista de espera, montados a partir do AttendeeOverview (domain).
// Cada linha é um tipo (Row); só mostra dados, não acessa banco nem API.
// Usa o Avatar (iniciais) dos comentários e as cores das etiquetas dos eventos (colors.xml)
class AttendeeAdapter(event: Event, overview: AttendeeOverview) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    // linhas na ordem da tela
    private sealed class Row {
        data class Summary(val event: Event, val overview: AttendeeOverview) : Row()
        data class Header(val text: Int, val count: Int) : Row()
        data class Person(val attendee: Attendee, val queuePosition: Int) : Row()
    }

    private companion object {
        const val TYPE_SUMMARY = 0
        const val TYPE_HEADER = 1
        const val TYPE_PERSON = 2
    }

    // seções vazias não aparecem; a fila numera na ordem de chegada (1º, 2º...)
    private val rows: List<Row> = buildList {
        add(Row.Summary(event, overview))
        if (overview.enrolled.isNotEmpty()) {
            add(Row.Header(R.string.enrolled_section, overview.enrolled.size))
            overview.enrolled.forEach { add(Row.Person(it, queuePosition = 0)) }
        }
        if (overview.waiting.isNotEmpty()) {
            add(Row.Header(R.string.waitlist_section, overview.waiting.size))
            overview.waiting.forEachIndexed { index, attendee -> add(Row.Person(attendee, index + 1)) }
        }
    }

    private class SummaryHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtEventTitle: TextView = view.findViewById(R.id.txtEventTitle)
        val txtEventDate: TextView = view.findViewById(R.id.txtEventDate)
        val txtOccupied: TextView = view.findViewById(R.id.txtOccupied)
        val txtWaiting: TextView = view.findViewById(R.id.txtWaiting)
        val txtCheckins: TextView = view.findViewById(R.id.txtCheckins)
        val txtUnlisted: TextView = view.findViewById(R.id.txtUnlisted)
        val layoutEmpty: LinearLayout = view.findViewById(R.id.layoutEmpty)
    }

    private class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtSection: TextView = view.findViewById(R.id.txtSection)
    }

    private class PersonHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtInitials: TextView = view.findViewById(R.id.txtInitials)
        val txtName: TextView = view.findViewById(R.id.txtName)
        val txtCourse: TextView = view.findViewById(R.id.txtCourse)
        val txtSince: TextView = view.findViewById(R.id.txtSince)
        val chipState: Chip = view.findViewById(R.id.chipState)
    }

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.Summary -> TYPE_SUMMARY
        is Row.Header -> TYPE_HEADER
        is Row.Person -> TYPE_PERSON
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SUMMARY -> SummaryHolder(inflater.inflate(R.layout.item_attendee_summary, parent, false))
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_attendee_header, parent, false))
            else -> PersonHolder(inflater.inflate(R.layout.item_attendee, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Summary -> bindSummary(holder as SummaryHolder, row)
            is Row.Header -> (holder as HeaderHolder).let {
                it.txtSection.text = it.itemView.context.getString(row.text, row.count)
            }
            is Row.Person -> bindPerson(holder as PersonHolder, row)
        }
    }

    private fun bindSummary(holder: SummaryHolder, row: Row.Summary) {
        val context = holder.itemView.context
        val overview = row.overview
        holder.txtEventTitle.text = row.event.title
        holder.txtEventDate.text = row.event.formattedDate()
        holder.txtOccupied.text = context.getString(R.string.count_of_total, overview.occupied, overview.capacity)
        holder.txtWaiting.text = overview.waiting.size.toString()
        holder.txtCheckins.text = context.getString(R.string.count_of_total, overview.checkedIn, overview.enrolled.size)
        holder.txtUnlisted.visibility = if (overview.unlisted > 0) View.VISIBLE else View.GONE
        holder.txtUnlisted.text = context.getString(R.string.unlisted_attendees, overview.unlisted)
        val empty = overview.enrolled.isEmpty() && overview.waiting.isEmpty()
        holder.layoutEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun bindPerson(holder: PersonHolder, row: Row.Person) {
        val context = holder.itemView.context
        val attendee = row.attendee
        holder.txtInitials.text = Avatar.initials(attendee.name)
        holder.txtName.text = attendee.name
        holder.txtCourse.text = attendee.course
        val since = if (attendee.enrolled()) R.string.attendee_enrolled_at else R.string.attendee_waiting_since
        holder.txtSince.text = context.getString(since, attendee.formattedSince())

        // inscritos: verde quando presente, âmbar enquanto não chegou; fila: a posição em azul
        when {
            !attendee.enrolled() -> paintChip(holder.chipState, context.getString(R.string.queue_position, row.queuePosition),
                R.color.chip_course_bg, R.color.chip_course_text)
            attendee.checkedIn() -> paintChip(holder.chipState, context.getString(R.string.checked_in),
                R.color.chip_open_bg, R.color.chip_open_text)
            else -> paintChip(holder.chipState, context.getString(R.string.not_checked_in),
                R.color.chip_closed_bg, R.color.chip_closed_text)
        }
    }

    private fun paintChip(chip: Chip, text: String, background: Int, textColor: Int) {
        chip.text = text
        chip.setTextColor(ContextCompat.getColorStateList(chip.context, textColor))
        chip.chipBackgroundColor = ContextCompat.getColorStateList(chip.context, background)
    }

    override fun getItemCount() = rows.size
}
