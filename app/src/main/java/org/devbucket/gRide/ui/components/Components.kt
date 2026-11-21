package org.devbucket.gRide.ui.components

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.devbucket.gRide.R
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.LocalTaxi
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Tram
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.devbucket.gRide.data.Mode
import org.devbucket.gRide.data.TransitZone
import org.devbucket.gRide.ui.glass.GlassSurface
import org.devbucket.gRide.ui.glass.isGlass
import org.devbucket.gRide.ui.theme.Late
import org.devbucket.gRide.ui.theme.OnTime
import org.devbucket.gRide.ui.theme.isDark
import org.devbucket.gRide.ui.theme.modeColor
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// --------------------------------------------------------------- formatting

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

fun Instant?.hhmm(): String = this?.atZone(TransitZone)?.format(HHMM) ?: "–"
fun ZonedDateTime.hhmm(): String = withZoneSameInstant(TransitZone).format(HHMM)

@Composable
fun relative(instant: Instant): String {
    val mins = Duration.between(Instant.now(), instant).toMinutes()
    return when {
        mins <= 0 -> stringResource(R.string.now)
        mins < 60 -> stringResource(R.string.in_minutes, mins.toInt())
        else -> instant.hhmm()
    }
}

@Composable
fun durationText(minutes: Long): String =
    if (minutes >= 60) stringResource(R.string.duration_h_min, (minutes / 60).toInt(), (minutes % 60).toInt())
    else stringResource(R.string.duration_min, minutes.toInt())

@Composable
fun distanceText(meters: Float): String =
    if (meters < 1000) stringResource(R.string.distance_m, meters.toInt()) else stringResource(R.string.distance_km, meters / 1000f)

// ----------------------------------------------------------------- badges

@Composable
fun LineBadge(
    number: String,
    mode: Mode,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Box(
        modifier
            .widthIn(min = if (compact) 30.dp else 40.dp)
            .clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
            .background(modeColor(mode))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 1.dp else 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            number.ifBlank { stringResource(mode.label) },
            color = if (mode == Mode.REGIONAL_BUS) Color(0xFF14240A) else Color.White,
            fontWeight = FontWeight.Bold,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
            maxLines = 1,
        )
    }
}

fun modeIcon(mode: Mode): ImageVector = when (mode) {
    Mode.TRAM -> Icons.Filled.Tram
    Mode.CITY_BUS, Mode.REGIONAL_BUS -> Icons.Filled.DirectionsBus
    Mode.TRAIN, Mode.SUBURBAN -> Icons.Filled.Train
    Mode.ON_DEMAND -> Icons.Filled.LocalTaxi
    Mode.WALK -> Icons.AutoMirrored.Filled.DirectionsWalk
    Mode.OTHER -> Icons.Filled.DirectionsBus
}

/** The German bus-stop "H" sign, drawn without image assets. */
@Composable
fun StopSign(modifier: Modifier = Modifier, size: Dp = 40.dp, favorite: Boolean = false) {
    Box(modifier.size(size + if (favorite) 6.dp else 0.dp)) {
        Canvas(Modifier.size(size).align(Alignment.BottomStart)) {
            val r = this.size.minDimension / 2
            drawCircle(Color(0xFF1B7A3E), r)
            drawCircle(Color(0xFFF7C91E), r * 0.80f)
            val stroke = r * 0.22f
            val h = r * 0.95f
            val w = r * 0.62f
            val c = center
            val green = Color(0xFF1B7A3E)
            drawLine(green, Offset(c.x - w / 2, c.y - h / 2), Offset(c.x - w / 2, c.y + h / 2), stroke)
            drawLine(green, Offset(c.x + w / 2, c.y - h / 2), Offset(c.x + w / 2, c.y + h / 2), stroke)
            drawLine(green, Offset(c.x - w / 2, c.y), Offset(c.x + w / 2, c.y), stroke)
            drawCircle(green, r * 0.80f, style = Stroke(r * 0.06f))
        }
        if (favorite) {
            Icon(
                Icons.Filled.Star, null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size * 0.5f).align(Alignment.TopEnd).offset(x = 2.dp, y = (-2).dp),
            )
        }
    }
}

@Composable
fun DelayText(delay: Long?, modifier: Modifier = Modifier) {
    if (delay == null) return
    Text(
        if (delay >= 0) "+$delay" else "$delay",
        color = if (delay > 0) Late else OnTime,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}

// ------------------------------------------------------- adaptive surfaces

/** A card: Material surface in Expressive mode, a glass panel in Liquid Glass mode. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    corner: Dp = 26.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (isGlass) {
        GlassSurface(
            modifier = modifier,
            cornerRadius = corner,
            onClick = onClick,
            blur = 10.dp,
            refractionHeight = 10.dp,
            refractionAmount = 18.dp,
            contentAlignment = Alignment.TopStart,
        ) { Column(content = content) }
    } else if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(corner),
            color = MaterialTheme.colorScheme.surfaceContainer) { Column(content = content) }
    } else {
        Surface(modifier = modifier, shape = RoundedCornerShape(corner),
            color = MaterialTheme.colorScheme.surfaceContainer) { Column(content = content) }
    }
}

/** Large rounded search field look-alike that opens a search screen. */
/**
 * Press progress for Expressive shape morphing. Like the glass press: on press it animates to 1
 * (tween 140 ms); on release the morph is finished first, then it springs back. So even a very
 * quick tap shows the complete morph.
 */
@Composable
fun rememberPressMorph(source: MutableInteractionSource): Float {
    val press = remember { Animatable(0f) }
    LaunchedEffect(source) {
        var grow: Job? = null
        source.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> grow = launch { press.animateTo(1f, tween(140)) }
                is PressInteraction.Release, is PressInteraction.Cancel -> {
                    val job = grow
                    launch {
                        job?.join()
                        press.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f))
                    }
                }
            }
        }
    }
    return press.value
}

private fun morphCorner(rest: Dp, pressed: Dp, p: Float): Dp = rest + (pressed - rest) * p

@Composable
fun SearchPill(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
) {
    val inner: @Composable RowScope.() -> Unit = {
        if (leading != null) {
            Icon(leading, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Icon(icon, null)
    }
    if (isGlass) {
        GlassSurface(modifier.fillMaxWidth().height(60.dp), onClick = onClick) {
            Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, content = inner)
        }
    } else {
        val source = remember { MutableInteractionSource() }
        val p = rememberPressMorph(source)
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth().height(60.dp),
            shape = RoundedCornerShape(morphCorner(30.dp, 16.dp, p)),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            interactionSource = source,
        ) {
            Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically, content = inner)
        }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
) {
    if (isGlass) {
        GlassSurface(modifier.size(size), onClick = onClick) {
            Icon(icon, contentDescription)
        }
    } else {
        val source = remember { MutableInteractionSource() }
        val p = rememberPressMorph(source)
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier.size(size),
            shape = RoundedCornerShape(morphCorner(size / 2, size * 0.28f, p)),
            interactionSource = source,
        ) { Icon(icon, contentDescription) }
    }
}

/** The one back button used on every screen, so it always has the same size and place. */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RoundIconButton(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), onClick = onClick, modifier = modifier, size = 52.dp)
}

@Composable
fun PrimaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    if (isGlass) {
        GlassSurface(
            modifier.height(52.dp),
            onClick = onClick,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f),
        ) {
            Row(Modifier.padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)) }
                Text(text, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        val source = remember { MutableInteractionSource() }
        val p = rememberPressMorph(source)
        Button(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            shape = RoundedCornerShape(morphCorner(26.dp, 10.dp, p)),
            interactionSource = source,
        ) {
            if (icon != null) { Icon(icon, null); Spacer(Modifier.width(8.dp)) }
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SecondaryAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    if (isGlass) {
        GlassSurface(modifier.height(52.dp), onClick = onClick) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)) }
                Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        }
    } else {
        val source = remember { MutableInteractionSource() }
        val p = rememberPressMorph(source)
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.height(52.dp),
            shape = RoundedCornerShape(morphCorner(26.dp, 10.dp, p)),
            interactionSource = source,
        ) {
            if (icon != null) { Icon(icon, null); Spacer(Modifier.width(8.dp)) }
            Text(text)
        }
    }
}

/**
 * A single-choice segmented control: connected buttons that morph (Expressive) or a glass capsule
 * with a sliding indicator (Liquid Glass).
 */
@Composable
fun SegmentedChoice(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isGlass) {
        GlassSurface(modifier.fillMaxWidth().height(52.dp), pressEffect = true) {
            BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)) {
                val itemWidth = maxWidth / options.size
                val x by animateDpAsState(itemWidth * selected, label = "segment")
                Box(
                    Modifier
                        .offset(x = x)
                        .width(itemWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.45f else 0.22f))
                )
                Row(Modifier.fillMaxSize()) {
                    options.forEachIndexed { i, label ->
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(interactionSource = null, indication = null) { onSelect(i) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            options.forEachIndexed { i, label ->
                val checked = i == selected
                val source = remember { MutableInteractionSource() }
                val p = rememberPressMorph(source)
                val c by animateFloatAsState(if (checked) 1f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "checked")
                val outer = 24.dp
                // inner corners: 8dp normally, fully round when selected, tighter while pressed
                val inner = morphCorner(morphCorner(8.dp, outer, c), 4.dp, p)
                val first = i == 0
                val last = i == options.lastIndex
                val shape = RoundedCornerShape(
                    topStart = if (first) outer else inner,
                    bottomStart = if (first) outer else inner,
                    topEnd = if (last) outer else inner,
                    bottomEnd = if (last) outer else inner,
                )
                Surface(
                    onClick = { onSelect(i) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = shape,
                    color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    interactionSource = source,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(
                            label, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------- states

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        LoadingIndicator(Modifier.size(56.dp))
    }
}

@Composable
fun ErrorState(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(40.dp))
        Text(message, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (onRetry != null) TextButton(onClick = onRetry) { Text(stringResource(R.string.try_again)) }
    }
}

@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 32.dp),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** Drag-handle look for our custom sheets. */
@Composable
fun SheetHandle(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(width = 40.dp, height = 5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        )
    }
}

/** Thin outline used to separate list rows. */
@Composable
fun RowDivider(modifier: Modifier = Modifier, start: Dp = 24.dp) {
    Box(
        modifier
            .padding(start = start, end = 24.dp)
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isGlass) 0.5f else 1f))
    )
}

/** Small chip with a mode icon + label, used in journey summaries. */
@Composable
fun WalkChip(minutes: Long, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$minutes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier) {
    Box(modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}
