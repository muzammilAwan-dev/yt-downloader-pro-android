package io.github.muzammilawan.clipharbor.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.github.muzammilawan.clipharbor.AppInfo
import io.github.muzammilawan.clipharbor.R
import io.github.muzammilawan.clipharbor.core.VideoQuality
import io.github.muzammilawan.clipharbor.core.ErrorRecovery
import io.github.muzammilawan.clipharbor.download.NotificationAccess
import io.github.muzammilawan.clipharbor.download.TaskRecord

@Composable
fun YtdlpTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFFFB4AB), secondary = Color(0xFFE6BDB8),
    ) else lightColorScheme(primary = Color(0xFF9C252B), secondary = Color(0xFF775653)), content = content)
}

/** Original Material layout. Touch targets, safe insets and scrolling are structural, not a TalkBack test result. */
@Composable
fun HomeScreen(
    state: HomeState,
    externalBusy: Boolean,
    notificationAccess: NotificationAccess,
    notificationRequestPending: Boolean,
    onRequestNotifications: () -> Unit,
    onUrlChange: (String) -> Unit,
    onPaste: () -> Unit,
    onResolve: () -> Unit,
    onSelectQuality: (VideoQuality) -> Unit,
    onSelectAudio: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onRetryEngine: () -> Unit,
    onPendingLink: () -> Unit,
    onOpenFile: (PublishedMedia) -> Unit,
    onDiagnostics: () -> Unit,
    tasks: List<TaskRecord> = emptyList(),
    durableQueue: Boolean = false,
    onStageLink: () -> Unit = {},
    onReviewTask: (String, Boolean) -> Unit = { _, _ -> },
    onRemoveTask: (String) -> Unit = {},
    onSelectExactVideo: (String) -> Unit = {},
) {
    val enabled = !state.busy && !externalBusy
    val focusManager = LocalFocusManager.current
    val findFormats = { focusManager.clearFocus(); onResolve() }
    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.ytdlp_icon), contentDescription = null, modifier = Modifier.size(48.dp))
                Column(Modifier.weight(1f)) {
                    Text(AppInfo.DISPLAY_NAME, style = MaterialTheme.typography.headlineMedium)
                    Text(AppInfo.FULL_NAME, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text("A link. Your choice. A saved file.", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Text("Paste or share an HTTPS media link. You decide when to start.", style = MaterialTheme.typography.bodyMedium)

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1 · Review the link", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                    OutlinedTextField(value = state.url, onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(), enabled = enabled,
                        label = { Text("HTTPS media link") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { if (enabled && state.engineReady) findFormats() }),
                        isError = state.inputError != null,
                        supportingText = { Text(state.inputError ?: "One link at a time. No command options.") })
                    OutlinedButton(onClick = onPaste, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Paste from clipboard") }
                    Button(onClick = findFormats, enabled = enabled && state.engineReady &&
                        (state.failureCode?.let { ErrorRecovery.forCode(it).freshResolve } != false),
                        modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.phase == HomePhase.CHOOSING) "Refresh formats" else "Find formats")
                    }
                    if (durableQueue) OutlinedButton(onClick = onStageLink, enabled = enabled && state.engineReady,
                        modifier = Modifier.fillMaxWidth()) { Text("Add link to queue · start later") }
                }
            }

            if (state.phase == HomePhase.CHOOSING) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("2 · Choose what to save", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                        Text(state.title.orEmpty(), style = MaterialTheme.typography.titleSmall)
                        FormatPicker(state, enabled, onSelectQuality, onSelectAudio, onSelectExactVideo)
                        Text("Optional notifications show progress and Cancel while downloading. Cancel is unavailable once saving begins. Downloads also work without notification permission.",
                            style = MaterialTheme.typography.bodySmall)
                        when (notificationAccess) {
                            NotificationAccess.ENABLED -> Text("Download notifications are enabled.", style = MaterialTheme.typography.bodySmall)
                            NotificationAccess.PERMISSION_REQUIRED -> {
                                Text("Notification permission is not granted. You can still download and cancel from Home.", style = MaterialTheme.typography.bodySmall)
                                OutlinedButton(onClick = onRequestNotifications, enabled = enabled && !notificationRequestPending,
                                    modifier = Modifier.fillMaxWidth()) {
                                    Text(if (notificationRequestPending) "Waiting for permission choice…" else "Enable notifications")
                                }
                            }
                            NotificationAccess.DISABLED -> Text("Download notifications are turned off in Android settings. Use Home for progress and Cancel.",
                                style = MaterialTheme.typography.bodySmall)
                        }
                        Button(onClick = onDownload, enabled = enabled && state.canDownload,
                            modifier = Modifier.fillMaxWidth()) { Text(state.downloadLabel) }
                        OutlinedButton(onClick = onCancel, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Dismiss selection") }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (externalBusy) Text("Self-test is running. Download controls are temporarily unavailable.")
                    Text(state.status, Modifier.semantics { liveRegion = LiveRegionMode.Polite }, style = MaterialTheme.typography.bodyMedium)
                    state.failureCode?.let { code ->
                        Text("${code.name} · ${ErrorRecovery.forCode(code).contractAction}", style = MaterialTheme.typography.bodySmall)
                        if (ErrorRecovery.forCode(code).freshResolve) OutlinedButton(onClick = findFormats, enabled = enabled,
                            modifier = Modifier.fillMaxWidth()) { Text("Review fresh formats · confirm download again") }
                        else OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("Read local diagnostics") }
                    }
                    state.storageReport?.let { Text(it.summary, style = MaterialTheme.typography.bodySmall) }
                    if (notificationAccess != NotificationAccess.ENABLED && state.phase in setOf(HomePhase.DOWNLOADING, HomePhase.PUBLISHING)) {
                        Text("Notification controls are unavailable. Watch progress here; Cancel attempt works here while downloading, not while saving.",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (state.busy) {
                        val percent = state.progress
                        if (state.phase == HomePhase.DOWNLOADING && percent != null) {
                            LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
                            Text(if (state.selectedAudioFormatId != null) "Original audio: $percent% · saving follows"
                                else "Current stream: $percent% · merging and saving follow", style = MaterialTheme.typography.bodySmall)
                        } else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    if (state.phase in setOf(HomePhase.RESOLVING, HomePhase.DOWNLOADING)) {
                        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel attempt") }
                    }
                    if (!state.engineReady && !state.busy) {
                        Button(onClick = onRetryEngine, enabled = !externalBusy, modifier = Modifier.fillMaxWidth()) { Text("Retry engine setup") }
                    }
                    state.saved?.let { saved ->
                        Text(saved.name, style = MaterialTheme.typography.titleSmall)
                        Text(saved.location, style = MaterialTheme.typography.bodySmall)
                        if (saved.uri.startsWith("content://")) {
                            Button(onClick = { onOpenFile(saved) }, modifier = Modifier.fillMaxWidth()) { Text("Open saved file") }
                        }
                    }
                }
            }
            if (state.pendingUrl != null) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Another shared link is waiting. Your current attempt was not replaced.")
                        OutlinedButton(onClick = onPendingLink, enabled = enabled, modifier = Modifier.fillMaxWidth()) { Text("Review waiting link") }
                    }
                }
            }
            if (durableQueue) TaskQueue(tasks, enabled, onReviewTask, onRemoveTask, onOpenFile)
            Text("Phase 1 preview · Host checks do not verify Android queue recovery, background execution, notification controls or playback. Device checks are deferred.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onDiagnostics, modifier = Modifier.fillMaxWidth()) { Text("Diagnostics & local logs") }
            Text("Unofficial · Free and open source · ${AppInfo.ENGINE_CREDIT}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
