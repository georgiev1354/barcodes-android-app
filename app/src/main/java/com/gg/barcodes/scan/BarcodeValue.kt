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
}
