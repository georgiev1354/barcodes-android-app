package com.gg.barcodes.scan

import com.google.mlkit.vision.barcode.common.Barcode
import zxingcpp.BarcodeReader

/** Barcode formats the user can enable, in the order shown in settings (most common first). */
enum class ScanFormat(
    val mlKit: Int,
    val label: String,
    /**
     * Decode with ZXing-C++ instead of ML Kit. Set for the formats without a check digit: ML Kit
     * reads those with a learned model that regularly drops or swaps characters (e.g. Code 39
     * "88F7C7C4ADDA" as "88F7C74ADDA"), while ZXing decodes the bars strictly.
     */
    val zxing: BarcodeReader.Format? = null,
) {
    CODE_128(Barcode.FORMAT_CODE_128, "Code 128"),
    CODE_39(Barcode.FORMAT_CODE_39, "Code 39", BarcodeReader.Format.CODE_39),
    ITF(Barcode.FORMAT_ITF, "ITF", BarcodeReader.Format.ITF),
    EAN_13(Barcode.FORMAT_EAN_13, "EAN-13"),
    QR(Barcode.FORMAT_QR_CODE, "QR"),
    EAN_8(Barcode.FORMAT_EAN_8, "EAN-8"),
    UPC_A(Barcode.FORMAT_UPC_A, "UPC-A"),
    UPC_E(Barcode.FORMAT_UPC_E, "UPC-E"),
    CODE_93(Barcode.FORMAT_CODE_93, "Code 93"),
    CODABAR(Barcode.FORMAT_CODABAR, "Codabar", BarcodeReader.Format.CODABAR),
    DATA_MATRIX(Barcode.FORMAT_DATA_MATRIX, "Data Matrix"),
    PDF417(Barcode.FORMAT_PDF417, "PDF417"),
    AZTEC(Barcode.FORMAT_AZTEC, "Aztec");

    /**
     * No mandatory check digit: a partly faded code can decode as a wrong but valid-looking value,
     * so the tracker needs a larger lead before accepting it.
     */
    val weakCheck: Boolean get() = zxing != null

    companion object {
        /** What the stickers carry. Every extra format is another way to misread a faded 1D code. */
        val Default = setOf(CODE_128, CODE_39, ITF, EAN_13, QR)

        fun fromMlKit(code: Int): ScanFormat? = entries.firstOrNull { it.mlKit == code }

        /** ZXing reports variants such as CODE_39_STD or ITF_14 for the format that was asked for. */
        fun fromZxing(format: BarcodeReader.Format): ScanFormat? =
            entries.firstOrNull { it.zxing != null && format.name.startsWith(it.zxing.name) }
    }
}
