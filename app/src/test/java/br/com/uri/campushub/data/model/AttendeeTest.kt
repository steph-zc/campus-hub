package br.com.uri.campushub.data.model

import br.com.uri.campushub.testing.TestEvents.NOW
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// testes do modelo Attendee (data/model): situação, presença e data mostradas ao organizador
class AttendeeTest {

    @Test
    fun `enrolled and waiting students`() {
        assertTrue(Attendee(status = Attendee.ENROLLED).enrolled())
        assertFalse(Attendee(status = Attendee.WAITING).enrolled())
    }

    @Test
    fun `check-in is marked by the check-in time`() {
        assertFalse(Attendee().checkedIn())
        assertTrue(Attendee(checkedInAt = Timestamp(NOW)).checkedIn())
    }

    @Test
    fun `since is shown in the campus time zone`() {
        assertEquals("15/10/2026 às 12:00", Attendee(since = Timestamp(NOW)).formattedSince())
        assertEquals("", Attendee().formattedSince())
    }
}
