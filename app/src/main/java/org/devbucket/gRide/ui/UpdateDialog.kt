package org.devbucket.gRide.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MobileFriendly
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.devbucket.gRide.R
import org.devbucket.gRide.ui.components.rememberPressMorph

/** Compact "gRide x.y.z is here" dialog: icon, title, and Update / Later side by side. */
@OptIn(ExperimentalMaterial3Api::class)
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

    BasicAlertDialog(onDismissRequest = { if (!downloading) vm.dismissUpdate() }) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.Outlined.MobileFriendly, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
                Text(
                    if (release != null) stringResource(R.string.update_title, release.version) else stringResource(R.string.update),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                )
                when (state) {
                    is UpdateState.Downloading -> {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        )
                        Text(
                            stringResource(R.string.downloading, (state.progress * 100).toInt()),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is UpdateState.NeedsPermission -> Text(
                        stringResource(R.string.install_permission_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    is UpdateState.Error -> Text(
                        state.message,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                    else -> Unit
                }
                if (!downloading) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (release != null) {
                            val source = remember { MutableInteractionSource() }
                            val p = rememberPressMorph(source)
                            Button(
                                onClick = vm::startUpdate,
                                modifier = Modifier.weight(1f).height(56.dp),
                                shape = RoundedCornerShape(28.dp - 16.dp * p),
                                interactionSource = source,
                            ) {
                                Text(
                                    stringResource(if (state is UpdateState.NeedsPermission) R.string.install else R.string.update),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        }
                        val source = remember { MutableInteractionSource() }
                        val p = rememberPressMorph(source)
                        FilledTonalButton(
                            onClick = vm::dismissUpdate,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = RoundedCornerShape(28.dp - 16.dp * p),
                            interactionSource = source,
                        ) {
                            Text(stringResource(R.string.later), style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
