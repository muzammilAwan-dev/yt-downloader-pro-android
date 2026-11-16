package io.github.muzammilawan.clipharbor.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.BroadcastReceiver
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
        val task = intent?.getStringExtra(EXTRA_TASK)
        val host = ServiceHost(task?.takeIf(DownloadCancelAction::validTask))
        val accepted = admission.serviceStarted(task, host)
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

    private inner class ServiceHost(private val task: String?) : ForegroundAdmission.Host {
        private var stopped = false
        private var lastNotice: Int? = null

        override fun promote() {
            val manager = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
            }
            ServiceCompat.startForeground(this@DownloadService, NOTICE_ID, notification(task, null),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0)
            activeHost = this
        }

        override fun progress(percent: Int) { refresh(percent) }
        override fun publishing() { refresh(-1) }
        override fun cancelling() { refresh(-2) }

        private fun refresh(value: Int) {
            if (stopped || activeHost !== this || lastNotice == value) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this@DownloadService, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) return
            lastNotice = value
            getSystemService(NotificationManager::class.java).notify(NOTICE_ID, notification(task, value))
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

    private fun notification(task: String?, value: Int?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val saving = value == -1
        val cancelling = value == -2
        val builder = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(AppInfo.DISPLAY_NAME)
            .setContentText(when {
                saving -> "Saving the finished file…"
                cancelling -> "Canceling. Waiting for task cleanup…"
                else -> "Downloading selected media…"
            })
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, value?.takeIf { it >= 0 } ?: 0, value == null || saving || cancelling)
        if (task != null && !saving && !cancelling) {
            val cancel = PendingIntent.getBroadcast(this, 0, Intent(this, DownloadCancelReceiver::class.java).apply {
                action = DownloadCancelAction.encode(task)
            }, PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "Cancel", cancel)
        }
        return builder.build()
    }

    companion object {
        private const val EXTRA_TASK = "task"
        internal const val CHANNEL = "downloads"
        private const val NOTICE_ID = 1001

        fun intent(context: Context, task: String? = null): Intent = Intent(context, DownloadService::class.java).apply {
            if (task != null) putExtra(EXTRA_TASK, task)
        }
    }
}

/** System notification action only: no session attachment, work launch, restart or cleanup wait. */
class DownloadCancelReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val task = DownloadCancelAction.decode(intent.action) ?: return
        val app = context.applicationContext as YtdlpApplication
        try {
            if (app.foreground.cancel(task)) app.foregroundEvent("notification_cancel")
        } catch (_: Exception) {
            // The coordinator's finally still cancels the Job. Never log action/input/exception text.
            app.foregroundEvent("notification_cancel_error")
        }
    }
}
