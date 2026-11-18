package br.com.uri.campushub.ui.details

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.MotionEvent
import androidx.core.content.ContextCompat
import br.com.uri.campushub.R
import br.com.uri.campushub.data.model.Event
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

// código do mapa (OpenStreetMap, pela biblioteca osmdroid) compartilhado pelas telas:
// os detalhes do evento (show) e o seletor de ponto do organizador (ui/organizer/MapPointPicker);
// trocar de biblioteca no futuro mexe só nesses arquivos e nos layouts
object EventMap {

    // perto o bastante para distinguir os prédios, que ficam a 20-30 m um do outro
    private const val ZOOM = 18.0

    // precisa rodar antes do setContentView, que cria o MapView
    fun init(context: Context) {
        val config = Configuration.getInstance()
        // os servidores do OpenStreetMap exigem que o app se identifique
        config.userAgentValue = context.packageName
        // guarda os pedaços do mapa no cache do app, que não precisa de permissão
        config.osmdroidBasePath = File(context.cacheDir, "osmdroid")
        config.osmdroidTileCache = File(config.osmdroidBasePath, "tiles")
    }

    // ponto inicial quando o evento ainda não tem local marcado: o campus da URI em Santiago
    val CAMPUS = GeoPoint(-29.1940761, -54.8869966)

    // mapa (somente leitura) dos detalhes do evento
    fun show(map: MapView, event: Event) {
        val point = event.coordinates ?: return
        val center = GeoPoint(point.latitude, point.longitude)

        prepare(map, center)
        val marker = newPin(map, center)
        marker.title = event.location
        marker.snippet = event.address
        map.overlays.add(marker)
        map.invalidate()
    }

    // configuração comum aos mapas do app (detalhes e formulário do organizador):
    // pedaços do OpenStreetMap, gestos de zoom, crédito da licença e a rolagem da tela sem roubar o toque
    @SuppressLint("ClickableViewAccessibility")
    fun prepare(map: MapView, center: GeoPoint) {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(ZOOM)
        map.controller.setCenter(center)

        map.overlays.clear()
        // a licença do OpenStreetMap pede o crédito visível no mapa
        map.overlays.add(CopyrightOverlay(map.context))

        // ao arrastar o mapa, a rolagem da tela não rouba o toque
        map.setOnTouchListener { view, motion ->
            if (motion.action == MotionEvent.ACTION_DOWN) {
                view.parent.requestDisallowInterceptTouchEvent(true)
            }
            false
        }
    }

    // marcador vermelho do app; quem chama decide se adiciona ao mapa e se ele pode ser arrastado
    fun newPin(map: MapView, position: GeoPoint): Marker {
        val marker = Marker(map)
        marker.position = position
        // sem isso o osmdroid usa o marcador padrão dele, com o desenho de uma mão
        marker.icon = ContextCompat.getDrawable(map.context, R.drawable.ic_map_pin)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        return marker
    }

    // abre o ponto no app de mapas do celular; sem nenhum instalado, abre o site do OpenStreetMap
    fun openExternal(context: Context, event: Event) {
        val point = event.coordinates ?: return
        val lat = point.latitude
        val lon = point.longitude
        val label = Uri.encode(event.title)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon($label)")))
        } catch (_: ActivityNotFoundException) {
            val site = "https://www.openstreetmap.org/?mlat=$lat&mlon=$lon#map=17/$lat/$lon"
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(site)))
        }
    }
}
