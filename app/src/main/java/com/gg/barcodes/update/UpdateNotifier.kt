package com.gg.barcodes.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** The update notifications; independent of the app's own notification code. */
internal object UpdateNotifier {
    const val ACTION_INSTALL = "self_update.action.INSTALL"
    private const val CHANNEL_ID = "self_update"
    private const val NOTIFICATION_ID = 0x5e1f

    fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Обновления")
                .setDescription("Нова версия на приложението")
                .build()
        )
    }

    /** Tapping opens the app; with [install], the app then installs the downloaded update. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, text: String, install: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        if (install) launch.action = ACTION_INSTALL
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Updater.prefs(context).getInt(Updater.KEY_ICON, 0).takeIf { it != 0 } ?: context.applicationInfo.icon
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
