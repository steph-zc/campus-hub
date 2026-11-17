package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Event
import com.google.firebase.Timestamp
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

// testes do CampusTime (domain): as datas seguem o fuso do campus mesmo com o aparelho em outro fuso.
// Simula um aparelho em GMT (como o emulador) trocando o fuso padrão da JVM durante o teste
class CampusTimeTest {

    private lateinit var deviceZone: TimeZone

    @Before
    fun setUp() {
        deviceZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("GMT"))
    }

    @After
    fun tearDown() = TimeZone.setDefault(deviceZone)

    // 20/11/2026 no seletor (meia-noite UTC), como o MaterialDatePicker devolve
    private val pickerMillis = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(2026, Calendar.NOVEMBER, 20)
    }.timeInMillis

    // o organizador escolhe 19:15 num aparelho em GMT: grava 19:15 de Brasília (22:15 UTC)
    @Test
    fun `form time is saved in the campus zone`() {
        val date = EventDateTime.combine(pickerMillis, 19, 15)

        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { time = date }
        assertEquals(22, utc.get(Calendar.HOUR_OF_DAY))
        assertEquals(15, utc.get(Calendar.MINUTE))
        // e o formulário reabre no mesmo dia
        assertEquals(pickerMillis, EventDateTime.toPickerUtcMillis(date))
    }

    // o mesmo instante aparece como 19:15 na tela, e não 22:15 do fuso do aparelho
    @Test
    fun `event time is shown in the campus zone`() {
        val event = Event(date = Timestamp(EventDateTime.combine(pickerMillis, 19, 15)))

        assertEquals("19:15", event.time())
        assertEquals("20/11/2026 às 19:15", event.formattedDate())
        assertEquals("19:15", CampusTime.formatter("HH:mm").format(event.date!!.toDate()))
    }
}
