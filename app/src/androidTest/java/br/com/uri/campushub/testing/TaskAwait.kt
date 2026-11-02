package br.com.uri.campushub.testing

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

// ajudantes para as Tasks do Firebase nos testes do aparelho (androidTest)
// os testes rodam na thread do instrumentation, então dá para bloquear esperando a resposta
// do emulador com o Tasks.await (na thread principal do app isso não é permitido)

private const val TIMEOUT_SECONDS = 15L

fun <T> Task<T>.await(): T = Tasks.await(this, TIMEOUT_SECONDS, TimeUnit.SECONDS)

// confere que o Firestore recusou a operação por causa do firestore.rules
// (o erro pode vir direto ou embrulhado, como acontece nas transações)
fun Task<*>.assertPermissionDenied() {
    val error = try {
        await()
        fail("As regras deveriam ter negado a operação, mas ela foi aceita")
        return
    } catch (error: ExecutionException) {
        error.cause
    }
    val firestoreError = generateSequence(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    assertEquals(
        "Erro diferente de permissão negada: $error",
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        firestoreError?.code
    )
}
