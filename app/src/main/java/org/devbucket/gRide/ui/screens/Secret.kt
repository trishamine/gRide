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
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.devbucket.gRide.R
import org.devbucket.gRide.data.ApiChoice
import org.devbucket.gRide.data.DataSource
import org.devbucket.gRide.ui.AppViewModel
import org.devbucket.gRide.ui.BottomBarSpacer
import org.devbucket.gRide.ui.UpdateState
import org.devbucket.gRide.ui.components.AppCard
import org.devbucket.gRide.ui.components.BackButton
import org.devbucket.gRide.ui.components.PrimaryAction
import org.devbucket.gRide.ui.components.SecondaryAction
import org.devbucket.gRide.ui.components.SegmentedChoice

/** Hidden settings, unlocked by tapping the version in Settings 5 times. */
@Composable
fun SecretScreen(vm: AppViewModel) {
    val settings by vm.store.settings.collectAsStateWithLifecycle()
    val bahnland by DataSource.bahnlandAvailable.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            BackButton(vm::back)
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.secret_settings), style = MaterialTheme.typography.headlineSmall)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SecretCard(stringResource(R.string.data_source), stringResource(R.string.data_source_desc)) {
                SegmentedChoice(
                    options = listOf(
                        stringResource(R.string.api_auto),
                        stringResource(R.string.api_bahnland),
                        stringResource(R.string.api_transitous),
                    ),
                    selected = settings.apiChoice.ordinal,
                    onSelect = { vm.setApiChoice(ApiChoice.entries[it]) },
                )
                Text(
                    stringResource(
                        when (bahnland) {
                            true -> R.string.bahnland_ok
                            false -> R.string.bahnland_down
                            null -> R.string.bahnland_unknown
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (bahnland == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SecretCard(stringResource(R.string.test_update), stringResource(R.string.test_update_desc)) {
                PrimaryAction(stringResource(R.string.test_update_button), vm::fakeUpdate, icon = Icons.Filled.SystemUpdate)
                when (val u = vm.update) {
                    UpdateState.Checking -> Text(stringResource(R.string.checking), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    is UpdateState.Error -> Text(u.message, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
            }

            SecretCard(stringResource(R.string.setup), stringResource(R.string.replay_setup_desc)) {
                PrimaryAction(stringResource(R.string.replay_setup), vm::replaySetup, icon = Icons.Filled.Replay)
            }

            SecretCard(stringResource(R.string.hide_menu), stringResource(R.string.hide_menu_desc)) {
                SecondaryAction(stringResource(R.string.hide_secret), {
                    vm.store.updateSettings { it.copy(secretUnlocked = false) }
                    vm.back()
                }, icon = Icons.Filled.VisibilityOff)
            }
            BottomBarSpacer()
        }
    }
}

@Composable
private fun SecretCard(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
