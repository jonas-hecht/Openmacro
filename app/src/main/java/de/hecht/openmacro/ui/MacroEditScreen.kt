/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro.ui

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.hecht.openmacro.R
import de.hecht.openmacro.data.MacroRepository
import de.hecht.openmacro.model.Macro
import de.hecht.openmacro.model.Trigger

private enum class EditDialog { NONE, TRIGGER_TYPE, BLUETOOTH_DEVICE, ACTION, DELETE }

private val MacroSaver = Saver<Macro, String>(
    save = { MacroRepository.json.encodeToString(Macro.serializer(), it) },
    restore = { MacroRepository.json.decodeFromString(Macro.serializer(), it) },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MacroEditScreen(
    initial: Macro,
    isNew: Boolean,
    onSave: (Macro) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var draft by rememberSaveable(stateSaver = MacroSaver) { mutableStateOf(initial) }
    var dialog by rememberSaveable { mutableStateOf(EditDialog.NONE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isNew) R.string.new_macro else R.string.edit_macro)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(onClick = { dialog = EditDialog.DELETE }) {
                            Icon(painterResource(R.drawable.ic_delete), stringResource(R.string.delete))
                        }
                    }
                    TextButton(
                        enabled = draft.isComplete,
                        onClick = {
                            val name = draft.name.trim().ifEmpty { draft.trigger!!.describe(context) }
                            onSave(draft.copy(name = name))
                        },
                    ) { Text(stringResource(R.string.save)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text(stringResource(R.string.macro_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle(R.string.trigger)
            when (val trigger = draft.trigger) {
                null -> OutlinedButton(onClick = { dialog = EditDialog.TRIGGER_TYPE }) {
                    Text(stringResource(R.string.add_trigger))
                }
                is Trigger.BluetoothConnected -> RemovableItem(
                    title = stringResource(R.string.trigger_bluetooth_connected),
                    subtitle = "${trigger.deviceName} (${trigger.address})",
                    onClick = { dialog = EditDialog.BLUETOOTH_DEVICE },
                    onRemove = { draft = draft.copy(trigger = null) },
                )
            }

            SectionTitle(R.string.actions)
            draft.actions.forEachIndexed { index, action ->
                RemovableItem(
                    title = stringResource(action.label),
                    subtitle = if (action.requiresShizuku) stringResource(R.string.requires_shizuku) else null,
                    onRemove = {
                        draft = draft.copy(actions = draft.actions.filterIndexed { i, _ -> i != index })
                    },
                )
            }
            if (availableActions.any { it !in draft.actions }) {
                OutlinedButton(onClick = { dialog = EditDialog.ACTION }) {
                    Text(stringResource(R.string.add_action))
                }
            }
        }
    }

    val dismiss = { dialog = EditDialog.NONE }
    when (dialog) {
        EditDialog.NONE -> Unit
        EditDialog.TRIGGER_TYPE -> ChoiceDialog(
            title = stringResource(R.string.add_trigger),
            options = listOf(stringResource(R.string.trigger_bluetooth_connected)),
            onPick = { dialog = EditDialog.BLUETOOTH_DEVICE },
            onDismiss = dismiss,
        )
        EditDialog.BLUETOOTH_DEVICE -> BluetoothDeviceDialog(
            onPick = { trigger ->
                draft = draft.copy(trigger = trigger)
                dismiss()
            },
            onDismiss = dismiss,
        )
        EditDialog.ACTION -> {
            val options = availableActions.filter { it !in draft.actions }
            ChoiceDialog(
                title = stringResource(R.string.add_action),
                options = options.map { stringResource(it.label) },
                onPick = { index ->
                    draft = draft.copy(actions = draft.actions + options[index])
                    dismiss()
                },
                onDismiss = dismiss,
            )
        }
        EditDialog.DELETE -> AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.delete_macro_question)) },
            confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun SectionTitle(text: Int) {
    Text(
        stringResource(text),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun RemovableItem(
    title: String,
    subtitle: String?,
    onRemove: () -> Unit,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier,
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            IconButton(onClick = onRemove) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.remove))
            }
        },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<String>,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { index, option ->
                    ListItem(
                        modifier = Modifier.clickable { onPick(index) },
                        colors = dialogItemColors(),
                        headlineContent = { Text(option) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun dialogItemColors() = ListItemDefaults.colors(containerColor = Color.Transparent)

private fun hasBluetoothPermission(context: Context) =
    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
        PackageManager.PERMISSION_GRANTED

/** Lets the user pick one of the paired Bluetooth devices. */
@Composable
private fun BluetoothDeviceDialog(onPick: (Trigger.BluetoothConnected) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasBluetoothPermission(context)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.BLUETOOTH_CONNECT)
    }

    val devices = remember(granted) {
        if (!granted) return@remember emptyList()
        try {
            context.getSystemService(BluetoothManager::class.java).adapter
                ?.bondedDevices.orEmpty()
                .map { Trigger.BluetoothConnected(it.address, it.name ?: it.address) }
                .sortedBy { it.deviceName.lowercase() }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_bluetooth_device)) },
        text = {
            when {
                !granted -> Text(stringResource(R.string.bluetooth_permission_required))
                devices.isEmpty() -> Text(stringResource(R.string.no_paired_devices))
                else -> LazyColumn {
                    items(devices, key = { it.address }) { device ->
                        ListItem(
                            modifier = Modifier.clickable { onPick(device) },
                            colors = dialogItemColors(),
                            headlineContent = { Text(device.deviceName) },
                            supportingContent = { Text(device.address) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!granted) {
                TextButton(onClick = { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
                    Text(stringResource(R.string.grant_permission))
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
