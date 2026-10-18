package com.gg.barcodes.update

import java.net.URI

/** The server's answer to check-update; the format is in docs/self-update.md. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    /** Lower-case hex SHA-256 of the APK file. */
    val sha256: String,
    /** APK size in bytes; 0 when the server didn't say. */
    val size: Long = 0,
    val minSdk: Int = 0,
    val changelog: String = "",
)

/** Pure decisions about an update, kept out of the Android/org.json code so they can be unit-tested. */
object UpdatePolicy {
    fun isNewer(info: UpdateInfo, currentVersionCode: Long, sdkInt: Int): Boolean =
        info.versionCode > currentVersionCode && sdkInt >= info.minSdk

    fun checkUrl(base: String, currentVersionCode: Long, sdkInt: Int): String =
        base + (if ('?' in base) '&' else '?') + "versionCode=$currentVersionCode&sdk=$sdkInt"

    /**
     * Absolute URL of the APK; a relative [apkUrl] resolves against [checkUrl].
     * Null unless it is HTTPS (or HTTP when [allowHttp], for local tests with a debug build).
     */
    fun resolveApkUrl(checkUrl: String, apkUrl: String, allowHttp: Boolean = false): String? {
        val uri = runCatching { URI(checkUrl).resolve(apkUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val schemeOk = scheme == "https" || (allowHttp && scheme == "http")
        return if (schemeOk && !uri.host.isNullOrEmpty()) uri.toString() else null
    }

    fun isSha256(hex: String): Boolean = hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
