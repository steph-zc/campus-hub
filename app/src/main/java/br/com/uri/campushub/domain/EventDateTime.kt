package br.com.uri.campushub.domain

import java.util.Calendar
import java.util.Date
import java.util.TimeZone

// junta a data e a hora escolhidas no formulário de evento em um único instante
object EventDateTime {

    private val UTC = TimeZone.getTimeZone("UTC")

    // o seletor de data do Material devolve a meia-noite do dia escolhido em UTC;
    // aqui o dia é lido em UTC e montado de novo no fuso do aparelho, com a hora escolhida.
    // Sem isso, no Brasil (UTC-3) o evento cairia no dia anterior
    fun combine(pickerUtcMillis: Long, hour: Int, minute: Int, zone: TimeZone = TimeZone.getDefault()): Date {
        val picked = Calendar.getInstance(UTC).apply { timeInMillis = pickerUtcMillis }
        return Calendar.getInstance(zone).apply {
            clear()
            set(picked.get(Calendar.YEAR), picked.get(Calendar.MONTH), picked.get(Calendar.DAY_OF_MONTH), hour, minute)
        }.time
    }

    // caminho inverso, para abrir o seletor já no dia salvo do evento
    fun toPickerUtcMillis(date: Date, zone: TimeZone = TimeZone.getDefault()): Long {
        val local = Calendar.getInstance(zone).apply { time = date }
        return Calendar.getInstance(UTC).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
}
