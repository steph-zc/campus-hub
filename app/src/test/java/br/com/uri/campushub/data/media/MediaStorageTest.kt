package br.com.uri.campushub.data.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import br.com.uri.campushub.testing.await
import br.com.uri.campushub.testing.awaitException
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowMediaMetadataRetriever
import org.robolectric.shadows.util.DataSource
import java.io.File

// testes do MediaStorage (data/media): arquivos das publicações na pasta privada do app (files/media)
// o Robolectric dá um Context real com pasta de arquivos e um MediaMetadataRetriever simulado;
// os arquivos são criados, copiados e apagados de verdade
@RunWith(RobolectricTestRunner::class)
class MediaStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val storage = MediaStorage(context)
    private val mediaDir = File(context.filesDir, "media")

    @Before
    fun setUp() {
        mediaDir.deleteRecursively()
    }

    @After
    fun tearDown() {
        ShadowMediaMetadataRetriever.reset()
    }

    @Test
    fun `new files go to the media folder with unique names`() {
        val first = storage.newFile("jpg")
        val second = storage.newFile("jpg")

        assertEquals(mediaDir, first.parentFile)
        assertTrue(first.name.endsWith(".jpg"))
        assertNotEquals(first.name, second.name)
    }

    @Test
    fun `find returns the file only when it is on this device`() {
        val file = storage.newFile("m4a").apply { writeText("audio") }

        assertEquals(file, storage.find(file.name))
        assertNull(storage.find("de-outro-aparelho.m4a"))
        assertNull(storage.find(""))
    }

    @Test
    fun `delete removes the file`() {
        val file = storage.newFile("mp4").apply { writeText("video") }

        storage.delete(file.name)
        assertFalse(file.exists())
    }

    // publicação sem arquivo (vinda de outro aparelho): excluir não pode apagar a pasta inteira
    @Test
    fun `delete with empty name keeps the folder`() {
        storage.newFile("jpg").writeText("foto")

        storage.delete("")
        assertTrue(mediaDir.exists())
    }

    // endereço content:// que a câmera recebe para gravar no arquivo do app.
    // O FileProvider do AndroidX compara caminhos usando "/", então no Windows (que usa "\")
    // ele nunca acha a pasta; por isso a chamada estática é simulada com o MockK e o teste
    // confere que o app pede o endereço com a mesma autoridade declarada no AndroidManifest
    @Test
    fun `camera gets a content address from the app file provider`() {
        val file = storage.newFile("jpg")
        val expected = Uri.parse("content://br.com.uri.campushub.fileprovider/media/${file.name}")
        mockkStatic(FileProvider::class)
        try {
            every {
                FileProvider.getUriForFile(context, "br.com.uri.campushub.fileprovider", file)
            } returns expected

            assertEquals(expected, storage.shareableUri(file))
        } finally {
            unmockkStatic(FileProvider::class)
        }
    }

    @Test
    fun `gallery file is copied into the media folder`() {
        val original = File(context.cacheDir, "da-galeria.jpg").apply { writeText("conteudo da foto") }

        val fileName = storage.importFrom(Uri.fromFile(original), "jpg").await()

        val copy = File(mediaDir, fileName)
        assertEquals("conteudo da foto", copy.readText())
        assertTrue(fileName.endsWith(".jpg"))
    }

    @Test
    fun `gallery file that cannot be opened fails the import`() {
        val missing = Uri.fromFile(File(context.cacheDir, "apagado.jpg"))

        storage.importFrom(missing, "jpg").awaitException()
    }

    @Test
    fun `duration comes from the media metadata`() {
        val file = storage.newFile("mp4").apply { writeText("video") }
        ShadowMediaMetadataRetriever.addMetadata(
            file.absolutePath, MediaMetadataRetriever.METADATA_KEY_DURATION, "8000"
        )

        assertEquals(8_000L, storage.durationOf(file.name))
    }

    @Test
    fun `file without duration counts as zero`() {
        val file = storage.newFile("mp4").apply { writeText("nao e video") }

        assertEquals(0L, storage.durationOf(file.name))
    }

    @Test
    fun `missing file counts as zero`() {
        assertEquals(0L, storage.durationOf("nao-existe.mp4"))
    }

    // arquivo que o leitor de mídia não consegue abrir também conta como 0
    @Test
    fun `unreadable file counts as zero`() {
        val file = storage.newFile("mp4").apply { writeText("corrompido") }
        ShadowMediaMetadataRetriever.addException(
            DataSource.toDataSource(file.absolutePath), IllegalArgumentException("arquivo inválido")
        )

        assertEquals(0L, storage.durationOf(file.name))
    }
}
