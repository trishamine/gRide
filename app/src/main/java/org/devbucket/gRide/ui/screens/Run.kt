package org.devbucket.gRide.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.devbucket.gRide.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.devbucket.gRide.data.Departure
import org.devbucket.gRide.data.Journey
import org.devbucket.gRide.data.Leg
import org.devbucket.gRide.data.LegPoint
import org.devbucket.gRide.data.RunStop
import org.devbucket.gRide.data.VehicleRun
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.BottomBarSpacer
import org.devbucket.gRide.ui.CameraRequest
import org.devbucket.gRide.ui.Load
import org.devbucket.gRide.ui.components.AppCard
import org.devbucket.gRide.ui.components.BackButton
import org.devbucket.gRide.ui.components.DelayText
import org.devbucket.gRide.ui.components.ErrorState
import org.devbucket.gRide.ui.components.LoadingState
import org.devbucket.gRide.ui.components.RoundIconButton
import org.devbucket.gRide.ui.components.hhmm
import org.devbucket.gRide.ui.map.GRideMap
import org.devbucket.gRide.ui.theme.isDark
import org.devbucket.gRide.ui.theme.modeColor

/** Departure details: the whole run of one bus / tram — where it came from and every stop up to its last one. */
@Composable
fun RunScreen(vm: AppViewModel, dep: Departure) {

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(vm::back)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.departure_details), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            val followedLines by vm.store.followedLines.collectAsStateWithLifecycle()
            val following = dep.line in followedLines
            RoundIconButton(
                if (following) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                stringResource(if (following) R.string.unfollow_line else R.string.follow_line, dep.line),
                onClick = { vm.store.toggleLine(dep.line) },
            )
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    DepartureRow(dep)
                }
            }
            when (val r = vm.run) {
                is Load.Loading, Load.Idle -> item { LoadingState() }
                is Load.Error -> item { ErrorState(r.message, onRetry = { vm.loadRun(dep) }) }
                is Load.Ok -> {
                    item { AppCard(Modifier.fillMaxWidth()) { RunTimeline(r.data) } }
                    item { RunMap(vm, r.data) }
                }
            }
            item { BottomBarSpacer() }
        }
    }
}

@Composable
private fun RunTimeline(run: VehicleRun) {
    val color = modeColor(run.departure.mode)
    val stops = run.stops
    val here = run.here.coerceIn(0, stops.lastIndex)
    val passed = run.lastPassed()
    var showPrevious by rememberSaveable { mutableStateOf(false) }
    var showNext by rememberSaveable { mutableStateOf(true) }

    Column(Modifier.padding(vertical = 12.dp)) {
        val before = if (here > 1) stops.subList(1, here) else emptyList()
        val after = if (here < stops.lastIndex - 1) stops.subList(here + 1, stops.lastIndex) else emptyList()

        if (here > 0) {
            StopLine(stops.first(), color, kind = Kind.END, passed = passed >= 0, isFirst = true)
            if (before.isNotEmpty()) {
                ToggleLine(
                    pluralStringResource(R.plurals.previous_stops, before.size, before.size),
                    showPrevious, color,
                ) { showPrevious = !showPrevious }
                if (showPrevious) before.forEachIndexed { i, s -> StopLine(s, color, Kind.MIDDLE, passed = passed >= i + 1) }
            }
        }
        StopLine(stops[here], color, kind = Kind.HERE, passed = passed >= here, isFirst = here == 0, isLast = here == stops.lastIndex)
        if (here < stops.lastIndex) {
            if (after.isNotEmpty()) {
                ToggleLine(
                    pluralStringResource(R.plurals.stops_until_last, after.size, after.size),
                    showNext, color,
                ) { showNext = !showNext }
                if (showNext) after.forEachIndexed { i, s -> StopLine(s, color, Kind.MIDDLE, passed = passed >= here + 1 + i) }
            }
            StopLine(stops.last(), color, kind = Kind.END, passed = passed >= stops.lastIndex, isLast = true)
        }
    }
}

private enum class Kind { END, HERE, MIDDLE }

@Composable
private fun StopLine(
    s: RunStop,
    color: androidx.compose.ui.graphics.Color,
    kind: Kind,
    passed: Boolean,
    isFirst: Boolean = false,
    isLast: Boolean = false,
) {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(horizontal = 16.dp)
            .alpha(if (passed && kind != Kind.HERE) 0.55f else 1f),
    ) {
        Column(Modifier.width(58.dp).padding(vertical = 10.dp), horizontalAlignment = Alignment.End) {
            Text(
                s.planned.hhmm(),
                style = if (kind == Kind.MIDDLE) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
            )
            DelayText(s.delayMinutes)
        }
        Canvas(Modifier.width(36.dp).fillMaxHeight()) {
            val x = size.width / 2
            val cy = 22.dp.toPx()
            val stroke = 5.dp.toPx()
            if (!isFirst) drawLine(color, Offset(x, 0f), Offset(x, cy), stroke)
            if (!isLast) drawLine(color, Offset(x, cy), Offset(x, size.height), stroke)
            val r = when (kind) { Kind.HERE -> 10.dp.toPx(); Kind.END -> 8.dp.toPx(); Kind.MIDDLE -> 6.dp.toPx() }
            if (passed || kind == Kind.HERE) {
                drawCircle(color, r, Offset(x, cy))
                if (kind == Kind.HERE) drawCircle(surface, r * 0.45f, Offset(x, cy))
            } else {
                drawCircle(surface, r, Offset(x, cy))
                drawCircle(color, r - 1.5.dp.toPx(), Offset(x, cy), style = Stroke(3.dp.toPx()))
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(
                s.name,
                style = if (kind == Kind.MIDDLE) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
                fontWeight = if (kind == Kind.HERE) FontWeight.Black else null,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (kind != Kind.MIDDLE && s.platform.isNotBlank()) {
                Text(platformLabel(s.platform), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (kind == Kind.HERE) {
                Text(stringResource(R.string.your_stop), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun ToggleLine(text: String, open: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(58.dp))
        Canvas(Modifier.width(36.dp).fillMaxHeight()) {
            drawLine(color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 5.dp.toPx())
        }
        Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(22.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun RunMap(vm: AppViewModel, run: VehicleRun) {
    val first = run.stops.first()
    val here = run.stops[run.here.coerceIn(0, run.stops.lastIndex)]
    val last = run.stops.last()
    fun point(s: RunStop) = LegPoint(s.name, s.platform, s.planned, s.estimated, s.coord)
    val journey = remember(run) {
        Journey(
            legs = listOf(
                Leg(
                    mode = run.departure.mode, line = run.departure.line, lineName = run.departure.lineName,
                    towards = run.departure.destination, from = point(first), to = point(last),
                    durationSeconds = 0, path = run.path, intermediateStops = emptyList(), cancelled = false,
                )
            ),
            changes = 0,
        )
    }
    val camera = remember(run) { CameraRequest(run.path.ifEmpty { run.stops.mapNotNull { it.coord } }) }
    val herePlace = remember(here) {
        here.coord?.let {
            org.devbucket.gRide.data.Place(here.id, here.name, "", org.devbucket.gRide.data.LocationType.STOP, it.lat, it.lon)
        }
    }
    AppCard(Modifier.fillMaxWidth().height(240.dp)) {
      androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        GRideMap(
            dark = isDark,
            stops = listOfNotNull(herePlace),
            showStops = herePlace != null,
            journey = journey,
            user = vm.userLocation,
            selectedStopId = herePlace?.id,
            camera = camera,
            bottomInsetPx = 0,
            onStopClick = {},
            onMoved = { _, _, _ -> },
            interactive = false,
            modifier = Modifier.fillMaxSize().clip(androidx.compose.foundation.shape.RoundedCornerShape(26.dp)),
        )
        // The map is a preview: swipes over it scroll the page instead of moving the map.
        androidx.compose.foundation.layout.Box(
            Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
        )
      }
    }
}
