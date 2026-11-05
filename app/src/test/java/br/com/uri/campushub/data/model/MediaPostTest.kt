package br.com.uri.campushub.data.model

import br.com.uri.campushub.testing.TestEvents.NOW
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Test

// testes do modelo MediaPost (data/model): publicação de foto, áudio ou vídeo, montada em memória
class MediaPostTest {

    @Test
    fun `date is formatted with day and time`() {
        assertEquals("15/10/2026 às 12:00", MediaPost(createdAt = Timestamp(NOW)).formattedDate())
    }

    @Test
    fun `post without server date shows empty text`() {
        assertEquals("", MediaPost(type = MediaPost.TYPE_AUDIO).formattedDate())
    }

    // os tipos ficam gravados no Firestore; mudar o texto quebraria as publicações antigas
    @Test
    fun `types keep the values saved in Firestore`() {
        assertEquals("photo", MediaPost.TYPE_PHOTO)
        assertEquals("audio", MediaPost.TYPE_AUDIO)
        assertEquals("video", MediaPost.TYPE_VIDEO)
    }

    // fotos não têm duração; o valor padrão também permite o toObject() do Firestore
    @Test
    fun `new post has no duration`() {
        assertEquals(0L, MediaPost().durationMs)
    }
}
