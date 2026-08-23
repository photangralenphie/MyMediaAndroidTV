package com.jonas.mymedia.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.jonas.mymedia.tv.data.ApiClient
import com.jonas.mymedia.tv.data.DownloadStore
import com.jonas.mymedia.tv.data.MediaPreview
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable
fun MediaContextMenu(
    api: ApiClient,
    downloads: DownloadStore,
    online: Boolean,
    item: MediaPreview,
    onDismiss: () -> Unit,
    onChanged: (JSONObject) -> Unit,
    tvShowDownloadCount: Int = 3,
    navigateLabel: String? = null,
    onNavigate: (() -> Unit)? = null,
    removeLabel: String? = null,
    onRemove: (() -> Unit)? = null,
) {
    var detail by remember(item) { mutableStateOf<JSONObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showCollections by remember { mutableStateOf(false) }
    var showTvDownloadDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(item, online, downloads.revision) {
        runCatching { if (online) api.detail(item) else downloads.detail(item.id) ?: error("This title is not available offline") }
            .onSuccess { detail = it }
            .onFailure { error = it.message }
    }

    fun update(key: String, value: Boolean) {
        scope.launch {
            saving = true
            runCatching {
                if (item.kind == "collection") api.updateCollection(item.id, pinned = value)
                else api.updateMedia(item.kind, item.id, JSONObject().put(key, value))
            }.onSuccess { onChanged(it); onDismiss() }.onFailure { error = it.message }
            saving = false
        }
    }

    if (showCollections) {
        AddToCollectionDialog(
            api = api,
            mediaId = item.id,
            onDismiss = { showCollections = false },
            onDone = {
                showCollections = false
                onChanged(detail ?: JSONObject())
                onDismiss()
            },
        )
        return
    }
    if (showTvDownloadDialog) {
        val value = detail ?: return
        TvShowDownloadDialog(
            nextCount = tvShowDownloadCount,
            onDismiss = { showTvDownloadDialog = false },
            onAll = {
                showTvDownloadDialog = false
                downloads.startTvShowDownload(api, value, null)
                onDismiss()
            },
            onNext = {
                showTvDownloadDialog = false
                downloads.startTvShowDownload(api, value, tvShowDownloadCount)
                onDismiss()
            },
        )
        return
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(520.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(item.name, style = MaterialTheme.typography.headlineSmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val value = detail
            if (item.kind != "collection") {
                val downloading = item.id in downloads.activeIds
                val downloaded = downloads.hasDownload(item.id)
                if (online || downloaded) {
                    ContextAction(
                        when {
                            downloading -> "Cancel"
                            downloaded -> "Delete download"
                            else -> "Download"
                        },
                        when {
                            downloading -> Icons.Default.Close
                            downloaded -> Icons.Default.Delete
                            else -> AppIcons.Download
                        },
                        saving || (value == null && !downloading && !downloaded),
                    ) {
                        when {
                            downloading -> downloads.cancel(item.id)
                            downloaded -> scope.launch {
                                downloads.delete(item.id)
                                onChanged(value ?: JSONObject())
                                onDismiss()
                            }
                            item.kind == "tvShow" -> showTvDownloadDialog = true
                            value != null -> {
                                downloads.startMediaDownload(api, item.kind, value)
                                onDismiss()
                            }
                        }
                    }
                }
                if (online) {
                    val favorite = value?.optBoolean("isFavorite") ?: false
                    ContextAction(if (favorite) "Unfavorite" else "Favorite", Icons.Default.Star, saving || value == null) {
                        update("isFavorite", !favorite)
                    }
                    val watched = value?.optBoolean("isWatched") ?: false
                    ContextAction(
                        if (watched) "Mark unwatched" else "Mark watched",
                        if (watched) AppIcons.EyeSlash else AppIcons.Eye,
                        saving || value == null,
                    ) { update("isWatched", !watched) }
                    ContextAction("Add to collection", AppIcons.CollectionStack, saving) { showCollections = true }
                }
            }
            if (online) {
                val pinned = value?.optBoolean("isPinned") ?: false
                ContextAction(if (pinned) "Unpin" else "Pin", AppIcons.Pin, saving || value == null) { update("isPinned", !pinned) }
            }
            if (navigateLabel != null && onNavigate != null) ContextAction(navigateLabel, Icons.Default.CheckCircle, false) { onDismiss(); onNavigate() }
            if (online && removeLabel != null && onRemove != null) ContextAction(removeLabel, AppIcons.CollectionStack, false) { onDismiss(); onRemove() }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

@Composable
private fun ContextAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, disabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = !disabled, modifier = Modifier.fillMaxWidth()) {
        Icon(icon, null, Modifier.size(22.dp)); Spacer(Modifier.width(10.dp)); Text(label, Modifier.fillMaxWidth())
    }
}
