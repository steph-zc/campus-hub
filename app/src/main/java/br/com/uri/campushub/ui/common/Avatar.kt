package br.com.uri.campushub.ui.common

// iniciais mostradas nos círculos do perfil e dos comentários
object Avatar {

    // "Stefan Zanella" vira "SZ"; um nome só vira uma letra
    fun initials(name: String): String {
        val words = name.trim().split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return ""
        val first = words.first().first()
        val last = if (words.size > 1) words.last().first().toString() else ""
        return (first + last).uppercase()
    }
}
