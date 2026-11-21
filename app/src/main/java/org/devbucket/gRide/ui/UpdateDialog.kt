package org.devbucket.gRide.ui

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
        title = { Text(if (release != null) "gRide ${release.version} is here" else "Update") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "You have version ${vm.installedVersion}.",
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
                        Text("Downloading… ${(state.progress * 100).toInt()} %", style = MaterialTheme.typography.labelLarge)
                    }
                    is UpdateState.NeedsPermission -> Text(
                        "Android asks you to allow gRide to install apps. Turn it on, then come back — the update installs right away.",
                        color = MaterialTheme.colorScheme.primary,
                    )
                    is UpdateState.Error -> Text(state.message, color = MaterialTheme.colorScheme.error)
                    else -> Unit
                }
            }
        },
        confirmButton = {
            if (!downloading && release != null) {
                PrimaryAction(if (state is UpdateState.NeedsPermission) "Install" else "Update", vm::startUpdate)
            }
        },
        dismissButton = {
            if (!downloading) {
                if (state is UpdateState.Available) TextButton(onClick = vm::skipUpdate) { Text("Skip version") }
                TextButton(onClick = vm::dismissUpdate) { Text("Later") }
            }
        },
    )
}
