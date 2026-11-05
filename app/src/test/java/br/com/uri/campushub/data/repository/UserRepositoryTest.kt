package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.testing.await
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do UserRepository (data/repository): perfil do aluno em users/{uid}
// o Firestore é simulado pelo MockK só no caminho users/uid-ana; outro caminho faria o teste falhar
@RunWith(RobolectricTestRunner::class)
class UserRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val users = mockk<CollectionReference>()
    private val userDoc = mockk<DocumentReference>()
    private val repository = UserRepository(db)

    private val ana = User(name = "Ana Souza", course = "Direito", email = "ana@uri.edu.br")

    @Before
    fun setUp() {
        every { db.collection("users") } returns users
        every { users.document("uid-ana") } returns userDoc
    }

    @Test
    fun `create saves the profile in the user document`() {
        every { userDoc.set(ana) } returns Tasks.forResult(null)

        repository.create("uid-ana", ana).await()
        verify { userDoc.set(ana) }
    }

    @Test
    fun `get turns the document into a User`() {
        val snapshot = mockk<DocumentSnapshot>()
        every { userDoc.get() } returns Tasks.forResult(snapshot)
        every { snapshot.toObject(User::class.java) } returns ana

        assertEquals(ana, repository.get("uid-ana").await())
    }

    @Test
    fun `get returns null when the profile does not exist`() {
        val snapshot = mockk<DocumentSnapshot>()
        every { userDoc.get() } returns Tasks.forResult(snapshot)
        every { snapshot.toObject(User::class.java) } returns null

        assertNull(repository.get("uid-ana").await())
    }

    // update muda só nome e curso; o email salvo no documento continua igual
    @Test
    fun `update changes only name and course`() {
        val changes = mapOf("name" to "Ana Lima", "course" to "Medicina")
        every { userDoc.update(changes) } returns Tasks.forResult(null)

        repository.update("uid-ana", "Ana Lima", "Medicina").await()
        verify { userDoc.update(changes) }
    }
}
