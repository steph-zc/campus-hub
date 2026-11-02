package br.com.uri.campushub.data.repository

import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.awaitException
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.AuthResult
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do AuthRepository (data/repository) com o Firebase Authentication simulado pelo MockK
// o Robolectric fornece a thread principal do Android, onde o continueWith entrega o resultado
@RunWith(RobolectricTestRunner::class)
class AuthRepositoryTest {

    private val auth = mockk<FirebaseAuth>()
    private val user = mockk<FirebaseUser>()
    private val repository = AuthRepository(auth)

    private fun loggedIn() {
        every { auth.currentUser } returns user
        every { user.uid } returns "uid-ana"
        every { user.email } returns "ana@uri.edu.br"
    }

    private fun loggedOut() {
        every { auth.currentUser } returns null
    }

    @Test
    fun `session saved by Firebase counts as logged in`() {
        loggedIn()
        assertTrue(repository.isLoggedIn())
        assertEquals("ana@uri.edu.br", repository.currentUserEmail())
        assertEquals("uid-ana", repository.requireUserId())
    }

    @Test
    fun `without session nobody is logged in`() {
        loggedOut()
        assertFalse(repository.isLoggedIn())
        assertNull(repository.currentUserEmail())
    }

    @Test(expected = IllegalStateException::class)
    fun `screens after login cannot run without a user`() {
        loggedOut()
        repository.requireUserId()
    }

    @Test
    fun `login sends email and password to Firebase`() {
        val result = mockk<AuthResult>()
        every { auth.signInWithEmailAndPassword("ana@uri.edu.br", "segredo1") } returns Tasks.forResult(result)

        assertEquals(result, repository.login("ana@uri.edu.br", "segredo1").await())
    }

    @Test
    fun `register returns the uid of the new account`() {
        val result = mockk<AuthResult>()
        every { result.user } returns user
        every { user.uid } returns "uid-nova"
        every { auth.createUserWithEmailAndPassword("nova@uri.edu.br", "segredo1") } returns Tasks.forResult(result)

        assertEquals("uid-nova", repository.register("nova@uri.edu.br", "segredo1").await())
    }

    // ex.: email já usado; a tela mostra a mensagem do erro
    @Test
    fun `register failure reaches the screen`() {
        every { auth.createUserWithEmailAndPassword(any(), any()) } returns
            Tasks.forException(Exception("The email address is already in use"))

        val error = repository.register("ana@uri.edu.br", "segredo1").awaitException()
        assertTrue(error.message!!.contains("already in use"))
    }

    @Test
    fun `password reset asks Firebase to send the email`() {
        every { auth.sendPasswordResetEmail("ana@uri.edu.br") } returns Tasks.forResult(null)

        repository.sendPasswordReset("ana@uri.edu.br").await()
        verify { auth.sendPasswordResetEmail("ana@uri.edu.br") }
    }

    @Test
    fun `logout ends the Firebase session`() {
        every { auth.signOut() } just runs

        repository.logout()
        verify { auth.signOut() }
    }
}
