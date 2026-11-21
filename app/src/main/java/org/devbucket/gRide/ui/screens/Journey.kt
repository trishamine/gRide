package org.devbucket.gRide.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.devbucket.gRide.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.devbucket.gRide.data.Journey
import org.devbucket.gRide.data.Leg
import org.devbucket.gRide.data.LegPoint
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.NavigationBarSpacer
import org.devbucket.gRide.ui.components.DelayText
import org.devbucket.gRide.ui.components.EmptyState
import org.devbucket.gRide.ui.components.LineBadge
import org.devbucket.gRide.ui.components.BackButton
import org.devbucket.gRide.ui.components.RoundIconButton
import org.devbucket.gRide.ui.components.durationText
import org.devbucket.gRide.ui.components.hhmm
import org.devbucket.gRide.ui.components.modeIcon
import org.devbucket.gRide.ui.components.relative
import org.devbucket.gRide.ui.theme.Late
import org.devbucket.gRide.ui.theme.modeColor

@Composable
fun JourneyScreen(vm: AppViewModel, index: Int) {
    val journey = vm.journey(index)
    MapSheetScaffold(
        peekHeight = 340.dp,
        collapsedHeight = 120.dp,
        overlay = {
            Box(Modifier.statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp)) {
                BackButton(vm::back)
            }
        },
    ) {
        if (journey == null) {
            EmptyState(stringResource(R.string.connection_gone))
            return@MapSheetScaffold
        }
        JourneyHeader(journey)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp)) {
            itemsIndexed(journey.legs) { _, leg ->
                if (leg.isWalk) WalkLeg(leg) else TransitLeg(leg)
            }
            item { NavigationBarSpacer() }
        }
    }
}

@Composable
private fun JourneyHeader(j: Journey) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 4.dp)) {
        Text(durationText(j.durationMinutes), style = MaterialTheme.typography.headlineMedium)
        val changes = if (j.changes == 0) stringResource(R.string.direct) else pluralStringResource(R.plurals.changes, j.changes, j.changes)
        Text(
            "${j.departure.hhmm()} – ${j.arrival.hhmm()} · $changes" + (j.departure?.let { " · " + relative(it) } ?: ""),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun TransitLeg(leg: Leg) {
    var expanded by remember { mutableStateOf(false) }
    val color = modeColor(leg.mode)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        // times
        Column(Modifier.width(58.dp).padding(vertical = 8.dp), horizontalAlignment = Alignment.End) {
            TimeWithDelay(leg.from)
            Spacer(Modifier.weight(1f))
            TimeWithDelay(leg.to)
        }
        // line
        Canvas(Modifier.width(32.dp).fillMaxHeight().padding(vertical = 12.dp)) {
            val x = size.width / 2
            val r = 7.dp.toPx()
            drawLine(color, Offset(x, r), Offset(x, size.height - r), 6.dp.toPx())
            for (y in listOf(r, size.height - r)) {
                drawCircle(color, r, Offset(x, y))
                drawCircle(Color.White, r * 0.5f, Offset(x, y))
            }
        }
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            PointText(leg.from)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(modeIcon(leg.mode), null, tint = color, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(6.dp))
                LineBadge(leg.line, leg.mode)
                Spacer(Modifier.width(8.dp))
                Text(
                    "→ ${leg.towards}",
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (leg.cancelled) TextDecoration.LineThrough else null,
                )
            }
            if (leg.cancelled) Text(stringResource(R.string.cancelled), color = Late, fontWeight = FontWeight.Bold)
            val stops = leg.intermediateStops
            Row(
                Modifier
                    .padding(vertical = 6.dp)
                    .clickable(enabled = stops.isNotEmpty()) { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val stopText = if (stops.isEmpty()) stringResource(R.string.no_stop_between) else pluralStringResource(R.plurals.stops, stops.size, stops.size)
                Text(
                    "$stopText · ${durationText(leg.durationSeconds / 60L)}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stops.isNotEmpty()) Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
            }
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                    stops.forEach { s ->
                        Row {
                            Text(s.time.hhmm(), Modifier.width(52.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            PointText(leg.to)
        }
    }
}

@Composable
private fun WalkLeg(leg: Leg) {
    val color = modeColor(leg.mode)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(horizontal = 16.dp)) {
        Spacer(Modifier.width(58.dp))
        Canvas(Modifier.width(32.dp).fillMaxHeight()) {
            drawLine(
                color, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), 4.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 8.dp.toPx())),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
        }
        Row(Modifier.weight(1f).padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.walk_to, durationText((leg.durationSeconds / 60L).coerceAtLeast(1)), leg.to.name),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TimeWithDelay(p: LegPoint) {
    Column(horizontalAlignment = Alignment.End) {
        Text(p.planned.hhmm(), style = MaterialTheme.typography.titleMedium)
        DelayText(p.delayMinutes)
    }
}

@Composable
private fun PointText(p: LegPoint) {
    Column {
        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (p.platform.isNotBlank()) Text(platformLabel(p.platform), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
