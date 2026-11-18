package br.com.uri.campushub.testing

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.core.widget.NestedScrollView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingRootException
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf

// ajudantes do Espresso para os testes de ponta a ponta (pacote e2e)

private const val WAIT_TIMEOUT_MS = 15_000L
private const val WAIT_STEP_MS = 200L

// o Espresso espera a thread principal ficar livre, mas não sabe que o app está aguardando
// a resposta do Firebase; aqui a conferência é repetida até passar ou acabar o tempo
fun waitFor(
    matcher: Matcher<View>,
    assertion: ViewAssertion = matches(isDisplayed())
): ViewInteraction {
    val deadline = SystemClock.uptimeMillis() + WAIT_TIMEOUT_MS
    while (true) {
        try {
            return onView(matcher).check(assertion)
        } catch (error: Throwable) {
            val stillLoading = error is NoMatchingViewException ||
                error is NoMatchingRootException ||
                error is AssertionError
            if (!stillLoading || SystemClock.uptimeMillis() > deadline) throw error
            SystemClock.sleep(WAIT_STEP_MS)
        }
    }
}

// rola a tela (NestedScrollView) até a view ficar no meio da área visível;
// o scrollTo() do Espresso só conhece o ScrollView comum.
// As telas desenham atrás das barras do sistema e reservam o espaço delas com padding
// (applySystemBarsPadding). Rolar só o mínimo (requestRectangleOnScreen) deixava a view colada
// na borda de baixo, dentro da faixa de gestos da navegação: com o aparelho lento, o launcher
// tratava o toque como gesto da barra e o clique não chegava ao app. Centralizar, descontando
// o padding, mantém o toque longe das bordas
fun nestedScrollTo(): ViewAction = object : ViewAction {

    override fun getConstraints(): Matcher<View> = allOf(
        isDescendantOfA(isAssignableFrom(NestedScrollView::class.java)),
        withEffectiveVisibility(Visibility.VISIBLE)
    )

    override fun getDescription() = "rolar o NestedScrollView até a view"

    override fun perform(uiController: UiController, view: View) {
        val scroll = nearestNestedScrollView(view)
        // retângulo da view nas coordenadas do conteúdo do NestedScrollView
        val rect = Rect(0, 0, view.width, view.height)
        scroll.offsetDescendantRectToMyCoords(view, rect)
        val visibleHeight = scroll.height - scroll.paddingTop - scroll.paddingBottom
        val target = rect.centerY() - scroll.paddingTop - visibleHeight / 2
        // o NestedScrollView limita sozinho ao fim do conteúdo
        scroll.scrollTo(0, target.coerceAtLeast(0))
        uiController.loopMainThreadUntilIdle()
    }

    private fun nearestNestedScrollView(view: View): NestedScrollView {
        var parent = view.parent
        while (parent !is NestedScrollView) parent = parent.parent
        return parent
    }
}
