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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// integração do FavoriteRepository com o emulador do Firestore: users/{uid}/favorites/{eventId}
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class FavoriteRepositoryIntegrationTest {

    private val db = FirebaseEmulator.firestore
    private val favorites = FavoriteRepository(db)
    private val events = EventRepository(db)

    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
        createAdmin()
        saveEvents(mapOf(
            "palestra" to Event(title = "Palestra", date = daysFromNow(3), capacity = 10),
            "feira" to Event(title = "Feira", date = daysFromNow(5), capacity = 10)
        ))
    }

    // mesmo caminho da tela Meus favoritos: ids favoritados e depois os eventos desses ids;
    // favoritar não reserva vaga
    @Test
    fun favoritesAreSavedListedAndRemoved() {
        val uid = createStudent("ana@campushub.test", "Ana", "Direito")

        favorites.setFavorite(uid, "palestra", true).await()
        favorites.setFavorite(uid, "palestra", true).await()
        favorites.setFavorite(uid, "feira", true).await()

        val ids = favorites.getEventIds(uid).await()
        assertEquals(listOf("Palestra", "Feira"), events.getByIds(ids).await().map { it.title })
        assertEquals(0, events.get("palestra").await()?.enrolledCount)

        favorites.setFavorite(uid, "palestra", false).await()
        assertFalse(favorites.isFavorite(uid, "palestra").await())
        assertTrue(favorites.isFavorite(uid, "feira").await())
    }
}
