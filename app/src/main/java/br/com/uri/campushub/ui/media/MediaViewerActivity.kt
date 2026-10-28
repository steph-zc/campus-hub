package br.com.uri.campushub.ui.media

import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.com.uri.campushub.R
import br.com.uri.campushub.ui.common.setupToolbar
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

// mostra uma publicação em tela cheia: a foto, ou o vídeo tocando com os controles do Android
class MediaViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_AUTHOR = "author"
        const val EXTRA_DATE = "date"
        const val EXTRA_IS_VIDEO = "is_video"

        private const val STATE_POSITION = "video_position"
        private const val STATE_PLAYING = "video_playing"
    }

    private lateinit var videoFull: VideoView
    private lateinit var controller: MediaController

    // onde o vídeo estava ao sair da tela ou girar o celular, e se estava tocando
    // começa tocando ao abrir; pausado ou já terminado, continua parado ao voltar
    private var videoPosition = 0
    private var videoPlaying = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_viewer)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val imgFull = findViewById<ImageView>(R.id.imgFull)
        videoFull = findViewById(R.id.videoFull)

        setupToolbar(toolbar)
        // pela ActionBar: o toolbar.title seria trocado pelo nome do app
        supportActionBar?.title = intent.getStringExtra(EXTRA_AUTHOR)
        supportActionBar?.subtitle = intent.getStringExtra(EXTRA_DATE)

        // a mídia fica atrás das barras do sistema; só a barra de cima desce para não ficar embaixo do relógio
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, 0)
            insets
        }

        val file = File(intent.getStringExtra(EXTRA_FILE_PATH)!!)
        if (intent.getBooleanExtra(EXTRA_IS_VIDEO, false)) {
            imgFull.visibility = View.GONE
            videoPosition = savedInstanceState?.getInt(STATE_POSITION) ?: 0
            videoPlaying = savedInstanceState?.getBoolean(STATE_PLAYING) ?: true
            showVideo(file)
        } else {
            Glide.with(this).load(file).into(imgFull)
        }
    }

    private fun showVideo(file: File) {
        videoFull.visibility = View.VISIBLE
        controller = MediaController(this)
        controller.setAnchorView(videoFull)
        videoFull.setMediaController(controller)
        videoFull.setVideoPath(file.absolutePath)
        // no fim os controles ficam na tela para assistir de novo
        videoFull.setOnCompletionListener { controller.show(0) }
    }

    // o Android solta o vídeo quando a tela some; ao voltar ele fica onde parou
    override fun onResume() {
        super.onResume()
        if (videoFull.visibility != View.VISIBLE) return
        videoFull.seekTo(videoPosition)
        if (videoPlaying) videoFull.start() else controller.show(0)
    }

    override fun onPause() {
        super.onPause()
        if (videoFull.visibility != View.VISIBLE) return
        videoPosition = videoFull.currentPosition
        videoPlaying = videoFull.isPlaying
        videoFull.pause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_POSITION, videoPosition)
        outState.putBoolean(STATE_PLAYING, videoPlaying)
    }
}
