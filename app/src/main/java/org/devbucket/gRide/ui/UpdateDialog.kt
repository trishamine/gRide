package org.devbucket.gRide.ui

import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.devbucket.gRide.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.devbucket.gRide.ui.components.PrimaryAction

/** "A new version is available" with release notes, download progress and install. */
@Composable
fun UpdateDialog(vm: AppViewModel) {
    val state = vm.update
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.NeedsPermission -> state.release
        else -> null
    }
    val downloading = state is UpdateState.Downloading

    AlertDialog(
        onDismissRequest = { if (!downloading) vm.dismissUpdate() },
        icon = { Icon(Icons.Filled.SystemUpdate, null) },
        title = { Text(if (release != null) stringResource(R.string.update_title, release.version) else stringResource(R.string.update)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.you_have_version, vm.installedVersion),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!release?.notes.isNullOrBlank()) {
                    Text(
                        release!!.notes.replace("\r", ""),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    )
                }
                when (state) {
                    is UpdateState.Downloading -> {
                        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.downloading, (state.progress * 100).toInt()), style = MaterialTheme.typography.labelLarge)
                    }
                    is UpdateState.NeedsPermission -> Text(
                        stringResource(R.string.install_permission_hint),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is UpdateState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            if (!downloading && release != null) {
                PrimaryAction(stringResource(if (state is UpdateState.NeedsPermission) R.string.install else R.string.update), vm::startUpdate)
            }
        },
        dismissButton = {
            if (!downloading) {
                if (state is UpdateState.Available) TextButton(onClick = vm::skipUpdate) { Text(stringResource(R.string.skip_version)) }
                TextButton(onClick = vm::dismissUpdate) { Text(stringResource(R.string.later)) }
            }
        },
    )
}
