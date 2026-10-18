package com.gg.barcodes.scan

import android.content.Context
import androidx.core.content.edit

/** Scan options kept across launches. */
class ScanSettings(context: Context) {
    private val prefs = context.getSharedPreferences("scan_settings", Context.MODE_PRIVATE)

    /** Never empty; falls back to [ScanFormat.Default]. */
    var formats: Set<ScanFormat>
        get() = prefs.getStringSet(KEY_FORMATS, null)
            ?.mapNotNull { name -> ScanFormat.entries.firstOrNull { it.name == name } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: ScanFormat.Default
        set(value) = prefs.edit { putStringSet(KEY_FORMATS, value.mapTo(HashSet()) { it.name }) }

    /** Drop "S/N:", "P/N:" and similar labels in front of the value. */
    var stripLabels: Boolean
        get() = prefs.getBoolean(KEY_STRIP_LABELS, true)
        set(value) = prefs.edit { putBoolean(KEY_STRIP_LABELS, value) }

    /** Show Wi-Fi QR codes as network name and password instead of the raw text. */
    var readableWifi: Boolean
        get() = prefs.getBoolean(KEY_READABLE_WIFI, true)
        set(value) = prefs.edit { putBoolean(KEY_READABLE_WIFI, value) }

    private companion object {
        const val KEY_FORMATS = "formats"
        const val KEY_STRIP_LABELS = "stripLabels"
        const val KEY_READABLE_WIFI = "readableWifi"
    }
}
