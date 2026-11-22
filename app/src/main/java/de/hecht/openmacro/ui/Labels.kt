/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro.ui

import android.content.Context
import androidx.annotation.StringRes
import de.hecht.openmacro.R
import de.hecht.openmacro.model.Action
import de.hecht.openmacro.model.Macro
import de.hecht.openmacro.model.Trigger

/** All actions that can be added to a macro, in the order they are offered. */
val availableActions: List<Action> = listOf(Action.EnableWifiHotspot, Action.EnableMobileData)

@get:StringRes
val Action.label: Int
    get() = when (this) {
        Action.EnableWifiHotspot -> R.string.action_wifi_hotspot_on
        Action.EnableMobileData -> R.string.action_mobile_data_on
    }

fun Trigger.describe(context: Context): String = when (this) {
    is Trigger.BluetoothConnected -> context.getString(R.string.trigger_bluetooth_summary, deviceName)
}

fun Macro.summary(context: Context): String {
    val trigger = trigger?.describe(context) ?: context.getString(R.string.no_trigger)
    val actions = actions.joinToString { context.getString(it.label) }
        .ifEmpty { context.getString(R.string.no_actions) }
    return "$trigger → $actions"
}
