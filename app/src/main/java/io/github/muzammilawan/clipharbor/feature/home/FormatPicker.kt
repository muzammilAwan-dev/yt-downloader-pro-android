package io.github.muzammilawan.clipharbor.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.muzammilawan.clipharbor.core.AudioPresentation
import io.github.muzammilawan.clipharbor.core.VideoQualities
import io.github.muzammilawan.clipharbor.core.VideoQuality

/** Only the selected media mode is expanded. Variant lists scroll in a bounded viewport. */
@Composable
internal fun FormatPicker(state: HomeState, enabled: Boolean, onVideo: (VideoQuality) -> Unit, onAudio: (String) -> Unit) {
    val videos = VideoQualities.visibleChoices(state.qualities)
    val audioMode = state.selectedAudioFormatId != null || videos.isEmpty()
    var allAudio by remember(state.audioChoices) { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { VideoQualities.default(state.qualities)?.let(onVideo) },
            enabled = enabled && videos.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Text(if (!audioMode) "✓ Video + audio" else "Video + audio")
        }
        OutlinedButton(onClick = { AudioPresentation.quickChoices(state.audioChoices).firstOrNull()?.let { onAudio(it.formatId) } },
            enabled = enabled && state.audioChoices.isNotEmpty(), modifier = Modifier.weight(1f)) {
            Text(if (audioMode) "✓ Audio only" else "Audio only")
        }
    }
    Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
        if (audioMode) {
            Text("Original audio · no conversion", style = MaterialTheme.typography.bodySmall)
            val visible = if (allAudio) state.audioChoices else AudioPresentation.quickChoices(state.audioChoices, state.selectedAudioFormatId)
            visible.forEach { choice ->
                FormatRow(choice.label, choice.detail, state.selectedAudioFormatId == choice.formatId, enabled) { onAudio(choice.formatId) }
            }
        } else {
            Text("Reported resolution · H.264/AAC preferred", style = MaterialTheme.typography.bodySmall)
            videos.forEach { choice ->
                FormatRow(choice.label, choice.detail, state.selectedQuality == choice.quality, enabled) { onVideo(choice.quality) }
            }
        }
    }
    if (audioMode) {
        Text("Quick choices are one stream per container/codec, not a best-quality or language recommendation. Exact IDs and reported sizes distinguish variants.",
            style = MaterialTheme.typography.bodySmall)
        if (state.audioChoices.size > AudioPresentation.quickChoices(state.audioChoices, state.selectedAudioFormatId).size) {
            OutlinedButton(onClick = { allAudio = !allAudio }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(if (allAudio) "Show quick choices" else "All ${state.audioChoices.size} original streams")
            }
        }
    } else {
        Text("Short-edge resolution includes portrait video. No upscaling; lower resolution may be used. High-resolution merges use MKV. Saved results report actual dimensions.",
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FormatRow(label: String, detail: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).selectable(selected = selected, enabled = enabled,
        role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f).padding(start = 8.dp, top = 8.dp, bottom = 8.dp)) {
            Text(label, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
    }
}
