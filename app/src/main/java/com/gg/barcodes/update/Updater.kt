package com.gg.barcodes.update

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int?) : UpdateState
    /** Downloaded and verified; installs when the user leaves the app, or taps "Обнови". */
    data class Ready(val info: UpdateInfo) : UpdateState
    /** The user must allow "Install unknown apps" for this app first. */
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-update: check → download → verify → install. The same code in every app (docs/self-update.md);
 * the only app-specific input is [init].
 *
 * Checks when the app comes to the foreground (at most hourly, no background polling). Installing
 * restarts the app, so it happens silently once the user leaves it, or right away on the user's tap.
 */
object Updater {
    /** Small check interval, so switching back and forth between apps doesn't hit the server every time. */
    private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
    private const val DIR = "self-update"
    private const val PREFS = "self_update"
    private const val KEY_AUTO = "auto"
    private const val KEY_LAST_CHECK = "lastCheck"
    private const val KEY_NOTICE = "notice"
    internal const val KEY_ICON = "icon"

    private var checkUrl = ""
    private var isBusy: () -> Boolean = { false }
    private var initialized = false

    val enabled: Boolean get() = checkUrl.isNotBlank()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _autoUpdate = MutableStateFlow(true)
    /** Check on open and install on leaving the app; off = only from the "Провери сега" button. */
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    private val _lastCheck = MutableStateFlow(0L)
    val lastCheck: StateFlow<Long> = _lastCheck.asStateFlow()

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var ready: Pair<UpdateInfo, File>? = null

    @Volatile
    internal var foreground = false
        private set

    /**
     * Call once from the launcher activity's onCreate (before its onStart).
     *
     * @param checkUrl `https://<host>/<applicationId>/check-update`; blank disables updates (debug builds).
     * @param smallIcon monochrome notification icon.
     * @param isBusy true while the app must not be restarted (e.g. an SMS is being sent).
     */
    fun init(
        activity: Activity,
        checkUrl: String,
        @DrawableRes smallIcon: Int,
        isBusy: () -> Boolean = { false },
    ) {
        this.checkUrl = checkUrl
        this.isBusy = isBusy
        if (initialized) return
        initialized = true
        val app = activity.application
        val sp = prefs(app)
        sp.edit { putInt(KEY_ICON, smallIcon) }
        _autoUpdate.value = sp.getBoolean(KEY_AUTO, true)
        _lastCheck.value = sp.getLong(KEY_LAST_CHECK, 0)
        UpdateNotifier.ensureChannel(app)
        val lifecycle = Lifecycle(app)
        app.registerActivityLifecycleCallbacks(lifecycle)
        // This activity's onActivityCreated has already passed (init runs inside its onCreate).
        lifecycle.attach(activity)
    }

    fun setAutoUpdate(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(KEY_AUTO, on) }
        _autoUpdate.value = on
    }

    /** Checks and downloads; installs right away if the user has left the app meanwhile. */
    suspend fun check(context: Context) {
        if (!enabled || !mutex.tryLock()) return
        val app = context.applicationContext
        try {
            _state.value = UpdateState.Checking
            val current = currentVersionCode(app)
            val info = UpdateClient.fetch(checkUrl, current, Build.VERSION.SDK_INT, userAgent(app))
            val now = System.currentTimeMillis()
            prefs(app).edit { putLong(KEY_LAST_CHECK, now) }
            _lastCheck.value = now
            if (info == null || !UpdatePolicy.isNewer(info, current, Build.VERSION.SDK_INT)) {
                ready = null
                clearDownloads(app)
                _state.value = UpdateState.UpToDate
                return
            }
            val url = UpdatePolicy.resolveApkUrl(checkUrl, info.apkUrl, allowHttp = isDebuggable(app))
                ?: throw IOException("Невалиден адрес на обновлението (нужен е HTTPS)")
            val file = File(dir(app), "update-${info.versionCode}.apk")
            _state.value = UpdateState.Downloading(info, null)
            UpdateClient.download(url, info, file, userAgent(app)) { _state.value = UpdateState.Downloading(info, it) }
            UpdateInstaller.verify(app, file, info.versionCode)?.let {
                file.delete()
                throw IOException(it)
            }
            dir(app).listFiles()?.filter { it != file }?.forEach { it.delete() }
            ready = info to file
            _state.value = UpdateState.Ready(info)
        } catch (e: CancellationException) {
            _state.value = UpdateState.Idle
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            return
        } finally {
            mutex.unlock()
        }
        if (!foreground) installIfAuto(app)
    }

    /** The user tapped "Обнови" (in the app or in a notification). */
    suspend fun installNow(context: Context) {
        if (ready == null && mutex.isLocked) {
            // A check is already running (e.g. the one on app start): wait for it instead.
            mutex.lock()
            mutex.unlock()
        }
        if (ready == null) check(context)
        install(context.applicationContext, userInitiated = true)
    }

    private suspend fun checkIfDue(context: Context) {
        if (_autoUpdate.value && System.currentTimeMillis() - _lastCheck.value >= CHECK_INTERVAL_MS) check(context)
    }

    private suspend fun installIfAuto(context: Context) {
        if (_autoUpdate.value && _state.value is UpdateState.Ready) install(context, userInitiated = false)
    }

    private suspend fun install(context: Context, userInitiated: Boolean) {
        val (info, file) = ready ?: return
        if (!file.exists()) {
            ready = null
            _state.value = UpdateState.Idle
            return
        }
        if (!UpdateInstaller.canInstall(context)) {
            // The banner asks for the permission next time the app is open.
            _state.value = UpdateState.NeedsPermission(info)
            return
        }
        // Installing restarts the app; don't cut off work in progress.
        if (isBusy()) {
            if (userInitiated) _state.value = UpdateState.Failed("Изчакайте текущата операция да приключи и опитайте пак")
            return
        }
        _state.value = UpdateState.Installing(info)
        try {
            withContext(Dispatchers.IO) { UpdateInstaller.install(context, file, info) }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Неуспешно инсталиране: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    internal fun awaitingUser() {
        ready?.let { _state.value = UpdateState.Ready(it.first) }
    }

    internal fun onNeedsConfirmation(context: Context, versionCode: Long, versionName: String) {
        awaitingUser()
        val key = "$versionCode:confirm"
        val sp = prefs(context)
        if (sp.getString(KEY_NOTICE, null) == key) return
        sp.edit { putString(KEY_NOTICE, key) }
        UpdateNotifier.show(context, "Налична е версия $versionName", "Докоснете, за да я инсталирате.", install = true)
    }

    internal fun onInstallFailed(message: String) {
        _state.value = UpdateState.Failed(message)
    }

    /** After this app was replaced: removes the APK; true if it was an update we downloaded. */
    internal fun onPackageReplaced(context: Context): Boolean {
        val files = dir(context).listFiles().orEmpty()
        files.forEach { it.delete() }
        return files.any { it.name.endsWith(".apk") }
    }

    internal fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun currentVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private fun userAgent(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${context.packageName}/${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)}; Android ${Build.VERSION.SDK_INT})"
    }

    private fun isDebuggable(context: Context) = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun clearDownloads(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    private fun dir(context: Context) = File(context.cacheDir, DIR)

    /** Foreground = at least one started activity. Replaces any onStart/onStop code in the app. */
    private class Lifecycle(private val app: Application) : Application.ActivityLifecycleCallbacks {
        private var started = 0

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = attach(activity)

        fun attach(activity: Activity) {
            handleIntent(activity.intent)
            (activity as? ComponentActivity)?.addOnNewIntentListener { handleIntent(it) }
        }

        override fun onActivityStarted(activity: Activity) {
            if (started++ == 0) {
                foreground = true
                scope.launch { checkIfDue(app) }
            }
        }

        override fun onActivityResumed(activity: Activity) {
            // Back from system Settings: the install permission may have been granted.
            val s = _state.value
            if (s is UpdateState.NeedsPermission && UpdateInstaller.canInstall(app)) _state.value = UpdateState.Ready(s.info)
        }

        override fun onActivityStopped(activity: Activity) {
            if (--started == 0 && !activity.isChangingConfigurations) {
                foreground = false
                scope.launch { installIfAuto(app) }
            }
        }

        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit

        /** Tap on the "Докоснете, за да я инсталирате" notification. */
        private fun handleIntent(intent: Intent?) {
            if (intent?.action != UpdateNotifier.ACTION_INSTALL) return
            intent.action = null
            scope.launch { installNow(app) }
        }
    }
}
