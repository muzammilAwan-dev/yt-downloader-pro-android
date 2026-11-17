package io.github.muzammilawan.clipharbor.feature.shell

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.github.muzammilawan.clipharbor.AppInfo

/** Persistent destinations; diagnostics stays a local tool reached from the top bar. */
enum class AppDestination(val label: String, val glyph: String) {
    HOME("Home", "⌂"),
    DOWNLOADS("Downloads", "↓"),
    LIBRARY("Library", "▤"),
}

/**
 * Three-destination shell. Window insets are applied once around the whole scaffold so the top bar, content and
 * navigation bar never double-pad; inner screens only handle the keyboard where they need it.
 */
@Composable
fun AppShell(
    destination: AppDestination,
    onDestination: (AppDestination) -> Unit,
    onDiagnostics: () -> Unit,
    home: @Composable () -> Unit,
    downloads: @Composable () -> Unit,
    library: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(AppInfo.DISPLAY_NAME, Modifier.semantics { heading() },
                        style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = onDiagnostics) { Text("Diagnostics") }
                }
            },
            bottomBar = {
                NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                    AppDestination.entries.forEach { item ->
                        NavigationBarItem(
                            selected = destination == item,
                            onClick = { onDestination(item) },
                            icon = { Text(item.glyph) },
                            label = { Text(item.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (destination) {
                    AppDestination.HOME -> home()
                    AppDestination.DOWNLOADS -> downloads()
                    AppDestination.LIBRARY -> library()
                }
            }
        }
    }
}
