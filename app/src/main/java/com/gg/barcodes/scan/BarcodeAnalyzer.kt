package com.gg.barcodes.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import zxingcpp.BarcodeReader
import kotlin.math.pow

/**
 * Converts each camera frame to an upright bitmap and decodes every barcode in it: ML Kit for most
 * formats, ZXing-C++ for those without a check digit ([ScanFormat.zxing]).
 *
 * With [squareFrame] the bitmap is first cropped to its centered square, the same part of the
 * frame the scan screen shows, so nothing outside the visible area is scanned.
 *
 * The same bitmap is decoded and handed to [onFrame], so detection boxes are in that bitmap's
 * pixel coordinates. Runs synchronously on the analyzer executor, which gives natural
 * back-pressure together with STRATEGY_KEEP_ONLY_LATEST.
 *
 * Every other frame is decoded from a contrast-enhanced grayscale copy instead (same size, so the
 * boxes still match the original). Faded or low-contrast print that ML Kit misses in the raw frame
 * often decodes there, so such a barcode keeps collecting votes.
 *
 * With [readText], a frame with a value of a format without a check digit also goes through ML
 * Kit text recognition, and values that the text printed next to their barcode reads the same are
 * marked [Detection.textConfirmed] (see [PrintedText]). Off, no text recognition runs at all.
 */
class BarcodeAnalyzer(
    formats: Set<ScanFormat>,
    squareFrame: Boolean,
    readText: Boolean,
    private val onFrame: (bitmap: Bitmap, detections: List<Detection>) -> Unit,
) : ImageAnalysis.Analyzer {

    /** Set from any thread; the scanner is rebuilt on the next frame, not per frame. */
    @Volatile var formats: Set<ScanFormat> = formats

    @Volatile var squareFrame: Boolean = squareFrame

    @Volatile var readText: Boolean = readText

    private var scannerFormats = formats
    private var scanner = createScanner(formats)
    private val zxing = BarcodeReader()
    private var textRecognizer: TextRecognizer? = null

    private var frameCount = 0
    private var pixels = IntArray(0)

    override fun analyze(image: ImageProxy) {
        val wanted = formats
        if (wanted != scannerFormats) {
            scanner?.close()
            scanner = createScanner(wanted)
            scannerFormats = wanted
        }
        val square = squareFrame
        val bitmap = image.use { it.toBitmap().rotated(it.imageInfo.rotationDegrees) }
            .let { if (square) it.centerSquare() else it }
        val input = if (frameCount++ % 2 == 1) enhanced(bitmap) else bitmap
        val detections = try {
            val decoded = decodeMlKit(input) + decodeZxing(input, wanted)
            if (readText && decoded.any { it.weakCheck }) checkPrintedText(bitmap, decoded) else decoded
        } catch (_: Exception) {
            return
        }
        onFrame(bitmap, detections)
    }

    private fun decodeMlKit(input: Bitmap): List<Detection> {
        val scanner = scanner ?: return emptyList()
        val barcodes = Tasks.await(scanner.process(InputImage.fromBitmap(input, 0)))
        return barcodes.mapNotNull { barcode ->
            val value = barcode.rawValue?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val rect = barcode.boundingBox ?: return@mapNotNull null
            val format = ScanFormat.fromMlKit(barcode.format)
            Detection(
                value = value,
                format = format?.label ?: "?",
                box = Box(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()),
                weakCheck = format?.weakCheck ?: false,
            )
        }
    }

    private fun decodeZxing(input: Bitmap, formats: Set<ScanFormat>): List<Detection> {
        val wanted = formats.mapNotNullTo(HashSet()) { it.zxing }
        if (wanted.isEmpty()) return emptyList()
        if (zxing.options.formats != wanted) {
            zxing.options = BarcodeReader.Options(
                formats = wanted,
                tryHarder = true,
                tryRotate = true,
                tryInvert = false,
                // Each extra scan line that must decode to the same value rules out more misreads.
                minLineCount = ZxingMinLineCount,
                maxNumberOfSymbols = 8,
                // Raw content, like ML Kit's rawValue.
                textMode = BarcodeReader.TextMode.PLAIN,
            )
        }
        return zxing.read(input).mapNotNull { result ->
            val value = result.text?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val format = ScanFormat.fromZxing(result.format) ?: return@mapNotNull null
            val p = result.position
            val xs = listOf(p.topLeft.x, p.topRight.x, p.bottomRight.x, p.bottomLeft.x)
            val ys = listOf(p.topLeft.y, p.topRight.y, p.bottomRight.y, p.bottomLeft.y)
            Detection(
                value = value,
                format = format.label,
                box = Box(xs.min().toFloat(), ys.min().toFloat(), xs.max().toFloat(), ys.max().toFloat()),
                weakCheck = true,
            )
        }
    }

    /** Marks weak values whose printed text matches; reads the original frame, not the enhanced one. */
    private fun checkPrintedText(bitmap: Bitmap, detections: List<Detection>): List<Detection> {
        val recognizer = textRecognizer
            ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { textRecognizer = it }
        val text = Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)))
        val lines = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val rect = line.boundingBox ?: return@mapNotNull null
                TextLine(line.text, Box(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()))
            }
        }
        return detections.map { d ->
            if (d.weakCheck && PrintedText.matches(d, lines)) d.copy(textConfirmed = true) else d
        }
    }

    fun close() {
        scanner?.close()
        textRecognizer?.close()
    }

    /**
     * Grayscale copy with the 1st–99th luminance percentiles stretched to full range, then
     * darkened in the midtones (gamma), so pale bars come out close to black.
     */
    private fun enhanced(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val size = width * height
        if (pixels.size != size) pixels = IntArray(size)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        val histogram = IntArray(256)
        for (i in 0 until size) {
            val p = pixels[i]
            val luma = (77 * (p shr 16 and 0xFF) + 150 * (p shr 8 and 0xFF) + 29 * (p and 0xFF)) shr 8
            pixels[i] = luma
            histogram[luma]++
        }
        val low = percentile(histogram, size / 100)
        val high = percentile(histogram, size - size / 100).coerceAtLeast(low + 1)

        val lut = IntArray(256) { v ->
            val t = ((v - low).toFloat() / (high - low)).coerceIn(0f, 1f)
            val g = (255f * t.toDouble().pow(Gamma).toFloat()).toInt()
            (0xFF shl 24) or (g shl 16) or (g shl 8) or g
        }
        for (i in 0 until size) pixels[i] = lut[pixels[i]]
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    /** Smallest luminance with at least [count] pixels at or below it. */
    private fun percentile(histogram: IntArray, count: Int): Int {
        var sum = 0
        for (v in histogram.indices) {
            sum += histogram[v]
            if (sum >= count) return v
        }
        return 255
    }

    // Only the formats the user picked: each extra one is another way to misread a faded 1D code.
    // Null when ZXing handles all of them.
    private fun createScanner(formats: Set<ScanFormat>): BarcodeScanner? {
        val codes = formats.filter { it.zxing == null }.map { it.mlKit }
        if (codes.isEmpty()) return null
        return BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(codes.first(), *codes.drop(1).toIntArray())
                .build()
        )
    }

    private fun Bitmap.centerSquare(): Bitmap {
        if (width == height) return this
        val side = minOf(width, height)
        return Bitmap.createBitmap(this, (width - side) / 2, (height - side) / 2, side, side)
    }

    private fun Bitmap.rotated(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private companion object {
        /** Above 1 darkens midtones: a faded bar at 60% gray maps to about 40%. */
        const val Gamma = 1.6

        const val ZxingMinLineCount = 3
    }
}
