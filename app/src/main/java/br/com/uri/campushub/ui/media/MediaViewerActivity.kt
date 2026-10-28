package br.com.uri.campushub.ui.media

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.com.uri.campushub.R
import br.com.uri.campushub.ui.common.setupToolbar
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import java.io.File

// mostra uma publicação em tela cheia
class MediaViewerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_AUTHOR = "author"
        const val EXTRA_DATE = "date"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_viewer)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val imgFull = findViewById<ImageView>(R.id.imgFull)

        setupToolbar(toolbar)
        // pela ActionBar: o toolbar.title seria trocado pelo nome do app
        supportActionBar?.title = intent.getStringExtra(EXTRA_AUTHOR)
        supportActionBar?.subtitle = intent.getStringExtra(EXTRA_DATE)

        // a foto fica atrás das barras do sistema; só a barra de cima desce para não ficar embaixo do relógio
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, 0)
            insets
        }

        val file = File(intent.getStringExtra(EXTRA_FILE_PATH)!!)
        Glide.with(this).load(file).into(imgFull)
    }
}
