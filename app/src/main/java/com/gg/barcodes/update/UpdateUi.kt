package com.gg.barcodes.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Put above the app's content: shown only while a downloaded update waits for the user. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    val (info, needsPermission) = when (val s = state) {
        is UpdateState.Ready -> s.info to false
        is UpdateState.NeedsPermission -> s.info to true
        else -> return
    }
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Налична е версия ${info.versionName}", fontWeight = FontWeight.Bold)
            info.changelog.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (needsPermission) {
                Text("Разрешете инсталирането от това приложение — след това обновленията ще стават сами.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { openInstallPermission(context) }) { Text("Разреши") }
            } else {
                Button(onClick = { scope.launch { Updater.installNow(context) } }) { Text("Обнови сега") }
            }
        }
    }
}

/** "Обновления": version, auto-update switch, last check, status and "Провери сега". */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    val auto by Updater.autoUpdate.collectAsState()
    val lastCheck by Updater.lastCheck.collectAsState()
    val version = remember {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обновления") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Версия $version")
                if (!Updater.enabled) {
                    Text("Обновленията са изключени в тази компилация.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автоматично обновяване", modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { Updater.setAutoUpdate(context, it) })
                }
                if (lastCheck > 0) {
                    Text("Последна проверка: ${dateTime.format(Instant.ofEpochMilli(lastCheck).atZone(ZoneId.systemDefault()))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (val s = state) {
                    UpdateState.Idle -> Unit
                    UpdateState.Checking -> Text("Проверява се…")
                    UpdateState.UpToDate -> Text("Имате последната версия.")
                    is UpdateState.Downloading -> {
                        Text("Изтегля се версия ${s.info.versionName}…")
                        if (s.percent != null) LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.Ready -> Text("Версия ${s.info.versionName} е изтеглена.")
                    is UpdateState.NeedsPermission -> Text("Версия ${s.info.versionName} е изтеглена, но инсталирането не е разрешено.")
                    is UpdateState.Installing -> Text("Инсталира се версия ${s.info.versionName}…")
                    is UpdateState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Проверява при отваряне на приложението; новата версия се инсталира сама, " +
                        "щом излезете от него. Първия път Android може да поиска потвърждение.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Ready -> TextButton(onClick = { scope.launch { Updater.installNow(context) } }) { Text("Обнови сега") }
                is UpdateState.NeedsPermission -> TextButton(onClick = { openInstallPermission(context) }) { Text("Разреши") }
                UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> Unit
                else -> if (Updater.enabled) {
                    TextButton(onClick = { scope.launch { Updater.check(context) } }) { Text("Провери сега") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Затвори") } },
    )
}

private val dateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

private fun openInstallPermission(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
