package io.github.muzammilawan.clipharbor.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.muzammilawan.clipharbor.AppInfo
import io.github.muzammilawan.clipharbor.MainActivity
import io.github.muzammilawan.clipharbor.YtdlpApplication

/** Foreground protection for the already-admitted in-process task, never a queue or restart runner. */
class DownloadService : Service() {
    private val app get() = application as YtdlpApplication
    private val admission get() = app.foreground
    private var activeHost: ServiceHost? = null
    private var lastStartId = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        val host = ServiceHost()
        val accepted = admission.serviceStarted(intent?.getStringExtra(EXTRA_TASK), host)
        app.foregroundEvent(if (accepted) "promoted" else "rejected")
        return START_NOT_STICKY // No replay/rescheduling after process death.
    }

    @RequiresApi(35)
    override fun onTimeout(startId: Int, fgsType: Int) {
        val host = activeHost
        try {
            if (host == null) stopSelf() else admission.timedOut(host, host::stop)
        } catch (_: Exception) {
            // Admission's finally cancels the Job even if native cancellation failed; never log the exception.
            stopSelf()
            app.foregroundEvent("timeout_cleanup_error")
        }
        app.foregroundEvent("timeout")
    }

    override fun onDestroy() {
        activeHost?.let(admission::serviceLost)
        activeHost = null
        app.foregroundEvent("destroyed")
        super.onDestroy()
    }

    private inner class ServiceHost : ForegroundAdmission.Host {
        private var stopped = false
        private var lastNotice: Int? = null

        override fun promote() {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
            ServiceCompat.startForeground(this@DownloadService, NOTICE_ID, notification(null),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
            activeHost = this
        }

        override fun progress(percent: Int) { refresh(percent) }
        override fun publishing() { refresh(-1) }

        private fun refresh(value: Int) {
            if (stopped || activeHost !== this || lastNotice == value) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@DownloadService, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            lastNotice = value
            getSystemService(NotificationManager::class.java).notify(NOTICE_ID, notification(value))
        }

        override fun stop() {
            if (stopped) return
            stopped = true
            // A late/previous host must not demote a newer task in this same Service instance.
            if (activeHost != null && activeHost !== this && activeHost?.stopped == false) return
            try { ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE) }
            finally { stopSelfResult(lastStartId) }
        }
    }

    private fun notification(value: Int?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val saving = value == -1
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(AppInfo.DISPLAY_NAME)
            .setContentText(if (saving) "Saving the finished file…" else "Downloading video and audio…")
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, value?.takeIf { it >= 0 } ?: 0, value == null || saving)
            .build()
    }

    companion object {
        private const val EXTRA_TASK = "task"
        private const val CHANNEL = "downloads"
        private const val NOTICE_ID = 1001

        fun intent(context: Context, task: String? = null): Intent = Intent(context, DownloadService::class.java).apply {
            if (task != null) putExtra(EXTRA_TASK, task)
        }
    }
}
