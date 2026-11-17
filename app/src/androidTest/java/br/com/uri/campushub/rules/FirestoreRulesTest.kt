package br.com.uri.campushub.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventImageChange
import br.com.uri.campushub.data.model.EventStatus
import br.com.uri.campushub.data.model.MediaPost
import br.com.uri.campushub.data.model.Role
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.data.repository.CommentRepository
import br.com.uri.campushub.data.repository.EnrollmentRepository
import br.com.uri.campushub.data.repository.EventImageRepository
import br.com.uri.campushub.data.repository.EventRepository
import br.com.uri.campushub.data.repository.FavoriteRepository
import br.com.uri.campushub.data.repository.MediaRepository
import br.com.uri.campushub.data.repository.RatingRepository
import br.com.uri.campushub.data.repository.UserRepository
import br.com.uri.campushub.data.repository.WaitlistRepository
import br.com.uri.campushub.testing.EmulatorData.ADMIN_EMAIL
import br.com.uri.campushub.testing.EmulatorData.createAdmin
import br.com.uri.campushub.testing.EmulatorData.createOrganizer
import br.com.uri.campushub.testing.EmulatorData.createStudent
import br.com.uri.campushub.testing.EmulatorData.daysFromNow
import br.com.uri.campushub.testing.EmulatorData.eventDraft
import br.com.uri.campushub.testing.EmulatorData.minutesFromNow
import br.com.uri.campushub.testing.EmulatorData.moveEventDate
import br.com.uri.campushub.testing.EmulatorData.saveEvents
import br.com.uri.campushub.testing.EmulatorData.signIn
import br.com.uri.campushub.testing.EmulatorData.signUp
import br.com.uri.campushub.testing.FirebaseEmulator
import br.com.uri.campushub.testing.assertPermissionDenied
import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.awaitError
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import java.util.Date
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        const val CAIO_EMAIL = "caio.org@campushub.test"

        // eventos criados pela conta admin antes de cada teste
        const val OPEN = "aberto"
        const val LAW_ONLY = "so-direito"
        const val FULL = "lotado"
        const val ENDED = "encerrado"
        const val CLOSED = "inscricoes-encerradas"
    }

    private val db = FirebaseEmulator.firestore

    private val users = UserRepository(db)
    private val enrollments = EnrollmentRepository(db)
    private val events = EventRepository(db)
    private val favorites = FavoriteRepository(db)
    private val comments = CommentRepository(db)
    private val ratings = RatingRepository(db)
    private val media = MediaRepository(db)
    private val images = EventImageRepository(db)
    private val waitlist = WaitlistRepository(db)

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
            ENDED to Event(title = "Encerrado", date = daysFromNow(-1), capacity = 10),
            CLOSED to Event(title = "Inscrições encerradas", date = daysFromNow(7), capacity = 10,
                status = EventStatus.CLOSED.value)
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

    // organizador cria e edita só os próprios eventos, com os campos e limites do formulário
    @Test
    fun organizerCreatesAndEditsOnlyTheirOwnEvents() {
        val caioUid = createOrganizer(CAIO_EMAIL, "Caio", "Direito")
        val id = events.create(caioUid, eventDraft()).await()
        events.update(id, eventDraft(title = "Hackathon 2026", capacity = 40)).await()
        assertEquals("Hackathon 2026", events.get(id).await()?.title)

        // em nome de outro dono, já com inscritos ou com data passada
        val forged = eventDraft().toMap()
        db.collection("events").document().set(forged + mapOf("organizerId" to anaUid, "enrolledCount" to 0))
            .assertPermissionDenied()
        db.collection("events").document().set(forged + mapOf("organizerId" to caioUid, "enrolledCount" to 5))
            .assertPermissionDenied()
        events.create(caioUid, eventDraft(days = -1)).assertPermissionDenied()

        // a edição não troca o dono nem o contador, e não mexe nos eventos do admin
        db.collection("events").document(id).update("organizerId", anaUid).assertPermissionDenied()
        db.collection("events").document(id).update("enrolledCount", 3).assertPermissionDenied()
        events.update(OPEN, eventDraft()).assertPermissionDenied()

        // aluno comum não cria evento
        signIn(ANA_EMAIL)
        events.create(anaUid, eventDraft()).assertPermissionDenied()
    }

    // os limites do formulário valem no servidor, e a capacidade não fica abaixo dos inscritos
    @Test
    fun eventFormLimitsAndCapacityAreCheckedByTheRules() {
        val caioUid = createOrganizer(CAIO_EMAIL, "Caio", "Direito")
        events.create(caioUid, eventDraft(title = "")).assertPermissionDenied()
        events.create(caioUid, eventDraft(capacity = 0)).assertPermissionDenied()
        events.create(caioUid, eventDraft(capacity = 5001)).assertPermissionDenied()
        val id = events.create(caioUid, eventDraft(capacity = 2)).await()
        events.publish(id).await()

        signIn(ANA_EMAIL)
        enrollments.enroll(anaUid, id).await()
        signIn(BETO_EMAIL)
        enrollments.enroll(betoUid, id).await()

        signIn(CAIO_EMAIL)
        events.update(id, eventDraft(capacity = 1)).assertPermissionDenied()
        events.update(id, eventDraft(capacity = 3)).await()
        assertEquals(1, events.get(id).await()?.remainingSpots())
    }

    // a capa vai junto com o evento: imageVersion e eventImages/{id} sempre batem,
    // só o dono troca ou remove, e a imagem respeita o limite de tamanho
    @Test
    fun eventImageGoesWithTheEventAndOnlyItsOwnerChangesIt() {
        val caioUid = createOrganizer(CAIO_EMAIL, "Caio", "Direito")
        val first = byteArrayOf(1, 2, 3)
        val id = events.create(caioUid, eventDraft(), first).await()
        val firstVersion = events.get(id).await()!!.imageVersion
        assertArrayEquals(first, images.read(id).await())

        // trocar gera outra versão; remover volta para 0 e apaga os bytes
        events.update(id, eventDraft(), EventImageChange.Replace(byteArrayOf(4, 5))).await()
        assertNotEquals(firstVersion, events.get(id).await()!!.imageVersion)
        assertArrayEquals(byteArrayOf(4, 5), images.read(id).await())
        events.update(id, eventDraft(), EventImageChange.Remove).await()
        assertEquals(0L, events.get(id).await()!!.imageVersion)
        assertNull(images.read(id).await())

        // evento apontando para uma imagem que não existe, ou imagem sem mudar o evento
        db.collection("events").document(id).update("imageVersion", 99L).assertPermissionDenied()
        images.document(id).set(EventImageRepository.fields(first, 99L)).assertPermissionDenied()

        // imagem maior que o limite do documento
        val tooBig = ByteArray(EventImageRepository.MAX_BYTES + 1)
        events.update(id, eventDraft(), EventImageChange.Replace(tooBig)).assertPermissionDenied()

        // apagar a imagem deixando o evento com a versão antiga
        events.update(id, eventDraft(), EventImageChange.Replace(first)).await()
        images.document(id).delete().assertPermissionDenied()

        // a capa do rascunho só o dono vê; depois de publicado, qualquer aluno vê,
        // mas não troca nem remove a de outro dono
        signIn(ANA_EMAIL)
        images.read(id).assertPermissionDenied()
        signIn(CAIO_EMAIL)
        events.publish(id).await()
        signIn(ANA_EMAIL)
        assertArrayEquals(first, images.read(id).await())
        events.update(id, eventDraft(), EventImageChange.Replace(byteArrayOf(7))).assertPermissionDenied()
        events.update(id, eventDraft(), EventImageChange.Remove).assertPermissionDenied()
    }

    // ---------- publicar e encerrar ----------

    // rascunho: só o dono lê; a lista dos alunos não traz e a consulta sem filtro é recusada
    @Test
    fun draftIsVisibleOnlyToItsOrganizer() {
        val caioUid = createOrganizer(CAIO_EMAIL, "Caio", "Direito")
        val id = events.create(caioUid, eventDraft(title = "Rascunho")).await()
        assertEquals(EventStatus.DRAFT, events.get(id).await()?.currentStatus())
        assertEquals(listOf(id), events.getByOrganizer(caioUid).await().map { it.id })

        signIn(ANA_EMAIL)
        db.collection("events").document(id).get().assertPermissionDenied()
        assertFalse(events.getAll().await().any { it.id == id })
        assertEquals(emptyList<Event>(), events.getByIds(listOf(id)).await())
        db.collection("events").get().assertPermissionDenied()
        events.prepareSampleData().assertPermissionDenied()
        // evento que não existe continua voltando como "não encontrado"
        assertNull(events.get("apagado").await())

        // inscrição direto pelo SDK, pulando a conferência do app
        db.runBatch { batch ->
            batch.set(enrollmentDoc(anaUid, id), enrollmentData(id))
            batch.update(db.collection("events").document(id), "enrolledCount", FieldValue.increment(1))
        }.assertPermissionDenied()
    }

    // só o dono muda a situação, e só para a frente: rascunho → publicado → inscrições encerradas
    @Test
    fun onlyTheOwnerPublishesAndClosesEnrollmentsInOrder() {
        val caioUid = createOrganizer(CAIO_EMAIL, "Caio", "Direito")
        val id = events.create(caioUid, eventDraft()).await()
        val doc = db.collection("events").document(id)

        // pular etapas, ou outra conta publicando
        events.closeEnrollments(id).assertPermissionDenied()
        events.finish(id).assertPermissionDenied()
        signIn(ANA_EMAIL)
        events.publish(id).assertPermissionDenied()

        signIn(CAIO_EMAIL)
        events.publish(id).await()
        signIn(ANA_EMAIL)
        assertTrue(events.getAll().await().any { it.id == id })
        events.closeEnrollments(id).assertPermissionDenied()

        // status só sai junto com nada mais; valor inventado é recusado
        signIn(CAIO_EMAIL)
        doc.update(mapOf("status" to "closed", "title" to "Outro")).assertPermissionDenied()
        doc.update("status", "archived").assertPermissionDenied()
        doc.update("status", "draft").assertPermissionDenied()

        events.closeEnrollments(id).await()
        assertEquals(EventStatus.CLOSED, events.get(id).await()?.currentStatus())

        // inscrições encerradas não voltam, mas o evento ainda é editado
        events.publish(id).assertPermissionDenied()
        events.update(id, eventDraft(title = "Editado")).await()

        // os eventos do admin não são do organizador
        events.closeEnrollments(OPEN).assertPermissionDenied()
    }

    // encerrar o evento: só o dono e só 1 hora depois do início; depois disso nada mais muda
    @Test
    fun eventIsFinishedOnlyOneHourAfterItStarts() {
        val caioUid = createOrganizer(CAIO_EMAIL, "Caio", "Direito")
        val id = events.create(caioUid, eventDraft()).await()
        events.publish(id).await()

        // começou há 30 minutos: ainda não dá
        moveEventDate(id, minutesFromNow(-30))
        signIn(CAIO_EMAIL)
        events.finish(id).assertPermissionDenied()

        // começou há 2 horas: outra conta não encerra, o dono sim
        moveEventDate(id, minutesFromNow(-120))
        signIn(ANA_EMAIL)
        events.finish(id).assertPermissionDenied()
        signIn(CAIO_EMAIL)
        events.finish(id).await()
        assertEquals(EventStatus.FINISHED, events.get(id).await()?.currentStatus())

        // encerrado não volta nem é editado
        db.collection("events").document(id).update("status", "published").assertPermissionDenied()
        events.update(id, eventDraft(title = "Editado", days = 3)).assertPermissionDenied()
    }

    // excluir: só o dono e só sem inscritos; a capa vai junto
    @Test
    fun ownerDeletesOnlyAnEventWithoutEnrollments() {
        val caioUid = createOrganizer(CAIO_EMAIL, "Caio", "Direito")
        val id = events.create(caioUid, eventDraft(), byteArrayOf(1, 2, 3)).await()
        events.publish(id).await()
        val event = events.get(id).await()!!

        // com uma aluna inscrita, nem o dono exclui
        signIn(ANA_EMAIL)
        events.delete(event).assertPermissionDenied()
        enrollments.enroll(anaUid, id).await()
        signIn(CAIO_EMAIL)
        events.delete(event).assertPermissionDenied()

        // ela desiste: o evento fica sem inscritos e o dono exclui, com a capa
        signIn(ANA_EMAIL)
        enrollments.cancel(anaUid, id).await()
        signIn(CAIO_EMAIL)
        events.delete(event).await()
        assertNull(events.get(id).await())
        assertNull(images.read(id).await())

        // os eventos do admin continuam só com o admin
        db.collection("events").document(OPEN).delete().assertPermissionDenied()
    }

    // evento de organizador: avaliação só depois que ele encerra, não no horário de início
    @Test
    fun organizerEventIsRatedOnlyAfterItIsFinished() {
        val caioUid = createOrganizer(CAIO_EMAIL, "Caio", "Direito")
        val id = events.create(caioUid, eventDraft()).await()
        events.publish(id).await()
        signIn(ANA_EMAIL)
        enrollments.enroll(anaUid, id).await()

        moveEventDate(id, minutesFromNow(-120))
        signIn(ANA_EMAIL)
        ratings.save(anaUid, id, 5).assertPermissionDenied()

        signIn(CAIO_EMAIL)
        events.finish(id).await()
        signIn(ANA_EMAIL)
        ratings.save(anaUid, id, 5).await()
    }

    // rascunho com data que já passou não é publicado (ninguém conseguiria se inscrever)
    @Test
    fun pastDraftCannotBePublished() {
        val caioUid = createOrganizer(CAIO_EMAIL, "Caio", "Direito")
        val id = events.create(caioUid, eventDraft()).await()
        moveEventDate(id, daysFromNow(-1))

        signIn(CAIO_EMAIL)
        events.publish(id).assertPermissionDenied()
    }

    // inscrições encerradas: ninguém entra, mesmo pulando o app; quem já estava pode desistir
    @Test
    fun closedEventRejectsNewEnrollmentsButAllowsCancelling() {
        enrollments.enroll(anaUid, OPEN).await()
        signIn(ADMIN_EMAIL)
        db.collection("events").document(OPEN).update("status", EventStatus.CLOSED.value).await()

        signIn(BETO_EMAIL)
        assertTrue(EnrollmentRepository.isClosed(enrollments.enroll(betoUid, CLOSED).awaitError()))
        db.runBatch { batch ->
            batch.set(enrollmentDoc(betoUid, CLOSED), enrollmentData(CLOSED))
            batch.update(db.collection("events").document(CLOSED), "enrolledCount", FieldValue.increment(1))
        }.assertPermissionDenied()

        signIn(ANA_EMAIL)
        enrollments.cancel(anaUid, OPEN).await()
        assertEquals(0, events.get(OPEN).await()?.enrolledCount)
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

    // ---------- lista de espera ----------

    private fun waitlistDoc(eventId: String, uid: String) =
        db.collection("events").document(eventId).collection("waitlist").document(uid)

    // só entra na fila de evento lotado, em nome próprio e com a hora do servidor;
    // só sai a própria pessoa, e a hora de entrada não muda
    @Test
    fun waitlistIsOnlyForFullEventsAndForOneself() {
        waitlist.join(anaUid, OPEN).assertPermissionDenied()
        waitlist.join(betoUid, FULL).assertPermissionDenied()
        waitlistDoc(FULL, anaUid).set(mapOf("joinedAt" to Timestamp(Date(0)))).assertPermissionDenied()

        waitlist.join(anaUid, FULL).await()
        assertEquals(1, waitlist.getPosition(anaUid, FULL).await())
        waitlistDoc(FULL, anaUid).update("joinedAt", Timestamp(Date(0))).assertPermissionDenied()

        signIn(BETO_EMAIL)
        waitlist.leave(anaUid, FULL).assertPermissionDenied()
        waitlist.join(betoUid, FULL).await()
        assertEquals(2, waitlist.getPosition(betoUid, FULL).await())
    }

    // lotado, mas fechado por outro motivo: a fila é recusada (rascunho, inscrições encerradas,
    // já começou, exclusivo de outro curso, ou o aluno já está inscrito)
    @Test
    fun waitlistRefusesEventsTheStudentCouldNotJoin() {
        signIn(ADMIN_EMAIL)
        saveEvents(mapOf(
            "lotado-encerrado" to Event(title = "A", date = daysFromNow(7), capacity = 1, enrolledCount = 1,
                status = EventStatus.CLOSED.value),
            "lotado-rascunho" to Event(title = "B", date = daysFromNow(7), capacity = 1, enrolledCount = 1,
                status = EventStatus.DRAFT.value),
            "lotado-passado" to Event(title = "C", date = daysFromNow(-1), capacity = 1, enrolledCount = 1),
            "lotado-direito" to Event(title = "D", date = daysFromNow(7), capacity = 1, enrolledCount = 1,
                course = "Direito", openToAll = false),
            "uma-vaga" to Event(title = "E", date = daysFromNow(7), capacity = 1)
        ))
        signIn(ANA_EMAIL)
        for (id in listOf("lotado-encerrado", "lotado-rascunho", "lotado-passado", "lotado-direito")) {
            waitlist.join(anaUid, id).assertPermissionDenied()
        }

        // a Ana pega a última vaga: o evento lota, mas ela já está inscrita
        enrollments.enroll(anaUid, "uma-vaga").await()
        waitlist.join(anaUid, "uma-vaga").assertPermissionDenied()
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

    // ---------- organizadores ----------

    // cada conta lê só o próprio organizers/{uid}; ninguém grava nessa coleção pelo app
    @Test
    fun organizerRoleIsPrivateAndCannotBeSelfGranted() {
        val caioUid = createOrganizer(CAIO_EMAIL, "Caio", "Direito")
        assertEquals(Role.ORGANIZER, users.getRole(caioUid).await())

        signIn(ANA_EMAIL)
        assertEquals(Role.STUDENT, users.getRole(anaUid).await())
        db.collection("organizers").document(caioUid).get().assertPermissionDenied()
        db.collection("organizers").document(anaUid).set(mapOf<String, Any>()).assertPermissionDenied()
    }

    // ---------- ajudantes ----------

    private fun enrollmentDoc(uid: String, eventId: String) =
        db.collection("users").document(uid).collection("enrollments").document(eventId)

    // os mesmos campos que o EnrollmentRepository grava
    private fun enrollmentData(eventId: String) =
        mapOf("eventId" to eventId, "enrolledAt" to FieldValue.serverTimestamp())
}
