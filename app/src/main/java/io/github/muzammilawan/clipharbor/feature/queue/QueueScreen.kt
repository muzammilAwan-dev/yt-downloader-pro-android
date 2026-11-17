package io.github.muzammilawan.clipharbor.feature.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.muzammilawan.clipharbor.download.TaskEntity
import io.github.muzammilawan.clipharbor.download.TaskMediaMode

/**
 * Stateless read-only queue view. The caller owns collection of [tasks]; this function renders only.
 * No URL, no cookie, no auth headers are displayed: only the taxonomy stage name, media mode, error code
 * and a generic "Saved." marker when an artifact URI exists.
 */
@Composable
fun QueueScreen(
    tasks: List<TaskEntity>,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack) { Text("Back to downloads") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Recent tasks", style = MaterialTheme.typography.headlineSmall)
        Text("Read-only view of the durable queue. Survives app restarts.", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        if (tasks.isEmpty()) {
            Text("No downloads yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(tasks, key = { it.id }) { task -> TaskRow(task) }
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskEntity) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(task.stage.name, style = MaterialTheme.typography.titleSmall)
            val mode = if (task.mediaMode == TaskMediaMode.AUDIO) "Audio only"
                else "Video + audio · ${task.requestedResolution}p ceiling"
            Text(mode, style = MaterialTheme.typography.bodySmall)
            task.errorCode?.let { Text("Error: $it", style = MaterialTheme.typography.bodySmall) }
            if (task.artifactUri != null) Text("Saved.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
