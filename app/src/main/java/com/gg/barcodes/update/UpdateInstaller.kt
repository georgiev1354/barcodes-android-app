package com.gg.barcodes.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File

/** Checks a downloaded APK and hands it to [PackageInstaller]; the outcome arrives in [InstallResultReceiver]. */
object UpdateInstaller {
    const val EXTRA_VERSION_NAME = "versionName"
    const val EXTRA_VERSION_CODE = "versionCode"

    /** "Install unknown apps" for this app, which the user grants in system Settings. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Null if [apk] is this app, at [expectedVersionCode], signed with the installed app's key;
     * otherwise why not. Android enforces the signature anyway — this just fails early, with a reason.
     */
    fun verify(context: Context, apk: File, expectedVersionCode: Long): String? {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: return "Изтегленият файл не е валидно APK"
        if (archive.packageName != context.packageName) return "Обновлението е за друго приложение (${archive.packageName})"
        if (archive.longVersionCode != expectedVersionCode) {
            return "Версията в APK (${archive.longVersionCode}) не съвпада с обявената ($expectedVersionCode)"
        }
        val installed = installedInfo(pm, context.packageName)
        if (signers(archive).intersect(signers(installed)).isEmpty()) {
            return "Обновлението е подписано с друг ключ и не може да се инсталира"
        }
        return null
    }

    fun install(context: Context, apk: File, info: UpdateInfo) {
        val installer = context.packageManager.packageInstaller
        // A session left over from an earlier, unconfirmed attempt would only confuse the user.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Silent once this app is the installer of record (i.e. after the first update through it).
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }
                val result = Intent(context, InstallResultReceiver::class.java)
                    .putExtra(EXTRA_VERSION_NAME, info.versionName)
                    .putExtra(EXTRA_VERSION_CODE, info.versionCode)
                // Mutable: the installer fills in the status extras.
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, result,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    /** All certificates the package may be signed with, including earlier ones after a key rotation. */
    private fun signers(info: PackageInfo): Set<Signature> {
        val signing = info.signingInfo ?: return emptySet()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs?.toSet().orEmpty()
    }
}
