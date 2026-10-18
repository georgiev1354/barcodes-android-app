package com.gg.barcodes.ui

import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as ComposeSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Observer
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gg.barcodes.R
import com.gg.barcodes.scan.LiveScan
import com.gg.barcodes.scan.ScanViewModel
import kotlin.math.abs

private val ConfirmedColor = Color(0xFF00C853)
private val PendingColor = Color(0xFFFFAB00)

private val ZoomPresets = listOf(1f, 2f, 4f)

/** Beyond this, digital zoom only enlarges blur and stops helping the decoder. */
private const val MaxUsefulZoom = 8f

@Composable
fun ScanScreen(viewModel: ScanViewModel, onShowSettings: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val live by viewModel.live.collectAsStateWithLifecycle()
    val expectedCount by viewModel.expectedCount.collectAsStateWithLifecycle()
    var torchOn by rememberSaveable { mutableStateOf(false) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }

    val controller = remember {
        // Preview and analysis share the 16:9 aspect so analysis boxes map onto the preview.
        val selector = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1920, 1080),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                )
            )
            .build()
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            previewResolutionSelector = selector
            imageAnalysisResolutionSelector = selector
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisOutputImageFormat = ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888
        }
    }
    DisposableEffect(controller, lifecycleOwner) {
        controller.setImageAnalysisAnalyzer(viewModel.analyzerExecutor, viewModel.analyzer)
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.clearImageAnalysisAnalyzer()
            controller.unbind()
        }
    }
    DisposableEffect(controller, lifecycleOwner) {
        // The first emission means the camera is ready; restore the zoom used in the last scan.
        var restored = false
        val observer = Observer<ZoomState> { state ->
            if (!restored) {
                restored = true
                val saved = viewModel.zoomRatio.coerceIn(state.minZoomRatio, state.maxZoomRatio)
                if (saved != state.zoomRatio) controller.setZoomRatio(saved)
            }
            zoomRatio = state.zoomRatio
            maxZoomRatio = minOf(state.maxZoomRatio, MaxUsefulZoom)
            viewModel.zoomRatio = state.zoomRatio
        }
        controller.zoomState.observe(lifecycleOwner, observer)
        onDispose { controller.zoomState.removeObserver(observer) }
    }
    DisposableEffect(torchOn) {
        controller.enableTorch(torchOn)
        onDispose { }
    }

    Box(modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PreviewView(it).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    this.controller = controller
                }
            },
        )
        TrackOverlay(live, Modifier.fillMaxSize())

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(stringResource(R.string.expected_label), color = Color.White)
            listOf<Int?>(null, 2, 3, 4).forEach { count ->
                FilterChip(
                    selected = expectedCount == count,
                    onClick = { viewModel.setExpectedCount(count) },
                    label = { Text(count?.toString() ?: stringResource(R.string.expected_auto), color = Color.White) },
                )
            }
            Box(Modifier.weight(1f))
            IconButton(onClick = onShowSettings) {
                Icon(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings_title),
                    tint = Color.White,
                )
            }
            FilledIconToggleButton(checked = torchOn, onCheckedChange = { torchOn = it }) {
                Icon(painterResource(R.drawable.ic_flash), contentDescription = stringResource(R.string.torch))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (maxZoomRatio > 1f) {
                ZoomControls(
                    zoomRatio = zoomRatio,
                    maxZoomRatio = maxZoomRatio,
                    onZoomChange = { controller.setZoomRatio(it) },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            val confirmed = live.tracks.count { it.confirmed }
            Text(
                text = if (live.tracks.isEmpty()) {
                    stringResource(R.string.scan_hint)
                } else {
                    stringResource(R.string.scan_progress, confirmed, live.tracks.size)
                },
                color = Color.White,
            )
            OutlinedButton(onClick = viewModel::restart, modifier = Modifier.padding(top = 8.dp)) {
                Text(stringResource(R.string.scan_restart), color = Color.White)
            }
        }
    }
}

/** Preset buttons plus a slider; pinch-to-zoom on the preview works as well. */
@Composable
private fun ZoomControls(
    zoomRatio: Float,
    maxZoomRatio: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ZoomPresets.filter { it <= maxZoomRatio }.forEach { preset ->
            FilterChip(
                selected = abs(zoomRatio - preset) < 0.05f,
                onClick = { onZoomChange(preset) },
                label = { Text("%.0f×".format(preset), color = Color.White) },
            )
        }
        Slider(
            value = zoomRatio.coerceIn(1f, maxZoomRatio),
            onValueChange = onZoomChange,
            valueRange = 1f..maxZoomRatio,
            modifier = Modifier.weight(1f),
        )
        Text("%.1f×".format(zoomRatio), color = Color.White)
    }
}

/** Draws tracked barcodes over the FILL_CENTER preview: green when confirmed, amber while voting. */
@Composable
private fun TrackOverlay(live: LiveScan, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (live.imageWidth == 0 || live.imageHeight == 0) return@Canvas
        val scale = maxOf(size.width / live.imageWidth, size.height / live.imageHeight)
        val dx = (size.width - live.imageWidth * scale) / 2f
        val dy = (size.height - live.imageHeight * scale) / 2f
        for (track in live.tracks) {
            val b = track.box
            drawRect(
                color = if (track.confirmed) ConfirmedColor else PendingColor,
                topLeft = Offset(dx + b.left * scale, dy + b.top * scale),
                size = ComposeSize(b.width * scale, b.height * scale),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}
