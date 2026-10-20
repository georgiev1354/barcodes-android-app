package com.gg.barcodes.scan

/** Turns a decoded value into what the user wants to copy. */
object BarcodeValue {

    // AIM symbology identifier, e.g. "]C1" in front of a GS1-128 code. It describes the symbol,
    // it is not part of the data.
    private val symbologyId = Regex("""^][A-Za-z][0-9A-Za-z]""")

    // A printed-style label in front of the value: "S/N:", "S/N 123", "SN: 123", "P/N#123".
    // "SN"/"PN" without a separator are left alone, since "SN1234" may well be the value itself.
    private val label = Regex("""^\s*(?:[SP]\s*/\s*N\s*[:#.]?|[SP]N\s*[:#.])\s*""", RegexOption.IGNORE_CASE)

    /** Returns the cleaned value, or null if nothing is left. */
    fun clean(raw: String, stripLabels: Boolean): String? {
        var value = raw.replaceFirst(symbologyId, "")
        if (stripLabels) value = value.replaceFirst(label, "")
        return value.takeIf { it.isNotBlank() }
    }

    /**
     * Applies what is known about the detection's format. An ITF value is digits in pairs, at
     * least 6 of them: anything else is a misread (often a partial scan) and gives null. An
     * ITF-14 whose GTIN check digit is valid counts as checked, so it confirms as fast as Code 128.
     */
    fun checkFormat(detection: Detection): Detection? {
        if (detection.format != "ITF") return detection
        val value = detection.value
        if (value.length < 6 || value.length % 2 != 0 || !value.all { it in '0'..'9' }) return null
        return if (value.length == 14 && hasValidGtinCheckDigit(value)) detection.copy(weakCheck = false) else detection
    }

    /** GS1 mod-10: weights 3, 1, 3, … from the digit left of the check digit. */
    private fun hasValidGtinCheckDigit(digits: String): Boolean {
        val body = digits.dropLast(1)
        val sum = body.reversed().withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 3 else 1 }
        return (10 - sum % 10) % 10 == digits.last() - '0'
    }
}
