package br.com.uri.campushub.testing

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestoreException
import org.junit.Assert.assertEquals
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

// ajudantes para as Tasks do Firebase nos testes do aparelho (androidTest)
// os testes rodam na thread do instrumentation, então dá para bloquear esperando a resposta
// do emulador com o Tasks.await (na thread principal do app isso não é permitido)

private const val TIMEOUT_SECONDS = 15L

fun <T> Task<T>.await(): T = Tasks.await(this, TIMEOUT_SECONDS, TimeUnit.SECONDS)

// espera a Task falhar e devolve o erro original (sem o ExecutionException do Tasks.await)
fun Task<*>.awaitError(): Exception {
    try {
        await()
    } catch (error: ExecutionException) {
        return error.cause as Exception
    }
    throw AssertionError("A operação deveria ter falhado, mas foi aceita")
}

// confere que o Firestore recusou a operação por causa do firestore.rules
// (o erro pode vir direto ou embrulhado, como acontece nas transações)
fun Task<*>.assertPermissionDenied() {
    val error = awaitError()
    val firestoreError = generateSequence<Throwable>(error) { it.cause }
        .filterIsInstance<FirebaseFirestoreException>()
        .firstOrNull()
    assertEquals(
        "Erro diferente de permissão negada: $error",
        FirebaseFirestoreException.Code.PERMISSION_DENIED,
        firestoreError?.code
    )
}
