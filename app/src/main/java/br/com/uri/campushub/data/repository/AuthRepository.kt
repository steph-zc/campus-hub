package br.com.uri.campushub.data.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth

// tudo que envolve a conta do aluno no Firebase Authentication
// o FirebaseAuth vem por parâmetro para os testes poderem usar o emulador do Firebase
class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    // o Firebase guarda a sessão, então ela continua depois de fechar o app
    fun isLoggedIn() = auth.currentUser != null

    fun currentUserEmail() = auth.currentUser?.email

    // as telas depois do login só abrem com alguém logado
    fun requireUserId(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Nenhum usuário logado")

    fun login(email: String, password: String): Task<AuthResult> =
        auth.signInWithEmailAndPassword(email, password)

    // devolve o uid da conta nova; o Firebase já deixa ela logada
    fun register(email: String, password: String): Task<String> =
        auth.createUserWithEmailAndPassword(email, password)
            .continueWith { task -> task.result.user!!.uid }

    fun sendPasswordReset(email: String): Task<Void> =
        auth.sendPasswordResetEmail(email)

    fun logout() = auth.signOut()
}
