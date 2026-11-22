/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro.engine

import android.content.Context
import android.util.Log
import de.hecht.openmacro.R
import de.hecht.openmacro.model.Action
import de.hecht.openmacro.model.Macro
import de.hecht.openmacro.privileged.ShizukuManager
import de.hecht.openmacro.privileged.SystemServices
import de.hecht.openmacro.ui.label

/** Executes the actions of a macro one after another. */
object MacroRunner {
    private const val TAG = "MacroRunner"

    suspend fun run(context: Context, macro: Macro) {
        Log.i(TAG, "Running macro '${macro.name}'")
        if (macro.actions.any { it.requiresShizuku } && !ShizukuManager.awaitReady()) {
            Notifications.showError(
                context, macro, context.getString(R.string.error_shizuku_unavailable),
            )
            return
        }
        for (action in macro.actions) {
            try {
                execute(action)
                Log.i(TAG, "Action ${action.javaClass.simpleName} succeeded")
            } catch (e: Exception) {
                Log.e(TAG, "Action ${action.javaClass.simpleName} failed", e)
                Notifications.showError(
                    context, macro, "${context.getString(action.label)}: ${e.message}",
                )
            }
        }
    }

    private suspend fun execute(action: Action) = when (action) {
        Action.EnableWifiHotspot -> SystemServices.startWifiHotspot()
        Action.EnableMobileData -> SystemServices.enableMobileData()
    }
}
