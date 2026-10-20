package com.gg.barcodes.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.gg.barcodes.BuildConfig
import com.gg.barcodes.R
import com.gg.barcodes.scan.ScanFormat

@Composable
fun SettingsScreen(
    formats: Set<ScanFormat>,
    onFormatsChange: (Set<ScanFormat>) -> Unit,
    stripLabels: Boolean,
    onStripLabelsChange: (Boolean) -> Unit,
    readableWifi: Boolean,
    onReadableWifiChange: (Boolean) -> Unit,
    squareFrame: Boolean,
    onSquareFrameChange: (Boolean) -> Unit,
    onShowUpdates: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
                }
                Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineSmall)
            }
        }

        item { SectionTitle(stringResource(R.string.settings_formats)) }
        item {
            Text(
                stringResource(R.string.settings_formats_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        items(ScanFormat.entries, key = { it.name }) { format ->
            val checked = format in formats
            // At least one format must stay on, or nothing could ever be scanned.
            val enabled = !(checked && formats.size == 1)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = checked,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { on -> onFormatsChange(if (on) formats + format else formats - format) },
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(format.label, style = MaterialTheme.typography.bodyLarge)
                    if (format.weakCheck) {
                        Text(
                            stringResource(R.string.settings_format_weak),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        if (formats != ScanFormat.Default) {
            item {
                TextButton(onClick = { onFormatsChange(ScanFormat.Default) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Text(stringResource(R.string.settings_formats_default))
                }
            }
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(stringResource(R.string.settings_camera)) }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_square_frame),
                summary = stringResource(R.string.settings_square_frame_summary),
                checked = squareFrame,
                onCheckedChange = onSquareFrameChange,
            )
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(stringResource(R.string.settings_result)) }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_strip_labels),
                summary = stringResource(R.string.settings_strip_labels_summary),
                checked = stripLabels,
                onCheckedChange = onStripLabelsChange,
            )
        }
        item {
            SwitchRow(
                title = stringResource(R.string.settings_readable_wifi),
                summary = stringResource(R.string.settings_readable_wifi_summary),
                checked = readableWifi,
                onCheckedChange = onReadableWifiChange,
            )
        }

        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionTitle(stringResource(R.string.settings_app)) }
        // PLAY-STORE: the "Updates" entry is replaced by the version; Google Play does the updating
        // (docs/play-store.md).
        // item {
        //     Column(
        //         Modifier
        //             .fillMaxWidth()
        //             .clickable(onClick = onShowUpdates)
        //             .padding(horizontal = 16.dp, vertical = 12.dp),
        //     ) {
        //         Text(stringResource(R.string.settings_updates), style = MaterialTheme.typography.bodyLarge)
        //         Text(
        //             stringResource(R.string.settings_updates_summary),
        //             style = MaterialTheme.typography.bodySmall,
        //             color = MaterialTheme.colorScheme.onSurfaceVariant,
        //         )
        //     }
        // }
        item {
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
    )
}
