package br.com.uri.campushub.domain

import kotlin.math.max
import kotlin.math.roundToInt

// tamanho da imagem de capa do evento depois de reduzida (usado pelo ImageCompressor em data/media).
// Kotlin puro, sem Android: só a conta de proporção
object ImageSizing {

    // lado maior da capa: nítida na tela do celular e leve para caber num documento do Firestore
    const val MAX_SIDE = 800

    // largura e altura reduzidas mantendo a proporção; imagem já pequena fica do mesmo tamanho
    fun fit(width: Int, height: Int, maxSide: Int = MAX_SIDE): Pair<Int, Int> {
        require(width > 0 && height > 0) { "imagem sem tamanho" }
        val largest = max(width, height)
        if (largest <= maxSide) return width to height
        val scale = maxSide.toDouble() / largest
        // coerceAtLeast: uma faixa muito fina (ex.: 5000 x 2) não pode virar 0 pixel
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }
}
