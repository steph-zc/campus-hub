package br.com.uri.campushub.e2e

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.R
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.testing.EmulatorData.createOrganizer
import br.com.uri.campushub.testing.EmulatorData.eventDraft
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.nestedScrollTo
import br.com.uri.campushub.testing.waitFor
import br.com.uri.campushub.ui.organizer.EventFormActivity
import com.google.firebase.firestore.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// testes de ponta a ponta (E2E) do organizador com o Espresso: a tela do formulário
// (EventFormActivity) roda no aparelho com a conta organizadora logada no Firebase Local Emulator Suite,
// e no fim o teste confere no emulador do Firestore o que a tela gravou (EventRepository).
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class OrganizerFlowTest {

    private companion object {
        const val ORGANIZER_EMAIL = "caio.org@campushub.test"
        const val PASTED_COORDINATES = "-29.194076, -54.886997"
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val events = EventRepository(FirebaseEmulator.firestore)
    private lateinit var eventId: String

    // emulador zerado com um evento do organizador ainda sem ponto no mapa; o organizador fica logado
    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
        val uid = createOrganizer(ORGANIZER_EMAIL, "Caio Souza", "Direito")
        eventId = events.create(uid, eventDraft()).await()
    }

    private fun openForm() = ActivityScenario.launchActivityForResult<EventFormActivity>(
        Intent(context, EventFormActivity::class.java).putExtra(EventFormActivity.EXTRA_EVENT_ID, eventId)
    )

    // o organizador cola as coordenadas no campo de endereço, localiza no mapa e salva o ponto exato
    @Test
    fun organizerMarksTheExactPointWithPastedCoordinates() {
        openForm().use {
            waitFor(withId(R.id.btnFindOnMap))
            onView(withId(R.id.edtAddress)).perform(nestedScrollTo(), replaceText(PASTED_COORDINATES))
            closeSoftKeyboard()
            onView(withId(R.id.btnFindOnMap)).perform(nestedScrollTo(), click())

            // o ponto aparece embaixo do mapa, com ponto decimal
            waitFor(withId(R.id.txtMapPoint), matches(withText(PASTED_COORDINATES)))
            onView(withId(R.id.btnSave)).perform(nestedScrollTo(), click())
            // result espera a tela fechar depois da gravação no Firestore
            assertEquals(Activity.RESULT_OK, it.result.resultCode)
        }

        assertEquals(GeoPoint(-29.194076, -54.886997), events.get(eventId).await()?.coordinates)
    }

    // o organizador remove o ponto: o evento é salvo sem mapa, mesmo com o endereço preenchido
    @Test
    fun organizerRemovesThePointAndTheEventHasNoMap() {
        openForm().use {
            waitFor(withId(R.id.btnFindOnMap))
            onView(withId(R.id.edtAddress)).perform(nestedScrollTo(), replaceText(PASTED_COORDINATES))
            closeSoftKeyboard()
            onView(withId(R.id.btnFindOnMap)).perform(nestedScrollTo(), click())
            waitFor(withId(R.id.txtMapPoint), matches(withText(PASTED_COORDINATES)))

            onView(withId(R.id.btnClearPoint)).perform(nestedScrollTo(), click())
            waitFor(withId(R.id.txtMapPoint), matches(withText(R.string.map_point_none)))
            onView(withId(R.id.btnSave)).perform(nestedScrollTo(), click())
            // result espera a tela fechar depois da gravação no Firestore
            assertEquals(Activity.RESULT_OK, it.result.resultCode)
        }

        assertNull(events.get(eventId).await()?.coordinates)
    }
}
