package br.com.uri.campushub.data.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import br.com.uri.campushub.data.repository.EventImageRepository
import java.util.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

// testes do JpegEncoder (data/media): imagem reduzida → JPEG que cabe no documento do Firestore.
// GraphicsMode NATIVE: o Robolectric usa a biblioteca gráfica de verdade do Android,
// então a imagem é comprimida e lida de volta como no aparelho
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class JpegEncoderTest {

    // pixels aleatórios quase não comprimem: servem para forçar o limite de tamanho
    private fun noise(width: Int, height: Int): Bitmap {
        val random = Random(42)
        val pixels = IntArray(width * height) { Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256)) }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    private fun decode(jpeg: ByteArray) = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)

    // capa de 800 x 600: cabe no limite do app sem mudar as dimensões e sai em JPEG
    @Test
    fun `cover keeps its size and fits the limit`() {
        val jpeg = JpegEncoder.encode(noise(800, 600))

        assertTrue(jpeg.size <= EventImageRepository.MAX_BYTES)
        assertEquals(800, decode(jpeg).width)
        // JPEG começa com os bytes FF D8
        assertEquals(0xFF.toByte(), jpeg[0])
        assertEquals(0xD8.toByte(), jpeg[1])
    }

    // limite pequeno: a qualidade cai e, se ainda não couber, as dimensões caem pela metade
    @Test
    fun `image that does not fit is reduced until it does`() {
        val jpeg = JpegEncoder.encode(noise(400, 400), maxBytes = 20_000)

        assertTrue(jpeg.size <= 20_000)
        assertTrue(decode(jpeg).width < 400)
    }

    // imagem simples cabe já na primeira qualidade
    @Test
    fun `simple image keeps its size`() {
        val plain = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }

        val result = decode(JpegEncoder.encode(plain))

        assertEquals(300, result.width)
        assertEquals(200, result.height)
    }

    // limite impossível: depois de chegar a 1 pixel desiste com erro (a tela mostra o aviso)
    @Test
    fun `impossible limit fails`() {
        val result = runCatching { JpegEncoder.encode(noise(4, 4), maxBytes = 1) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
