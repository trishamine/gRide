package org.devbucket.gRide.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.devbucket.gRide.data.LocationProvider
import org.devbucket.gRide.data.Place
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.BottomBarSpacer
import org.devbucket.gRide.ui.Load
import org.devbucket.gRide.ui.components.EmptyState
import org.devbucket.gRide.ui.components.ErrorState
import org.devbucket.gRide.ui.components.LoadingState
import org.devbucket.gRide.ui.components.PrimaryAction
import org.devbucket.gRide.ui.components.SearchPill
import org.devbucket.gRide.ui.components.SectionTitle
import org.devbucket.gRide.ui.components.SegmentedChoice
import org.devbucket.gRide.ui.SearchTarget

@Composable
fun DeparturesScreen(vm: AppViewModel) {
    var section by rememberSaveable { mutableIntStateOf(0) }
    val favorites by vm.store.favoriteStops.collectAsStateWithLifecycle()
    val history by vm.store.stopHistory.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.loadNearby()
    }
    LaunchedEffect(section) {
        if (section == 1 && vm.nearby !is Load.Ok && LocationProvider.hasPermission(context)) vm.loadNearby()
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(
            "Departures",
            Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.headlineLarge,
        )
        SearchPill(
            text = "Search stop",
            icon = Icons.Filled.Search,
            leading = Icons.Filled.Place,
            onClick = { vm.openSearch(SearchTarget.STOP) },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
        SegmentedChoice(
            options = listOf("Favourites & History", "Stops nearby"),
            selected = section,
            onSelect = { section = it },
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (section == 0) {
                if (favorites.isEmpty() && history.isEmpty()) {
                    item { EmptyState("Search for a stop to see its departures. Your favourite and recent stops will be listed here.") }
                }
                items(favorites, key = { "f" + it.id }) { stop ->
                    StopRow(
                        stop, favorite = true, distanceMeters = vm.distanceTo(stop),
                        onClick = { vm.openStopBoard(stop) },
                        menu = listOf(
                            "Remove from favourites" to { vm.store.toggleFavorite(stop) },
                            "Show on map" to { vm.openStopSheet(stop) },
                        ),
                    )
                }
                val recent = history.filter { h -> favorites.none { it.id == h.id } }
                if (recent.isNotEmpty()) item { SectionTitle("Recent", Modifier.padding(top = 8.dp)) }
                items(recent, key = { "h" + it.id }) { stop ->
                    StopRow(
                        stop, favorite = false, distanceMeters = vm.distanceTo(stop),
                        onClick = { vm.openStopBoard(stop) },
                        menu = listOf(
                            "Add to favourites" to { vm.store.toggleFavorite(stop) },
                            "Remove from history" to { vm.store.removeStopFromHistory(stop) },
                            "Show on map" to { vm.openStopSheet(stop) },
                        ),
                    )
                }
            } else {
                if (!LocationProvider.hasPermission(context)) {
                    item {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            EmptyState("Allow location access to see the stops around you.")
                            PrimaryAction("Allow location", {
                                permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            }, icon = Icons.Filled.MyLocation)
                        }
                    }
                } else when (val n = vm.nearby) {
                    is Load.Loading, Load.Idle -> item { LoadingState() }
                    is Load.Error -> item { ErrorState(n.message, onRetry = vm::loadNearby) }
                    is Load.Ok -> {
                        if (n.data.isEmpty()) item { EmptyState("No stops within 1.2 km.") }
                        items(n.data, key = { "n" + it.id }) { stop: Place ->
                            StopRow(
                                stop, favorite = favorites.any { it.id == stop.id },
                                distanceMeters = stop.distanceMeters?.toFloat() ?: vm.distanceTo(stop),
                                onClick = { vm.openStopBoard(stop) },
                                menu = listOf(
                                    (if (favorites.any { it.id == stop.id }) "Remove from favourites" else "Add to favourites") to { vm.store.toggleFavorite(stop) },
                                    "Show on map" to { vm.openStopSheet(stop) },
                                ),
                            )
                        }
                    }
                }
            }
            item { BottomBarSpacer() }
        }
    }
}
