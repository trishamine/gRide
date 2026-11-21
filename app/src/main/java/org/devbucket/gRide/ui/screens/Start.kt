package org.devbucket.gRide.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.devbucket.gRide.R
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.devbucket.gRide.data.LocationProvider
import org.devbucket.gRide.data.TripEntry
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.BottomBarSpacer
import org.devbucket.gRide.ui.LocalBottomBarHeight
import org.devbucket.gRide.ui.components.EmptyState
import org.devbucket.gRide.ui.components.RoundIconButton
import org.devbucket.gRide.ui.components.RowDivider
import org.devbucket.gRide.ui.components.SearchPill
import org.devbucket.gRide.ui.components.SectionTitle
import org.devbucket.gRide.ui.components.StopSign
import org.devbucket.gRide.ui.glass.GlassSurface
import org.devbucket.gRide.ui.glass.isGlass

@Composable
fun StartScreen(vm: AppViewModel) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.locateMe()
    }
    LaunchedEffect(Unit) {
        if (!asked && !LocationProvider.hasPermission(context)) {
            asked = true
            permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    MapSheetScaffold(
        peekHeight = 300.dp + LocalBottomBarHeight.current,
        collapsedHeight = 84.dp + LocalBottomBarHeight.current,
        resetKey = vm.sheetStop?.id,
        overlay = {
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Top,
            ) {
                AppTitleChip()
                Spacer(Modifier.weight(1f))
                RoundIconButton(Icons.Filled.MyLocation, stringResource(R.string.my_location), onClick = {
                    if (LocationProvider.hasPermission(context)) vm.locateMe()
                    else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                })
            }
        },
    ) {
        val stop = vm.sheetStop
        if (stop != null) {
            StopBoard(vm, stop, onClose = vm::closeStopSheet, showMapButton = false, modifier = Modifier.weight(1f))
        } else {
            HomeSheet(vm, Modifier.weight(1f))
        }
    }
}

@Composable
private fun AppTitleChip() {
    val content: @Composable () -> Unit = {
        Text(
            "gRide",
            Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
        )
    }
    if (isGlass) GlassSurface { content() }
    else Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 3.dp) { content() }
}

@Composable
private fun HomeSheet(vm: AppViewModel, modifier: Modifier) {
    val trips by vm.store.trips.collectAsStateWithLifecycle()
    val favorites by vm.store.favoriteStops.collectAsStateWithLifecycle()

    LazyColumn(modifier, contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text(
                stringResource(R.string.where_to),
                Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                style = MaterialTheme.typography.headlineLarge,
            )
        }
        item {
            SearchPill(
                text = stringResource(R.string.plan_a_trip),
                icon = Icons.Filled.Search,
                leading = Icons.AutoMirrored.Filled.ArrowForward,
                onClick = vm::startTripPlanning,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        if (favorites.isNotEmpty()) {
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(favorites, key = { it.id }) { stop ->
                        FavoriteStopChip(stop.name) { vm.openStopSheet(stop) }
                    }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.favourites_history), Modifier.padding(top = 8.dp)) }
        if (trips.isEmpty()) {
            item { EmptyState(stringResource(R.string.trips_empty)) }
        }
        items(trips, key = { it.key }) { entry ->
            TripRow(
                entry,
                onClick = { vm.planSaved(entry) },
                menu = listOf(
                    stringResource(R.string.plan_trip) to { vm.planSaved(entry) },
                    stringResource(R.string.return_trip) to { vm.planSaved(entry, swap = true) },
                    (if (entry.favorite) stringResource(R.string.remove_favourite) else stringResource(R.string.add_favourite)) to { vm.store.toggleTripFavorite(entry) },
                    stringResource(R.string.delete) to { vm.store.removeTrip(entry) },
                ),
            )
            RowDivider()
        }
        item { BottomBarSpacer() }
    }
}

@Composable
private fun FavoriteStopChip(name: String, onClick: () -> Unit) {
    val content: @Composable () -> Unit = {
        Row(Modifier.padding(start = 8.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            StopSign(size = 28.dp)
            Spacer(Modifier.width(8.dp))
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
        }
    }
    if (isGlass) GlassSurface(onClick = onClick) { content() }
    else Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) { content() }
}

@Composable
fun TripRow(entry: TripEntry, onClick: () -> Unit, menu: List<Pair<String, () -> Unit>>) {
    val line = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 24.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.width(14.dp).fillMaxHeight().padding(vertical = 10.dp)) {
            val x = size.width / 2
            val r = 5.dp.toPx()
            drawCircle(line, r, Offset(x, r), style = Stroke(2.dp.toPx()))
            drawLine(line, Offset(x, r * 2 + 2.dp.toPx()), Offset(x, size.height - r * 2 - 2.dp.toPx()), 2.dp.toPx())
            drawCircle(line, r, Offset(x, size.height - r))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(entry.from.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(entry.to.fullName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (entry.favorite) Icon(Icons.Filled.Star, stringResource(R.string.favourite), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        MoreMenu(menu)
    }
}
