package br.com.uri.campushub.domain

import java.security.SecureRandom

// conteúdo do QR Code de check-in: identifica o evento, o aluno e o segredo dele.
// Formato: "campushub-checkin:v1:{eventId}:{uid}:{token}". O token é aleatório (32 dígitos hex),
// gravado no documento do aluno na lista do organizador (events/{eventId}/attendees/{uid}),
// que só ele e o organizador dono leem: um QR inventado ou de outra pessoa não bate com o salvo.
// Regra pura (só java.security); a leitura com a câmera (etapa seguinte) usa o parse
object CheckinCode {

    private const val PREFIX = "campushub-checkin"
    private const val VERSION = "v1"
    private const val TOKEN_BYTES = 16

    // o que o QR carrega, já separado
    data class Payload(val eventId: String, val userId: String, val token: String)

    // 32 dígitos hexadecimais (o mesmo formato que o firestore.rules exige)
    private val TOKEN_FORMAT = Regex("^[0-9a-f]{32}$")

    // ids do Firestore e uids do Authentication não têm ":"; qualquer outro texto é recusado
    private val ID_FORMAT = Regex("^[A-Za-z0-9_-]{1,128}$")

    // segredo novo do aluno: 16 bytes do gerador seguro do sistema, em hexadecimal
    fun newToken(random: SecureRandom = SecureRandom()): String {
        val bytes = ByteArray(TOKEN_BYTES).also { random.nextBytes(it) }
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun isValidToken(token: String) = TOKEN_FORMAT.matches(token)

    fun encode(eventId: String, userId: String, token: String): String {
        require(ID_FORMAT.matches(eventId) && ID_FORMAT.matches(userId) && isValidToken(token)) {
            "dados inválidos para o QR de check-in"
        }
        return listOf(PREFIX, VERSION, eventId, userId, token).joinToString(":")
    }

    // texto lido da câmera → dados do check-in; null se não for um QR do CampusHub válido
    fun parse(text: String): Payload? {
        val parts = text.trim().split(":")
        if (parts.size != 5 || parts[0] != PREFIX || parts[1] != VERSION) return null
        val (eventId, userId, token) = Triple(parts[2], parts[3], parts[4])
        if (!ID_FORMAT.matches(eventId) || !ID_FORMAT.matches(userId) || !isValidToken(token)) return null
        return Payload(eventId, userId, token)
    }
}
