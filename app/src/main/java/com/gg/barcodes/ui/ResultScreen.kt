package com.gg.barcodes.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.gg.barcodes.R
import com.gg.barcodes.scan.ScanResult
import com.gg.barcodes.scan.ScannedBarcode
import com.gg.barcodes.scan.WifiCode

@Composable
fun ResultScreen(
    result: ScanResult,
    readableWifi: Boolean,
    onScanAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val image = remember(result) { result.image.asImageBitmap() }
    BackHandler(onBack = onScanAgain)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.result_title), style = MaterialTheme.typography.headlineSmall)
        }
        item {
            Image(
                bitmap = image,
                contentDescription = stringResource(R.string.result_image_description),
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(result.barcodes, key = { it.number }) { barcode ->
            val wifi = remember(barcode.value, readableWifi) {
                if (readableWifi) WifiCode.parse(barcode.value) else null
            }
            if (wifi != null) WifiCard(barcode, wifi) else PlainCard(barcode)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { copyToClipboard(context, result.barcodes.joinToString("\n") { it.value }) },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.copy_all)) }
                Button(onClick = onScanAgain, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.scan_again))
                }
            }
        }
    }
}

@Composable
private fun PlainCard(barcode: ScannedBarcode) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { copyToClipboard(context, barcode.value) },
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Number(barcode.number)
            Column(Modifier.weight(1f)) {
                Text(barcode.value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                Text(barcode.format, style = MaterialTheme.typography.bodySmall)
            }
            CopyButton { copyToClipboard(context, barcode.value) }
        }
    }
}

/** A Wi-Fi QR code split into name, password and security, each copyable, plus the raw text. */
@Composable
private fun WifiCard(barcode: ScannedBarcode, wifi: WifiCode) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Number(barcode.number)
                Column {
                    Text(stringResource(R.string.wifi_title), style = MaterialTheme.typography.titleMedium)
                    Text(barcode.format, style = MaterialTheme.typography.bodySmall)
                }
            }
            Column(Modifier.padding(start = 32.dp, top = 4.dp)) {
                LabeledValue(stringResource(R.string.wifi_ssid), wifi.ssid) { copyToClipboard(context, wifi.ssid) }
                val password = wifi.password
                if (password != null) {
                    LabeledValue(stringResource(R.string.wifi_password), password) {
                        copyToClipboard(context, password, sensitive = true)
                    }
                }
                LabeledValue(stringResource(R.string.wifi_security), securityLabel(wifi), onCopy = null)
                if (wifi.hidden) {
                    LabeledValue(stringResource(R.string.wifi_hidden), stringResource(R.string.wifi_hidden_yes), onCopy = null)
                }
                LabeledValue(stringResource(R.string.wifi_raw), barcode.value, small = true) {
                    copyToClipboard(context, barcode.value)
                }
            }
        }
    }
}

@Composable
private fun securityLabel(wifi: WifiCode): String = when {
    wifi.isOpen -> stringResource(R.string.wifi_open)
    wifi.security.equals("WPA", ignoreCase = true) -> "WPA/WPA2"
    wifi.security.equals("SAE", ignoreCase = true) -> "WPA3"
    else -> wifi.security ?: "—"
}

@Composable
private fun LabeledValue(label: String, value: String, small: Boolean = false, onCopy: (() -> Unit)?) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.heightIn(min = 48.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.titleMedium,
                fontFamily = FontFamily.Monospace,
            )
        }
        if (onCopy != null) CopyButton(onCopy)
    }
}

@Composable
private fun Number(number: Int) {
    Text(
        "$number",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(end = 16.dp),
    )
}

@Composable
private fun CopyButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painterResource(R.drawable.ic_copy), contentDescription = stringResource(R.string.copy))
    }
}

private fun copyToClipboard(context: Context, text: String, sensitive: Boolean = false) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText(context.getString(R.string.app_name), text)
    if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // Keeps the password out of the clipboard preview and keyboard suggestions.
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    clipboard.setPrimaryClip(clip)
    // Android 13+ shows its own confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
    }
}
