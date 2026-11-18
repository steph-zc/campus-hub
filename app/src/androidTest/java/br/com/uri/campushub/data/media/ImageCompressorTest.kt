package br.com.uri.campushub.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.data.repository.EventImageRepository
import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.awaitError
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

// teste do ImageCompressor (data/media) no aparelho: o ImageDecoder do Android lê a foto
// já reduzida e o JpegEncoder comprime. Não usa o Firebase; a foto é um arquivo gerado pelo teste
@RunWith(AndroidJUnit4::class)
class ImageCompressorTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val compressor = ImageCompressor(context)

    // foto de 1600 x 1200 gravada em PNG, como uma imagem que o Photo Picker devolveria
    private fun photo(width: Int, height: Int): Uri {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(30, 90, 160))
        val file = File(context.cacheDir, "foto-teste.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    // foto grande vira JPEG de 800 px no lado maior, com a proporção mantida e dentro do limite
    @Test
    fun largePhotoBecomesAnEightHundredPixelJpeg() {
        val jpeg = compressor.compress(photo(1600, 1200)).await()

        val result = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)
        assertEquals(800, result.width)
        assertEquals(600, result.height)
        assertTrue(jpeg.size <= EventImageRepository.MAX_BYTES)
    }

    // arquivo que não é imagem: a Task falha e a tela mostra "Não foi possível usar essa imagem"
    @Test
    fun fileThatIsNotAnImageFails() {
        val file = File(context.cacheDir, "texto.png").apply { writeText("não sou uma imagem") }

        compressor.compress(Uri.fromFile(file)).awaitError()
    }
}
