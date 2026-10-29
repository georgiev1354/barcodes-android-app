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

    /** Square camera frame instead of the full 3:4 one: a smaller area, fewer stray barcodes. */
    var squareFrame: Boolean
        get() = prefs.getBoolean(KEY_SQUARE_FRAME, false)
        set(value) = prefs.edit { putBoolean(KEY_SQUARE_FRAME, value) }

    /** Check values without a check digit against the text printed next to the barcode. */
    var readText: Boolean
        get() = prefs.getBoolean(KEY_READ_TEXT, false)
        set(value) = prefs.edit { putBoolean(KEY_READ_TEXT, value) }

    private companion object {
        const val KEY_FORMATS = "formats"
        const val KEY_STRIP_LABELS = "stripLabels"
        const val KEY_READABLE_WIFI = "readableWifi"
        const val KEY_SQUARE_FRAME = "squareFrame"
        const val KEY_READ_TEXT = "readText"
    }
}
