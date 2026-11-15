package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.Role
import br.com.uri.campushub.data.model.User
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.FirebaseFirestore

// perfil do aluno no Firestore, em users/{uid}, e o papel da conta, em organizers/{uid}
// o uid é o mesmo do Authentication, o que liga os documentos à conta
class UserRepository(private val db: FirebaseFirestore = FirebaseFirestore.getInstance()) {

    private fun userDoc(uid: String) = db.collection("users").document(uid)

    // existe só para as contas de organizador; é criado à mão no console do Firebase,
    // porque as regras não deixam o app gravar nessa coleção (ninguém se promove sozinho)
    private fun organizerDoc(uid: String) = db.collection("organizers").document(uid)

    fun create(uid: String, user: User): Task<Void> =
        userDoc(uid).set(user)

    // null se o documento não existir
    fun get(uid: String): Task<User?> =
        userDoc(uid).get().continueWith { task -> task.result.toObject(User::class.java) }

    // update altera só esses campos e mantém o email salvo no documento
    fun update(uid: String, name: String, course: String): Task<Void> =
        userDoc(uid).update(mapOf("name" to name, "course" to course))

    // o papel vem da existência do documento: com organizers/{uid} é organizador, sem ele é aluno
    // (as regras deixam cada conta ler só o próprio documento dessa coleção)
    fun getRole(uid: String): Task<Role> =
        organizerDoc(uid).get().continueWith { task ->
            if (task.result.exists()) Role.ORGANIZER else Role.STUDENT
        }
}
