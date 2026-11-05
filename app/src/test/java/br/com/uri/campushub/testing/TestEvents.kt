package br.com.uri.campushub.testing

import br.com.uri.campushub.data.model.Event
import com.google.firebase.Timestamp
import java.util.Calendar
import java.util.Date

// eventos prontos para os testes unitários, sem Firestore
// (usa o modelo Event do app e o Timestamp do Firebase, que é uma classe comum de Java)
object TestEvents {

    // "agora" fixo: os testes passam a qualquer dia e hora, sem depender do relógio do computador
    val NOW: Date = date(2026, Calendar.OCTOBER, 15, 12, 0)

    fun date(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.time

    // dia relativo ao NOW: negativo = já aconteceu, positivo = ainda vai acontecer
    fun daysFromNow(days: Int): Date =
        Calendar.getInstance().apply {
            time = NOW
            add(Calendar.DAY_OF_MONTH, days)
        }.time

    // distância grande o bastante para um evento ser "passado" ou "futuro" no relógio real;
    // usada nos testes das funções chamadas sem o parâmetro "now", como as telas fazem
    const val TEN_YEARS_IN_DAYS = 3650

    // monta um evento só com o que cada teste precisa; o resto fica com valores neutros
    fun event(
        id: String = "event-1",
        title: String = "Palestra de teste",
        daysFromNow: Int? = 7,
        capacity: Int = 50,
        enrolledCount: Int = 0,
        course: String = "Ciência da Computação",
        openToAll: Boolean = true,
        category: String = "Palestra"
    ) = Event(
        id = id,
        title = title,
        date = daysFromNow?.let { Timestamp(daysFromNow(it)) },
        capacity = capacity,
        enrolledCount = enrolledCount,
        course = course,
        openToAll = openToAll,
        category = category
    )
}
