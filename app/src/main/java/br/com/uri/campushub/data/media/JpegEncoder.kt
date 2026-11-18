package br.com.uri.campushub.data.media

import android.graphics.Bitmap
import br.com.uri.campushub.data.repository.EventImageRepository
import java.io.ByteArrayOutputStream

// comprime a imagem já reduzida (Bitmap do Android) em JPEG que caiba no documento do Firestore
// (EventImageRepository.MAX_BYTES). Usado pelo ImageCompressor; não acessa banco nem API
object JpegEncoder {

    // qualidade inicial do JPEG e o mínimo antes de desistir de reduzir só a qualidade
    private const val START_QUALITY = 85
    private const val MIN_QUALITY = 40
    private const val QUALITY_STEP = 15

    // JPEG com até maxBytes: baixa a qualidade aos poucos; se nem a mínima couber,
    // reduz as dimensões pela metade e tenta de novo
    fun encode(bitmap: Bitmap, maxBytes: Int = EventImageRepository.MAX_BYTES): ByteArray {
        var current = bitmap
        while (true) {
            var quality = START_QUALITY
            while (quality >= MIN_QUALITY) {
                val jpeg = compress(current, quality)
                if (jpeg.size <= maxBytes) return jpeg
                quality -= QUALITY_STEP
            }
            // 1 x 1 pixel e ainda não coube: o limite é impossível
            check(current.width > 1 || current.height > 1) { "imagem não cabe no limite" }
            current = Bitmap.createScaledBitmap(
                current,
                (current.width / 2).coerceAtLeast(1),
                (current.height / 2).coerceAtLeast(1),
                true
            )
        }
    }

    private fun compress(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        return output.toByteArray()
    }
}
