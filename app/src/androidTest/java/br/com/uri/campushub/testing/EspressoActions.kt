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

// rola a tela de detalhes (NestedScrollView) até a view aparecer;
// o scrollTo() do Espresso só conhece o ScrollView comum
fun nestedScrollTo(): ViewAction = object : ViewAction {

    override fun getConstraints(): Matcher<View> = allOf(
        isDescendantOfA(isAssignableFrom(NestedScrollView::class.java)),
        withEffectiveVisibility(Visibility.VISIBLE)
    )

    override fun getDescription() = "rolar o NestedScrollView até a view"

    override fun perform(uiController: UiController, view: View) {
        // pede aos pais da view que mostrem esse retângulo; o NestedScrollView rola até ele
        view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        uiController.loopMainThreadUntilIdle()
    }
}
