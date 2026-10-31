package br.com.uri.campushub.domain

import br.com.uri.campushub.testing.TestEvents.NOW
import br.com.uri.campushub.testing.TestEvents.TEN_YEARS_IN_DAYS
import br.com.uri.campushub.testing.TestEvents.event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// testes de quem pode avaliar e das notas aceitas (RatingRules, em domain)
// regra pura: eventos em memória pelo TestEvents
class RatingRulesTest {

    @Test
    fun `enrolled student can rate an event that already happened`() {
        assertTrue(RatingRules.canRate(event(daysFromNow = -1), isEnrolled = true, now = NOW))
    }

    @Test
    fun `student who was not enrolled cannot rate`() {
        assertFalse(RatingRules.canRate(event(daysFromNow = -1), isEnrolled = false, now = NOW))
    }

    @Test
    fun `event that did not happen yet cannot be rated`() {
        assertFalse(RatingRules.canRate(event(daysFromNow = 3), isEnrolled = true, now = NOW))
    }

    @Test
    fun `stars inside the range are kept`() {
        for (stars in RatingRules.MIN_STARS..RatingRules.MAX_STARS) {
            assertEquals(stars, RatingRules.normalizeStars(stars))
        }
    }

    @Test
    fun `zero stars become the minimum`() {
        assertEquals(RatingRules.MIN_STARS, RatingRules.normalizeStars(0))
    }

    @Test
    fun `more than five stars become the maximum`() {
        assertEquals(RatingRules.MAX_STARS, RatingRules.normalizeStars(7))
    }

    // sem o "now", a regra usa o relógio real, como na tela de detalhes
    @Test
    fun `rule uses the current time when no date is given`() {
        assertTrue(RatingRules.canRate(event(daysFromNow = -TEN_YEARS_IN_DAYS), isEnrolled = true))
        assertFalse(RatingRules.canRate(event(daysFromNow = TEN_YEARS_IN_DAYS), isEnrolled = true))
    }
}
