package org.devbucket.gRide.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.devbucket.gRide.data.UiStyle
import org.devbucket.gRide.ui.theme.isDark

/** Which visual style the app is currently drawn in. */
val LocalUiStyle = staticCompositionLocalOf { UiStyle.EXPRESSIVE }

/** What liquid-glass surfaces refract (the map / wallpaper, and for floating chrome also the page). */
val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> { emptyBackdrop() }

val isGlass: Boolean
    @Composable get() = LocalUiStyle.current == UiStyle.LIQUID_GLASS

/**
 * A Liquid Glass surface (github.com/Kyant0/AndroidLiquidGlass).
 *
 * Press behaviour: the glass grows by +10dp in width (Animatable press: press -> animateTo(1f, tween(140));
 * on release/cancel the grow job is joined first, so even a quick tap plays the full grow, then
 * animateTo(0f, spring(0.5f, 300f, 0.001f))). While pressed it lights up with White @ 0.28 * press.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    cornerRadius: Dp? = null,          // null = capsule
    onClick: (() -> Unit)? = null,
    pressEffect: Boolean = onClick != null,
    tint: Color = Color.Unspecified,
    blur: Dp = 4.dp,
    refractionHeight: Dp = 14.dp,
    refractionAmount: Dp = 28.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    val shape = remember(cornerRadius) { if (cornerRadius == null) Capsule() else RoundedRectangle(cornerRadius) }
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val dark = isDark
    val surface = when {
        tint.isSpecified -> tint
        dark -> Color(0xFF14171B).copy(alpha = 0.58f)
        else -> Color.White.copy(alpha = 0.5f)
    }
    val growPx = with(LocalDensity.current) { 10.dp.toPx() }

    Box(
        modifier = modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    blur(blur.toPx())
                    lens(refractionHeight.toPx(), refractionAmount.toPx())
                },
                layerBlock = if (pressEffect) {
                    {
                        val w = size.width
                        if (w > 0f) scaleX = (w + growPx * press.value) / w
                    }
                } else null,
                onDrawSurface = {
                    drawRect(surface)
                    val p = press.value
                    if (p > 0f) drawRect(Color.White.copy(alpha = 0.28f * p))
                },
            )
            .then(if (pressEffect) Modifier.glassPress(press, scope) else Modifier)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = null, indication = null, onClick = onClick)
                else Modifier
            ),
        contentAlignment = contentAlignment,
    ) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
            content()
        }
    }
}

/**
 * Tracks presses anywhere inside (also when a child consumes the touch) and drives [press].
 */
fun Modifier.glassPress(press: Animatable<Float, AnimationVector1D>, scope: CoroutineScope): Modifier =
    pointerInput(press) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val grow = scope.launch { press.animateTo(1f, tween(140)) }
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    if (event.changes.none { it.pressed }) break
                }
            } finally {
                scope.launch {
                    grow.join()
                    press.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f))
                }
            }
        }
    }
