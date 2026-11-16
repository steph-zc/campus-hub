package br.com.uri.campushub.data.location

import android.location.Address
import android.location.Geocoder
import androidx.test.core.app.ApplicationProvider
import br.com.uri.campushub.testing.await
import com.google.firebase.firestore.GeoPoint
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowGeocoder

// testes do AddressGeocoder (data/location): endereço digitado → ponto do mapa.
// O Robolectric simula o Geocoder do Android (ShadowGeocoder): o teste diz o que ele responde, sem internet
@RunWith(RobolectricTestRunner::class)
class AddressGeocoderTest {

    private val android = Geocoder(ApplicationProvider.getApplicationContext(), Locale.forLanguageTag("pt-BR"))
    private val geocoder = AddressGeocoder(android)

    @After
    fun tearDown() {
        ShadowGeocoder.reset()
    }

    @Test
    fun `found address becomes a map point`() {
        val campus = Address(Locale.forLanguageTag("pt-BR")).apply {
            latitude = -29.1940761
            longitude = -54.8869966
        }
        shadowOf(android).setFromLocation(listOf(campus))

        assertEquals(GeoPoint(-29.1940761, -54.8869966), geocoder.find("Rua Batista Bonoto Sobrinho, 733").await())
    }

    // endereço que o serviço não conhece: o evento é salvo sem mapa
    @Test
    fun `unknown address has no map point`() {
        shadowOf(android).setFromLocation(emptyList())

        assertNull(geocoder.find("Rua que não existe, 0").await())
    }

    // sem internet o serviço responde com erro; o formulário salva mesmo assim
    @Test
    fun `service error has no map point`() {
        shadowOf(android).setErrorMessage("sem conexão")

        assertNull(geocoder.find("Rua Batista Bonoto Sobrinho, 733").await())
    }

    // aparelho sem serviço de localização: nem tenta consultar
    @Test
    fun `device without geocoder has no map point`() {
        ShadowGeocoder.setIsPresent(false)

        assertNull(geocoder.find("Rua Batista Bonoto Sobrinho, 733").await())
    }

    @Test
    fun `blank address has no map point`() {
        assertNull(geocoder.find("   ").await())
    }

    // construtor usado pela tela: cria o Geocoder do Android a partir do contexto
    @Test
    fun `screen constructor builds the geocoder from the context`() {
        val fromScreen = AddressGeocoder(ApplicationProvider.getApplicationContext<android.content.Context>())

        assertNull(fromScreen.find("").await())
    }
}
