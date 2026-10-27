package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.Event
import java.util.Date

// quem pode avaliar e quais notas valem
object RatingRules {

    const val MIN_STARS = 1
    const val MAX_STARS = 5

    // avaliar exige as duas coisas: ter se inscrito e o evento já ter acontecido
    fun canRate(event: Event, isEnrolled: Boolean, now: Date = Date()) =
        isEnrolled && event.hasEnded(now)

    // o RatingBar deixa arrastar até 0, mas a nota fica sempre entre 1 e 5
    fun normalizeStars(stars: Int) = stars.coerceIn(MIN_STARS, MAX_STARS)
}
