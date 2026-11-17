package io.github.muzammilawan.clipharbor.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.muzammilawan.clipharbor.publish.LibraryCatalog
import io.github.muzammilawan.clipharbor.publish.LibraryItem
import io.github.muzammilawan.clipharbor.publish.LibraryKind

/**
 * Library destination: only files this app published, with search, kind filter, open, share and an explicit delete.
 * Filtering and sorting are pure ([LibraryCatalog]); thumbnail decoding and device behaviour are untested here.
 */
@Composable
fun LibraryScreen(
    items: List<LibraryItem>,
    loading: Boolean,
    error: String?,
    query: String,
    kind: LibraryKind?,
    onQueryChange: (String) -> Unit,
    onKindChange: (LibraryKind?) -> Unit,
    onRefresh: () -> Unit,
    onOpen: (LibraryItem) -> Unit,
    onShare: (LibraryItem) -> Unit,
    onDelete: (LibraryItem) -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<LibraryItem?>(null) }
    val shown = remember(items, query, kind) { LibraryCatalog.visible(items, query, kind) }

    Column(
        modifier = Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Library", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(onClick = onRefresh, enabled = !loading) { Text("Refresh") }
        }
        Text(
            "${shown.size} shown · ${LibraryCatalog.formatBytes(LibraryCatalog.totalBytes(shown))} · only files saved by YTDLP",
            style = MaterialTheme.typography.bodySmall,
        )

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search saved files") },
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = kind == null, onClick = { onKindChange(null) },
                label = { Text("All") }, modifier = Modifier.weight(1f))
            FilterChip(selected = kind == LibraryKind.VIDEO, onClick = { onKindChange(LibraryKind.VIDEO) },
                label = { Text("Video") }, modifier = Modifier.weight(1f))
            FilterChip(selected = kind == LibraryKind.AUDIO, onClick = { onKindChange(LibraryKind.AUDIO) },
                label = { Text("Audio") }, modifier = Modifier.weight(1f))
        }

        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }

        if (!loading && shown.isEmpty()) {
            Text(
                if (query.isNotBlank() || kind != null) "No saved file matches this search or filter."
                else "No saved files yet. Finished downloads appear here.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        shown.forEach { item ->
            LibraryCard(
                item = item,
                enabled = !loading,
                onOpen = { onOpen(item) },
                onShare = { onShare(item) },
                onDelete = { pendingDelete = item },
            )
        }

        Text(
            "Delete removes only a file this app saved, after your confirmation. Files are chosen through Android, never a whole-device media scan.",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this file?") },
            text = { Text("\"${item.name}\" will be removed. This cannot be undone. Other apps' files are never touched.") },
            confirmButton = {
                TextButton(onClick = { pendingDelete = null; onDelete(item) }) { Text("Delete file") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Keep") } },
        )
    }
}

@Composable
private fun LibraryCard(
    item: LibraryItem,
    enabled: Boolean,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val kind = LibraryCatalog.kindOf(item.mimeType)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                    Text(glyph(kind), style = MaterialTheme.typography.headlineSmall)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(label(kind), LibraryCatalog.formatBytes(item.sizeBytes),
                            LibraryCatalog.formatDuration(item.durationMs)).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(item.location, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpen, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Open") }
                OutlinedButton(onClick = onShare, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Share") }
                OutlinedButton(onClick = onDelete, enabled = enabled, modifier = Modifier.weight(1f)) { Text("Delete") }
            }
        }
    }
}

private fun label(kind: LibraryKind): String = when (kind) {
    LibraryKind.VIDEO -> "Video"
    LibraryKind.AUDIO -> "Audio"
    LibraryKind.IMAGE -> "Image"
    LibraryKind.OTHER -> "File"
}

private fun glyph(kind: LibraryKind): String = when (kind) {
    LibraryKind.VIDEO -> "▶"
    LibraryKind.AUDIO -> "♪"
    LibraryKind.IMAGE -> "▣"
    LibraryKind.OTHER -> "▤"
}
