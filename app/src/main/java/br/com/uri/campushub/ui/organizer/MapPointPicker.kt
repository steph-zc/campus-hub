package br.com.uri.campushub.ui.organizer

import br.com.uri.campushub.ui.details.EventMap
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import com.google.firebase.firestore.GeoPoint as FirestorePoint

// mapa do formulário do organizador (EventFormActivity) para marcar o ponto exato do evento.
// Tocar no mapa coloca o marcador ali; arrastar o marcador ajusta o ponto.
// Usa o osmdroid (OpenStreetMap) com a configuração comum do EventMap (ui/details).
// Trabalha com o GeoPoint do Firestore, o mesmo tipo salvo em Event.coordinates;
// não acessa banco nem API: quem salva é a tela
class MapPointPicker(
    private val map: MapView,
    // avisado sempre que o ponto muda (toque, arrasto, busca ou remoção)
    private val onChange: (FirestorePoint?) -> Unit
) {

    // zoom ao centralizar num ponto encontrado: perto o bastante para ver o prédio
    private val pointZoom = 18.0

    private var marker: Marker? = null

    // ponto marcado agora; null quando o evento fica sem mapa
    var point: FirestorePoint? = null
        private set

    // monta o mapa já com o ponto salvo (edição) ou centralizado no campus (evento novo)
    fun start(initial: FirestorePoint?) {
        EventMap.prepare(map, initial?.toMapPoint() ?: EventMap.CAMPUS)

        // toque simples no mapa: o marcador vai para onde o organizador tocou
        val taps = object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(tapped: GeoPoint): Boolean {
                place(tapped, center = false)
                return true
            }

            override fun longPressHelper(pressed: GeoPoint): Boolean = false
        }
        // o overlay de toques fica embaixo do marcador, para o arrasto do marcador continuar funcionando
        map.overlays.add(0, MapEventsOverlay(taps))

        initial?.let { place(it.toMapPoint(), center = false) }
    }

    // ponto vindo de fora (coordenadas coladas ou endereço encontrado): move o marcador e centraliza
    fun moveTo(target: FirestorePoint) {
        place(target.toMapPoint(), center = true)
    }

    // tira o ponto: o evento será salvo sem mapa (os detalhes escondem o cartão do mapa)
    fun clear() {
        marker?.let { map.overlays.remove(it) }
        marker = null
        update(null)
        map.invalidate()
    }

    private fun place(position: GeoPoint, center: Boolean) {
        val pin = marker ?: EventMap.newPin(map, position).also { created ->
            created.isDraggable = true
            // sem título o osmdroid não abre o balão de informação ao tocar no marcador
            created.setOnMarkerClickListener { _, _ -> true }
            created.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDrag(dragged: Marker) = Unit
                override fun onMarkerDragStart(dragged: Marker) = Unit
                override fun onMarkerDragEnd(dragged: Marker) = update(dragged.position)
            })
            map.overlays.add(created)
            marker = created
        }
        pin.position = position
        if (center) {
            map.controller.setZoom(pointZoom)
            map.controller.animateTo(position)
        }
        update(position)
        map.invalidate()
    }

    private fun update(position: GeoPoint?) {
        point = position?.let { FirestorePoint(it.latitude, it.longitude) }
        onChange(point)
    }

    private fun FirestorePoint.toMapPoint() = GeoPoint(latitude, longitude)
}
