package org.devbucket.gRide.ui.screens

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.devbucket.gRide.R
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
            stringResource(R.string.tab_settings),
            Modifier.padding(start = 8.dp, top = 16.dp, bottom = 4.dp),
            style = MaterialTheme.typography.headlineLarge,
        )

        SettingsCard(stringResource(R.string.language), null) {
            LanguagePicker()
        }

        SettingsCard(stringResource(R.string.design), stringResource(R.string.design_desc)) {
            SegmentedChoice(
                options = listOf(stringResource(R.string.style_expressive), stringResource(R.string.style_glass)),
                selected = settings.uiStyle.ordinal,
                onSelect = { i -> vm.store.updateSettings { it.copy(uiStyle = UiStyle.entries[i]) } },
            )
            Text(
                if (settings.uiStyle == UiStyle.EXPRESSIVE)
                    stringResource(R.string.expressive_desc)
                else
                    stringResource(R.string.glass_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard(stringResource(R.string.theme), null) {
            SegmentedChoice(
                options = ThemeMode.entries.map { stringResource(it.label) },
                selected = settings.themeMode.ordinal,
                onSelect = { i -> vm.store.updateSettings { it.copy(themeMode = ThemeMode.entries[i]) } },
            )
            SwitchRow(
                stringResource(R.string.dynamic_colours),
                stringResource(R.string.dynamic_colours_desc),
                settings.dynamicColor,
            ) { v -> vm.store.updateSettings { it.copy(dynamicColor = v) } }
        }

        SettingsCard(stringResource(R.string.tab_departures), null) {
            SwitchRow(
                stringResource(R.string.auto_refresh),
                stringResource(R.string.auto_refresh_desc),
                settings.autoRefresh,
            ) { v -> vm.store.updateSettings { it.copy(autoRefresh = v) } }
        }

        SettingsCard(stringResource(R.string.updates), stringResource(R.string.version, vm.installedVersion)) {
            val status = when (val u = vm.update) {
                UpdateState.Checking -> stringResource(R.string.checking)
                UpdateState.UpToDate -> stringResource(R.string.latest_version)
                is UpdateState.Available -> stringResource(R.string.version_available, u.release.version)
                is UpdateState.Downloading -> stringResource(R.string.downloading, (u.progress * 100).toInt())
                is UpdateState.NeedsPermission -> stringResource(R.string.allow_install_finish)
                is UpdateState.Error -> u.message
                UpdateState.Idle -> null
            }
            if (status != null) Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (vm.update is UpdateState.Available || vm.update is UpdateState.NeedsPermission) {
                PrimaryAction(stringResource(R.string.update_now), vm::openUpdateDialog, icon = Icons.Filled.SystemUpdate)
            } else {
                SecondaryAction(stringResource(R.string.check_updates), { vm.checkForUpdates(manual = true) }, icon = Icons.Filled.Refresh)
            }
        }

        SettingsCard(stringResource(R.string.history), stringResource(R.string.history_desc)) {
            SecondaryAction(stringResource(R.string.clear_history), { vm.store.clearHistory() }, icon = Icons.Filled.DeleteSweep)
        }

        SettingsCard(stringResource(R.string.about), null) {
            VersionText(vm, settings.secretUnlocked)
            Text(
                stringResource(R.string.about_text),
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
            stringResource(R.string.version, vm.installedVersion),
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
                pluralStringResource(R.plurals.taps_left, 5 - taps, 5 - taps),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(unlocked) {
            SecondaryAction(stringResource(R.string.secret_settings), { vm.push(Overlay.Secret) })
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
