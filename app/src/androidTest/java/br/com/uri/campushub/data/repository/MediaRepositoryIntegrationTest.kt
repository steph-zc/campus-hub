package br.com.uri.campushub.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.MediaPost
import br.com.uri.campushub.testing.EmulatorData.createAdmin
import br.com.uri.campushub.testing.EmulatorData.createStudent
import br.com.uri.campushub.testing.EmulatorData.daysFromNow
import br.com.uri.campushub.testing.EmulatorData.saveEvents
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// integração do MediaRepository com o emulador do Firestore: events/{eventId}/media
// só os dados da publicação vão para o Firestore; o arquivo fica no aparelho (MediaStorage)
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class MediaRepositoryIntegrationTest {

    private companion object {
        const val EVENT = "feira"
    }

    private val media = MediaRepository(FirebaseEmulator.firestore)

    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
        createAdmin()
        saveEvents(mapOf(EVENT to Event(title = "Feira", date = daysFromNow(3), capacity = 10)))
    }

    @Test
    fun postsComeNewestFirstWithTheirDataAndCanBeDeleted() {
        val uid = createStudent("ana@campushub.test", "Ana", "Direito")
        val photoId = media.add(EVENT, uid, "Ana", MediaPost.TYPE_PHOTO, "foto.jpg").await().id
        val audioId = media.add(EVENT, uid, "Ana", MediaPost.TYPE_AUDIO, "audio.m4a", 5_000).await().id

        val posts = media.getAll(EVENT).await()
        assertEquals(listOf(audioId, photoId), posts.map { it.id })
        val audio = posts.first()
        assertEquals(uid, audio.userId)
        assertEquals(MediaPost.TYPE_AUDIO, audio.type)
        assertEquals("audio.m4a", audio.fileName)
        assertEquals(5_000L, audio.durationMs)
        assertNotNull(audio.createdAt)
        assertEquals(0L, posts.last().durationMs)

        media.delete(EVENT, photoId).await()
        assertEquals(listOf(audioId), media.getAll(EVENT).await().map { it.id })
    }
}
