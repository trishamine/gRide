package org.devbucket.gRide.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.PinDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.devbucket.gRide.data.LocationProvider
import org.devbucket.gRide.data.LocationType
import org.devbucket.gRide.data.Place
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.Load
import org.devbucket.gRide.ui.SearchTarget
import org.devbucket.gRide.ui.components.EmptyState
import org.devbucket.gRide.ui.components.ErrorState
import org.devbucket.gRide.ui.components.LoadingState
import org.devbucket.gRide.ui.components.BackButton
import org.devbucket.gRide.ui.components.RoundIconButton
import org.devbucket.gRide.ui.components.RowDivider
import org.devbucket.gRide.ui.components.SectionTitle
import org.devbucket.gRide.ui.components.StopSign
import org.devbucket.gRide.ui.glass.GlassSurface
import org.devbucket.gRide.ui.glass.isGlass

@Composable
fun SearchScreen(vm: AppViewModel, target: SearchTarget) {
    val focus = remember { FocusRequester() }
    val favorites by vm.store.favoriteStops.collectAsStateWithLifecycle()
    val history by vm.store.stopHistory.collectAsStateWithLifecycle()
    val trips by vm.store.trips.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.chooseCurrentLocation(target)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(vm::back)
            Spacer(Modifier.width(10.dp))
            SearchField(
                value = vm.searchQuery,
                onValueChange = { vm.onSearchQuery(it, target) },
                placeholder = when (target) {
                    SearchTarget.FROM -> "Where do you start?"
                    SearchTarget.TO -> "Where to?"
                    SearchTarget.STOP -> "Search stop"
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }

        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (vm.searchQuery.trim().length < 2) {
                item {
                    SearchRow(
                        icon = { Icon(Icons.Filled.MyLocation, null, tint = MaterialTheme.colorScheme.primary) },
                        title = if (target == SearchTarget.STOP) "Nearest stop" else "Current location",
                        subtitle = "Use your position",
                    ) {
                        if (LocationProvider.hasPermission(context)) vm.chooseCurrentLocation(target)
                        else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
                (vm.searchResults as? Load.Error)?.let { item { ErrorState(it.message, null) } }
                if (favorites.isNotEmpty()) {
                    item { SectionTitle("Favourite stops") }
                    items(favorites, key = { "f" + it.id }) { p -> PlaceRow(p, favorite = true) { vm.choose(p, target) } }
                }
                val recent = buildList {
                    addAll(history)
                    if (!target.stopsOnly) trips.forEach { add(it.from); add(it.to) }
                }.distinctBy { it.id }.filter { r -> favorites.none { it.id == r.id } }.take(12)
                if (recent.isNotEmpty()) {
                    item { SectionTitle("Recent") }
                    items(recent, key = { "r" + it.id }) { p -> PlaceRow(p, favorite = false, recent = true) { vm.choose(p, target) } }
                }
            } else when (val r = vm.searchResults) {
                is Load.Loading, Load.Idle -> item { LoadingState() }
                is Load.Error -> item { ErrorState(r.message, onRetry = { vm.onSearchQuery(vm.searchQuery, target) }) }
                is Load.Ok -> {
                    if (r.data.isEmpty()) item { EmptyState("Nothing found for “${vm.searchQuery}”.") }
                    items(r.data, key = { it.id }) { p -> PlaceRow(p, favorite = favorites.any { it.id == p.id }) { vm.choose(p, target) } }
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier) {
    val field: @Composable () -> Unit = {
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { }),
            trailingIcon = if (value.isNotEmpty()) ({
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Filled.Close, "Clear") }
            }) else null,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (isGlass) {
        GlassSurface(modifier.height(56.dp), contentAlignment = Alignment.CenterStart) { Box(Modifier.padding(horizontal = 6.dp)) { field() } }
    } else {
        Surface(modifier.height(56.dp), shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Box(Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.CenterStart) { field() }
        }
    }
}

@Composable
private fun PlaceRow(p: Place, favorite: Boolean, recent: Boolean = false, onClick: () -> Unit) {
    SearchRow(
        icon = {
            when (p.type) {
                LocationType.STOP -> StopSign(size = 36.dp, favorite = favorite)
                LocationType.ADDRESS -> Icon(Icons.Outlined.Home, null)
                LocationType.POI -> Icon(Icons.Outlined.PinDrop, null)
                else -> Icon(if (recent) Icons.Outlined.History else Icons.Outlined.LocationCity, null)
            }
        },
        title = p.name,
        subtitle = p.locality,
        onClick = onClick,
    )
}

@Composable
private fun SearchRow(icon: @Composable () -> Unit, title: String, subtitle: String, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) { icon() }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
        RowDivider(start = 82.dp)
    }
}
