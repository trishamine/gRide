package org.devbucket.gRide.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.devbucket.gRide.R
import org.devbucket.gRide.data.Disruption
import org.devbucket.gRide.data.Mode
import org.devbucket.gRide.data.Place
import org.devbucket.gRide.data.TransitZone
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.BottomBarSpacer
import org.devbucket.gRide.ui.Load
import org.devbucket.gRide.ui.Overlay
import org.devbucket.gRide.ui.components.AppCard
import org.devbucket.gRide.ui.components.EmptyState
import org.devbucket.gRide.ui.components.LineBadge
import org.devbucket.gRide.ui.components.LoadingState
import org.devbucket.gRide.ui.components.PrimaryAction
import org.devbucket.gRide.ui.components.SecondaryAction
import org.devbucket.gRide.ui.components.SectionTitle
import java.time.format.DateTimeFormatter

private val DayFormat = DateTimeFormatter.ofPattern("d.M.yyyy")

/** Does this news item concern a followed line or stop? */
fun Disruption.matches(lines: Set<String>, stops: List<Place>): Boolean =
    this.lines.any { it.number in lines } ||
        stopIds.any { id -> stops.any { s -> id == s.id || id.startsWith(s.id + ":") } }

/** Augsburg line numbers: 1–9 trams, 2-digit city buses, 3-digit regional buses. */
fun guessMode(number: String): Mode = when {
    number.toIntOrNull() in 1..9 -> Mode.TRAM
    number.toIntOrNull() in 10..99 -> Mode.CITY_BUS
    number.startsWith("AST") -> Mode.ON_DEMAND
    number.firstOrNull()?.isLetter() == true -> Mode.TRAIN
    else -> Mode.REGIONAL_BUS
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewsScreen(vm: AppViewModel) {
    val lines by vm.store.followedLines.collectAsStateWithLifecycle()
    val stops by vm.store.followedStops.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (vm.news !is Load.Ok) vm.loadNews() }
    val followsAnything = lines.isNotEmpty() || stops.isNotEmpty()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            stringResource(R.string.tab_news),
            Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
            style = MaterialTheme.typography.headlineLarge,
        )
        PullToRefreshBox(isRefreshing = false, onRefresh = vm::loadNews, modifier = Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // What you follow is saved on the device, so it stays here even when the news can't load.
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(10.dp))
                                Text(stringResource(R.string.you_follow), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                if (followsAnything) TextButton(onClick = { vm.push(Overlay.Follow) }) {
                                    Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.edit))
                                }
                            }
                            if (!followsAnything) {
                                Text(stringResource(R.string.follow_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                PrimaryAction(stringResource(R.string.choose_lines_stops), { vm.push(Overlay.Follow) })
                            } else {
                                if (lines.isNotEmpty()) FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    lines.sortedBy { it.padStart(4, '0') }.forEach { LineBadge(it, guessMode(it), compact = true) }
                                }
                                if (stops.isNotEmpty()) Text(
                                    stops.joinToString(" · ") { it.name },
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                val n = vm.news
                if (followsAnything) {
                    item { SectionTitle(stringResource(R.string.your_news), Modifier.padding(top = 4.dp)) }
                    when (n) {
                        is Load.Ok -> {
                            val mine = n.data.filter { it.matches(lines, stops) }
                            if (mine.isEmpty()) item { EmptyState(stringResource(R.string.no_followed_news)) }
                            items(mine, key = { "m" + it.id }) { DisruptionCard(it) }
                        }
                        is Load.Error -> item { NewsError(n.message, onRetry = null) }
                        else -> item { LoadingState() }
                    }
                }

                item {
                    Column(Modifier.padding(top = 12.dp)) {
                        SectionTitle(stringResource(R.string.city_news))
                        Text(
                            stringResource(R.string.city_news_sub),
                            Modifier.padding(horizontal = 24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                when (n) {
                    is Load.Ok -> {
                        val city = n.data.filter { it.provider == "AVG" }
                        if (city.isEmpty()) item { EmptyState(stringResource(R.string.no_city_news)) }
                        items(city, key = { "c" + it.id }) { DisruptionCard(it) }
                    }
                    is Load.Error -> item { NewsError(n.message, onRetry = vm::retryBahnland) }
                    else -> item { LoadingState() }
                }
                item { BottomBarSpacer() }
            }
        }
    }
}

/** e.g. "Server Error 429" with a "Try again" button below. */
@Composable
private fun NewsError(message: String, onRetry: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.error)
        Text(message, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (onRetry != null) SecondaryAction(stringResource(R.string.try_again), onRetry)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DisruptionCard(d: Disruption) {
    var expanded by remember { mutableStateOf(false) }
    val uri = LocalUriHandler.current
    AppCard(Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    when (d.kind) {
                        "stopBlocking" -> Icons.Filled.Block
                        "lineInfo" -> Icons.Filled.Timeline
                        else -> Icons.Filled.Construction
                    },
                    null,
                    tint = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    d.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = if (expanded) 6 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (d.lines.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    d.lines.take(if (expanded) 40 else 10).forEach { LineBadge(it.number, it.mode, compact = true) }
                    if (!expanded && d.lines.size > 10) Text("+${d.lines.size - 10}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val from = d.validFrom?.atZone(TransitZone)?.format(DayFormat)
            val to = d.validTo?.atZone(TransitZone)?.format(DayFormat)
            val validity = when {
                from != null && to != null -> stringResource(R.string.valid_from_until, from, to)
                from != null -> stringResource(R.string.valid_from, from)
                to != null -> stringResource(R.string.valid_until, to)
                else -> null
            }
            if (validity != null) Text(validity, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AnimatedVisibility(expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(AnnotatedString.fromHtml(d.html), style = MaterialTheme.typography.bodyMedium)
                    d.attachments.forEach { a ->
                        TextButton(onClick = { runCatching { uri.openUri(a.url) } }) {
                            Icon(Icons.Filled.Description, null)
                            Spacer(Modifier.width(6.dp))
                            Text(a.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
