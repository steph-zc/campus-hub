package br.com.uri.campushub.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.testing.EmulatorData.PASSWORD
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.awaitError
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// integração do AuthRepository com o emulador do Firebase Authentication
// (cadastro, login, logout e recuperação de senha) e do perfil gravado no emulador do Firestore,
// na mesma ordem da tela de cadastro (RegisterActivity)
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class AuthRepositoryIntegrationTest {

    private companion object {
        const val EMAIL = "ana@campushub.test"
    }

    private val auth = AuthRepository(FirebaseEmulator.auth)
    private val users = UserRepository(FirebaseEmulator.firestore)

    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
    }

    @Test
    fun registeringCreatesALoggedInAccountWithProfile() {
        val uid = auth.register(EMAIL, PASSWORD).await()
        users.create(uid, User("Ana", "Direito", EMAIL)).await()

        assertTrue(auth.isLoggedIn())
        assertEquals(uid, auth.requireUserId())
        assertEquals(EMAIL, auth.currentUserEmail())
        assertEquals(User("Ana", "Direito", EMAIL), users.get(uid).await())
    }

    @Test
    fun sameEmailCannotRegisterTwice() {
        auth.register(EMAIL, PASSWORD).await()
        auth.logout()

        assertTrue(auth.register(EMAIL, PASSWORD).awaitError() is FirebaseAuthUserCollisionException)
    }

    @Test
    fun loginChecksThePasswordAndLogoutEndsTheSession() {
        auth.register(EMAIL, PASSWORD).await()
        auth.logout()
        assertFalse(auth.isLoggedIn())

        assertTrue(auth.login(EMAIL, "senha-errada").awaitError() is FirebaseAuthInvalidCredentialsException)
        assertFalse(auth.isLoggedIn())

        auth.login(EMAIL, PASSWORD).await()
        assertTrue(auth.isLoggedIn())
    }

    // o emulador não envia email de verdade: guarda o link, que aparece na Emulator UI (aba Authentication)
    @Test
    fun passwordResetIsSentToARegisteredEmail() {
        auth.register(EMAIL, PASSWORD).await()
        auth.logout()

        auth.sendPasswordReset(EMAIL).await()
    }
}
