/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import de.hecht.openmacro.data.MacroRepository
import de.hecht.openmacro.engine.MacroService
import de.hecht.openmacro.model.Macro
import de.hecht.openmacro.privileged.ShizukuManager
import de.hecht.openmacro.ui.MacroEditScreen
import de.hecht.openmacro.ui.MacroListScreen
import de.hecht.openmacro.ui.theme.OpenmacroTheme

private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

class MainActivity : ComponentActivity() {

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            OpenmacroTheme { OpenmacroNavigation(onOpenShizuku = ::openShizuku) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Permission may have been changed in the Shizuku app meanwhile.
        ShizukuManager.refresh()
    }

    private fun openShizuku() {
        packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)?.let { startActivity(it) }
    }
}

/** `null` shows the macro list, [NEW_MACRO] the editor for a new macro, otherwise a macro id. */
private const val NEW_MACRO = ""

@Composable
private fun OpenmacroNavigation(onOpenShizuku: () -> Unit) {
    val context = LocalContext.current
    val macros by MacroRepository.macros.collectAsState()
    val shizukuStatus by ShizukuManager.status.collectAsState()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    // Start or stop the background service whenever macros change.
    LaunchedEffect(macros) { MacroService.sync(context) }

    val editingId = editing
    if (editingId == null) {
        MacroListScreen(
            macros = macros,
            shizukuStatus = shizukuStatus,
            onToggle = { macro, enabled -> MacroRepository.setEnabled(macro.id, enabled) },
            onOpen = { editing = it.id },
            onCreate = { editing = NEW_MACRO },
            onRequestShizukuPermission = ShizukuManager::requestPermission,
            onOpenShizuku = onOpenShizuku,
        )
        return
    }

    BackHandler { editing = null }
    val isNew = editingId == NEW_MACRO
    val initial = remember(editingId) { if (isNew) Macro() else MacroRepository.get(editingId) }
    if (initial == null) {
        editing = null
        return
    }
    MacroEditScreen(
        initial = initial,
        isNew = isNew,
        onSave = { macro ->
            MacroRepository.upsert(macro)
            if (macro.actions.any { it.requiresShizuku } &&
                shizukuStatus == ShizukuManager.Status.PERMISSION_REQUIRED
            ) {
                ShizukuManager.requestPermission()
            }
            editing = null
        },
        onDelete = {
            MacroRepository.delete(editingId)
            editing = null
        },
        onBack = { editing = null },
    )
}
