package br.com.uri.campushub.domain

import com.google.firebase.firestore.GeoPoint
import java.util.Locale

// lê coordenadas digitadas ou coladas no campo de endereço do formulário de evento,
// no formato que o Google Maps e o OpenStreetMap copiam: "-29.1940761, -54.8869966".
// Aceita vírgula, ponto e vírgula ou espaço entre os números (o decimal é sempre ponto).
// Não usa banco nem API: só texto → GeoPoint do Firestore (o mesmo tipo de Event.coordinates)
object CoordinateParser {

    // latitude e longitude com sinal opcional e decimal com ponto, separadas por , ; ou espaço
    private val PATTERN = Regex("""^\s*([+-]?\d{1,3}(?:\.\d+)?)\s*[,; ]\s*([+-]?\d{1,3}(?:\.\d+)?)\s*$""")

    // null quando o texto não é um par de coordenadas válido (então ele é tratado como endereço)
    fun parse(text: String): GeoPoint? {
        val match = PATTERN.matchEntire(text) ?: return null
        val latitude = match.groupValues[1].toDouble()
        val longitude = match.groupValues[2].toDouble()
        // fora desses limites o ponto não existe na Terra (e o GeoPoint lançaria exceção)
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return GeoPoint(latitude, longitude)
    }

    // texto mostrado abaixo do mapa; sempre com ponto decimal (Locale.US), para poder ser
    // copiado e colado de volta no campo de endereço ou no Google Maps.
    // 6 casas decimais ≈ 10 cm, mais do que o GPS de um celular consegue distinguir
    fun format(point: GeoPoint): String =
        String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude)
}
