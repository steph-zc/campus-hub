package br.com.uri.campushub.data.repository

import br.com.uri.campushub.testing.await
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.AggregateQuery
import com.google.firebase.firestore.AggregateQuerySnapshot
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do WaitlistRepository (data/repository): fila em events/{eventId}/waitlist/{uid}
// e a posição calculada pela agregação count do Firestore.
// Firestore simulado pelo MockK; o Robolectric dá a thread principal usada pelo continueWith
@RunWith(RobolectricTestRunner::class)
class WaitlistRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val waitlist = mockk<CollectionReference>()
    private val myEntry = mockk<DocumentReference>()
    private val repository = WaitlistRepository(db)

    @Before
    fun setUp() {
        every { db.collection("events").document("event-1").collection("waitlist") } returns waitlist
        every { waitlist.document("uid-ana") } returns myEntry
    }

    // documento da aluna na fila: com a hora de entrada, ou inexistente (null)
    private fun savedEntry(joinedAt: Timestamp?) {
        val snapshot = mockk<DocumentSnapshot>()
        every { myEntry.get() } returns Tasks.forResult(snapshot)
        every { snapshot.getTimestamp("joinedAt") } returns joinedAt
    }

    // a fila só guarda a hora de entrada, e ela vem do servidor
    @Test
    fun `joining saves the server time`() {
        every { myEntry.set(any()) } returns Tasks.forResult(null)

        repository.join("uid-ana", "event-1").await()

        verify { myEntry.set(mapOf("joinedAt" to FieldValue.serverTimestamp())) }
    }

    @Test
    fun `leaving deletes the entry`() {
        every { myEntry.delete() } returns Tasks.forResult(null)

        repository.leave("uid-ana", "event-1").await()

        verify { myEntry.delete() }
    }

    @Test
    fun `student outside the queue has position zero`() {
        savedEntry(null)

        assertEquals(0, repository.getPosition("uid-ana", "event-1").await())
    }

    // posição = quantos entraram até a hora dela, contados no servidor
    @Test
    fun `position counts who joined until the student`() {
        val joinedAt = Timestamp(1_700_000_000, 0)
        savedEntry(joinedAt)
        val before = mockk<Query>()
        val count = mockk<AggregateQuery>()
        val result = mockk<AggregateQuerySnapshot>()
        every { waitlist.whereLessThanOrEqualTo("joinedAt", joinedAt) } returns before
        every { before.count() } returns count
        every { count.get(AggregateSource.SERVER) } returns Tasks.forResult(result)
        every { result.count } returns 3L

        assertEquals(3, repository.getPosition("uid-ana", "event-1").await())
    }
}
