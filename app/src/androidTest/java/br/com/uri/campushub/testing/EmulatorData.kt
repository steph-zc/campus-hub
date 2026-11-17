package br.com.uri.campushub.testing

import br.com.uri.campushub.data.model.Event
import br.com.uri.campushub.data.model.EventDraft
import br.com.uri.campushub.data.model.User
import br.com.uri.campushub.data.repository.UserRepository
import com.google.firebase.Timestamp
import java.util.Date
import java.util.concurrent.TimeUnit

// dados de teste gravados no Firebase Local Emulator Suite pelos testes do aparelho:
// contas no emulador do Authentication e documentos no emulador do Firestore.
// Usa o FirebaseEmulator (instâncias ligadas ao emulador) e o UserRepository do app
object EmulatorData {

    const val PASSWORD = "senha123"
    const val ADMIN_EMAIL = "admin@campushub.test"

    private val auth get() = FirebaseEmulator.auth
    private val db get() = FirebaseEmulator.firestore

    // cria a conta no emulador do Authentication; o Firebase já deixa ela logada
    fun signUp(email: String): String {
        auth.signOut()
        return auth.createUserWithEmailAndPassword(email, PASSWORD).await().user!!.uid
    }

    fun signIn(email: String) {
        auth.signOut()
        auth.signInWithEmailAndPassword(email, PASSWORD).await()
    }

    // conta com perfil em users/{uid}, como o cadastro do app faz; fica logada
    fun createStudent(email: String, name: String, course: String): String {
        val uid = signUp(email)
        UserRepository(db).create(uid, User(name, course, email)).await()
        return uid
    }

    // conta admin (admins/{uid}), a única que o firestore.rules deixa gravar eventos; fica logada
    fun createAdmin(): String {
        val uid = signUp(ADMIN_EMAIL)
        FirebaseEmulator.makeAdmin(uid)
        return uid
    }

    // conta com perfil de aluno e organizers/{uid}, como o console faz; fica logada
    fun createOrganizer(email: String, name: String, course: String): String {
        val uid = createStudent(email, name, course)
        FirebaseEmulator.makeOrganizer(uid)
        return uid
    }

    // grava eventos com ids fixos; precisa da conta admin logada
    fun saveEvents(events: Map<String, Event>) {
        db.runBatch { batch ->
            events.forEach { (id, event) -> batch.set(db.collection("events").document(id), event) }
        }.await()
    }

    // o admin muda a data de um evento (por exemplo, para ele já ter acontecido)
    fun moveEventDate(eventId: String, date: Timestamp) {
        signIn(ADMIN_EMAIL)
        db.collection("events").document(eventId).update("date", date).await()
    }

    // formulário de evento do organizador, como a EventFormActivity monta (sem ponto no mapa)
    fun eventDraft(
        title: String = "Hackathon",
        days: Int = 10,
        capacity: Int = 30,
        course: String = "Direito",
        openToAll: Boolean = true
    ) = EventDraft(
        title = title,
        description = "Maratona de programação no campus",
        category = "Competição",
        date = daysFromNow(days),
        location = "Laboratório 3",
        address = "Rua Batista Bonoto Sobrinho, 733 - Santiago - RS",
        coordinates = null,
        capacity = capacity,
        course = course,
        openToAll = openToAll
    )

    // horário relativo a agora em minutos (negativo = já começou), para a regra de "1 hora depois do início"
    fun minutesFromNow(minutes: Int) =
        Timestamp(Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(minutes.toLong())))

    fun daysFromNow(days: Int) =
        Timestamp(Date(System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days.toLong())))
}
