/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

package de.hecht.openmacro.engine

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import de.hecht.openmacro.MainActivity
import de.hecht.openmacro.R
import de.hecht.openmacro.model.Macro

object Notifications {
    const val CHANNEL_SERVICE = "service"
    const val CHANNEL_ERRORS = "errors"
    const val ID_SERVICE = 1

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_SERVICE,
                    context.getString(R.string.channel_service),
                    NotificationManager.IMPORTANCE_MIN,
                ),
                NotificationChannel(
                    CHANNEL_ERRORS,
                    context.getString(R.string.channel_errors),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            ),
        )
    }

    fun serviceNotification(context: Context, activeMacros: Int): Notification =
        Notification.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText(
                context.resources.getQuantityString(
                    R.plurals.service_notification_text, activeMacros, activeMacros,
                ),
            )
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .build()

    fun showError(context: Context, macro: Macro, message: String) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val notification = Notification.Builder(context, CHANNEL_ERRORS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.error_notification_title, macro.name))
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(openAppIntent(context))
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(macro.id.hashCode(), notification)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
    )
}
