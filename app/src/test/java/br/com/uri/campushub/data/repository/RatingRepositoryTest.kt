package br.com.uri.campushub.data.repository

import br.com.uri.campushub.data.model.RatingSummary
import br.com.uri.campushub.testing.await
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.AggregateField
import com.google.firebase.firestore.AggregateQuery
import com.google.firebase.firestore.AggregateQuerySnapshot
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// testes do RatingRepository (data/repository): avaliações em events/{eventId}/ratings/{uid}
// e a média calculada pela agregação do Firestore (average e count)
// Firestore simulado pelo MockK; o Robolectric dá a thread principal usada pelo continueWith
@RunWith(RobolectricTestRunner::class)
class RatingRepositoryTest {

    private val db = mockk<FirebaseFirestore>()
    private val ratings = mockk<CollectionReference>()
    private val myRating = mockk<DocumentReference>()
    private val repository = RatingRepository(db)

    @Before
    fun setUp() {
        every { db.collection("events").document("event-1").collection("ratings") } returns ratings
        every { ratings.document("uid-ana") } returns myRating
    }

    private fun savedValue(value: Long?) {
        val snapshot = mockk<DocumentSnapshot>()
        every { myRating.get() } returns Tasks.forResult(snapshot)
        every { snapshot.getLong("value") } returns value
    }

    // resultado falso da agregação do Firestore
    private fun aggregationReturns(average: Double?, count: Long) {
        val query = mockk<AggregateQuery>()
        val snapshot = mockk<AggregateQuerySnapshot>()
        every { ratings.aggregate(any(), *anyVararg()) } returns query
        every { query.get(AggregateSource.SERVER) } returns Tasks.forResult(snapshot)
        every { snapshot.get(any<AggregateField.AverageAggregateField>()) } returns average
        every { snapshot.get(any<AggregateField.CountAggregateField>()) } returns count
    }

    @Test
    fun `my rating is the saved value`() {
        savedValue(4)
        assertEquals(4, repository.getMyRating("uid-ana", "event-1").await())
    }

    @Test
    fun `my rating is zero when I did not rate yet`() {
        savedValue(null)
        assertEquals(0, repository.getMyRating("uid-ana", "event-1").await())
    }

    // o documento usa o uid como id: salvar de novo troca a nota, não cria outra
    @Test
    fun `saving writes the rating in the student document`() {
        val data = slot<Any>()
        every { myRating.set(capture(data)) } returns Tasks.forResult(null)

        repository.save("uid-ana", "event-1", 5).await()

        val saved = data.captured as Map<*, *>
        assertEquals("uid-ana", saved["userId"])
        assertEquals(5, saved["value"])
        assertEquals(FieldValue.serverTimestamp(), saved["updatedAt"])
    }

    @Test
    fun `summary has the average and count calculated by Firestore`() {
        aggregationReturns(average = 4.5, count = 2)
        assertEquals(RatingSummary(4.5, 2), repository.getSummary("event-1").await())
    }

    // sem avaliações o Firestore devolve média nula; a tela recebe 0
    @Test
    fun `summary without ratings has average zero`() {
        aggregationReturns(average = null, count = 0)
        assertEquals(RatingSummary(0.0, 0), repository.getSummary("event-1").await())
    }
}
