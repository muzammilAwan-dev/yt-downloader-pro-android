package io.github.muzammilawan.clipharbor.diagnostics

import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import io.github.muzammilawan.clipharbor.AppInfo
import io.github.muzammilawan.clipharbor.BuildConfig
import io.github.muzammilawan.clipharbor.core.EngineInfo
import io.github.muzammilawan.clipharbor.log.DiagnosticReport
import io.github.muzammilawan.clipharbor.log.ReportContext
import java.util.Locale

/** Collects the device/app facts that go into every diagnostic report (docs/12 §4). */
object ReportContextFactory {
    fun build(
        context: Context,
        engineInfo: EngineInfo,
        verbose: Boolean,
        freeSpaceMb: Long?,
    ): ReportContext = ReportContext(
        appName = AppInfo.DISPLAY_NAME,
        versionName = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE.toString(),
        buildType = BuildConfig.BUILD_TYPE,
        phase = PHASE,
        gitSha = BuildConfig.GIT_SHA,
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        androidRelease = Build.VERSION.RELEASE ?: "?",
        apiLevel = Build.VERSION.SDK_INT.toString(),
        abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "?",
        locale = Locale.getDefault().toLanguageTag(),
        freeSpaceMb = freeSpaceMb,
        engine = engineInfo,
        settings = mapOf(
            "verbose" to verbose.toString(),
            "maxHeight" to "1080",
            "updateChannel" to "stable",
        ),
        permissions = mapOf(
            "notifications" to if (notificationsEnabled(context)) "granted" else "denied",
            "safTree" to "unset",
        ),
    )

    private fun notificationsEnabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.areNotificationsEnabled()
    }

    const val PHASE = "1-preview"
}
