package com.gg.barcodes.scan

/**
 * A Wi-Fi network QR code: `WIFI:S:<ssid>;T:<WPA|WEP|SAE|nopass>;P:<password>;H:<true>;;`.
 * `\`, `;`, `,`, `:` and `"` inside values are escaped with a backslash.
 */
data class WifiCode(
    val ssid: String,
    val password: String?,
    /** As written in the code (WPA, WEP, SAE, nopass…), or null if absent. */
    val security: String?,
    val hidden: Boolean,
) {
    val isOpen: Boolean get() = security.equals("nopass", ignoreCase = true) || password.isNullOrEmpty()

    companion object {
        private const val PREFIX = "WIFI:"

        /** Returns null for anything that isn't a Wi-Fi code with a network name. */
        fun parse(value: String): WifiCode? {
            if (!value.startsWith(PREFIX, ignoreCase = true)) return null
            val fields = HashMap<String, String>()
            for (field in splitUnescaped(value.substring(PREFIX.length), ';')) {
                val colon = indexOfUnescaped(field, ':')
                if (colon <= 0) continue
                fields.putIfAbsent(field.substring(0, colon).uppercase(), unescape(field.substring(colon + 1)))
            }
            val ssid = fields["S"]?.unquoted()?.takeIf { it.isNotEmpty() } ?: return null
            return WifiCode(
                ssid = ssid,
                password = fields["P"]?.unquoted()?.takeIf { it.isNotEmpty() },
                security = fields["T"]?.takeIf { it.isNotEmpty() },
                hidden = fields["H"].equals("true", ignoreCase = true),
            )
        }

        private fun splitUnescaped(text: String, separator: Char): List<String> {
            val parts = mutableListOf<String>()
            var start = 0
            var i = 0
            while (i < text.length) {
                when (text[i]) {
                    '\\' -> i++
                    separator -> {
                        parts += text.substring(start, i)
                        start = i + 1
                    }
                }
                i++
            }
            parts += text.substring(minOf(start, text.length))
            return parts.filter { it.isNotEmpty() }
        }

        private fun indexOfUnescaped(text: String, char: Char): Int {
            var i = 0
            while (i < text.length) {
                if (text[i] == '\\') i++ else if (text[i] == char) return i
                i++
            }
            return -1
        }

        private fun unescape(text: String): String = buildString {
            var i = 0
            while (i < text.length) {
                if (text[i] == '\\' && i + 1 < text.length) i++
                append(text[i])
                i++
            }
        }

        private fun String.unquoted(): String =
            if (length >= 2 && startsWith('"') && endsWith('"')) substring(1, length - 1) else this
    }
}
