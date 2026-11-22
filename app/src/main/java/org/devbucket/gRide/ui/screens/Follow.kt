package org.devbucket.gRide.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.devbucket.gRide.R
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.BottomBarSpacer
import org.devbucket.gRide.ui.Load
import org.devbucket.gRide.ui.SearchTarget
import org.devbucket.gRide.ui.components.AppCard
import org.devbucket.gRide.ui.components.BackButton
import org.devbucket.gRide.ui.components.LineBadge
import org.devbucket.gRide.ui.components.SecondaryAction
import org.devbucket.gRide.ui.components.StopSign

/** Choose the lines and stops whose traffic news appear in "Your news". Saved on the device. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FollowScreen(vm: AppViewModel) {
    val lines by vm.store.followedLines.collectAsStateWithLifecycle()
    val stops by vm.store.followedStops.collectAsStateWithLifecycle()
    val favorites by vm.store.favoriteStops.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(Unit) { if (vm.news !is Load.Ok) vm.loadNews() }
    fun add() {
        if (input.isNotBlank()) vm.store.toggleLine(input.trim().let { if (it.startsWith("ast", true)) it.uppercase() else it })
        input = ""
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(vm::back)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.follow), style = MaterialTheme.typography.headlineSmall)
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.lines), style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = input,
                                onValueChange = { input = it.take(8) },
                                label = { Text(stringResource(R.string.line_number_hint)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { add() }),
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = ::add) { Icon(Icons.Filled.Add, stringResource(R.string.add_line)) }
                        }
                        if (lines.isNotEmpty()) {
                            Text(stringResource(R.string.following), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                lines.sortedBy { it.padStart(4, '0') }.forEach { n ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { vm.store.toggleLine(n) },
                                        label = { LineBadge(n, guessMode(n), compact = true) },
                                        trailingIcon = { Icon(Icons.Filled.Close, stringResource(R.string.stop_following), Modifier.size(18.dp)) },
                                        colors = chipColors(),
                                    )
                                }
                            }
                        }
                        val withNews = (vm.news as? Load.Ok)?.data.orEmpty().flatMap { it.lines }
                            .distinctBy { it.number }.filter { it.number !in lines }
                            .sortedBy { it.number.padStart(4, '0') }
                        if (withNews.isNotEmpty()) {
                            Text(stringResource(R.string.lines_with_news), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                withNews.forEach { l ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { vm.store.toggleLine(l.number) },
                                        label = { LineBadge(l.number, l.mode, compact = true) },
                                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                                        colors = chipColors(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.stops_title), style = MaterialTheme.typography.titleLarge)
                        stops.forEach { s ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StopSign(size = 32.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (s.locality.isNotBlank()) Text(s.locality, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { vm.store.toggleFollowStop(s) }) { Icon(Icons.Filled.Close, stringResource(R.string.stop_following)) }
                            }
                        }
                        SecondaryAction(stringResource(R.string.add_stop), { vm.openSearch(SearchTarget.FOLLOW) }, icon = Icons.Filled.Search)
                        val suggestions = favorites.filter { f -> stops.none { it.id == f.id } }
                        if (suggestions.isNotEmpty()) {
                            Text(stringResource(R.string.favourite_stops), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                suggestions.forEach { f ->
                                    FilterChip(
                                        selected = false,
                                        onClick = { vm.store.toggleFollowStop(f) },
                                        label = { Text(f.name) },
                                        leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) },
                                        colors = chipColors(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { BottomBarSpacer() }
        }
    }
}
