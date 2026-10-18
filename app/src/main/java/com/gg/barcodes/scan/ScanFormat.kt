package com.gg.barcodes.scan

import com.google.mlkit.vision.barcode.common.Barcode

/** Barcode formats the user can enable, in the order shown in settings (most common first). */
enum class ScanFormat(
    val mlKit: Int,
    val label: String,
    /** No mandatory check digit: a partly faded code can decode as a wrong but valid-looking value. */
    val weakCheck: Boolean = false,
) {
    CODE_128(Barcode.FORMAT_CODE_128, "Code 128"),
    EAN_13(Barcode.FORMAT_EAN_13, "EAN-13"),
    QR(Barcode.FORMAT_QR_CODE, "QR"),
    EAN_8(Barcode.FORMAT_EAN_8, "EAN-8"),
    UPC_A(Barcode.FORMAT_UPC_A, "UPC-A"),
    UPC_E(Barcode.FORMAT_UPC_E, "UPC-E"),
    CODE_93(Barcode.FORMAT_CODE_93, "Code 93"),
    CODE_39(Barcode.FORMAT_CODE_39, "Code 39", weakCheck = true),
    CODABAR(Barcode.FORMAT_CODABAR, "Codabar", weakCheck = true),
    ITF(Barcode.FORMAT_ITF, "ITF", weakCheck = true),
    DATA_MATRIX(Barcode.FORMAT_DATA_MATRIX, "Data Matrix"),
    PDF417(Barcode.FORMAT_PDF417, "PDF417"),
    AZTEC(Barcode.FORMAT_AZTEC, "Aztec");

    companion object {
        /** What the stickers carry. Every extra format is another way to misread a faded 1D code. */
        val Default = setOf(CODE_128, EAN_13, QR)

        fun fromMlKit(code: Int): ScanFormat? = entries.firstOrNull { it.mlKit == code }
    }
}
