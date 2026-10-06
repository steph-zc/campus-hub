package br.com.uri.campushub

import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.chip.Chip

// preenche as etiquetas de curso e de acesso; usado na lista e nos detalhes
object EventChips {

    fun bind(chipCourse: Chip, chipAccess: Chip, event: Event) {
        chipCourse.text = event.course
        chipCourse.visibility = if (event.course.isEmpty()) View.GONE else View.VISIBLE

        // verde e cadeado aberto para todos, âmbar e cadeado fechado para exclusivos
        if (event.openToAll) {
            showAccessChip(chipAccess, R.string.open_to_all, R.drawable.ic_lock_open,
                R.color.chip_open_bg, R.color.chip_open_text)
        } else {
            showAccessChip(chipAccess, R.string.course_only, R.drawable.ic_lock,
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
}
