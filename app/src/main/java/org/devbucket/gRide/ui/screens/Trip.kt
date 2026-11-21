package org.devbucket.gRide.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.devbucket.gRide.R
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.devbucket.gRide.data.Journey
import org.devbucket.gRide.data.ModeGroup
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.Load
import org.devbucket.gRide.ui.NavigationBarSpacer
import org.devbucket.gRide.ui.SearchTarget
import org.devbucket.gRide.ui.TimeMode
import org.devbucket.gRide.ui.components.AppCard
import org.devbucket.gRide.ui.components.DelayText
import org.devbucket.gRide.ui.components.EmptyState
import org.devbucket.gRide.ui.components.ErrorState
import org.devbucket.gRide.ui.components.LineBadge
import org.devbucket.gRide.ui.components.LoadingState
import org.devbucket.gRide.ui.components.BackButton
import org.devbucket.gRide.ui.components.RoundIconButton
import org.devbucket.gRide.ui.components.RowDivider
import org.devbucket.gRide.ui.components.SecondaryAction
import org.devbucket.gRide.ui.components.WalkChip
import org.devbucket.gRide.ui.components.hhmm
import org.devbucket.gRide.ui.components.modeIcon
import org.devbucket.gRide.ui.components.relative
import org.devbucket.gRide.ui.theme.Late
import org.devbucket.gRide.ui.theme.modeColor

@Composable
fun TripScreen(vm: AppViewModel) {
    var showTime by remember { mutableStateOf(false) }

    MapSheetScaffold(
        peekHeight = 400.dp,
        collapsedHeight = 110.dp,
        overlay = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                BackButton(vm::back)
                Spacer(Modifier.width(8.dp))
                FromToCard(vm, Modifier.weight(1f))
            }
        },
    ) {
        Row(Modifier.padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.public_transport), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = vm::planTrip) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
        }
        LazyRow(
            Modifier.padding(vertical = 8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = vm.timeMode != TimeMode.NOW,
                    onClick = { showTime = true },
                    leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                    trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                    label = { Text(if (vm.timeMode == TimeMode.NOW) stringResource(R.string.leave_now) else "${stringResource(vm.timeMode.label)} ${vm.tripTime.hhmm()}") },
                    colors = chipColors(),
                )
            }
            items(ModeGroup.entries.size) { i ->
                val g = ModeGroup.entries[i]
                FilterChip(
                    selected = g !in vm.excludedModes,
                    onClick = { vm.toggleMode(g) },
                    label = { Text(stringResource(g.label)) },
                    colors = chipColors(),
                )
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            when (val j = vm.journeys) {
                Load.Idle -> item {
                    EmptyState(if (vm.tripFrom == null) stringResource(R.string.choose_start_hint) else stringResource(R.string.choose_destination_hint))
                }
                Load.Loading -> item { LoadingState() }
                is Load.Error -> item { ErrorState(j.message, onRetry = vm::planTrip) }
                is Load.Ok -> {
                    if (j.data.isEmpty()) item { EmptyState(stringResource(R.string.no_connections)) }
                    itemsIndexed(j.data) { index, journey ->
                        JourneyRow(journey, onClick = { vm.openJourney(index) })
                        RowDivider()
                    }
                    if (j.data.isNotEmpty()) item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SecondaryAction(stringResource(R.string.earlier), { vm.shiftTrips(later = false) }, Modifier.weight(1f))
                            SecondaryAction(stringResource(R.string.later), { vm.shiftTrips(later = true) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            item { NavigationBarSpacer() }
        }
    }

    if (showTime) {
        TimeDialog(
            modes = TimeMode.entries,
            initialMode = vm.timeMode,
            initialTime = vm.tripTime,
            onDismiss = { showTime = false },
            onConfirm = { mode, time -> showTime = false; vm.setTripTime(mode, time) },
        )
    }
}

@Composable
private fun FromToCard(vm: AppViewModel, modifier: Modifier) {
    AppCard(modifier, corner = 24.dp) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                PointRow(
                    text = vm.tripFrom?.fullName ?: stringResource(R.string.choose_start),
                    start = true,
                    onClick = { vm.openSearch(SearchTarget.FROM) },
                )
                RowDivider(start = 32.dp)
                PointRow(
                    text = vm.tripTo?.fullName ?: stringResource(R.string.choose_destination),
                    start = false,
                    onClick = { vm.openSearch(SearchTarget.TO) },
                )
            }
            IconButton(onClick = vm::swapTrip) { Icon(Icons.Filled.SwapVert, stringResource(R.string.swap)) }
        }
    }
}

@Composable
private fun PointRow(text: String, start: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (start) {
            val c = MaterialTheme.colorScheme.onSurface
            Canvas(Modifier.size(18.dp)) { drawCircle(c, size.minDimension / 2.6f, style = Stroke(2.dp.toPx())) }
        } else {
            Icon(Icons.Filled.Place, null, tint = Late, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JourneyRow(journey: Journey, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(64.dp)) {
            val mins = journey.durationMinutes
            Text(
                if (mins >= 60) "${mins / 60}:${"%02d".format(mins % 60)}" else "$mins",
                style = MaterialTheme.typography.displaySmall,
            )
            Text(stringResource(if (mins >= 60) R.string.unit_h else R.string.unit_min), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${journey.departure.hhmm()} – ${journey.arrival.hhmm()}", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(6.dp))
                DelayText(journey.departureDelay?.takeIf { it != 0L })
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val parts = journey.legs.filter { !it.isWalk || it.durationSeconds >= 120 }
                parts.forEachIndexed { i, leg ->
                    if (i > 0) Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight, null,
                        Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (leg.isWalk) WalkChip(leg.durationSeconds / 60L)
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(modeIcon(leg.mode), null, Modifier.size(20.dp), tint = modeColor(leg.mode))
                        Spacer(Modifier.width(3.dp))
                        LineBadge(leg.line, leg.mode, compact = true)
                    }
                }
            }
            val first = journey.firstTransit
            if (first != null) {
                Text(
                    stringResource(R.string.departs_from, first.from.time.hhmm(), first.from.name),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                first.from.time?.let {
                    Text(relative(it), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Text(stringResource(R.string.walk_whole_way), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
