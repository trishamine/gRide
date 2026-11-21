package org.devbucket.gRide.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.devbucket.gRide.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.devbucket.gRide.data.Place
import org.devbucket.gRide.data.TransitZone
import org.devbucket.gRide.ui.TimeMode
import org.devbucket.gRide.ui.components.AppCard
import org.devbucket.gRide.ui.components.SegmentedChoice
import org.devbucket.gRide.ui.components.SheetHandle
import org.devbucket.gRide.ui.components.StopSign
import org.devbucket.gRide.ui.components.distanceText
import org.devbucket.gRide.ui.glass.GlassSurface
import org.devbucket.gRide.ui.glass.isGlass
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** A stop as a list row: H sign, name, place, distance and a ⋯ menu. */
@Composable
fun StopRow(
    place: Place,
    favorite: Boolean,
    distanceMeters: Float?,
    onClick: () -> Unit,
    menu: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    AppCard(modifier.fillMaxWidth(), onClick = onClick, corner = 22.dp) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            StopSign(size = 40.dp, favorite = favorite)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOfNotNull(place.locality.ifBlank { null }, distanceMeters?.let { distanceText(it) }).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            if (menu.isNotEmpty()) MoreMenu(menu)
        }
    }
}

@Composable
fun MoreMenu(items: List<Pair<String, () -> Unit>>) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreHoriz, stringResource(R.string.more)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (label, action) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { open = false; action() })
            }
        }
    }
}

/** Picks "now" or a date + time (and for trips: depart / arrive). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(
    modes: List<TimeMode>,
    initialMode: TimeMode,
    initialTime: ZonedDateTime,
    onDismiss: () -> Unit,
    onConfirm: (TimeMode, ZonedDateTime) -> Unit,
) {
    var modeIndex by remember { mutableIntStateOf(modes.indexOf(initialMode).coerceAtLeast(0)) }
    val start = initialTime.withZoneSameInstant(TransitZone)
    val today = LocalDate.now(TransitZone)
    var dayOffset by remember { mutableIntStateOf((start.toLocalDate().toEpochDay() - today.toEpochDay()).toInt().coerceIn(0, 6)) }
    val picker = rememberTimePickerState(start.hour, start.minute, is24Hour = true)
    val dayFormat = DateTimeFormatter.ofPattern("EEE d.M.")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.when_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SegmentedChoice(
                    options = modes.map { if (it == TimeMode.NOW) stringResource(R.string.now) else stringResource(it.label) },
                    selected = modeIndex,
                    onSelect = { modeIndex = it },
                )
                if (modes[modeIndex] != TimeMode.NOW) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(7) { i ->
                            val label = when (i) {
                                0 -> stringResource(R.string.today)
                                1 -> stringResource(R.string.tomorrow)
                                else -> today.plusDays(i.toLong()).format(dayFormat)
                            }
                            FilterChip(selected = dayOffset == i, onClick = { dayOffset = i }, label = { Text(label) })
                        }
                    }
                    TimeInput(state = picker)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val mode = modes[modeIndex]
                val time = if (mode == TimeMode.NOW) ZonedDateTime.now(TransitZone)
                else today.plusDays(dayOffset.toLong()).atTime(picker.hour, picker.minute).atZone(TransitZone)
                onConfirm(mode, time)
            }) { Text(stringResource(R.string.done)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun platformLabel(raw: String): String {
    val clean = raw.replace("Bstg.", "").replace("Gleis", "").replace("Steig", "").trim()
    return if (clean.isBlank()) "" else stringResource(R.string.platform, clean)
}
