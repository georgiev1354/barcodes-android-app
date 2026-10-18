package com.gg.barcodes.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/** Result of a [PackageInstaller] session started by [UpdateInstaller]. On success the process is replaced. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val versionName = intent.getStringExtra(UpdateInstaller.EXTRA_VERSION_NAME).orEmpty()
        val versionCode = intent.getLongExtra(UpdateInstaller.EXTRA_VERSION_CODE, 0)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null && Updater.foreground) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    Updater.awaitingUser()
                } else {
                    // Can't show the system dialog from the background: ask via a notification instead.
                    Updater.onNeedsConfirmation(context, versionCode, versionName)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            // The user closed the system dialog: keep offering the update.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.awaitingUser()
            else -> Updater.onInstallFailed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.let { "Неуспешно инсталиране: $it" } ?: "Неуспешно инсталиране (код $status)"
            )
        }
    }
}
