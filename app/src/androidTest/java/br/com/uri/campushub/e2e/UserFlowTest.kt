package br.com.uri.campushub.e2e

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withContentDescription
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.repository.CommentRepository
import br.com.uri.campushub.data.repository.EnrollmentRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.data.repository.FavoriteRepository
import br.com.uri.campushub.data.repository.WaitlistRepository
import br.com.uri.campushub.testing.EmulatorData.ADMIN_EMAIL
import br.com.uri.campushub.testing.EmulatorData.PASSWORD
import br.com.uri.campushub.testing.EmulatorData.createAdmin
import br.com.uri.campushub.testing.EmulatorData.createStudent
import br.com.uri.campushub.testing.EmulatorData.daysFromNow
import br.com.uri.campushub.testing.EmulatorData.saveEvents
import br.com.uri.campushub.testing.EmulatorData.signIn
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.nestedScrollTo
import br.com.uri.campushub.testing.waitFor
import br.com.uri.campushub.ui.auth.MainActivity
import br.com.uri.campushub.ui.details.EventDetailsActivity
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// testes de ponta a ponta (E2E) com o Espresso: o app inteiro roda no aparelho, a partir da
// tela inicial (MainActivity), e o teste toca e digita como um aluno. As telas usam as
// instâncias padrão do Firebase, que o FirebaseEmulator liga ao Firebase Local Emulator Suite;
// no fim, o teste confere no emulador do Firestore o que a tela gravou.
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class UserFlowTest {

    private companion object {
        const val EVENT_ID = "boas-vindas"
        const val EVENT_TITLE = "Palestra de Boas-vindas"
        const val CAPACITY = 2
        const val EMAIL = "ana@campushub.test"
        const val NAME = "Ana Lima"
        const val COURSE = "Direito"
    }

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = FirebaseEmulator.firestore

    // emulador zerado com um evento futuro criado pela conta admin; ninguém fica logado,
    // então o app abre na tela inicial com Login e Registrar
    @Before
    fun setUp() {
        FirebaseEmulator.clearData()
        createAdmin()
        saveEvents(mapOf(
            EVENT_ID to Event(
                title = EVENT_TITLE,
                category = "Palestra",
                location = "Auditório",
                date = daysFromNow(10),
                capacity = CAPACITY
            )
        ))
        FirebaseEmulator.auth.signOut()
    }

    // fluxo 1: aluno novo se cadastra, se inscreve no evento e o encontra em Meus eventos
    @Test
    fun newStudentRegistersEnrollsAndFindsTheEventInMyEvents() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // tela inicial → cadastro
            onView(withId(R.id.btnRegister)).perform(click())

            onView(withId(R.id.edtName)).perform(replaceText(NAME))
            onView(withId(R.id.spnCourse)).perform(click())
            onData(allOf(instanceOf(String::class.java), equalTo(COURSE))).perform(click())
            onView(withId(R.id.edtEmail)).perform(replaceText(EMAIL))
            onView(withId(R.id.edtPassword)).perform(replaceText(PASSWORD))
            onView(withId(R.id.edtConfirmPassword)).perform(replaceText(PASSWORD))
            closeSoftKeyboard()
            onView(withId(R.id.btnRegister)).perform(click())

            // conta criada: a lista de eventos cumprimenta pelo nome salvo no perfil
            waitFor(withText(context.getString(R.string.hello_user, NAME)))

            // lista → detalhes → inscrição
            waitFor(withText(EVENT_TITLE)).perform(click())
            waitFor(withId(R.id.btnEnroll), matches(isEnabled())).perform(click())
            waitFor(withText(R.string.enrolled_banner))
            waitFor(withText(context.getString(R.string.spots_of_capacity, CAPACITY - 1, CAPACITY)))

            // de volta à lista → Meus eventos mostra a inscrição
            pressBack()
            waitFor(withId(R.id.menuMyEvents)).perform(click())
            waitFor(withText(EVENT_TITLE))
        }

        // o que a tela gravou no Firestore: uma vaga ocupada
        assertEquals(1, EventRepository(db).get(EVENT_ID).await()?.enrolledCount)
    }

    // fluxo 2: aluno já cadastrado entra, favorita e comenta o evento, confere os favoritos e sai
    @Test
    fun studentLogsInFavoritesAndCommentsThenLogsOut() {
        val uid = createStudent(EMAIL, NAME, COURSE)
        FirebaseEmulator.auth.signOut()
        val comment = "Vou levar a turma toda!"

        ActivityScenario.launch(MainActivity::class.java).use {
            // tela inicial → login
            onView(withId(R.id.btnLogin)).perform(click())
            onView(withId(R.id.edtEmail)).perform(replaceText(EMAIL))
            onView(withId(R.id.edtPassword)).perform(replaceText(PASSWORD))
            closeSoftKeyboard()
            onView(withId(R.id.btnLogin)).perform(click())

            // lista → detalhes → favoritar (ícone de coração na barra)
            waitFor(withText(context.getString(R.string.hello_user, NAME)))
            waitFor(withText(EVENT_TITLE)).perform(click())
            waitFor(withId(R.id.btnEnroll), matches(isEnabled()))
            onView(withId(R.id.menuFavorite)).perform(click())
            waitFor(withContentDescription(R.string.remove_favorite))

            // comentar: escreve no campo e toca no ícone de enviar
            onView(withId(R.id.edtComment)).perform(nestedScrollTo(), replaceText(comment))
            closeSoftKeyboard()
            onView(withContentDescription(R.string.send_comment)).perform(click())
            waitFor(withText(comment), matches(withEffectiveVisibility(Visibility.VISIBLE)))
            waitFor(
                withId(R.id.txtCommentsTitle),
                matches(withText(context.getString(R.string.comments_count, 1)))
            )

            // de volta à lista → Meus favoritos mostra o evento
            pressBack()
            waitFor(withId(R.id.menuFavorites)).perform(click())
            waitFor(withText(EVENT_TITLE))

            // de volta à lista → Sair (menu de três pontos) → tela inicial
            pressBack()
            waitFor(withId(R.id.rvEvents))
            openActionBarOverflowOrOptionsMenu(context)
            onView(withText(R.string.logout)).perform(click())
            waitFor(withId(R.id.btnRegister))
        }

        // a sessão foi encerrada; depois o teste entra de novo, porque as regras
        // só deixam ler os favoritos e os comentários com alguém logado
        assertNull(FirebaseEmulator.auth.currentUser)
        signIn(EMAIL)
        assertEquals(listOf(EVENT_ID), FavoriteRepository(db).getEventIds(uid).await())
        assertEquals(listOf(comment), CommentRepository(db).getAll(EVENT_ID).await().map { it.text })
    }

    // fluxo 3: evento lotado. A aluna entra na lista de espera pelos detalhes, vê a posição e sai da fila
    @Test
    fun studentJoinsAndLeavesTheWaitlistOfAFullEvent() {
        signIn(ADMIN_EMAIL)
        saveEvents(mapOf("lotado" to Event(title = "Lotado", date = daysFromNow(5), capacity = 1, enrolledCount = 1)))
        val uid = createStudent(EMAIL, NAME, COURSE)
        val intent = Intent(context, EventDetailsActivity::class.java)
            .putExtra(EventDetailsActivity.EXTRA_EVENT_ID, "lotado")

        ActivityScenario.launch<EventDetailsActivity>(intent).use {
            waitFor(withId(R.id.btnEnroll), matches(withText(R.string.join_waitlist)))
            onView(withId(R.id.btnEnroll)).perform(nestedScrollTo(), click())
            waitFor(withId(R.id.txtWaitlist), matches(withText(context.getString(R.string.waitlist_position, 1))))

            onView(withId(R.id.btnLeaveWaitlist)).perform(nestedScrollTo(), click())
            // button1 = botão positivo do AlertDialog
            onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
            waitFor(withId(R.id.btnEnroll), matches(withText(R.string.join_waitlist)))
        }

        assertEquals(0, WaitlistRepository(db).getPosition(uid, "lotado").await())
    }

    // fluxo 4: a aluna está na fila e quem ocupava a vaga cancela (sem atender a fila).
    // Ao abrir o evento, o próprio app dela atende a fila: ela é inscrita e vê o aviso
    @Test
    fun studentInQueueIsEnrolledWhenASpotOpens() {
        signIn(ADMIN_EMAIL)
        saveEvents(mapOf("lotado" to Event(title = "Lotado", date = daysFromNow(5), capacity = 1)))
        val betoUid = createStudent("beto@campushub.test", "Beto", COURSE)
        EnrollmentRepository(db).enroll(betoUid, "lotado").await()
        val uid = createStudent(EMAIL, NAME, COURSE)
        WaitlistRepository(db).join(uid, "lotado").await()
        signIn("beto@campushub.test")
        EnrollmentRepository(db).cancel(betoUid, "lotado").await()
        signIn(EMAIL)

        val intent = Intent(context, EventDetailsActivity::class.java)
            .putExtra(EventDetailsActivity.EXTRA_EVENT_ID, "lotado")
        ActivityScenario.launch<EventDetailsActivity>(intent).use {
            waitFor(withId(R.id.txtEnrolled), matches(withText(R.string.promoted_banner)))
        }

        assertEquals(1, EventRepository(db).get("lotado").await()?.enrolledCount)
    }
}
