package br.com.uri.campushub.data.media

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import br.com.uri.campushub.domain.ImageSizing
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executor
import java.util.concurrent.Executors

// transforma a foto escolhida na galeria (Photo Picker) na capa do evento:
// lê a foto já reduzida para no máximo 800 px no lado maior (ImageSizing, domain)
// e comprime em JPEG até caber no documento do Firestore (JpegEncoder).
// Usa o ImageDecoder do Android, que já gira a foto conforme a câmera (EXIF).
// Não acessa banco nem API; roda fora da thread da tela, porque decodificar uma foto grande trava o app.
// Testado no aparelho (androidTest): o ImageDecoder nativo não roda no Robolectric no Windows
class ImageCompressor(
    private val context: Context,
    private val executor: Executor = defaultExecutor
) {

    companion object {
        private val defaultExecutor = Executors.newSingleThreadExecutor()
    }

    // lê a imagem do endereço devolvido pelo Photo Picker e devolve os bytes do JPEG pronto
    fun compress(uri: Uri): Task<ByteArray> =
        Tasks.call(executor) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val (width, height) = ImageSizing.fit(info.size.width, info.size.height)
                // o decoder já lê a imagem no tamanho reduzido, sem carregar a foto inteira na memória
                decoder.setTargetSize(width, height)
                // bitmap comum (não de hardware), que pode ser comprimido em JPEG
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            JpegEncoder.encode(bitmap)
        }
}
