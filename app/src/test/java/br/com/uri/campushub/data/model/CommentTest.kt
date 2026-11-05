package br.com.uri.campushub.data.model

import br.com.uri.campushub.testing.TestEvents.NOW
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// testes do modelo Comment (data/model), montado em memória, sem Firestore
class CommentTest {

    @Test
    fun `date is formatted with day and time`() {
        assertEquals("15/10/2026 às 12:00", Comment(createdAt = Timestamp(NOW)).formattedDate())
    }

    // logo depois de comentar, o serverTimestamp ainda não voltou do servidor
    @Test
    fun `comment without server date shows empty text`() {
        assertEquals("", Comment(text = "Ótimo evento").formattedDate())
    }

    // editedAt nulo = comentário nunca editado; o Firestore monta o objeto com os valores padrão
    @Test
    fun `new comment is not edited`() {
        val comment = Comment()
        assertEquals("", comment.id)
        assertNull(comment.editedAt)
    }
}
