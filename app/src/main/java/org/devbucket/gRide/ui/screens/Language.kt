package org.devbucket.gRide.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.devbucket.gRide.R
import org.devbucket.gRide.data.AppLanguage
import org.devbucket.gRide.ui.components.AppCard

/**
 * Pick the app language. Each language is written in itself (English, Deutsch, Русский), so it can be
 * found whatever language the app currently shows. Changing it restarts the screen in the new language.
 */
@Composable
fun LanguagePicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppLanguage.current(context)) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AppLanguage.options.forEach { (tag, name) ->
            val selected = tag == current
            val border by animateDpAsState(if (selected) 2.dp else 0.dp, label = "langBorder")
            AppCard(
                Modifier
                    .fillMaxWidth()
                    .border(border, MaterialTheme.colorScheme.primary, RoundedCornerShape(22.dp)),
                corner = 22.dp,
                onClick = {
                    current = tag
                    AppLanguage.set(context, tag)
                },
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        name ?: stringResource(R.string.language_system),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    else Icon(Icons.Outlined.Circle, null, tint = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
