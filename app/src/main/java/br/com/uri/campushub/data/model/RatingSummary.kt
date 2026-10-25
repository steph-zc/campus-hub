package br.com.uri.campushub.data.model

// resultado da agregação das avaliações de um evento
data class RatingSummary(
    val average: Double,
    val count: Long
)
