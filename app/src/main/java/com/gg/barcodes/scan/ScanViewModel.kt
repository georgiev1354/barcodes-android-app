package com.gg.barcodes.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class LiveScan(
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val tracks: List<TrackState> = emptyList(),
)

data class ScannedBarcode(val number: Int, val value: String, val format: String)

/** Final result: the frozen, annotated camera frame plus the confirmed values. */
data class ScanResult(val image: Bitmap, val barcodes: List<ScannedBarcode>)

class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = ScanSettings(application)

    private val _formats = MutableStateFlow(settings.formats)
    val formats: StateFlow<Set<ScanFormat>> = _formats.asStateFlow()

    private val _stripLabels = MutableStateFlow(settings.stripLabels)
    val stripLabels: StateFlow<Boolean> = _stripLabels.asStateFlow()

    private val _readableWifi = MutableStateFlow(settings.readableWifi)
    val readableWifi: StateFlow<Boolean> = _readableWifi.asStateFlow()

    val analyzerExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    val analyzer = BarcodeAnalyzer(_formats.value, ::onFrame)

    private val tracker = ConsensusTracker()

    private val _live = MutableStateFlow(LiveScan())
    val live: StateFlow<LiveScan> = _live.asStateFlow()

    private val _result = MutableStateFlow<ScanResult?>(null)
    val result: StateFlow<ScanResult?> = _result.asStateFlow()

    private val _expectedCount = MutableStateFlow<Int?>(null)
    val expectedCount: StateFlow<Int?> = _expectedCount.asStateFlow()

    /** Last camera zoom, re-applied when a new scan starts. Main thread only. */
    var zoomRatio = 1f

    // Frame in which the most tracks were visible at once; becomes the result image.
    private var bestFrame: Bitmap? = null
    private var bestFrameBoxes: Map<Int, Box> = emptyMap()

    // Tracker state is only touched on the analyzer executor; these flags cross threads.
    @Volatile private var finished = false
    @Volatile private var resetRequested = false

    fun setExpectedCount(count: Int?) {
        _expectedCount.value = count
    }

    fun setFormats(formats: Set<ScanFormat>) {
        if (formats.isEmpty() || formats == _formats.value) return
        settings.formats = formats
        _formats.value = formats
        analyzer.formats = formats
        // Tracks collected under the old formats shouldn't count towards the next result.
        restart()
    }

    fun setStripLabels(on: Boolean) {
        if (on == _stripLabels.value) return
        settings.stripLabels = on
        _stripLabels.value = on
        // Votes for "S/N: 123" and "123" would otherwise end up in the same track as rivals.
        restart()
    }

    /** Display only: scanning and the decoded values are unaffected. */
    fun setReadableWifi(on: Boolean) {
        settings.readableWifi = on
        _readableWifi.value = on
    }

    fun restart() {
        resetRequested = true
        _result.value = null
        _live.value = LiveScan()
        finished = false
    }

    private fun onFrame(bitmap: Bitmap, detections: List<Detection>) {
        if (resetRequested) {
            resetRequested = false
            tracker.reset()
            bestFrame = null
            bestFrameBoxes = emptyMap()
        }
        if (finished) return

        tracker.expectedCount = _expectedCount.value
        val stripLabels = _stripLabels.value
        val frame = tracker.onFrame(
            detections.mapNotNull { d -> BarcodeValue.clean(d.value, stripLabels)?.let { d.copy(value = it) } }
        )

        if (frame.seenInFrame.isNotEmpty() && frame.seenInFrame.size >= bestFrameBoxes.size) {
            bestFrame = bitmap
            bestFrameBoxes = frame.seenInFrame
        }
        _live.value = LiveScan(bitmap.width, bitmap.height, frame.tracks)

        if (frame.complete) {
            finished = true
            _result.value = buildResult(frame.tracks)
        }
    }

    private fun buildResult(tracks: List<TrackState>): ScanResult {
        val confirmed = tracks
            .filter { it.confirmed }
            .map { it.copy(box = bestFrameBoxes[it.id] ?: it.box) }
            .sortedInReadingOrder { it.box }
        val image = annotate(bestFrame!!, confirmed)
        val barcodes = confirmed.mapIndexed { i, t -> ScannedBarcode(i + 1, t.value, t.format) }
        return ScanResult(image, barcodes)
    }

    /** Draws each confirmed box with its list number onto a copy of the frame. */
    private fun annotate(frame: Bitmap, tracks: List<TrackState>): Bitmap {
        val out = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val unit = maxOf(out.width, out.height) / 400f
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * unit
            color = Color.rgb(0, 200, 83)
        }
        val badge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(0, 200, 83) }
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 10f * unit
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        tracks.forEachIndexed { i, track ->
            val b = track.box
            canvas.drawRect(b.left, b.top, b.right, b.bottom, stroke)
            val radius = 8f * unit
            val cx = b.left
            val cy = b.top
            canvas.drawCircle(cx, cy, radius, badge)
            canvas.drawText("${i + 1}", cx, cy - (text.ascent() + text.descent()) / 2f, text)
        }
        return out
    }

    override fun onCleared() {
        analyzerExecutor.execute { analyzer.close() }
        analyzerExecutor.shutdown()
    }
}
