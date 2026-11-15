package io.github.muzammilawan.clipharbor.download

enum class NotificationAccess { ENABLED, PERMISSION_REQUIRED, DISABLED }

/** Notification visibility is optional; it never admits, launches or retries a download. */
fun notificationAccess(api: Int, permissionGranted: Boolean, appEnabled: Boolean, channelEnabled: Boolean): NotificationAccess = when {
    api >= 33 && !permissionGranted -> NotificationAccess.PERMISSION_REQUIRED
    appEnabled && channelEnabled -> NotificationAccess.ENABLED
    else -> NotificationAccess.DISABLED
}

/** Per-task action identity, not a media URL or mutable PendingIntent extra. */
internal object DownloadCancelAction {
    private const val PREFIX = "io.github.muzammilawan.clipharbor.CANCEL_DOWNLOAD."
    private val identifier = Regex("[A-Za-z0-9_-]{1,80}")

    fun validTask(task: String?): Boolean = task != null && identifier.matches(task)

    fun encode(task: String): String {
        require(validTask(task)) { "Invalid task identifier" }
        return PREFIX + task
    }

    fun decode(action: String?): String? = action?.takeIf { it.startsWith(PREFIX) }
        ?.removePrefix(PREFIX)?.takeIf(::validTask)
}
