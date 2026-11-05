package br.com.uri.campushub.testing

import android.os.Looper
import com.google.android.gms.tasks.Task
import org.robolectric.Shadows.shadowOf

// espera uma Task do Firebase (Google Play Services Tasks) terminar dentro de um teste com Robolectric
// o continueWith dos repositórios entrega o resultado na thread principal do Android; o Robolectric
// deixa essa fila parada, então aqui ela é executada (idle) até a Task terminar.
// Também funciona com Tasks que terminam em outra thread, como a cópia de arquivo do MediaStorage.
private fun Task<*>.waitUntilComplete(timeoutMs: Long) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!isComplete) {
        shadowOf(Looper.getMainLooper()).idle()
        if (isComplete) break
        check(System.currentTimeMillis() < deadline) { "A Task não terminou em $timeoutMs ms" }
        Thread.sleep(5)
    }
    // os listeners encadeados também rodam na fila principal
    shadowOf(Looper.getMainLooper()).idle()
}

// resultado de uma Task que deve dar certo; falha o teste se ela der erro
fun <T> Task<T>.await(timeoutMs: Long = 5_000): T {
    waitUntilComplete(timeoutMs)
    exception?.let { throw AssertionError("A Task falhou: ${it.message}", it) }
    return result
}

// erro de uma Task que deve falhar; falha o teste se ela der certo
fun Task<*>.awaitException(timeoutMs: Long = 5_000): Exception {
    waitUntilComplete(timeoutMs)
    return exception ?: throw AssertionError("A Task deveria ter falhado")
}
