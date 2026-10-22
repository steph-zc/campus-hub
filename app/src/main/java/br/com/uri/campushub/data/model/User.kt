package br.com.uri.campushub.data.model

// perfil guardado em users/{uid}; os valores padrão permitem o toObject() do Firestore
data class User(
    val name: String = "",
    val course: String = "",
    val email: String = ""
)
