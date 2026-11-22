/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro

import android.app.Application
import de.hecht.openmacro.data.MacroRepository
import de.hecht.openmacro.engine.Notifications
import de.hecht.openmacro.privileged.ShizukuManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

class OpenmacroApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Needed to reflectively call the hidden system service interfaces used by actions.
        HiddenApiBypass.addHiddenApiExemptions("L")
        MacroRepository.init(this)
        Notifications.createChannels(this)
        ShizukuManager.init()
    }
}
