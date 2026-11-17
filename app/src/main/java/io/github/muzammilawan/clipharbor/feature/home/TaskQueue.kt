package io.github.muzammilawan.clipharbor.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.muzammilawan.clipharbor.download.QueuePresentation
import io.github.muzammilawan.clipharbor.download.TaskPhase
import io.github.muzammilawan.clipharbor.download.TaskRecord
import io.github.muzammilawan.clipharbor.core.ErrorCode
import io.github.muzammilawan.clipharbor.core.ErrorRecovery

@Composable
internal fun TaskQueue(rows: List<TaskRecord>, enabled: Boolean, progress: Int?,
    onReview: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit, onOpen: (PublishedMedia) -> Unit) {
    var all by remember { mutableStateOf(false) }
    var publicationRetry by remember { mutableStateOf<String?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Queue & recent saves · ${rows.size}", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Text("One explicit attempt at a time. Queued links survive restarts; interrupted attempts stay paused. Review always checks the site again before Download.",
                style = MaterialTheme.typography.bodySmall)
            if (rows.isEmpty()) Text("No queued links yet.", style = MaterialTheme.typography.bodySmall)
            Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                QueuePresentation.visible(rows, all).forEach { row ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.title ?: row.savedName ?: "Queued media link", style = MaterialTheme.typography.titleSmall)
                        Text(row.phase.name.lowercase().replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                        if (row.phase == TaskPhase.DOWNLOADING && progress != null) {
                            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                            Text("$progress% · merging and saving follow", style = MaterialTheme.typography.bodySmall)
                        } else if (row.phase in setOf(TaskPhase.RESOLVING, TaskPhase.DOWNLOADING, TaskPhase.PUBLISHING)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        row.selection.videoHeight?.let { Text("Requested ceiling: ${it}p", style = MaterialTheme.typography.bodySmall) }
                        row.selection.audioFormatId?.let { Text("Original audio · format $it", style = MaterialTheme.typography.bodySmall) }
                        row.selection.videoFormatId?.let { Text("Exact video · format $it", style = MaterialTheme.typography.bodySmall) }
                        row.reason?.let { reason ->
                            val recovery = runCatching { ErrorRecovery.forCode(ErrorCode.valueOf(reason)) }.getOrNull()
                            Text(recovery?.explanation ?: reason.replace('_', ' '), style = MaterialTheme.typography.bodySmall)
                        }
                        if (row.sourceUrl == null) Text("Source not retained for privacy. Re-enter the original link.", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (QueuePresentation.reviewable(row)) OutlinedButton(onClick = {
                                if (row.reason == "PUBLICATION_INTERRUPTED") publicationRetry = row.id else onReview(row.id, false)
                            }, enabled = enabled, modifier = Modifier.weight(1f)) { Text(if (row.sourceUrl == null) "Re-enter link" else "Review formats") }
                            if (QueuePresentation.removable(row)) OutlinedButton(onClick = { onRemove(row.id) }, enabled = enabled) { Text("Forget record") }
                        }
                        if (row.phase == TaskPhase.DONE && row.savedUri != null && row.savedMime != null) {
                            OutlinedButton(onClick = { onOpen(PublishedMedia(row.savedUri, row.savedName ?: "Saved media",
                                "Previously saved file", row.savedMime)) }, modifier = Modifier.fillMaxWidth()) { Text("Open saved file") }
                        }
                    }
                }
            }
            if (rows.size > 5) TextButton(onClick = { all = !all }, modifier = Modifier.fillMaxWidth()) {
                Text(if (all) "Show recent 5" else "All ${rows.size} records")
            }
            Text("Forgetting a record does not delete a saved file or private download parts. Sensitive query links cannot be resumed from history.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    publicationRetry?.let { id ->
        AlertDialog(onDismissRequest = { publicationRetry = null }, title = { Text("Check Downloads first") },
            text = { Text("Saving was interrupted. A completed file may already exist. After checking Downloads, you can explicitly start a new extraction; a second Download confirmation will still be required.") },
            confirmButton = { TextButton(onClick = { publicationRetry = null; if (enabled) onReview(id, true) }, enabled = enabled) { Text("Checked · review again") } },
            dismissButton = { TextButton(onClick = { publicationRetry = null }) { Text("Not now") } })
    }
}
