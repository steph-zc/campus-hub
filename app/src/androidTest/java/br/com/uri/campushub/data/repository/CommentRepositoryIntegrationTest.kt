package br.com.uri.campushub.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.testing.EmulatorData.createAdmin
import br.com.uri.campushub.testing.EmulatorData.createStudent
import br.com.uri.campushub.testing.EmulatorData.daysFromNow
import br.com.uri.campushub.testing.EmulatorData.saveEvents
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// integração do CommentRepository com o emulador do Firestore: events/{eventId}/comments
// as datas (createdAt e editedAt) são preenchidas pelo servidor
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class CommentRepositoryIntegrationTest {

    private companion object {
        const val EVENT = "palestra"
    }

    private val comments = CommentRepository(FirebaseEmulator.firestore)

    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
        createAdmin()
        saveEvents(mapOf(EVENT to Event(title = "Palestra", date = daysFromNow(3), capacity = 10)))
    }

    @Test
    fun commentsComeNewestFirstAndEditingMarksThem() {
        val uid = createStudent("ana@campushub.test", "Ana", "Direito")
        val firstId = comments.add(EVENT, uid, "Ana", "Primeiro").await().id
        val secondId = comments.add(EVENT, uid, "Ana", "Segundo").await().id

        comments.update(EVENT, firstId, "Primeiro, editado").await()
        val list = comments.getAll(EVENT).await()

        assertEquals(listOf(secondId, firstId), list.map { it.id })
        val edited = list.last()
        assertEquals("Primeiro, editado", edited.text)
        assertEquals("Ana", edited.authorName)
        assertNotNull(edited.createdAt)
        assertNotNull(edited.editedAt)
        assertNull(list.first().editedAt)

        comments.delete(EVENT, firstId).await()
        assertEquals(listOf(secondId), comments.getAll(EVENT).await().map { it.id })
    }
}
