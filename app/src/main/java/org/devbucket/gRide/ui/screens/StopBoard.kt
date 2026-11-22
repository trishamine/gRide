package org.devbucket.gRide.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.devbucket.gRide.R
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Directions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.devbucket.gRide.data.Departure
import org.devbucket.gRide.data.ModeGroup
import org.devbucket.gRide.data.Place
import org.devbucket.gRide.data.TransitZone
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.BottomBarSpacer
import org.devbucket.gRide.ui.Load
import org.devbucket.gRide.ui.TimeMode
import org.devbucket.gRide.ui.components.DelayText
import org.devbucket.gRide.ui.components.EmptyState
import org.devbucket.gRide.ui.components.ErrorState
import org.devbucket.gRide.ui.components.LineBadge
import org.devbucket.gRide.ui.components.LoadingState
import org.devbucket.gRide.ui.components.PrimaryAction
import org.devbucket.gRide.ui.components.BackButton
import org.devbucket.gRide.ui.components.RoundIconButton
import org.devbucket.gRide.ui.components.RowDivider
import org.devbucket.gRide.ui.components.SecondaryAction
import org.devbucket.gRide.ui.components.StopSign
import org.devbucket.gRide.ui.components.distanceText
import org.devbucket.gRide.ui.components.hhmm
import org.devbucket.gRide.ui.components.relative
import org.devbucket.gRide.ui.glass.isGlass
import org.devbucket.gRide.ui.theme.Late
import java.time.ZonedDateTime

/** Full-screen departure board (opened from the Departures tab). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopBoardScreen(vm: AppViewModel, stop: Place) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(vm::back)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.tab_departures), style = MaterialTheme.typography.headlineSmall)
        }
        PullToRefreshBox(
            isRefreshing = false,
            onRefresh = vm::refreshBoard,
            modifier = Modifier.weight(1f),
        ) {
            StopBoard(vm, stop, onClose = null, showMapButton = true)
        }
    }
}

/** Header, actions, filters and departures of a stop. Used in the map sheet and full screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StopBoard(vm: AppViewModel, stop: Place, onClose: (() -> Unit)?, showMapButton: Boolean, modifier: Modifier = Modifier) {
    val favorites by vm.store.favoriteStops.collectAsStateWithLifecycle()
    val favorite = favorites.any { it.id == stop.id }
    val board = if (vm.boardStop?.id == stop.id) vm.board else Load.Loading
    var hidden by remember(stop.id) { mutableStateOf(emptySet<ModeGroup>()) }
    var showTime by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val distance = vm.distanceTo(stop)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.padding(start = 24.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                StopSign(size = 48.dp, favorite = favorite)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(stop.name, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(stop.locality.ifBlank { null }, distance?.let { distanceText(it) }).joinToString(" · ")
                    if (sub.isNotBlank()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (onClose != null) RoundIconButton(Icons.Filled.Close, stringResource(R.string.close), onClick = onClose, size = 48.dp)
            }
        }
        val deps = (board as? Load.Ok)?.data.orEmpty()
        item {
            val lines = deps.distinctBy { it.line }.map { it.line to it.mode }
                .sortedWith(compareBy({ it.second.ordinal }, { it.first.padStart(4, '0') }))
            if (lines.isNotEmpty()) {
                FlowRow(
                    Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) { lines.forEach { (n, m) -> LineBadge(n, m, compact = true) } }
            } else Spacer(Modifier.height(12.dp))
        }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { PrimaryAction(stringResource(R.string.start_journey_here), { vm.planFrom(stop) }, icon = Icons.Filled.Directions) }
                item { SecondaryAction(stringResource(R.string.as_destination), { vm.planFrom(stop, asDestination = true) }, icon = Icons.Filled.Flag) }
                item {
                    SecondaryAction(
                        stringResource(if (favorite) R.string.unfavourite else R.string.favourite),
                        { vm.store.toggleFavorite(stop) },
                        icon = if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    )
                }
                item {
                    val followed by vm.store.followedStops.collectAsStateWithLifecycle()
                    val isFollowed = followed.any { it.id == stop.id }
                    SecondaryAction(
                        stringResource(if (isFollowed) R.string.following_news else R.string.follow_news),
                        { vm.store.toggleFollowStop(stop) },
                        icon = if (isFollowed) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                    )
                }
                if (showMapButton && stop.hasCoord) item { SecondaryAction(stringResource(R.string.map), { vm.openStopSheet(stop) }, icon = Icons.Filled.Map) }
                item {
                    val shareTitle = stringResource(R.string.share_title, stop.fullName)
                    SecondaryAction(stringResource(R.string.share), {
                        val text = shareTitle + (deps.take(3).joinToString("") {
                            "\n${it.time.hhmm()} ${it.line} → ${it.destination}"
                        })
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))
                    }, icon = Icons.Filled.Share)
                }
            }
        }
        item {
            val groups = deps.mapNotNull { ModeGroup.of(it.mode) }.distinct().sortedBy { it.ordinal }
            LazyRow(
                Modifier.padding(top = 16.dp, bottom = 4.dp),
                contentPadding = PaddingValues(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    FilterChip(
                        selected = vm.boardTime != null,
                        onClick = { showTime = true },
                        label = { Text(stringResource(R.string.departure_at, vm.boardTime?.hhmm() ?: stringResource(R.string.now_lower))) },
                        leadingIcon = { Icon(Icons.Filled.Schedule, null) },
                        colors = chipColors(),
                    )
                }
                if (groups.size > 1) items(groups) { g ->
                    FilterChip(
                        selected = g !in hidden,
                        onClick = { hidden = if (g in hidden) hidden - g else hidden + g },
                        label = { Text(stringResource(g.label)) },
                        colors = chipColors(),
                    )
                }
            }
        }
        when (board) {
            is Load.Loading, Load.Idle -> item { LoadingState() }
            is Load.Error -> item { ErrorState(board.message, onRetry = vm::refreshBoard) }
            is Load.Ok -> {
                val visible = deps.filter { ModeGroup.of(it.mode) !in hidden }
                if (visible.isEmpty()) item { EmptyState(stringResource(R.string.no_departures)) }
                items(visible, key = { "${it.line}|${it.destination}|${it.planned}|${it.platform}" }) { d ->
                    DepartureRow(d, onClick = { vm.openRun(d) })
                    RowDivider()
                }
                vm.boardUpdated?.let { t ->
                    item {
                        Text(
                            stringResource(R.string.updated_at, t.hhmm()) + if (vm.store.settings.value.autoRefresh && vm.boardTime == null) " " + stringResource(R.string.refreshes) else "",
                            Modifier.fillMaxWidth().padding(24.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        item { BottomBarSpacer() }
    }

    if (showTime) {
        TimeDialog(
            modes = listOf(TimeMode.NOW, TimeMode.DEPART),
            initialMode = if (vm.boardTime == null) TimeMode.NOW else TimeMode.DEPART,
            initialTime = vm.boardTime ?: ZonedDateTime.now(TransitZone),
            onDismiss = { showTime = false },
            onConfirm = { mode, time ->
                showTime = false
                vm.changeBoardTime(if (mode == TimeMode.NOW) null else time)
            },
        )
    }
}

@Composable
fun chipColors() = if (isGlass) FilterChipDefaults.filterChipColors(
    containerColor = Color.Transparent,
    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
) else FilterChipDefaults.filterChipColors()

@Composable
fun DepartureRow(d: Departure, onClick: (() -> Unit)? = null) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                d.planned.hhmm(),
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (d.cancelled) TextDecoration.LineThrough else null,
            )
            Spacer(Modifier.width(6.dp))
            if (d.cancelled) Text(stringResource(R.string.cancelled), color = Late, style = MaterialTheme.typography.titleSmall)
            else DelayText(d.delayMinutes)
            Spacer(Modifier.weight(1f))
            Text(relative(d.time), style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            LineBadge(d.line, d.mode)
            Spacer(Modifier.width(10.dp))
            Text(stringResource(d.mode.label), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(platformLabel(d.platform), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.direction, d.destination), style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
