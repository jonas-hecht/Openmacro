/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro.privileged

import android.content.pm.PackageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku

/** Tracks whether Shizuku is running and whether Openmacro may use it. */
object ShizukuManager {
    enum class Status { NOT_RUNNING, PERMISSION_REQUIRED, PERMISSION_DENIED, READY }

    private const val REQUEST_CODE = 1
    private val _status = MutableStateFlow(Status.NOT_RUNNING)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun init() {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { refresh() }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    fun refresh() {
        _status.value = when {
            !Shizuku.pingBinder() -> Status.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> Status.READY
            Shizuku.shouldShowRequestPermissionRationale() -> Status.PERMISSION_DENIED
            else -> Status.PERMISSION_REQUIRED
        }
    }

    fun requestPermission() {
        if (Shizuku.pingBinder()) Shizuku.requestPermission(REQUEST_CODE)
    }

    /**
     * Waits until Shizuku is usable. After boot or when the process was just started the
     * Shizuku binder may arrive a moment later, so give it some time.
     */
    suspend fun awaitReady(timeoutMs: Long = 10_000): Boolean {
        refresh()
        return withTimeoutOrNull(timeoutMs) { status.first { it == Status.READY } } != null
    }

    /** Package name matching the uid Shizuku runs our binder calls as (shell or root). */
    val callerPackage: String
        get() = if (Shizuku.getUid() == 0) "root" else "com.android.shell"
}
