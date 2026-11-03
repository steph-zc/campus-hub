package br.com.uri.campushub.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.testing.EmulatorData.createStudent
import br.com.uri.campushub.testing.EmulatorData.signUp
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// integração do UserRepository com o emulador do Firestore: perfil em users/{uid}
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class UserRepositoryIntegrationTest {

    private val users = UserRepository(FirebaseEmulator.firestore)

    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
    }

    // a tela de perfil troca nome e curso; o email salvo continua o mesmo
    @Test
    fun profileUpdateChangesNameAndCourseAndKeepsEmail() {
        val uid = createStudent("ana@campushub.test", "Ana", "Direito")

        users.update(uid, "Ana Souza", "Ciência da Computação").await()

        assertEquals(
            User("Ana Souza", "Ciência da Computação", "ana@campushub.test"),
            users.get(uid).await()
        )
    }

    // conta criada sem perfil (o cadastro falhou no meio): a leitura devolve null
    @Test
    fun missingProfileIsNull() {
        val uid = signUp("sem-perfil@campushub.test")

        assertNull(users.get(uid).await())
    }
}
