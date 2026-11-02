package br.com.uri.campushub.testing

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.URL

// liga o app ao Firebase Local Emulator Suite (Auth e Firestore rodando no computador)
// para os testes que rodam no aparelho (androidTest): nada é gravado no projeto real do Firebase.
// Antes, rode na raiz do projeto: firebase emulators:start
object FirebaseEmulator {

    // portas definidas no firebase.json
    private const val AUTH_PORT = 9099
    private const val FIRESTORE_PORT = 8080

    // 10.0.2.2 é o computador visto de dentro do emulador Android; num celular físico
    // passe -Pandroid.testInstrumentationRunnerArguments.firebaseEmulatorHost=localhost e use "adb reverse"
    val host: String by lazy {
        InstrumentationRegistry.getArguments().getString("firebaseEmulatorHost", "10.0.2.2")
    }

    private var connected = false

    // as instâncias padrão do Firebase, as mesmas que os repositórios e as telas usam
    val auth: FirebaseAuth
        get() = connect().let { FirebaseAuth.getInstance() }

    val firestore: FirebaseFirestore
        get() = connect().let { FirebaseFirestore.getInstance() }

    // o useEmulator só pode ser chamado antes do primeiro uso do Firebase, por isso uma vez só
    @Synchronized
    fun connect() {
        if (connected) return
        FirebaseAuth.getInstance().useEmulator(host, AUTH_PORT)
        FirebaseFirestore.getInstance().apply {
            useEmulator(host, FIRESTORE_PORT)
            // cache só em memória: cada teste lê o que está no emulador, não o que sobrou no aparelho
            firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
        }
        connected = true
    }

    // apaga todos os documentos e todas as contas do emulador, para cada teste começar do zero
    // (endpoints REST do próprio emulador; não existem no Firebase real)
    fun clearData() {
        connect()
        FirebaseAuth.getInstance().signOut()
        val projectId = FirebaseApp.getInstance().options.projectId
        delete("http://$host:$FIRESTORE_PORT/emulator/v1/projects/$projectId/databases/(default)/documents")
        delete("http://$host:$AUTH_PORT/emulator/v1/projects/$projectId/accounts")
    }

    private fun delete(address: String) {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "DELETE"
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK) { "O emulador respondeu $code em $address" }
        } catch (error: ConnectException) {
            throw IllegalStateException(
                "Firebase Emulator fora do ar em $host. Rode na raiz do projeto: firebase emulators:start",
                error
            )
        } finally {
            connection.disconnect()
        }
    }
}
