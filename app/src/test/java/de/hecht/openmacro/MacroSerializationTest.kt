/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro

import de.hecht.openmacro.data.MacroRepository
import de.hecht.openmacro.model.Action
import de.hecht.openmacro.model.Macro
import de.hecht.openmacro.model.Trigger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroSerializationTest {
    private val macro = Macro(
        id = "1",
        name = "Car",
        enabled = true,
        trigger = Trigger.BluetoothConnected("00:11:22:33:44:55", "Car audio"),
        actions = listOf(Action.EnableWifiHotspot, Action.EnableMobileData),
    )

    @Test
    fun roundTrip() {
        val json = MacroRepository.json.encodeToString(Macro.serializer(), macro)
        assertTrue(json.contains("\"type\":\"bluetooth_connected\""))
        assertTrue(json.contains("\"type\":\"wifi_hotspot_on\""))
        assertEquals(macro, MacroRepository.json.decodeFromString(Macro.serializer(), json))
    }

    @Test
    fun isComplete() {
        assertTrue(macro.isComplete)
        assertEquals(false, macro.copy(actions = emptyList()).isComplete)
        assertEquals(false, macro.copy(trigger = null).isComplete)
    }
}
