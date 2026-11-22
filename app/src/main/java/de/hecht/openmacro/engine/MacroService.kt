/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro.engine

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import de.hecht.openmacro.data.MacroRepository
import de.hecht.openmacro.model.Macro
import de.hecht.openmacro.model.Trigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps Openmacro running while at least one macro is enabled,
 * so trigger broadcasts are received reliably and Shizuku stays connected.
 */
class MacroService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED) return
            val device = intent.getParcelableExtra(
                BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java,
            ) ?: return
            val address = try {
                device.address
            } catch (e: SecurityException) {
                Log.w(TAG, "Missing Bluetooth permission", e)
                return
            }
            Log.i(TAG, "Bluetooth device connected: $address")
            MacroRepository.macros.value
                .filter { it.enabled }
                .filter { (it.trigger as? Trigger.BluetoothConnected)?.address == address }
                .forEach { macro -> scope.launch { MacroRunner.run(applicationContext, macro) } }
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(
            Notifications.ID_SERVICE,
            Notifications.serviceNotification(this, activeMacros().size),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        registerReceiver(
            bluetoothReceiver,
            IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED),
            RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val active = activeMacros()
        if (active.isEmpty()) {
            stopSelf()
        } else {
            getSystemService(NotificationManager::class.java).notify(
                Notifications.ID_SERVICE, Notifications.serviceNotification(this, active.size),
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        unregisterReceiver(bluetoothReceiver)
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "MacroService"

        private fun activeMacros(): List<Macro> =
            MacroRepository.macros.value.filter { it.enabled && it.isComplete }

        /**
         * Starts or stops the service depending on whether any macro is enabled.
         * Must be called from a context that may start foreground services
         * (visible activity, boot completed, ...).
         */
        fun sync(context: Context) {
            val intent = Intent(context, MacroService::class.java)
            if (activeMacros().isEmpty()) {
                context.stopService(intent)
                return
            }
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                Log.w(TAG, "BLUETOOTH_CONNECT not granted, Bluetooth triggers won't fire")
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Could not start macro service", e)
            }
        }
    }
}
