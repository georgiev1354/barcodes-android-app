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
import kotlin.math.pow

/**
 * Converts each camera frame to an upright bitmap and decodes every barcode in it.
 *
 * The same bitmap is decoded and handed to [onFrame], so detection boxes are in that bitmap's
 * pixel coordinates. Runs synchronously on the analyzer executor, which gives natural
 * back-pressure together with STRATEGY_KEEP_ONLY_LATEST.
 *
 * Every other frame is decoded from a contrast-enhanced grayscale copy instead (same size, so the
 * boxes still match the original). Faded or low-contrast print that ML Kit misses in the raw frame
 * often decodes there, so such a barcode keeps collecting votes.
 */
class BarcodeAnalyzer(
    formats: Set<ScanFormat>,
    private val onFrame: (bitmap: Bitmap, detections: List<Detection>) -> Unit,
) : ImageAnalysis.Analyzer {

    /** Set from any thread; the scanner is rebuilt on the next frame, not per frame. */
    @Volatile var formats: Set<ScanFormat> = formats

    private var scannerFormats = formats
    private var scanner = createScanner(formats)

    private var frameCount = 0
    private var pixels = IntArray(0)

    override fun analyze(image: ImageProxy) {
        val wanted = formats
        if (wanted != scannerFormats) {
            scanner.close()
            scanner = createScanner(wanted)
            scannerFormats = wanted
        }
        val bitmap = image.use { it.toBitmap().rotated(it.imageInfo.rotationDegrees) }
        val input = if (frameCount++ % 2 == 1) enhanced(bitmap) else bitmap
        val barcodes = try {
            Tasks.await(scanner.process(InputImage.fromBitmap(input, 0)))
        } catch (_: Exception) {
            return
        }
        val detections = barcodes.mapNotNull { barcode ->
            val value = barcode.rawValue?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val rect = barcode.boundingBox ?: return@mapNotNull null
            Detection(
                value = value,
                format = ScanFormat.fromMlKit(barcode.format)?.label ?: "?",
                box = Box(rect.left.toFloat(), rect.top.toFloat(), rect.right.toFloat(), rect.bottom.toFloat()),
            )
        }
        onFrame(bitmap, detections)
    }

    fun close() = scanner.close()

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
    private fun createScanner(formats: Set<ScanFormat>): BarcodeScanner {
        val codes = formats.map { it.mlKit }
        return BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(codes.first(), *codes.drop(1).toIntArray())
                .build()
        )
    }

    private fun Bitmap.rotated(degrees: Int): Bitmap {
        if (degrees == 0) return this
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }

    private companion object {
        /** Above 1 darkens midtones: a faded bar at 60% gray maps to about 40%. */
        const val Gamma = 1.6
    }
}
