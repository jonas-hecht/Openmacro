/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.hecht.openmacro.R
import de.hecht.openmacro.model.Macro
import de.hecht.openmacro.privileged.ShizukuManager.Status

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroListScreen(
    macros: List<Macro>,
    shizukuStatus: Status,
    onToggle: (Macro, Boolean) -> Unit,
    onOpen: (Macro) -> Unit,
    onCreate: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.new_macro))
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            val needsShizuku = macros.any { m -> m.actions.any { it.requiresShizuku } }
            if (needsShizuku && shizukuStatus != Status.READY) {
                ShizukuCard(shizukuStatus, onRequestShizukuPermission, onOpenShizuku)
            }
            if (macros.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_macros),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                // Bottom padding keeps the last item clear of the FAB.
                LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(macros, key = { it.id }) { macro ->
                        MacroRow(macro, onToggle = { onToggle(macro, it) }, onClick = { onOpen(macro) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun MacroRow(macro: Macro, onToggle: (Boolean) -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(macro.name) },
        supportingContent = { Text(macro.summary(context)) },
        trailingContent = { Switch(checked = macro.enabled, onCheckedChange = onToggle) },
    )
}

@Composable
private fun ShizukuCard(status: Status, onRequestPermission: () -> Unit, onOpenShizuku: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val text = when (status) {
                Status.NOT_RUNNING -> R.string.shizuku_not_running
                Status.PERMISSION_REQUIRED -> R.string.shizuku_permission_required
                Status.PERMISSION_DENIED -> R.string.shizuku_permission_denied
                Status.READY -> return@Column
            }
            Text(stringResource(text), color = MaterialTheme.colorScheme.onErrorContainer)
            if (status == Status.PERMISSION_REQUIRED) {
                Button(onClick = onRequestPermission) { Text(stringResource(R.string.grant_permission)) }
            } else {
                Button(onClick = onOpenShizuku) { Text(stringResource(R.string.open_shizuku)) }
            }
        }
    }
}
