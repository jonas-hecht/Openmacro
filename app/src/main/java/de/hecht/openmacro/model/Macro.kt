/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Macro(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger? = null,
    val actions: List<Action> = emptyList(),
) {
    val isComplete: Boolean get() = trigger != null && actions.isNotEmpty()
}

@Serializable
sealed interface Trigger {
    /** Fires when the Bluetooth device with the given MAC [address] connects. */
    @Serializable
    @SerialName("bluetooth_connected")
    data class BluetoothConnected(val address: String, val deviceName: String) : Trigger
}

@Serializable
sealed interface Action {
    val requiresShizuku: Boolean

    @Serializable
    @SerialName("wifi_hotspot_on")
    data object EnableWifiHotspot : Action {
        override val requiresShizuku get() = true
    }

    @Serializable
    @SerialName("mobile_data_on")
    data object EnableMobileData : Action {
        override val requiresShizuku get() = true
    }
}
