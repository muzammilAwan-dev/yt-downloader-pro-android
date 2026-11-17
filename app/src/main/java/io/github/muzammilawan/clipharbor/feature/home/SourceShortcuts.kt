package io.github.muzammilawan.clipharbor.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.muzammilawan.clipharbor.core.SiteCatalog
import io.github.muzammilawan.clipharbor.core.SiteShortcut

/**
 * Source shortcut grid from `contracts/sites.json` plus the "how to share" help.
 * Opening a site is the caller's job (an external browser intent); no WebView or login exists in Phase 2A.
 */
@Composable
fun SourceShortcuts(onOpenSite: (SiteShortcut) -> Unit) {
    var help by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Supported sources", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            Text("Opens the site in your browser. Copy or share a video link, then come back here. Android support is not yet verified for any site.",
                style = MaterialTheme.typography.bodySmall)
            SiteCatalog.sites.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { site ->
                        OutlinedButton(onClick = { onOpenSite(site) }, modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)) {
                            Text(site.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            TextButton(onClick = { help = true }, modifier = Modifier.fillMaxWidth()) {
                Text("How to share a link to YTDLP")
            }
        }
    }
    if (help) {
        AlertDialog(
            onDismissRequest = { help = false },
            title = { Text("Send a link to YTDLP") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Open the site and find the video you want.")
                    Text("2. Use the site's Share button and choose YTDLP, or Copy link and paste it on Home.")
                    Text("3. Review the link, choose a format and confirm. Nothing downloads until you tap Download.")
                    Text("Sites that need a private login (for example Instagram or Vimeo) may refuse anonymous access; in-app browser login is not part of this version.")
                }
            },
            confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } },
        )
    }
}
