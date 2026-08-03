package com.wardrobe.app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wardrobe.app.core.designsystem.theme.WardrobeElevation
import com.wardrobe.app.core.designsystem.theme.WardrobeMotion
import com.wardrobe.app.core.designsystem.theme.wardrobeShadow
import kotlinx.coroutines.delay

private const val TOAST_VISIBLE_MS = 2500L

/** `docs/design/component-library.md`'s "Confirmation Toast" — never stacks; a
 * new message replaces whatever's showing. */
class ConfirmationToastState {
    var message by mutableStateOf<String?>(null)
        private set

    private var token = 0

    fun show(message: String) {
        this.message = message
        token++
    }

    internal suspend fun awaitDismiss(currentToken: Int) {
        delay(TOAST_VISIBLE_MS)
        if (token == currentToken) message = null
    }

    internal val currentToken get() = token
}

@Composable
fun rememberConfirmationToastState(): ConfirmationToastState = remember { ConfirmationToastState() }

@Composable
fun ConfirmationToastHost(
    state: ConfirmationToastState,
    modifier: Modifier = Modifier,
) {
    val message = state.message
    LaunchedEffect(message) {
        if (message != null) state.awaitDismiss(state.currentToken)
    }

    AnimatedVisibility(
        visible = message != null,
        enter =
            fadeIn(tween(WardrobeMotion.DURATION_STANDARD)) +
                slideInVertically(tween(WardrobeMotion.DURATION_STANDARD)) { it / 2 },
        exit = fadeOut(tween(WardrobeMotion.DURATION_SMALL)),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.wardrobeShadow(WardrobeElevation.RAISED, RoundedCornerShape(14.dp)),
        ) {
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            )
        }
    }
}
