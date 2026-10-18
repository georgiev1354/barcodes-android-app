package com.gg.barcodes.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Runs in the new version right after an update: cleans up and says what happened. */
class UpdateCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Updater.onPackageReplaced(context)) return
        val pm = context.packageManager
        val label = pm.getApplicationLabel(context.applicationInfo).toString()
        val version = pm.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        UpdateNotifier.show(context, label, "Обновено до версия $version.", install = false)
    }
}
