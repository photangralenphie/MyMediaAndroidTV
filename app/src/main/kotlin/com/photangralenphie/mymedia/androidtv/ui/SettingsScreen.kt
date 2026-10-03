package com.photangralenphie.mymedia.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import com.photangralenphie.mymedia.androidtv.data.DownloadEntry
import com.photangralenphie.mymedia.androidtv.data.DownloadStore
import com.photangralenphie.mymedia.androidtv.data.SettingsPage
import com.photangralenphie.mymedia.androidtv.data.formatByteCount
import kotlinx.coroutines.launch

private data class DownloadListItem(
    val entry: DownloadEntry,
    val indented: Boolean = false,
    val subtitle: String,
    val displayedSizeBytes: Long = entry.sizeBytes,
)

@Composable
fun SettingsScreen(
    initialPage: SettingsPage,
    downloads: DownloadStore,
    initialHost: String,
    initialPort: Int,
    playEpisodesDirectly: Boolean,
    tvShowDownloadCount: Int,
    onSave: (String, Int) -> Unit,
    onEpisodeBehaviorChange: (Boolean) -> Unit,
    onTvShowDownloadCountChange: (Int) -> Unit,
) {
    var host by remember(initialHost) { mutableStateOf(initialHost) }
    var port by remember(initialPort) { mutableStateOf(initialPort.toString()) }
    var page by remember(initialPage) { mutableStateOf(initialPage) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(Modifier.fillMaxSize()) {
        SettingsNavigation(page = page, onPageSelected = { page = it })
        Column(
            Modifier.fillMaxHeight().width(760.dp).padding(horizontal = 38.dp, vertical = 34.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            when (page) {
                SettingsPage.Behaviour -> BehaviourSettings(
                    playEpisodesDirectly = playEpisodesDirectly,
                    tvShowDownloadCount = tvShowDownloadCount,
                    onEpisodeBehaviorChange = onEpisodeBehaviorChange,
                    onTvShowDownloadCountChange = onTvShowDownloadCountChange,
                )
                SettingsPage.Server -> ServerSettings(
                    host = host,
                    port = port,
                    onHostChange = { host = it },
                    onPortChange = { port = it },
                    onSave = onSave,
                )
                SettingsPage.Downloads -> DownloadSettings(
                    downloads = downloads,
                    onDelete = { id -> scope.launch { downloads.delete(id) } },
                    onDeleteAll = { confirmDeleteAll = true },
                )
            }
        }
    }

    if (confirmDeleteAll) {
        DeleteAllDownloadsDialog(
            onDismiss = { confirmDeleteAll = false },
            onConfirm = {
                confirmDeleteAll = false
                scope.launch { downloads.deleteAll() }
            },
        )
    }
}

@Composable
private fun SettingsNavigation(page: SettingsPage, onPageSelected: (SettingsPage) -> Unit) {
    Column(
        Modifier.fillMaxHeight().width(270.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = .96f))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
        listOf(
            Triple(SettingsPage.Behaviour, "Behaviour", Icons.Default.CheckCircle),
            Triple(SettingsPage.Server, "Server", Icons.Default.Settings),
            Triple(SettingsPage.Downloads, "Downloads", AppIcons.Download),
        ).forEach { (target, label, icon) ->
            OutlinedButton(
                onClick = { onPageSelected(target) },
                modifier = Modifier.fillMaxWidth(),
                colors = if (page == target) ButtonDefaults.colors() else OutlinedButtonDefaults.colors(),
                border = if (page == target) ButtonDefaults.border() else OutlinedButtonDefaults.border(),
            ) {
                Icon(icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Text(label, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun BehaviourSettings(
    playEpisodesDirectly: Boolean,
    tvShowDownloadCount: Int,
    onEpisodeBehaviorChange: (Boolean) -> Unit,
    onTvShowDownloadCountChange: (Int) -> Unit,
) {
    Text("Behaviour", style = MaterialTheme.typography.headlineSmall)
    Text("When selecting an episode", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        if (playEpisodesDirectly) Button(onClick = { onEpisodeBehaviorChange(true) }) { Text("Play episode") }
        else OutlinedButton(onClick = { onEpisodeBehaviorChange(true) }) { Text("Play episode") }
        if (!playEpisodesDirectly) Button(onClick = { onEpisodeBehaviorChange(false) }) { Text("Open episode details") }
        else OutlinedButton(onClick = { onEpisodeBehaviorChange(false) }) { Text("Open episode details") }
    }
    Text("TV show downloads", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(
            onClick = { onTvShowDownloadCountChange((tvShowDownloadCount - 1).coerceAtLeast(1)) },
            enabled = tvShowDownloadCount > 1,
        ) { Text("−") }
        Text("$tvShowDownloadCount episodes", modifier = Modifier.width(150.dp))
        OutlinedButton(
            onClick = { onTvShowDownloadCountChange((tvShowDownloadCount + 1).coerceAtMost(50)) },
            enabled = tvShowDownloadCount < 50,
        ) { Text("+") }
    }
}

@Composable
private fun ServerSettings(
    host: String,
    port: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onSave: (String, Int) -> Unit,
) {
    Text("Server", style = MaterialTheme.typography.headlineSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LabeledField(
            label = "IP address or host",
            value = host,
            onValueChange = onHostChange,
            modifier = Modifier.weight(1f),
            keyboardType = KeyboardType.Number,
            onSubmit = {},
            imeAction = ImeAction.Next,
        )
        LabeledField(
            label = "Port",
            value = port,
            onValueChange = { onPortChange(it.filter(Char::isDigit)) },
            modifier = Modifier.width(150.dp),
            keyboardType = KeyboardType.Number,
            onSubmit = {},
            imeAction = ImeAction.Next,
        )
    }
    val parsedPort = port.toIntOrNull()
    Button(
        onClick = { parsedPort?.let { onSave(host, it) } },
        enabled = host.isNotBlank() && parsedPort != null,
    ) { Text("Save and reconnect") }
}

@Composable
private fun ColumnScope.DownloadSettings(
    downloads: DownloadStore,
    onDelete: (String) -> Unit,
    onDeleteAll: () -> Unit,
) {
    val downloadItems = remember(downloads.revision) { buildDownloadList(downloads.entries()) }
    val topLevelCount = downloadItems.count { !it.indented }
    val totalSize = downloadItems.sumOf {
        if (it.entry.kind == "tvShow") it.entry.sizeBytes else it.displayedSizeBytes
    }

    Text("Downloads", style = MaterialTheme.typography.headlineSmall)
    Text(
        "$topLevelCount ${if (topLevelCount == 1) "title" else "titles"}  •  ${formatByteCount(totalSize)}",
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
    )
    if (downloadItems.isEmpty()) {
        Text("No downloads yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
    }
    LazyColumn(
        Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        items(downloadItems, key = { "${it.entry.kind}-${it.entry.id}" }) { item ->
            DownloadRow(item = item, onDelete = { onDelete(item.entry.id) })
        }
    }
    Button(onClick = onDeleteAll, enabled = downloadItems.isNotEmpty()) { Text("Delete all downloads") }
}

private fun buildDownloadList(entries: List<DownloadEntry>): List<DownloadListItem> {
    val playable = entries.filter { it.video?.isFile == true }
    val episodesByShow = playable.filter { it.kind == "episode" && it.parentId != null }.groupBy { it.parentId }
    val shows = entries.filter { it.kind == "tvShow" && episodesByShow[it.id].orEmpty().isNotEmpty() }
    val moviesAndOtherVideos = playable.filter { it.kind != "episode" && it.kind != "tvShow" }
    val knownShowIds = shows.mapTo(mutableSetOf()) { it.id }
    val orphanedEpisodes = playable.filter { it.kind == "episode" && it.parentId !in knownShowIds }

    return buildList {
        moviesAndOtherVideos.forEach { entry ->
            add(DownloadListItem(entry, subtitle = formatByteCount(entry.sizeBytes)))
        }
        shows.forEach { show ->
            val episodes = episodesByShow[show.id].orEmpty()
                .sortedWith(compareBy({ it.detail.season ?: 0 }, { it.detail.episode ?: 0 }))
            val totalSize = show.sizeBytes + episodes.sumOf { it.sizeBytes }
            add(
                DownloadListItem(
                    entry = show,
                    subtitle = "${episodes.size} ${if (episodes.size == 1) "episode" else "episodes"}  •  ${formatByteCount(totalSize)}",
                    displayedSizeBytes = totalSize,
                )
            )
            episodes.forEach { episode -> add(episodeDownloadListItem(episode, indented = true)) }
        }
        orphanedEpisodes.forEach { episode -> add(episodeDownloadListItem(episode)) }
    }
}

private fun episodeDownloadListItem(entry: DownloadEntry, indented: Boolean = false): DownloadListItem {
    val season = entry.detail.season ?: 0
    val episode = entry.detail.episode ?: 0
    return DownloadListItem(
        entry = entry,
        indented = indented,
        subtitle = "Season $season  •  Episode $episode  •  ${formatByteCount(entry.sizeBytes)}",
    )
}

@Composable
private fun DownloadRow(item: DownloadListItem, onDelete: () -> Unit) {
    val entry = item.entry
    Row(
        Modifier.fillMaxWidth().padding(start = if (item.indented) 32.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        NetworkArtwork(
            entry.artwork?.takeIf { it.isFile }?.toURI()?.toString(),
            entry.title,
            Modifier.width(if (item.indented) 92.dp else 110.dp)
                .height(if (item.indented) 52.dp else 62.dp)
                .clip(RoundedCornerShape(7.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(entry.title, maxLines = 1)
            Text(item.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f), maxLines = 1)
        }
        OutlinedButton(onClick = onDelete) { Text("Delete") }
    }
}

@Composable
private fun DeleteAllDownloadsDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(500.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Delete all downloads?", style = MaterialTheme.typography.headlineSmall)
            Text("Downloaded videos, artwork, and offline metadata will be removed.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(10.dp))
                Button(onClick = onConfirm) { Text("Delete all") }
            }
        }
    }
}
