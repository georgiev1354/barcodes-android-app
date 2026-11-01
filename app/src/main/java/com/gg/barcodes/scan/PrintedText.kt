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

    /** Characters text recognition confuses with digits. */
    private val digitLookalikes = mapOf('O' to '0', 'o' to '0', 'I' to '1', 'l' to '1')

    /** Fewer characters than this left once letters are ignored is too little to confirm a value. */
    private const val MinCompared = 3

    /**
     * True when a line just above or below [detection]'s box reads the same value. Latin letters
     * are ignored on both sides: some stickers print letters in or around the number.
     */
    fun matches(detection: Detection, lines: List<TextLine>): Boolean {
        val value = stripLetters(detection.value)
        if (value.length < MinCompared) return false
        return lines.any { line ->
            isBeside(line.box, detection.box) &&
                (contains(normalize(line.text, detection.value, lookalikes = false), value) ||
                    contains(normalize(line.text, detection.value, lookalikes = true), value))
        }
    }

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
     * Drops spaces, the `*` start/stop characters printed around Code 39 text, dashes the value
     * itself doesn't have, and Latin letters. With [lookalikes], letters that look like digits
     * become those digits instead of being dropped (both variants are tried, so a label such as
     * `LOT` doesn't turn into digits).
     */
    internal fun normalize(text: String, value: String, lookalikes: Boolean): String {
        val keepDash = '-' in value
        return buildString {
            for (c in text) {
                when {
                    c.isWhitespace() -> {}
                    c == '-' && !keepDash -> {}
                    lookalikes && c in digitLookalikes -> append(digitLookalikes.getValue(c))
                    c.isLatinLetter() -> {}
                    else -> append(c)
                }
            }
        }.trim('*')
    }

    internal fun stripLetters(value: String): String = value.filterNot { it.isLatinLetter() }

    private fun Char.isLatinLetter() = this in 'a'..'z' || this in 'A'..'Z'

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
