package io.github.muzammilawan.clipharbor.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.muzammilawan.clipharbor.download.QueuePresentation
import io.github.muzammilawan.clipharbor.download.TaskRecord
import io.github.muzammilawan.clipharbor.feature.home.PublishedMedia
import io.github.muzammilawan.clipharbor.feature.home.TaskQueue

/** Downloads destination: active, queued, paused and failed tasks in one place. */
@Composable
fun DownloadsScreen(
    tasks: List<TaskRecord>,
    enabled: Boolean,
    progress: Int?,
    wifiOnly: Boolean,
    onWifiOnlyChange: (Boolean) -> Unit,
    onCancelQueued: () -> Unit,
    onClearFinished: () -> Unit,
    onReview: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
    onOpen: (PublishedMedia) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Downloads", Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
        Text("Active work first, then queued and failed links. Queued links survive restarts; interrupted attempts stay paused until review.",
            style = MaterialTheme.typography.bodyMedium)
        Card(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Wi-Fi only", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (wifiOnly) "Downloads wait for an unmetered connection. Resolving a link stays allowed."
                        else "Downloads may use mobile data. A metered Wi-Fi hotspot also counts as metered.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = wifiOnly, onCheckedChange = onWifiOnlyChange)
            }
        }
        TaskQueue(tasks, enabled, progress, onReview, onRemove, onOpen)
        QueueActions(tasks, enabled, onCancelQueued, onClearFinished)
        Text("Cancel works while downloading, not while saving. Forgetting a record never deletes a saved file.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
    }
}

/** Batch actions (Phase 2B). Both are limited to records no process is working on. */
@Composable
private fun QueueActions(tasks: List<TaskRecord>, enabled: Boolean,
    onCancelQueued: () -> Unit, onClearFinished: () -> Unit) {
    val waiting = QueuePresentation.queuedCount(tasks)
    val finished = QueuePresentation.finishedCount(tasks)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Queue actions", style = MaterialTheme.typography.titleSmall)
            Text("$waiting waiting · $finished finished. A running attempt keeps its own Cancel button.",
                style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onCancelQueued, enabled = enabled && waiting > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel waiting links ($waiting)")
            }
            OutlinedButton(onClick = onClearFinished, enabled = enabled && finished > 0, modifier = Modifier.fillMaxWidth()) {
                Text("Clear finished records ($finished)")
            }
            Text("Clearing only forgets queue rows. Saved files stay in Downloads, and paused records are kept for review.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
