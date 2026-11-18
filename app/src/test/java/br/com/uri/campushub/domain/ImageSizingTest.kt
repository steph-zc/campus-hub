package br.com.uri.campushub.domain

import org.junit.Assert.assertEquals
import org.junit.Test

// testes do ImageSizing (domain): tamanho da capa do evento depois de reduzida
class ImageSizingTest {

    // foto deitada da câmera (4000 x 3000): o lado maior vira 800 e a proporção 4:3 continua
    @Test
    fun `landscape photo is scaled to 800 on the longest side`() {
        assertEquals(800 to 600, ImageSizing.fit(4000, 3000))
    }

    @Test
    fun `portrait photo is scaled to 800 on the height`() {
        assertEquals(450 to 800, ImageSizing.fit(1080, 1920))
    }

    // imagem já pequena não é ampliada (ficaria borrada e mais pesada)
    @Test
    fun `small image keeps its size`() {
        assertEquals(640 to 480, ImageSizing.fit(640, 480))
        assertEquals(800 to 800, ImageSizing.fit(800, 800))
    }

    // faixa muito fina: o lado menor nunca chega a 0 pixel
    @Test
    fun `thin strip keeps at least one pixel`() {
        assertEquals(800 to 1, ImageSizing.fit(5000, 2))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `image without size is rejected`() {
        ImageSizing.fit(0, 100)
    }
}
