package br.com.uri.campushub.domain

import com.google.firebase.firestore.GeoPoint
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// testes do CoordinateParser (domain): coordenadas coladas no campo de endereço viram o ponto do mapa
class CoordinateParserTest {

    private val campus = GeoPoint(-29.1940761, -54.8869966)

    // formato copiado do Google Maps
    @Test
    fun `comma separated pair is a point`() {
        assertEquals(campus, CoordinateParser.parse("-29.1940761, -54.8869966"))
    }

    @Test
    fun `semicolon space and extra blanks are accepted`() {
        assertEquals(campus, CoordinateParser.parse("  -29.1940761;-54.8869966 "))
        assertEquals(campus, CoordinateParser.parse("-29.1940761 -54.8869966"))
    }

    @Test
    fun `integers and plus sign are accepted`() {
        assertEquals(GeoPoint(10.0, 20.0), CoordinateParser.parse("+10, 20"))
    }

    // um endereço comum não é coordenada: a tela consulta o Geocoder
    @Test
    fun `address is not a point`() {
        assertNull(CoordinateParser.parse("Rua Batista Bonoto Sobrinho, 733"))
        assertNull(CoordinateParser.parse(""))
        assertNull(CoordinateParser.parse("-29.19"))
    }

    // vírgula decimal ("-29,19") fica ambígua com o separador; só o ponto é aceito
    @Test
    fun `decimal comma is not a point`() {
        assertNull(CoordinateParser.parse("-29,1940761, -54,8869966"))
    }

    @Test
    fun `out of range values are not a point`() {
        assertNull(CoordinateParser.parse("91, 0"))
        assertNull(CoordinateParser.parse("-91, 0"))
        assertNull(CoordinateParser.parse("0, 181"))
        assertNull(CoordinateParser.parse("0, -181"))
    }

    // o texto mostrado abaixo do mapa usa ponto decimal mesmo com o celular em português
    // e pode ser colado de volta no campo de endereço
    @Test
    fun `formatted point uses dot decimals and parses back`() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("pt-BR"))
        try {
            val text = CoordinateParser.format(campus)
            assertEquals("-29.194076, -54.886997", text)
            assertEquals(GeoPoint(-29.194076, -54.886997), CoordinateParser.parse(text))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `limits are valid points`() {
        assertEquals(GeoPoint(90.0, 180.0), CoordinateParser.parse("90, 180"))
        assertEquals(GeoPoint(-90.0, -180.0), CoordinateParser.parse("-90, -180"))
    }
}
