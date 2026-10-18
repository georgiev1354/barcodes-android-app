package com.gg.barcodes.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** HTTP side of updates: the version check and the APK download. */
object UpdateClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_APK_BYTES = 200L * 1024 * 1024

    /** The newer version the server offers, or null when it answers 204 (nothing new). */
    suspend fun fetch(checkUrl: String, currentVersionCode: Long, sdkInt: Int, userAgent: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val conn = open(UpdatePolicy.checkUrl(checkUrl, currentVersionCode, sdkInt), userAgent)
            conn.setRequestProperty("Accept", "application/json")
            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_NO_CONTENT -> null
                    HttpURLConnection.HTTP_OK -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                    else -> throw IOException("Сървърът за обновления върна код $code")
                }
            } finally {
                conn.disconnect()
            }
        }

    private fun parse(text: String): UpdateInfo {
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("Невалиден отговор от сървъра за обновления", e)
        }
        val info = UpdateInfo(
            versionCode = o.optLong("versionCode", -1),
            versionName = o.optString("versionName"),
            apkUrl = o.optString("apkUrl"),
            sha256 = o.optString("sha256").lowercase(),
            size = o.optLong("size", 0),
            minSdk = o.optInt("minSdk", 0),
            changelog = o.optString("changelog"),
        )
        if (info.versionCode < 1 || info.apkUrl.isBlank() || !UpdatePolicy.isSha256(info.sha256)) {
            throw IOException("Непълен отговор от сървъра за обновления")
        }
        return info
    }

    /**
     * Downloads [url] into [target], checking size and SHA-256. A file already there with the right
     * hash (from an earlier run) is reused. [onProgress] gets 0–100, or null while the size is unknown.
     */
    suspend fun download(url: String, info: UpdateInfo, target: File, userAgent: String, onProgress: (Int?) -> Unit) =
        withContext(Dispatchers.IO) {
            if (target.exists() && sha256(target) == info.sha256) return@withContext
            target.parentFile?.mkdirs()
            val part = File(target.path + ".part")
            val conn = open(url, userAgent)
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Изтеглянето върна код ${conn.responseCode}")
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
                if (total > MAX_APK_BYTES) throw IOException("Файлът за обновление е твърде голям")
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                var lastPercent = -1
                conn.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            read += n
                            if (read > MAX_APK_BYTES) throw IOException("Файлът за обновление е твърде голям")
                            digest.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                            val percent = if (total > 0) (read * 100 / total).toInt().coerceAtMost(100) else null
                            if (percent == null || percent != lastPercent) {
                                lastPercent = percent ?: -1
                                onProgress(percent)
                            }
                        }
                    }
                }
                if (info.size > 0 && read != info.size) throw IOException("Изтегленият файл е непълен")
                if (UpdatePolicy.hex(digest.digest()) != info.sha256) {
                    throw IOException("Изтегленият файл не съвпада с контролната сума (SHA-256)")
                }
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                conn.disconnect()
                part.delete()
            }
        }

    private fun open(url: String, userAgent: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("User-Agent", userAgent)
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return UpdatePolicy.hex(digest.digest())
    }
}
