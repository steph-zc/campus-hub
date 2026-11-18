package br.com.uri.campushub.e2e

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.R
import br.com.uri.campushub.data.media.JpegEncoder
import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.data.repository.EventImageRepository
import br.com.uri.campushub.data.repository.EnrollmentRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.data.repository.WaitlistRepository
import br.com.uri.campushub.testing.EmulatorData.createOrganizer
import br.com.uri.campushub.testing.EmulatorData.createStudent
import br.com.uri.campushub.testing.EmulatorData.signIn
import br.com.uri.campushub.testing.EmulatorData.eventDraft
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.nestedScrollTo
import br.com.uri.campushub.testing.waitFor
import br.com.uri.campushub.domain.CheckinCode
import br.com.uri.campushub.ui.checkin.GmsQrScanner
import br.com.uri.campushub.ui.checkin.QrScanner
import br.com.uri.campushub.ui.details.EventDetailsActivity
import br.com.uri.campushub.ui.organizer.AttendeesActivity
import org.hamcrest.Matchers.startsWith
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
    private lateinit var uid: String
    private lateinit var eventId: String

    // emulador zerado com um evento do organizador ainda sem ponto no mapa; o organizador fica logado
    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
        uid = createOrganizer(ORGANIZER_EMAIL, "Caio Souza", "Direito")
        eventId = events.create(uid, eventDraft()).await()
    }

    private fun openForm() = ActivityScenario.launchActivityForResult<EventFormActivity>(
        Intent(context, EventFormActivity::class.java).putExtra(EventFormActivity.EXTRA_EVENT_ID, eventId)
    )

    // o organizador abre "Ver inscritos" nos detalhes: o resumo e as duas seções mostram
    // a aluna inscrita e o aluno na fila, com nome e curso
    @Test
    fun organizerSeesEnrolledAndWaitingStudents() {
        val full = events.create(uid, eventDraft(title = "Oficina de Robótica", capacity = 1)).await()
        events.publish(full).await()
        val anaUid = createStudent("ana.lima@campushub.test", "Ana Lima", "Direito")
        EnrollmentRepository(FirebaseEmulator.firestore).enroll(anaUid, full).await()
        val betoUid = createStudent("beto.reis@campushub.test", "Beto Reis", "Medicina")
        WaitlistRepository(FirebaseEmulator.firestore).join(betoUid, full).await()
        signIn(ORGANIZER_EMAIL)

        val intent = Intent(context, EventDetailsActivity::class.java).putExtra(EventDetailsActivity.EXTRA_EVENT_ID, full)
        ActivityScenario.launch<EventDetailsActivity>(intent).use {
            waitFor(withId(R.id.btnAttendees))
            onView(withId(R.id.btnAttendees)).perform(nestedScrollTo(), click())

            waitFor(withText("Inscritos (1)"))
            onView(withText("Ana Lima")).check(matches(isDisplayed()))
            onView(withText("Lista de espera (1)")).check(matches(isDisplayed()))
            onView(withText("Beto Reis")).check(matches(isDisplayed()))
            onView(withText("Medicina")).check(matches(isDisplayed()))
            onView(withId(R.id.txtOccupied)).check(matches(withText("1/1")))
            onView(withId(R.id.txtCheckins)).check(matches(withText("0/1")))
        }
    }

    // o organizador lê o QR da aluna (leitura simulada no lugar da câmera): o check-in é confirmado,
    // a lista mostra "Presente" e, no app dela, o QR some e o aviso mostra o check-in
    @Test
    fun organizerScansTheStudentQrCode() {
        val id = events.create(uid, eventDraft(title = "Oficina de Robótica")).await()
        events.publish(id).await()
        val anaUid = createStudent("ana.lima@campushub.test", "Ana Lima", "Direito")
        EnrollmentRepository(FirebaseEmulator.firestore).enroll(anaUid, id).await()
        val token = FirebaseEmulator.firestore.collection("events").document(id).collection("attendees")
            .document(anaUid).get().await().getString("checkinToken")!!
        signIn(ORGANIZER_EMAIL)

        val code = CheckinCode.encode(id, anaUid, token)
        AttendeesActivity.scannerFactory = { QrScanner { _, onCode, _ -> onCode(code) } }
        try {
            val intent = Intent(context, AttendeesActivity::class.java).putExtra(AttendeesActivity.EXTRA_EVENT_ID, id)
            ActivityScenario.launch<AttendeesActivity>(intent).use {
                waitFor(withId(R.id.fabCheckin))
                onView(withId(R.id.fabCheckin)).perform(click())
                waitFor(withText(R.string.checkin_done_title))
                onView(withText("Ana Lima está presente.")).inRoot(isDialog()).check(matches(isDisplayed()))
                onView(withText(R.string.close)).inRoot(isDialog()).perform(click())

                waitFor(withId(R.id.txtCheckins), matches(withText("1/1")))
                onView(withText(R.string.checked_in)).check(matches(isDisplayed()))
            }
        } finally {
            AttendeesActivity.scannerFactory = { GmsQrScanner() }
        }

        signIn("ana.lima@campushub.test")
        val details = Intent(context, EventDetailsActivity::class.java).putExtra(EventDetailsActivity.EXTRA_EVENT_ID, id)
        ActivityScenario.launch<EventDetailsActivity>(details).use {
            waitFor(withId(R.id.txtEnrolled), matches(withText(startsWith("Check-in feito em"))))
            onView(withId(R.id.btnCheckinQr)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            onView(withId(R.id.btnCancelEnrollment)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    // o organizador cola as coordenadas no campo de endereço, localiza no mapa e salva o ponto exato
    @Test
    fun organizerMarksTheExactPointWithPastedCoordinates() {
        openForm().use {
            waitFor(withId(R.id.scrollContent))
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
            waitFor(withId(R.id.scrollContent))
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

    // evento com capa: o formulário mostra a imagem salva; o organizador remove e salva sem capa.
    // (o Photo Picker é uma tela do sistema, fora do alcance do Espresso; a capa é gravada pelo repositório)
    @Test
    fun organizerSeesTheSavedCoverAndRemovesIt() {
        val cover = Bitmap.createBitmap(160, 90, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(30, 90, 160)) }
        eventId = events.create(uid, eventDraft(title = "Feira de Ciências"), JpegEncoder.encode(cover)).await()

        openForm().use {
            waitFor(withId(R.id.imgCover))
            onView(withId(R.id.btnPickImage)).check(matches(withText(R.string.change_image)))

            onView(withId(R.id.btnRemoveImage)).perform(click())
            waitFor(withId(R.id.txtNoImage))
            onView(withId(R.id.btnSave)).perform(nestedScrollTo(), click())
            assertEquals(Activity.RESULT_OK, it.result.resultCode)
        }

        assertEquals(0L, events.get(eventId).await()?.imageVersion)
        assertNull(EventImageRepository(FirebaseEmulator.firestore).read(eventId).await())
    }

    // nos detalhes, o organizador publica o rascunho e depois encerra as inscrições,
    // confirmando cada passo no diálogo; o Firestore fica com a situação final
    @Test
    fun organizerPublishesTheDraftAndThenClosesEnrollments() {
        val intent = Intent(context, EventDetailsActivity::class.java)
            .putExtra(EventDetailsActivity.EXTRA_EVENT_ID, eventId)

        ActivityScenario.launch<EventDetailsActivity>(intent).use {
            waitFor(withId(R.id.txtEventStatus), matches(withText(R.string.draft_banner)))
            onView(withId(R.id.btnPublish)).perform(nestedScrollTo(), click())
            // button1 = botão positivo do AlertDialog
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

            waitFor(withId(R.id.btnCloseEnrollments))
            onView(withId(R.id.btnCloseEnrollments)).perform(nestedScrollTo(), click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

            waitFor(withId(R.id.txtEventStatus), matches(withText(R.string.closed_banner)))
            onView(withId(R.id.btnEnroll)).check(matches(withText(R.string.enrollments_closed)))
        }

        assertEquals(EventStatus.CLOSED, events.get(eventId).await()?.currentStatus())
    }

    // evento sem inscritos: o organizador exclui pelo menu dos detalhes, a tela fecha e o evento some
    @Test
    fun organizerDeletesAnEventWithoutEnrollments() {
        val intent = Intent(context, EventDetailsActivity::class.java)
            .putExtra(EventDetailsActivity.EXTRA_EVENT_ID, eventId)

        ActivityScenario.launch<EventDetailsActivity>(intent).use { scenario ->
            waitFor(withId(R.id.txtEventStatus))
            openActionBarOverflowOrOptionsMenu(context)
            onView(withText(R.string.delete_event)).perform(click())
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())

            // a tela fecha sozinha depois da exclusão
            val deadline = System.currentTimeMillis() + 15_000
            while (scenario.state != Lifecycle.State.DESTROYED && System.currentTimeMillis() < deadline) {
                Thread.sleep(200)
            }
            assertEquals(Lifecycle.State.DESTROYED, scenario.state)
        }

        assertNull(events.get(eventId).await())
    }
}
