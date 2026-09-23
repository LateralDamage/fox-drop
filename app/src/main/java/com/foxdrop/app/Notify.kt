package com.foxdrop.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

enum class Tab { SHOP, NEW, NEWS, EVENTS, STATUS, WISHLIST }

object Notify {
    const val EXTRA_TAB = "tab"
    /** Set on every notification tap; MainActivity plays the running fox when it sees it. */
    const val EXTRA_FOX = "fox"

    private val channels = mapOf(
        AlertKind.WISHLIST to NotificationManager.IMPORTANCE_HIGH,
        AlertKind.SHOP to NotificationManager.IMPORTANCE_DEFAULT,
        AlertKind.UPDATES to NotificationManager.IMPORTANCE_HIGH,
        AlertKind.NEWS to NotificationManager.IMPORTANCE_DEFAULT,
        AlertKind.COSMETICS to NotificationManager.IMPORTANCE_DEFAULT,
        AlertKind.EVENTS to NotificationManager.IMPORTANCE_HIGH,
    )

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        channels.forEach { (kind, importance) ->
            nm.createNotificationChannel(NotificationChannel(kind.key, kind.title, importance).apply {
                description = kind.blurb
            })
        }
    }

    fun canPost(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED && NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(context: Context, kind: AlertKind, tab: Tab, title: String, text: String, id: Int = title.hashCode()) {
        if (!canPost(context)) return
        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_TAB, tab.name)
            .putExtra(EXTRA_FOX, true)
        val pi = PendingIntent.getActivity(
            context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, kind.key)
            .setSmallIcon(R.drawable.ic_notify)
            .setColor(0xFFF26B1D.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}
