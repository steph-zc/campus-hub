package br.com.uri.campushub.ui.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.contract.ActivityResultContracts
import br.com.uri.campushub.domain.MediaRules

// igual ao CaptureVideo, mas pede à câmera para parar no limite de vídeo curto
// nem todo app de câmera respeita o pedido, por isso a seção confere a duração depois
class CaptureShortVideo : ActivityResultContracts.CaptureVideo() {

    override fun createIntent(context: Context, input: Uri): Intent =
        super.createIntent(context, input)
            .putExtra(MediaStore.EXTRA_DURATION_LIMIT, (MediaRules.MAX_VIDEO_MS / 1000).toInt())
}
