package br.com.uri.campushub.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.MediaPost
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.data.repository.CommentRepository
import br.com.uri.campushub.data.repository.EnrollmentRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.data.repository.FavoriteRepository
import br.com.uri.campushub.data.repository.MediaRepository
import br.com.uri.campushub.data.repository.RatingRepository
import br.com.uri.campushub.data.repository.UserRepository
import br.com.uri.campushub.testing.EmulatorData.ADMIN_EMAIL
import br.com.uri.campushub.testing.EmulatorData.createAdmin
import br.com.uri.campushub.testing.EmulatorData.createStudent
import br.com.uri.campushub.testing.EmulatorData.daysFromNow
import br.com.uri.campushub.testing.EmulatorData.moveEventDate
import br.com.uri.campushub.testing.EmulatorData.saveEvents
import br.com.uri.campushub.testing.EmulatorData.signIn
import br.com.uri.campushub.testing.EmulatorData.signUp
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.assertPermissionDenied
import br.com.uri.campushub.testing.await
import com.google.firebase.firestore.FieldValue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

// testes das regras de segurança (firestore.rules) no Firebase Local Emulator Suite
// cada teste usa contas reais do emulador do Authentication e o Firestore do emulador
// (criadas pelo EmulatorData),
// que aplica o firestore.rules do projeto em toda leitura e gravação.
// As operações permitidas passam pelos repositórios do app (o mesmo código das telas);
// as tentativas de burlar as regras gravam direto pelo SDK do Firestore, como faria um app alterado.
// Antes, rode na raiz do projeto: firebase emulators:start
@RunWith(AndroidJUnit4::class)
class FirestoreRulesTest {

    private companion object {
        const val ANA_EMAIL = "ana@campushub.test"
        const val BETO_EMAIL = "beto@campushub.test"

        // eventos criados pela conta admin antes de cada teste
        const val OPEN = "aberto"
        const val LAW_ONLY = "so-direito"
        const val FULL = "lotado"
        const val ENDED = "encerrado"
    }

    private val db = FirebaseEmulator.firestore

    private val users = UserRepository(db)
    private val enrollments = EnrollmentRepository(db)
    private val events = EventRepository(db)
    private val favorites = FavoriteRepository(db)
    private val comments = CommentRepository(db)
    private val ratings = RatingRepository(db)
    private val media = MediaRepository(db)

    private lateinit var anaUid: String
    private lateinit var betoUid: String

    // emulador zerado; o admin cria os eventos e dois alunos se cadastram
    // Ana (Ciência da Computação) fica logada no fim
    @Before
    fun setUp() {
        FirebaseEmulator.clearData()

        createAdmin()
        saveEvents(mapOf(
            OPEN to Event(title = "Aberto", date = daysFromNow(7), capacity = 10),
            LAW_ONLY to Event(title = "Só Direito", date = daysFromNow(7), capacity = 10, course = "Direito", openToAll = false),
            FULL to Event(title = "Lotado", date = daysFromNow(7), capacity = 1, enrolledCount = 1),
            ENDED to Event(title = "Encerrado", date = daysFromNow(-1), capacity = 10)
        ))

        betoUid = createStudent(BETO_EMAIL, "Beto", "Direito")
        anaUid = createStudent(ANA_EMAIL, "Ana", "Ciência da Computação")
    }

    // ---------- perfil ----------

    @Test
    fun studentCannotReadAnotherStudentsProfile() {
        assertEquals("Ana", users.get(anaUid).await()?.name)
        users.get(betoUid).assertPermissionDenied()
    }

    @Test
    fun profileEditChangesNameAndCourseButNotEmail() {
        users.update(anaUid, "Ana Souza", "Direito").await()

        db.collection("users").document(anaUid).update("email", "outro@campushub.test")
            .assertPermissionDenied()
    }

    @Test
    fun profileMustUseTheAccountEmail() {
        val caioUid = signUp("caio@campushub.test")

        users.create(caioUid, User("Caio", "Direito", "falso@campushub.test")).assertPermissionDenied()
        users.create(anaUid, User("Ana", "Direito", ANA_EMAIL)).assertPermissionDenied()
    }

    // ---------- eventos ----------

    @Test
    fun onlyAdminCreatesOrEditsEvents() {
        db.collection("events").document("falso").set(Event(title = "Evento falso", date = daysFromNow(3)))
            .assertPermissionDenied()
        db.collection("events").document(OPEN).update("title", "Outro título").assertPermissionDenied()
        events.saveSampleEvents().assertPermissionDenied()
    }

    // ---------- inscrições ----------

    @Test
    fun enrollingAndCancellingUpdateTheCounter() {
        enrollments.enroll(anaUid, OPEN).await()
        assertEquals(1, events.get(OPEN).await()?.enrolledCount)

        enrollments.cancel(anaUid, OPEN).await()
        assertEquals(0, events.get(OPEN).await()?.enrolledCount)
    }

    // o contador só muda junto com a inscrição do próprio aluno, e de 1 em 1
    @Test
    fun counterAndEnrollmentCannotChangeSeparately() {
        db.collection("events").document(OPEN).update("enrolledCount", FieldValue.increment(1))
            .assertPermissionDenied()
        db.collection("events").document(OPEN).update("enrolledCount", 0).assertPermissionDenied()
        enrollmentDoc(anaUid, OPEN).set(enrollmentData(OPEN)).assertPermissionDenied()
        db.runBatch { batch ->
            batch.set(enrollmentDoc(anaUid, OPEN), enrollmentData(OPEN))
            batch.update(db.collection("events").document(OPEN), "enrolledCount", FieldValue.increment(2))
        }.assertPermissionDenied()
    }

    // o app confere a vaga antes; um app alterado que pule essa conferência é barrado pelas regras
    @Test
    fun soldOutEventRejectsEnrollment() {
        db.runBatch { batch ->
            batch.set(enrollmentDoc(anaUid, FULL), enrollmentData(FULL))
            batch.update(db.collection("events").document(FULL), "enrolledCount", FieldValue.increment(1))
        }.assertPermissionDenied()
    }

    @Test
    fun exclusiveEventAcceptsOnlyItsCourse() {
        enrollments.enroll(anaUid, LAW_ONLY).assertPermissionDenied()

        signIn(BETO_EMAIL)
        enrollments.enroll(betoUid, LAW_ONLY).await()
    }

    @Test
    fun endedEventRejectsEnrollment() {
        enrollments.enroll(anaUid, ENDED).assertPermissionDenied()
    }

    @Test
    fun studentCannotActOnBehalfOfAnother() {
        enrollments.enroll(betoUid, OPEN).assertPermissionDenied()
        favorites.setFavorite(betoUid, OPEN, true).assertPermissionDenied()
        favorites.getEventIds(betoUid).assertPermissionDenied()

        favorites.setFavorite(anaUid, OPEN, true).await()
        assertEquals(listOf(OPEN), favorites.getEventIds(anaUid).await())
    }

    // ---------- comentários ----------

    @Test
    fun onlyTheAuthorEditsOrDeletesAComment() {
        val commentId = comments.add(OPEN, anaUid, "Ana", "Vou participar!").await().id

        signIn(BETO_EMAIL)
        comments.update(OPEN, commentId, "Texto trocado").assertPermissionDenied()
        comments.delete(OPEN, commentId).assertPermissionDenied()

        signIn(ANA_EMAIL)
        comments.update(OPEN, commentId, "Vou participar com a turma!").await()
        comments.delete(OPEN, commentId).await()
    }

    @Test
    fun commentAuthorIsAlwaysTheLoggedInStudent() {
        comments.add(OPEN, betoUid, "Beto", "Comentário em nome de outro").assertPermissionDenied()
        comments.add(OPEN, anaUid, "Ana", "x".repeat(501)).assertPermissionDenied()
    }

    // ---------- publicações ----------

    @Test
    fun onlyTheAuthorDeletesAPostAndTypesAreChecked() {
        val postId = media.add(OPEN, anaUid, "Ana", MediaPost.TYPE_PHOTO, "foto.jpg").await().id
        media.add(OPEN, anaUid, "Ana", "programa", "virus.exe").assertPermissionDenied()

        signIn(BETO_EMAIL)
        media.delete(OPEN, postId).assertPermissionDenied()

        signIn(ANA_EMAIL)
        media.delete(OPEN, postId).await()
    }

    // ---------- avaliações ----------

    @Test
    fun ratingNeedsEnrollmentAndEndedEvent() {
        // inscrita, mas o evento ainda não aconteceu
        enrollments.enroll(anaUid, OPEN).await()
        ratings.save(anaUid, OPEN, 5).assertPermissionDenied()
        // evento encerrado, mas sem inscrição
        ratings.save(anaUid, ENDED, 5).assertPermissionDenied()

        // o admin passa a data do evento para ontem
        moveEventDate(OPEN, daysFromNow(-1))

        signIn(ANA_EMAIL)
        ratings.save(anaUid, OPEN, 6).assertPermissionDenied()
        ratings.save(anaUid, OPEN, 5).await()
        assertEquals(5, ratings.getMyRating(anaUid, OPEN).await())
    }

    // ---------- admins ----------

    @Test
    fun studentCannotMakeThemselvesAdmin() {
        db.collection("admins").document(anaUid).set(mapOf<String, Any>()).assertPermissionDenied()
    }

    // ---------- ajudantes ----------

    private fun enrollmentDoc(uid: String, eventId: String) =
        db.collection("users").document(uid).collection("enrollments").document(eventId)

    // os mesmos campos que o EnrollmentRepository grava
    private fun enrollmentData(eventId: String) =
        mapOf("eventId" to eventId, "enrolledAt" to FieldValue.serverTimestamp())
}
