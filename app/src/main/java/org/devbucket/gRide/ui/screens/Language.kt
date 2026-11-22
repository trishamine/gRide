package org.devbucket.gRide.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.devbucket.gRide.R
import org.devbucket.gRide.data.AppLanguage

/**
 * Language dropdown. Every language is written in itself (English, Deutsch, Русский), so it can be
 * found whatever language the app shows right now. The app switches language in place (the activity
 * handles the locale change itself, see AndroidManifest), so the screen does not flash.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguagePicker(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var current by remember { mutableStateOf(AppLanguage.current(context)) }
    var expanded by remember { mutableStateOf(false) }
    val systemName = stringResource(R.string.language_system)
    fun nameOf(tag: String) = AppLanguage.options.firstOrNull { it.first == tag }?.second ?: systemName

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = nameOf(current),
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text(stringResource(R.string.language)) },
            leadingIcon = { Icon(Icons.Filled.Language, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AppLanguage.options.forEach { (tag, _) ->
                DropdownMenuItem(
                    text = { Text(nameOf(tag), style = MaterialTheme.typography.bodyLarge) },
                    trailingIcon = if (tag == current) ({ Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) }) else null,
                    onClick = {
                        expanded = false
                        current = tag
                        AppLanguage.set(context, tag)
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}
