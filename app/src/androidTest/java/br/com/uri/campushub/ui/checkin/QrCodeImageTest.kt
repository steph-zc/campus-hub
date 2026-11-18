package br.com.uri.campushub.ui.checkin

import androidx.test.ext.junit.runners.AndroidJUnit4
import br.com.uri.campushub.domain.CheckinCode
import br.com.uri.campushub.testing.QrDecoder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// teste do QrCodeImage no aparelho (o Bitmap de verdade do Android): o QR desenhado
// é lido de volta pela ZXing com o mesmo texto do CheckinCode
@RunWith(AndroidJUnit4::class)
class QrCodeImageTest {

    @Test
    fun drawnCodeIsReadBack() {
        val text = CheckinCode.encode("evento-123", "uidDaAna", CheckinCode.newToken())

        val bitmap = QrCodeImage.render(text, 480)

        assertEquals(480, bitmap.width)
        assertEquals(text, QrDecoder.decode(bitmap))
    }
}
