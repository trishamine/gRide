package org.devbucket.gRide.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.BottomBarSpacer
import org.devbucket.gRide.ui.components.AppCard
import org.devbucket.gRide.ui.components.BackButton
import org.devbucket.gRide.ui.components.PrimaryAction
import org.devbucket.gRide.ui.components.SecondaryAction

/** Hidden settings, unlocked by tapping the version in Settings 5 times. */
@Composable
fun SecretScreen(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(vm::back)
            Spacer(Modifier.width(16.dp))
            Text("Secret settings", style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Setup", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Play the welcome animation and setup again. Your favourites and history stay.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PrimaryAction("Replay setup", vm::replaySetup, icon = Icons.Filled.Replay)
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Hide this menu", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Removes the button from Settings. Tap the version 5 times to get it back.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SecondaryAction("Hide secret settings", {
                        vm.store.updateSettings { it.copy(secretUnlocked = false) }
                        vm.back()
                    }, icon = Icons.Filled.VisibilityOff)
                }
            }
            BottomBarSpacer()
        }
    }
}
