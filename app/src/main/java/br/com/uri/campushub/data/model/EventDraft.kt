package br.com.uri.campushub.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

// campos do evento que o organizador preenche no formulário (criar e editar).
// Ficam de fora o dono (organizerId) e o contador de inscritos (enrolledCount):
// o EventRepository define esses dois na criação, e a edição nunca mexe neles
data class EventDraft(
    val title: String,
    val description: String,
    val category: String,
    val date: Timestamp,
    // lugar dentro do campus (sala, auditório...); pode ficar vazio
    val location: String,
    val address: String,
    // ponto no mapa encontrado a partir do endereço; null se o endereço não foi achado
    val coordinates: GeoPoint?,
    val capacity: Int,
    // curso do organizador; usado quando o evento é exclusivo
    val course: String,
    val openToAll: Boolean
) {
    // os nomes são os mesmos campos do documento events/{eventId} (e da lista do firestore.rules)
    fun toMap(): Map<String, Any?> = mapOf(
        "title" to title,
        "description" to description,
        "category" to category,
        "date" to date,
        "location" to location,
        "address" to address,
        "coordinates" to coordinates,
        "capacity" to capacity,
        "course" to course,
        "openToAll" to openToAll
    )
}
