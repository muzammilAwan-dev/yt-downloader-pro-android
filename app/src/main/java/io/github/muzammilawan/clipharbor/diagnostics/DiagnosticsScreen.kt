package io.github.muzammilawan.clipharbor.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.muzammilawan.clipharbor.AppInfo

/** All mutable UI state of the Phase 0 diagnostics screen. */
class DiagnosticsState {
    var url by mutableStateOf("")
    var busy by mutableStateOf(false)
    var status by mutableStateOf("Ready. Paste a link or run the self-test.")
    var report by mutableStateOf("")
    var logTail by mutableStateOf("")
    var verbose by mutableStateOf(true)
    var engineLine by mutableStateOf("engine: not initialised")
}

@Composable
fun DiagnosticsScreen(
    state: DiagnosticsState,
    onUrlChange: (String) -> Unit,
    onRunSelfTest: () -> Unit,
    onDownload: () -> Unit,
    onShareLog: () -> Unit,
    onCopySummary: () -> Unit,
    onClearLogs: () -> Unit,
    onVerboseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(AppInfo.DISPLAY_NAME, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(AppInfo.FULL_NAME, style = MaterialTheme.typography.bodyMedium)
        Text(
            "Unofficial, free and open source. Powered by yt-dlp. Phase 0 engine spike.",
            style = MaterialTheme.typography.bodySmall,
        )
        SelectionContainer {
            Text(state.engineLine, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
        }

        OutlinedTextField(
            value = state.url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Video URL") },
            singleLine = true,
            enabled = !state.busy,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onRunSelfTest, enabled = !state.busy) { Text("Run self-test") }
            Button(onClick = onDownload, enabled = !state.busy) { Text("Download 1080p + merge") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onShareLog) { Text("Share log") }
            OutlinedButton(onClick = onCopySummary) { Text("Copy summary") }
            OutlinedButton(onClick = onClearLogs, enabled = !state.busy) { Text("Clear logs") }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Switch(checked = state.verbose, onCheckedChange = onVerboseChange)
            Text("Verbose logging", style = MaterialTheme.typography.bodyMedium)
            if (state.busy) CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp))
        }

        Text(state.status, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)

        if (state.report.isNotBlank()) {
            SelectionContainer {
                Text(
                    state.report,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Text("Log (newest last)", style = MaterialTheme.typography.labelMedium)
        SelectionContainer {
            Text(
                text = state.logTail.ifBlank { "(empty)" },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 320.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(8.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
