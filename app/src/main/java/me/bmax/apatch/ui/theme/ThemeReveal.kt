package me.bmax.apatch.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.clipPath
import kotlin.math.hypot

/**
 * Holds the transient state for the circular "ripple" reveal played when the
 * night/light theme changes.
 *
 * The tapped control calls [capture] with its centre (in root coordinates)
 * *before* applying the theme change. [capture] snapshots the current frame so
 * the overlay can peel the old theme away and let the new one spread from that
 * point. The snapshot fails soft: if it cannot be produced, the change simply
 * applies with no transition.
 */
class ThemeRevealState internal constructor() {
    internal var origin: Offset by mutableStateOf(Offset.Zero)
    internal var bitmap: ImageBitmap? by mutableStateOf(null)
    internal var captureFn: (suspend (Offset) -> Unit)? = null

    /** Snapshots the current frame, then the caller may apply the theme change. */
    suspend fun capture(at: Offset) {
        captureFn?.invoke(at)
    }
}

internal val LocalThemeRevealState = staticCompositionLocalOf<ThemeRevealState?> { null }

internal fun ImageBitmap.isUsable(): Boolean = width > 0 && height > 0

// The old theme has to travel the full screen diagonal, so a short spring reads
// as a blink. Use a longer, decelerating sweep that visibly spreads and settles.
private const val RevealDurationMillis = 600

@Composable
internal fun ThemeRevealOverlay(state: ThemeRevealState) {
    val bitmap = state.bitmap ?: return
    val origin = state.origin
    val progress = remember { Animatable(0f) }

    LaunchedEffect(bitmap) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = RevealDurationMillis,
                easing = FastOutSlowInEasing,
            ),
        )
        state.bitmap = null
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val maxRadius = hypot(size.width.toDouble(), size.height.toDouble()).toFloat()
        val radius = (progress.value * maxRadius).coerceAtLeast(1f)
        val fullScreen = Path().apply { addRect(Rect(Offset.Zero, size)) }
        val revealCircle = Path().apply { addOval(Rect(center = origin, radius = radius)) }
        // Keep the old-theme snapshot everywhere *outside* the growing circle so
        // the new theme appears to spread outwards from the tapped control.
        val oldThemeRegion = Path().apply {
            op(fullScreen, revealCircle, PathOperation.Difference)
        }
        clipPath(oldThemeRegion) {
            drawImage(bitmap)
        }
    }
}
