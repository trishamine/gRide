package org.devbucket.gRide.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.AutoAwesome
import org.devbucket.gRide.ui.Overlay
import org.devbucket.gRide.ui.UpdateState
import org.devbucket.gRide.ui.components.PrimaryAction
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.devbucket.gRide.data.ThemeMode
import org.devbucket.gRide.data.UiStyle
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.BottomBarSpacer
import org.devbucket.gRide.ui.components.AppCard
import org.devbucket.gRide.ui.components.SecondaryAction
import org.devbucket.gRide.ui.components.SegmentedChoice

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Settings",
            Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.headlineLarge,
        )

        SettingsCard("Design", "Choose how gRide looks.") {
            SegmentedChoice(
                options = listOf("Expressive", "Liquid Glass"),
                selected = settings.uiStyle.ordinal,
                onSelect = { i -> vm.store.updateSettings { it.copy(uiStyle = UiStyle.entries[i]) } },
            )
            Text(
                if (settings.uiStyle == UiStyle.EXPRESSIVE)
                    "Material 3 Expressive: bold shapes, springy motion and tonal surfaces."
                else
                    "Liquid Glass: floating, refracting glass panels over the map. Press a glass control to see it grow and light up.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard("Theme", null) {
            SegmentedChoice(
                options = ThemeMode.entries.map { it.label },
                selected = settings.themeMode.ordinal,
                onSelect = { i -> vm.store.updateSettings { it.copy(themeMode = ThemeMode.entries[i]) } },
            )
            SwitchRow(
                "Dynamic colours",
                "Use colours from your wallpaper",
                settings.dynamicColor,
            ) { v -> vm.store.updateSettings { it.copy(dynamicColor = v) } }
        }

        SettingsCard("Departures", null) {
            SwitchRow(
                "Auto-refresh",
                "Reload departures every 30 seconds",
                settings.autoRefresh,
            ) { v -> vm.store.updateSettings { it.copy(autoRefresh = v) } }
        }

        SettingsCard("Updates", "gRide ${vm.installedVersion}") {
            val status = when (val u = vm.update) {
                UpdateState.Checking -> "Checking…"
                UpdateState.UpToDate -> "You have the latest version."
                is UpdateState.Available -> "Version ${u.release.version} is available."
                is UpdateState.Downloading -> "Downloading ${(u.progress * 100).toInt()} %…"
                is UpdateState.NeedsPermission -> "Allow gRide to install updates to finish."
                is UpdateState.Error -> u.message
                UpdateState.Idle -> null
            }
            if (status != null) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vm.update is UpdateState.Available || vm.update is UpdateState.NeedsPermission) {
                PrimaryAction("Update now", vm::openUpdateDialog, icon = Icons.Filled.SystemUpdate)
            } else {
                SecondaryAction("Check for updates", { vm.checkForUpdates(manual = true) }, icon = Icons.Filled.Refresh)
            }
        }

        SettingsCard("History", "Removes recent stops and trips. Favourites are kept.") {
            SecondaryAction("Clear history", { vm.store.clearHistory() }, icon = Icons.Filled.DeleteSweep)
        }

        SettingsCard("About", null) {
            VersionText(vm, settings.secretUnlocked)
            Text(
                "Timetables and realtime data: Transitous (transitous.org), based on DELFI open data. Non-commercial use.\n" +
                    "Map: © OpenStreetMap contributors.\n" +
                    "Liquid Glass: Kyant0/AndroidLiquidGlass (backdrop).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BottomBarSpacer()
    }
}

/** Tap the version 5 times to unlock the secret settings. */
@Composable
private fun VersionText(vm: AppViewModel, unlocked: Boolean) {
    var taps by remember { mutableIntStateOf(0) }
    var lastTap by remember { mutableLongStateOf(0L) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "gRide ${vm.installedVersion}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.clickable(interactionSource = null, indication = null) {
                val now = System.currentTimeMillis()
                taps = if (now - lastTap < 1500) taps + 1 else 1
                lastTap = now
                if (taps >= 5 && !unlocked) {
                    vm.store.updateSettings { it.copy(secretUnlocked = true) }
                    taps = 0
                }
            },
        )
        if (!unlocked && taps in 2..4) {
            Text(
                "${5 - taps} more ${if (5 - taps == 1) "tap" else "taps"}…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(unlocked) {
            SecondaryAction("Secret settings", { vm.push(Overlay.Secret) }, icon = Icons.Filled.AutoAwesome)
        }
    }
}

@Composable
private fun SettingsCard(title: String, subtitle: String?, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle != null) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            thumbContent = if (checked) ({ Icon(Icons.Filled.Check, null, Modifier.padding(2.dp)) }) else null,
            colors = SwitchDefaults.colors(),
        )
    }
}
