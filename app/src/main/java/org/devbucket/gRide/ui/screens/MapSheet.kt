package org.devbucket.gRide.ui.screens

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.devbucket.gRide.ui.components.SheetHandle
import org.devbucket.gRide.ui.glass.GlassSurface
import org.devbucket.gRide.ui.glass.isGlass
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class SheetStop { COLLAPSED, HALF, EXPANDED }

/**
 * The map stays visible behind; content lives in a draggable sheet with three positions:
 * expanded, half ([peekHeight]) and collapsed (only the top [collapsedHeight] stays visible).
 * Swipe down to collapse; tap the top of a collapsed sheet to bring it back.
 * [resetKey]: when it changes, the sheet returns to the half position (e.g. a new stop was opened).
 */
@Composable
fun MapSheetScaffold(
    peekHeight: Dp,
    collapsedHeight: Dp = 96.dp,
    resetKey: Any? = null,
    overlay: @Composable BoxScope.() -> Unit,
    sheet: @Composable ColumnScope.() -> Unit,
) {
    val glass = isGlass
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var position by rememberSaveable { mutableStateOf(SheetStop.HALF) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val full = constraints.maxHeight.toFloat()
        val sheetHeightPx = full * 0.9f
        val expandedY = full - sheetHeightPx
        val halfY = (full - with(density) { peekHeight.toPx() }).coerceIn(expandedY, full)
        val collapsedY = (full - with(density) { collapsedHeight.toPx() }).coerceIn(halfY, full)
        fun yOf(p: SheetStop) = when (p) {
            SheetStop.EXPANDED -> expandedY
            SheetStop.HALF -> halfY
            SheetStop.COLLAPSED -> collapsedY
        }

        var y by remember { mutableFloatStateOf(yOf(position)) }
        var settleJob by remember { mutableStateOf<Job?>(null) }

        fun animateTo(target: SheetStop, velocity: Float = 0f) {
            position = target
            settleJob?.cancel()
            settleJob = scope.launch {
                animate(y, yOf(target), velocity, spring(dampingRatio = 0.85f, stiffness = 400f)) { v, _ -> y = v }
            }
        }

        fun settle(velocity: Float) {
            val stops = SheetStop.entries.sortedBy { yOf(it) } // expanded (top) → collapsed (bottom)
            val target = when {
                velocity > 1200f -> stops.firstOrNull { yOf(it) > y + 1f } ?: stops.last()
                velocity < -1200f -> stops.lastOrNull { yOf(it) < y - 1f } ?: stops.first()
                else -> stops.minBy { abs(yOf(it) - y) }
            }
            animateTo(target, velocity)
        }

        fun dragBy(delta: Float): Float {
            settleJob?.cancel()
            val old = y
            y = (y + delta).coerceIn(expandedY, collapsedY)
            return y - old
        }

        // keep the sheet on its position when sizes change; go back to half when the content changes
        LaunchedEffect(halfY, collapsedY, expandedY) { y = yOf(position) }
        LaunchedEffect(resetKey) { if (resetKey != null && position != SheetStop.HALF) animateTo(SheetStop.HALF) }

        val nested = remember(expandedY, collapsedY) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                    // finger moves up: first pull the sheet up, then scroll the list
                    if (available.y < 0 && y > expandedY) Offset(0f, dragBy(available.y)) else Offset.Zero

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                    // list is at its top and the finger moves down: move the sheet down
                    if (available.y > 0 && source == NestedScrollSource.UserInput) Offset(0f, dragBy(available.y)) else Offset.Zero

                override suspend fun onPreFling(available: Velocity): Velocity {
                    val atStop = SheetStop.entries.any { abs(yOf(it) - y) < 1f }
                    return if (!atStop) { settle(available.y); available } else Velocity.Zero
                }

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (SheetStop.entries.none { abs(yOf(it) - y) < 1f }) settle(available.y)
                    return Velocity.Zero
                }
            }
        }

        Box(Modifier.fillMaxSize(), content = overlay)

        val sheetModifier = Modifier
            .offset { IntOffset(0, y.roundToInt()) }
            .fillMaxWidth()
            .height(with(density) { sheetHeightPx.toDp() })
            .nestedScroll(nested)
            .draggable(
                state = rememberDraggableState { dragBy(it) },
                orientation = Orientation.Vertical,
                onDragStopped = { v -> settle(v) },
            )

        val content: @Composable () -> Unit = {
            Column(Modifier.fillMaxSize()) {
                SheetHandle(
                    Modifier.clickable(interactionSource = null, indication = null) {
                        animateTo(if (position == SheetStop.COLLAPSED) SheetStop.HALF else if (position == SheetStop.HALF) SheetStop.EXPANDED else SheetStop.HALF)
                    }
                )
                Box(Modifier.weight(1f)) {
                    Column(Modifier.fillMaxSize(), content = sheet)
                    // while collapsed, tapping anywhere on the visible top brings the sheet back
                    if (position == SheetStop.COLLAPSED) {
                        Box(Modifier.fillMaxSize().clickable(interactionSource = null, indication = null) { animateTo(SheetStop.HALF) })
                    }
                }
            }
        }

        if (glass) {
            GlassSurface(
                sheetModifier,
                cornerRadius = 32.dp,
                blur = 16.dp,
                refractionHeight = 22.dp,
                refractionAmount = 44.dp,
                contentAlignment = Alignment.TopStart,
            ) { content() }
        } else {
            Surface(
                sheetModifier,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shadowElevation = 8.dp,
            ) { content() }
        }
    }
}
