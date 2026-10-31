package br.com.uri.campushub.domain

import br.com.uri.campushub.data.model.MediaPost
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// testes das regras das publicações de foto, áudio e vídeo (MediaRules, em domain)
// regra pura: as publicações são montadas em memória com o modelo MediaPost
class MediaRulesTest {

    private val post = MediaPost(id = "post-1", userId = "author-uid", type = MediaPost.TYPE_PHOTO)

    @Test
    fun `author can delete their own post`() {
        assertTrue(MediaRules.canDelete(post, "author-uid"))
    }

    @Test
    fun `other students cannot delete the post`() {
        assertFalse(MediaRules.canDelete(post, "other-uid"))
    }

    @Test
    fun `missing user id never deletes, even on a post without author`() {
        assertFalse(MediaRules.canDelete(MediaPost(userId = ""), ""))
    }

    @Test
    fun `audio shorter than the minimum is rejected`() {
        assertFalse(MediaRules.isAudioLongEnough(MediaRules.MIN_AUDIO_MS - 1))
    }

    @Test
    fun `audio with exactly the minimum is accepted`() {
        assertTrue(MediaRules.isAudioLongEnough(MediaRules.MIN_AUDIO_MS))
    }

    @Test
    fun `video up to the limit is accepted`() {
        assertTrue(MediaRules.isVideoShortEnough(MediaRules.MAX_VIDEO_MS))
    }

    @Test
    fun `video a little over the limit is accepted because of the camera tolerance`() {
        assertTrue(MediaRules.isVideoShortEnough(MediaRules.MAX_VIDEO_MS + 1_000))
    }

    @Test
    fun `video past the tolerance is rejected`() {
        assertFalse(MediaRules.isVideoShortEnough(MediaRules.MAX_VIDEO_MS + 1_001))
    }

    @Test
    fun `durations are shown as minutes and seconds`() {
        assertEquals("0:00", MediaRules.formatDuration(0))
        assertEquals("0:07", MediaRules.formatDuration(7_000))
        assertEquals("0:59", MediaRules.formatDuration(59_999))
        assertEquals("1:00", MediaRules.formatDuration(MediaRules.MAX_AUDIO_MS))
        assertEquals("12:34", MediaRules.formatDuration(754_000))
    }

    @Test
    fun `negative duration is shown as zero`() {
        assertEquals("0:00", MediaRules.formatDuration(-500))
    }
}
