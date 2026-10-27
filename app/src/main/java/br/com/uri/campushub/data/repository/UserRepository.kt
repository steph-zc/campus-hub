package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.User
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore

// perfil do aluno no Firestore, em users/{uid}
// o uid é o mesmo do Authentication, o que liga o perfil à conta
class UserRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    fun create(uid: String, user: User): Task<Void> =
        userDoc(uid).set(user)

    // null se o documento não existir
    fun get(uid: String): Task<User?> =
        userDoc(uid).get().continueWith { task -> task.result.toObject(User::class.java) }

    // update altera só esses campos e mantém o email salvo no documento
    fun update(uid: String, name: String, course: String): Task<Void> =
        userDoc(uid).update(mapOf("name" to name, "course" to course))
}
