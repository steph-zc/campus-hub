package br.com.uri.campushub.domain

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

// fuso e idioma do campus, usados em todas as datas do app (formulário, listas, detalhes,
// comentários e mídias). Os eventos acontecem num lugar físico, então o horário é sempre o
// de Brasília, mesmo que o aparelho esteja em outro fuso (emuladores costumam vir em GMT).
// Só usa classes do Java (java.text e java.util), sem Android nem Firebase
object CampusTime {

    val ZONE: TimeZone = TimeZone.getTimeZone("America/Sao_Paulo")
    val LOCALE: Locale = Locale.forLanguageTag("pt-BR")

    // formatador já no fuso do campus; um novo a cada chamada, porque SimpleDateFormat não é thread-safe
    fun formatter(pattern: String) = SimpleDateFormat(pattern, LOCALE).apply { timeZone = ZONE }

    // calendário no fuso do campus, para montar ou desmontar datas e horas
    fun calendar(): Calendar = Calendar.getInstance(ZONE, LOCALE)
}
