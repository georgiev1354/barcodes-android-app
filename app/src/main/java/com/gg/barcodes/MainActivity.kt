package com.gg.barcodes

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
// PLAY-STORE: only the update notification permission request used Build.
// import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gg.barcodes.scan.ScanViewModel
import com.gg.barcodes.ui.ResultScreen
import com.gg.barcodes.ui.ScanScreen
import com.gg.barcodes.ui.SettingsScreen
import com.gg.barcodes.ui.theme.BarcodesTheme
// PLAY-STORE: self-update is not allowed on Google Play (docs/play-store.md).
// import com.gg.barcodes.update.UpdateBanner
// import com.gg.barcodes.update.UpdateDialog
// import com.gg.barcodes.update.Updater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // PLAY-STORE: self-update is not allowed on Google Play (docs/play-store.md).
        // Updater.init(
        //     this,
        //     checkUrl = BuildConfig.UPDATE_URL,
        //     smallIcon = R.drawable.ic_stat_barcodes,
        //     // Nothing is lost on a restart: a scan in progress is simply started again.
        //     isBusy = { false },
        // )

        setContent {
            BarcodesTheme {
                // PLAY-STORE: update banner and dialog removed (docs/play-store.md).
                // var showUpdates by remember { mutableStateOf(false) }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(Modifier.padding(innerPadding).fillMaxSize()) {
                        // PLAY-STORE: UpdateBanner()
                        Box(Modifier.weight(1f)) {
                            CameraPermissionGate {
                                // PLAY-STORE: was BarcodesApp(onShowUpdates = { showUpdates = true })
                                BarcodesApp(onShowUpdates = {})
                            }
                        }
                    }
                }
                // PLAY-STORE: if (showUpdates) UpdateDialog(onDismiss = { showUpdates = false })
            }
        }
    }
}

@Composable
fun BarcodesApp(
    onShowUpdates: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanViewModel = viewModel(),
) {
    val result by viewModel.result.collectAsStateWithLifecycle()
    // PLAY-STORE: notifications were only for self-update, so the app no longer asks for them.
    // // Asked after the camera, so the two system dialogs never overlap. Only the update notifications need it.
    // val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // val context = LocalContext.current
    // LaunchedEffect(Unit) {
    //     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
    //         ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
    //         PackageManager.PERMISSION_GRANTED
    //     ) {
    //         notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    //     }
    // }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(result) {
        if (result != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    var showSettings by rememberSaveable { mutableStateOf(false) }
    val formats by viewModel.formats.collectAsStateWithLifecycle()
    val stripLabels by viewModel.stripLabels.collectAsStateWithLifecycle()
    val readableWifi by viewModel.readableWifi.collectAsStateWithLifecycle()
    val squareFrame by viewModel.squareFrame.collectAsStateWithLifecycle()
    val readText by viewModel.readText.collectAsStateWithLifecycle()

    val current = result
    when {
        current != null -> ResultScreen(current, readableWifi, onScanAgain = viewModel::restart, modifier = modifier)
        // The camera is unbound while settings are open, so no frames are analyzed meanwhile.
        showSettings -> SettingsScreen(
            formats = formats,
            onFormatsChange = viewModel::setFormats,
            stripLabels = stripLabels,
            onStripLabelsChange = viewModel::setStripLabels,
            readableWifi = readableWifi,
            onReadableWifiChange = viewModel::setReadableWifi,
            squareFrame = squareFrame,
            onSquareFrameChange = viewModel::setSquareFrame,
            readText = readText,
            onReadTextChange = viewModel::setReadText,
            onShowUpdates = onShowUpdates,
            onBack = { showSettings = false },
            modifier = modifier,
        )
        else -> ScanScreen(viewModel, onShowSettings = { showSettings = true }, modifier)
    }
}

@Composable
private fun CameraPermissionGate(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var asked by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        granted = it
        asked = true
    }
    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }
    // Picks up a permission granted from the system settings screen.
    LifecycleResumeEffect(Unit) {
        granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        onPauseOrDispose { }
    }

    if (granted) {
        content()
        return
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.permission_rationale), textAlign = TextAlign.Center)
        Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
            Text(stringResource(R.string.permission_grant))
        }
        if (asked) {
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                )
            }) {
                Text(stringResource(R.string.permission_settings))
            }
        }
    }
}
