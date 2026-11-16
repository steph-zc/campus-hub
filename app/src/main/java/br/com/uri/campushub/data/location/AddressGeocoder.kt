package br.com.uri.campushub.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.firestore.GeoPoint
import java.util.Locale

// transforma o endereço digitado pelo organizador no ponto do mapa (GeoPoint do Firestore).
// Usa o Geocoder do Android, que consulta o serviço de localização do aparelho pela internet;
// não usa o Firestore. Se o endereço não for encontrado (ou não houver serviço), devolve null
// e o evento é salvo sem mapa, como os eventos antigos
// O Geocoder é recebido pelo construtor (como os repositórios recebem o Firestore),
// para os testes entregarem um Geocoder simulado
class AddressGeocoder(private val geocoder: Geocoder) {

    // a tela cria com o contexto; os endereços vêm em português do Brasil
    constructor(context: Context) : this(Geocoder(context, Locale.forLanguageTag("pt-BR")))

    fun find(address: String): Task<GeoPoint?> {
        val result = TaskCompletionSource<GeoPoint?>()
        if (address.isBlank() || !Geocoder.isPresent()) {
            result.setResult(null)
            return result.task
        }

        // versão com listener (Android 13+): a busca roda fora da thread da tela
        geocoder.getFromLocationName(address, 1, object : Geocoder.GeocodeListener {
            override fun onGeocode(addresses: MutableList<Address>) {
                result.trySetResult(addresses.firstOrNull()?.let { GeoPoint(it.latitude, it.longitude) })
            }

            // sem internet ou sem serviço: o evento continua podendo ser salvo
            override fun onError(errorMessage: String?) {
                result.trySetResult(null)
            }
        })
        return result.task
    }
}
