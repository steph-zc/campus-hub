package br.com.uri.campushub.domain

import java.util.Calendar
import java.util.Date
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

// testes do EventDateTime (domain): data do seletor (meia-noite UTC) + hora, no fuso do aparelho.
// JUnit puro; o fuso de Brasília é passado explicitamente para não depender do computador
class EventDateTimeTest {

    private val brasilia = TimeZone.getTimeZone("America/Sao_Paulo")
    private val utc = TimeZone.getTimeZone("UTC")

    // 20/11/2026 00:00 UTC, como o MaterialDatePicker devolve
    private val pickerMillis = Calendar.getInstance(utc).apply {
        clear()
        set(2026, Calendar.NOVEMBER, 20)
    }.timeInMillis

    private fun fields(date: Date, zone: TimeZone) = Calendar.getInstance(zone).apply { time = date }.let {
        listOf(it.get(Calendar.YEAR), it.get(Calendar.MONTH), it.get(Calendar.DAY_OF_MONTH),
            it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE))
    }

    // no Brasil (UTC-3) a meia-noite UTC ainda é o dia 19; o evento tem que ficar no dia 20
    @Test
    fun `picked day keeps the same day in the local time zone`() {
        val date = EventDateTime.combine(pickerMillis, 19, 30, brasilia)

        assertEquals(listOf(2026, Calendar.NOVEMBER, 20, 19, 30), fields(date, brasilia))
    }

    @Test
    fun `converting back opens the picker on the same day`() {
        val date = EventDateTime.combine(pickerMillis, 8, 0, brasilia)

        assertEquals(pickerMillis, EventDateTime.toPickerUtcMillis(date, brasilia))
    }

    // as telas chamam sem o fuso: usa o do aparelho, e a ida e a volta continuam batendo
    @Test
    fun `default time zone round trip keeps the day`() {
        val date = EventDateTime.combine(pickerMillis, 23, 59)

        assertEquals(pickerMillis, EventDateTime.toPickerUtcMillis(date))
    }
}
