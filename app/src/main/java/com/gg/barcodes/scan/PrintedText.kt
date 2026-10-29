package com.gg.barcodes.scan

import kotlin.math.max
import kotlin.math.min

/** One line of text recognized in the frame, in the same pixel space as the barcode boxes. */
data class TextLine(val text: String, val box: Box)

/**
 * Checks a decoded value against the human-readable text printed next to its barcode.
 *
 * Pure Kotlin so it can be unit-tested; [BarcodeAnalyzer] feeds it ML Kit's text lines.
 */
object PrintedText {

    /** Characters text recognition confuses with digits; only used for all-digit values. */
    private val digitLookalikes = mapOf('O' to '0', 'o' to '0', 'I' to '1', 'l' to '1')

    /** True when a line just above or below [detection]'s box reads the same value. */
    fun matches(detection: Detection, lines: List<TextLine>): Boolean =
        lines.any { isBeside(it.box, detection.box) && contains(normalize(it.text, detection.value), detection.value) }

    /**
     * The line lies mostly within the barcode's horizontal extent, and above or below it within
     * the larger of its height and a quarter of its width (ZXing's box can be only a few scan
     * lines tall, far from the text under the bars).
     */
    internal fun isBeside(line: Box, barcode: Box): Boolean {
        val overlap = min(line.right, barcode.right) - max(line.left, barcode.left)
        if (overlap < line.width * 0.5f) return false
        val gap = max(0f, max(line.top - barcode.bottom, barcode.top - line.bottom))
        return gap <= max(barcode.height, barcode.width / 4f)
    }

    /**
     * Drops spaces and the `*` start/stop characters printed around Code 39 text, and dashes the
     * value itself doesn't have. For an all-digit value, letters that look like digits become
     * those digits.
     */
    internal fun normalize(text: String, value: String): String {
        val digits = value.all { it.isDigit() }
        val keepDash = '-' in value
        return buildString {
            for (c in text) {
                when {
                    c.isWhitespace() -> {}
                    c == '-' && !keepDash -> {}
                    digits -> append(digitLookalikes[c] ?: c)
                    else -> append(c)
                }
            }
        }.trim('*')
    }

    /** [value] occurs in [text] as a whole: no letter or digit right before or after it. */
    internal fun contains(text: String, value: String): Boolean {
        var from = 0
        while (true) {
            val i = text.indexOf(value, from)
            if (i < 0) return false
            val end = i + value.length
            val before = text.getOrNull(i - 1)
            val after = text.getOrNull(end)
            if ((before == null || !before.isLetterOrDigit()) && (after == null || !after.isLetterOrDigit())) {
                return true
            }
            from = i + 1
        }
    }
}
