package io.github.muzammilawan.clipharbor.download

import org.junit.Assert.*
import org.junit.Test

class NotificationControlsTest {
    @Test fun android10Through12NeverRequiresTheAndroid13Permission() {
        for (api in 29..32) assertEquals(NotificationAccess.ENABLED, notificationAccess(api, false, true, true))
    }

    @Test fun android13AndNewerOnlyRequestsMissingPermission() {
        for (api in 33..37) {
            assertEquals(NotificationAccess.PERMISSION_REQUIRED, notificationAccess(api, false, false, true))
            assertEquals(NotificationAccess.ENABLED, notificationAccess(api, true, true, true))
        }
    }

    @Test fun blockedAppOrChannelCannotBeReportedAsEnabledEvenWithPermission() {
        for (api in 29..37) {
            assertEquals(NotificationAccess.DISABLED, notificationAccess(api, true, false, true))
            assertEquals(NotificationAccess.DISABLED, notificationAccess(api, true, true, false))
        }
    }

    @Test fun permissionDenialOnlyChangesVisibilityAndHasNoDownloadContinuation() {
        assertEquals(NotificationAccess.PERMISSION_REQUIRED, notificationAccess(33, false, false, false))
        assertEquals(NotificationAccess.DISABLED, notificationAccess(29, false, false, false))
        assertEquals(NotificationAccess.ENABLED, notificationAccess(33, true, true, true))
    }

    @Test fun taskActionsRoundTripAndDifferentTasksHaveDifferentPendingIntentIdentities() {
        val first = DownloadCancelAction.encode("task-1")
        val second = DownloadCancelAction.encode("task-2")
        assertNotEquals(first, second)
        assertEquals("task-1", DownloadCancelAction.decode(first))
        assertEquals("task-2", DownloadCancelAction.decode(second))
    }

    @Test fun actionCodecRejectsUrlsMissingTasksAndUnrelatedActions() {
        assertNull(DownloadCancelAction.decode(null))
        assertNull(DownloadCancelAction.decode("android.intent.action.SEND"))
        assertNull(DownloadCancelAction.decode("https://example.invalid/watch?token=private"))
        val prefix = DownloadCancelAction.encode("task-1").removeSuffix("task-1")
        for (suffix in listOf("", "a/b", "task?token=private", "a".repeat(81))) {
            assertNull(DownloadCancelAction.decode(prefix + suffix))
            assertThrows(IllegalArgumentException::class.java) { DownloadCancelAction.encode(suffix) }
        }
    }
}
