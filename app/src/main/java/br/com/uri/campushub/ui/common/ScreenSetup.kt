package br.com.uri.campushub.ui.common

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import br.com.uri.campushub.R
import com.google.android.material.appbar.MaterialToolbar

// configuração repetida nas telas que têm Toolbar própria (eventos, detalhes, minhas listas e perfil)
// são funções de extensão: dentro da Activity chamam-se como se fossem dela, ex.: setupToolbar(toolbar)

// a Toolbar do layout vira a barra da tela; com a seta, o toque nela fecha a tela
fun AppCompatActivity.setupToolbar(toolbar: MaterialToolbar, showBackArrow: Boolean = true) {
    setSupportActionBar(toolbar)
    if (showBackArrow) {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }
    }
}

// no Android 15+ o app desenha atrás das barras do sistema e do teclado, então ele mesmo reserva o espaço:
// - a barra de cima (appBar) desce até sair da barra de status
// - o conteúdo termina acima da barra de navegação
// - com o teclado aberto, a tela inteira encolhe por baixo; assim um campo de texto
//   no fim da tela (como o de comentário) sobe e fica visível acima do teclado
fun AppCompatActivity.applySystemBarsPadding(root: View, appBar: View, content: View) {
    val space = resources.getDimensionPixelSize(R.dimen.list_padding)

    ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
        val keyboardOpen = keyboard.bottom > 0

        appBar.setPadding(0, bars.top, 0, 0)
        root.setPadding(0, 0, 0, keyboard.bottom)
        // com o teclado aberto ele já cobre a barra de navegação, então ela não conta
        val bottomBar = if (keyboardOpen) 0 else bars.bottom
        content.setPadding(space, space, space, space + bottomBar)
        insets
    }
}
