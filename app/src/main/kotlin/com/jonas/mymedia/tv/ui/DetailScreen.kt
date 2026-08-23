package com.jonas.mymedia.tv.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.jonas.mymedia.tv.VideoActivity
import com.jonas.mymedia.tv.data.ApiClient
import com.jonas.mymedia.tv.data.BrowseSource
import com.jonas.mymedia.tv.data.DownloadStore
import com.jonas.mymedia.tv.data.MediaPreview
import com.jonas.mymedia.tv.data.Route
import com.jonas.mymedia.tv.data.optNullableString
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

@Composable
fun DetailScreen(
    api: ApiClient,
    downloads: DownloadStore,
    online: Boolean,
    preview: MediaPreview,
    playEpisodesDirectly: Boolean,
    tvShowDownloadCount: Int,
    open: (Route) -> Unit,
    onMediaChanged: () -> Unit,
    goBack: () -> Unit,
) {
    var detail by remember(preview) { mutableStateOf<JSONObject?>(null) }
    var error by remember(preview) { mutableStateOf<String?>(null) }
    var refresh by remember(preview) { mutableIntStateOf(0) }
    LaunchedEffect(preview, refresh, online, downloads.revision) {
        runCatching {
            if (online) api.detail(preview) else downloads.detail(preview.id) ?: error("This title is not available offline")
        }.onSuccess { detail = it; error = null }.onFailure { error = it.message }
    }
    when {
        detail != null -> DetailContent(api, downloads, online, preview.kind, detail!!, playEpisodesDirectly, tvShowDownloadCount, { detail = it }, open, onMediaChanged, goBack) { refresh++ }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(error ?: "Could not load details"); Button(onClick = goBack) { Text("Back") }
            }
        }
        else -> Box(Modifier.fillMaxSize())
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun DetailContent(
    api: ApiClient,
    downloads: DownloadStore,
    online: Boolean,
    kind: String,
    json: JSONObject,
    playEpisodesDirectly: Boolean,
    tvShowDownloadCount: Int,
    setJson: (JSONObject) -> Unit,
    open: (Route) -> Unit,
    onMediaChanged: () -> Unit,
    onDeletedOffline: () -> Unit,
    refresh: () -> Unit,
) {
    val title = json.optString("title", "Untitled")
    val id = json.optString("id")
    // Detail schemas provide artworkURL without maxSize, which is the API's largest image.
    val artwork = if (online) api.absoluteUrl(json.optNullableString("artworkURL")) else downloads.localArtwork(id)?.toURI()?.toString()
    val description = when (kind) {
        "movie" -> json.optNullableString("longDescription") ?: json.optNullableString("shortDescription")
        "episode" -> json.optNullableString("episodeLongDescription") ?: json.optNullableString("episodeShortDescription")
        "tvShow" -> json.optNullableString("showDescription")
        else -> json.optNullableString("collectionDescription")
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primaryFocus = remember(id) { FocusRequester() }
    val listState = rememberLazyListState()
    var actionError by remember { mutableStateOf<String?>(null) }
    var showCollections by remember { mutableStateOf(false) }
    var selectedSeason by remember(id) { mutableIntStateOf(0) }
    var showSeasonSelector by remember { mutableStateOf(false) }
    var contextItem by remember { mutableStateOf<MediaPreview?>(null) }
    var playbackChoice by remember { mutableStateOf<Pair<String, JSONObject>?>(null) }
    var showTvDownloadDialog by remember { mutableStateOf(false) }

    suspend fun resetHeroPosition() {
        // Focus restoration may issue a bring-into-view request on a later frame.
        repeat(2) { withFrameNanos { } }
        listState.scrollToItem(0, 0)
    }

    val playback = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh(); onMediaChanged()
    }
    fun launchPlayback(mediaKind: String, value: JSONObject, useDownload: Boolean) {
        val local = downloads.localVideo(value.optString("id"))
        val url = if (useDownload) local?.toURI()?.toString() else api.videoUrl(value.optString("id"))
        if (url == null) actionError = "This video has not been downloaded."
        else playback.launch(videoIntent(context, api, online, mediaKind, value, url))
    }
    fun play(mediaKind: String, value: JSONObject) {
        val hasDownload = downloads.isDownloaded(value.optString("id"))
        when {
            !online -> launchPlayback(mediaKind, value, useDownload = true)
            hasDownload -> playbackChoice = mediaKind to value
            else -> launchPlayback(mediaKind, value, useDownload = false)
        }
    }
    fun playEpisode(item: MediaPreview) {
        scope.launch {
            runCatching { if (online) api.detail(item) else downloads.detail(item.id) ?: error("Episode is not downloaded") }
                .onSuccess { play("episode", it) }.onFailure { actionError = it.message }
        }
    }
    fun mutate(changes: JSONObject) {
        scope.launch {
            val result = runCatching {
                if (kind == "collection") api.updateCollection(id, pinned = changes.optBoolean("isPinned"))
                else api.updateMedia(kind, id, changes)
            }.onSuccess { setJson(it); downloads.updateMetadata(it); onMediaChanged() }.onFailure { actionError = it.message }
            if (result.isSuccess) resetHeroPosition()
        }
    }
    val allChildren = when (kind) {
        "tvShow" -> json.optJSONArray("episodes").toPreviews().let { episodes ->
            if (online) episodes else episodes.mapNotNull { downloads.previewFor(it.id)?.takeIf { local -> downloads.isDownloaded(local.id) } }
        }
        "collection" -> json.optJSONArray("items").toPreviews()
        else -> emptyList()
    }
    val seasons = if (kind == "tvShow") allChildren.mapNotNull { it.season }.distinct().sorted() else emptyList()
    fun playNextEpisode() {
        scope.launch {
            actionError = null
            val episodes = allChildren.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
            var next: JSONObject? = null
            for (episode in episodes) {
                val episodeDetail = runCatching {
                    if (online) api.detail(episode) else downloads.detail(episode.id) ?: error("Episode is not downloaded")
                }.getOrElse {
                    actionError = it.message
                    return@launch
                }
                if (!episodeDetail.optBoolean("isWatched")) {
                    next = episodeDetail
                    break
                }
            }
            if (next != null) play("episode", next) else actionError = "All episodes are watched."
        }
    }
    LaunchedEffect(id, seasons) {
        if (kind == "tvShow" && selectedSeason !in seasons && seasons.isNotEmpty()) selectedSeason = seasons.first()
    }
    LaunchedEffect(id, kind) {
        runCatching { primaryFocus.requestFocus() }
        resetHeroPosition()
    }
    val visibleChildren = if (kind == "tvShow") allChildren.filter { it.season == selectedSeason } else allChildren
    val downloading = id in downloads.activeIds
    val downloaded = downloads.hasDownload(id)

    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoViewSpec) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 50.dp)) {
            item {
            Box(Modifier.fillMaxWidth().height(520.dp)) {
                NetworkArtwork(artwork, title, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .86f), Color.Black.copy(alpha = .25f), Color.Transparent))))
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, MaterialTheme.colorScheme.background.copy(alpha = .98f)))))
                Column(
                    Modifier.align(Alignment.BottomStart).fillMaxWidth(.82f).padding(start = 38.dp, end = 30.dp, bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    if (kind == "episode") json.optJSONObject("tvShow")?.let { Text(it.optString("name"), color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    Text(title, fontSize = 38.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(heroMetadata(kind, json), fontSize = 14.sp, color = Color.White.copy(alpha = .75f), maxLines = 1)
                    if (!description.isNullOrBlank()) Text(description, fontSize = 15.sp, lineHeight = 20.sp, color = Color.White.copy(alpha = .9f), maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Row(
                        Modifier.padding(vertical = 7.dp).onFocusChanged { focus ->
                            if (
                                focus.hasFocus &&
                                (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0)
                            ) {
                                scope.launch {
                                    repeat(2) { withFrameNanos { } }
                                    listState.animateScrollToItem(0, 0)
                                }
                            }
                        },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (kind == "movie" || kind == "episode") {
                            DetailPrimaryAction(
                                icon = Icons.Default.PlayArrow,
                                label = if (json.optInt("progressMinutes") > 0) "Resume" else "Play",
                                onClick = { play(kind, json) },
                                modifier = Modifier.focusRequester(primaryFocus),
                            )
                        }
                        if (kind == "tvShow") {
                            DetailPrimaryAction(
                                icon = Icons.Default.PlayArrow,
                                label = "Continue",
                                onClick = ::playNextEpisode,
                                modifier = Modifier.focusRequester(primaryFocus),
                            )
                        }
                        if ((online || downloaded) && (kind == "movie" || kind == "episode" || kind == "tvShow")) {
                            DetailSecondaryAction(
                                when {
                                    downloading -> Icons.Default.Close
                                    downloaded -> Icons.Default.Delete
                                    else -> AppIcons.Download
                                },
                                when {
                                    downloading -> "Cancel"
                                    downloaded -> "Delete download"
                                    else -> "Download"
                                },
                                {
                                    if (downloading) downloads.cancel(id)
                                    else {
                                        if (downloaded) scope.launch {
                                            downloads.delete(id)
                                            onMediaChanged()
                                            if (!online) onDeletedOffline()
                                        }
                                        else if (kind == "tvShow") showTvDownloadDialog = true
                                        else downloads.startMediaDownload(api, kind, json)
                                    }
                                },
                            )
                        }
                        if (online && kind != "collection") {
                            val favorite = json.optBoolean("isFavorite")
                            DetailSecondaryAction(Icons.Default.Star, if (favorite) "Unfavorite" else "Favorite", { mutate(JSONObject().put("isFavorite", !favorite)) })
                        }
                        if (online) {
                            val pinned = json.optBoolean("isPinned")
                            DetailSecondaryAction(AppIcons.Pin, if (pinned) "Unpin" else "Pin", { mutate(JSONObject().put("isPinned", !pinned)) }, if (kind == "collection") Modifier.focusRequester(primaryFocus) else Modifier)
                        }
                        if (online && kind != "collection") {
                            val watched = json.optBoolean("isWatched")
                            DetailSecondaryAction(if (watched) AppIcons.Eye else AppIcons.EyeSlash, if (watched) "Mark unwatched" else "Mark watched", { mutate(JSONObject().put("isWatched", !watched)) })
                            DetailSecondaryAction(AppIcons.CollectionStack, "Add to collection", { showCollections = true })
                        }
                    }
                    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    downloads.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                }
            }
        }
            if (kind != "collection") item { Metadata(json, kind) }
            if (kind == "episode") json.optJSONObject("tvShow")?.let { parent -> item {
                SectionTitle("TV show")
                val parentItem = if (online) MediaPreview.from(parent) else downloads.previewFor(parent.optString("id")) ?: MediaPreview.from(parent)
                Row(Modifier.padding(horizontal = 38.dp).fillMaxWidth()) {
                    MediaCard(parentItem, api, { open(Route.Detail(parentItem)) }, Modifier.weight(1f), downloaded = downloads.hasDownload(parentItem.id))
                    Spacer(Modifier.weight(2f))
                }
            } }
            if (kind == "movie" || kind == "episode") item { Credits(json.optJSONObject("credits"), open) }
            if (kind == "tvShow" && allChildren.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 38.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Episodes", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.weight(1f))
                        if (seasons.size > 1) {
                            Button(onClick = { showSeasonSelector = true }) { Text("Season $selectedSeason"); Spacer(Modifier.width(8.dp)); Text("▾") }
                        }
                    }
                }
                item { ThreeColumnMediaGrid(visibleChildren, api, downloads, { episode -> if (playEpisodesDirectly) playEpisode(episode) else open(Route.Detail(episode)) }, { contextItem = it }) }
            }
            if (kind == "collection" && allChildren.isNotEmpty()) {
                item { SectionTitle("Titles") }
                item { ThreeColumnMediaGrid(allChildren, api, downloads, { open(Route.Detail(it)) }, { contextItem = it }) }
            }
        }
    }

    playbackChoice?.let { (mediaKind, value) ->
        PlaybackSourceDialog(
            onDismiss = { playbackChoice = null },
            onStream = { playbackChoice = null; launchPlayback(mediaKind, value, useDownload = false) },
            onDownload = { playbackChoice = null; launchPlayback(mediaKind, value, useDownload = true) },
        )
    }
    if (showTvDownloadDialog) TvShowDownloadDialog(
        nextCount = tvShowDownloadCount,
        onDismiss = { showTvDownloadDialog = false },
        onAll = { showTvDownloadDialog = false; downloads.startTvShowDownload(api, json, null) },
        onNext = { showTvDownloadDialog = false; downloads.startTvShowDownload(api, json, tvShowDownloadCount) },
    )
    if (online && showCollections) AddToCollectionDialog(api, id, { showCollections = false }) { showCollections = false; onMediaChanged() }
    if (showSeasonSelector) SeasonDialog(seasons, selectedSeason, { showSeasonSelector = false }) { selectedSeason = it; showSeasonSelector = false }
    contextItem?.let { child ->
        MediaContextMenu(
            api, downloads, online, child, { contextItem = null }, { contextItem = null; onMediaChanged(); refresh() },
            tvShowDownloadCount = tvShowDownloadCount,
            navigateLabel = if (kind == "tvShow") "Open episode details" else null,
            onNavigate = if (kind == "tvShow") ({ open(Route.Detail(child)) }) else null,
            removeLabel = if (kind == "collection") "Remove from collection" else null,
            onRemove = if (kind == "collection") ({ scope.launch {
                runCatching { api.updateCollection(id, remove = listOf(child.id)) }.onSuccess { setJson(it); onMediaChanged() }.onFailure { actionError = it.message }
            } }) else null,
        )
    }
}

// TV's default focus pivot scrolls even fully visible controls toward the top third of the screen.
// Detail actions only need the minimum movement required to remain visible.
private val MinimalBringIntoViewSpec = object : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailingEdge = offset + size
        return when {
            offset >= 0 && trailingEdge <= containerSize -> 0f
            offset < 0 && trailingEdge > containerSize -> 0f
            abs(offset) < abs(trailingEdge - containerSize) -> offset
            else -> trailingEdge - containerSize
        }
    }
}

@Composable
private fun PlaybackSourceDialog(onDismiss: () -> Unit, onStream: () -> Unit, onDownload: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(470.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Choose playback source", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onStream, modifier = Modifier.fillMaxWidth()) { Text("Stream from server", Modifier.fillMaxWidth()) }
            OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) { Text("Play downloaded file", Modifier.fillMaxWidth()) }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
fun TvShowDownloadDialog(nextCount: Int, onDismiss: () -> Unit, onAll: () -> Unit, onNext: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(520.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Download TV show", style = MaterialTheme.typography.headlineSmall)
            Text("Choose how many episodes to keep available offline.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("Next $nextCount unwatched episodes", Modifier.fillMaxWidth()) }
            OutlinedButton(onClick = onAll, modifier = Modifier.fillMaxWidth()) { Text("All episodes", Modifier.fillMaxWidth()) }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Cancel", Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun DetailPrimaryAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = accentButtonColors(),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(icon, null, Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun DetailSecondaryAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    OutlinedButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier,
        scale = OutlinedButtonDefaults.scale(focusedScale = 1f),
        colors = accentButtonColors(),
        contentPadding = OutlinedButtonDefaults.ContentPadding,
    ) {
        Icon(icon, label, Modifier.size(OutlinedButtonDefaults.IconSize))
        AnimatedVisibility(
            visible = focused,
            enter = expandHorizontally(expandFrom = Alignment.Start) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.Start) + fadeOut(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(OutlinedButtonDefaults.IconSpacing))
                Text(label, maxLines = 1)
            }
        }
    }
}

@Composable
private fun accentButtonColors() = ButtonDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.primary,
    focusedContentColor = MaterialTheme.colorScheme.onPrimary,
    pressedContainerColor = MaterialTheme.colorScheme.primary,
    pressedContentColor = MaterialTheme.colorScheme.onPrimary,
)

@Composable
private fun ThreeColumnMediaGrid(items: List<MediaPreview>, api: ApiClient, downloads: DownloadStore, onClick: (MediaPreview) -> Unit, onContextMenu: (MediaPreview) -> Unit) {
    val downloadedIds = remember(downloads.revision) { downloads.downloadedIds() }
    Column(Modifier.fillMaxWidth().padding(horizontal = 38.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                rowItems.forEach { item ->
                    MediaCard(item, api, { onClick(item) }, Modifier.weight(1f), { onContextMenu(item) }, item.id in downloadedIds)
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun SeasonDialog(seasons: List<Int>, selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(420.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Select season", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(seasons) { season ->
                    if (season == selected) Button(onClick = { onSelect(season) }, modifier = Modifier.fillMaxWidth()) { Text("Season $season", Modifier.fillMaxWidth()) }
                    else OutlinedButton(onClick = { onSelect(season) }, modifier = Modifier.fillMaxWidth()) { Text("Season $season", Modifier.fillMaxWidth()) }
                }
            }
        }
    }
}

private fun videoIntent(context: Context, api: ApiClient, online: Boolean, kind: String, json: JSONObject, url: String) = Intent(context, VideoActivity::class.java).apply {
    putExtra(VideoActivity.EXTRA_URL, url); putExtra(VideoActivity.EXTRA_TITLE, json.optString("title"))
    if (online) putExtra(VideoActivity.EXTRA_BASE_URL, api.baseUrl)
    putExtra(VideoActivity.EXTRA_KIND, kind); putExtra(VideoActivity.EXTRA_ID, json.optString("id"))
    putExtra(VideoActivity.EXTRA_PROGRESS, json.optInt("progressMinutes")); putExtra(VideoActivity.EXTRA_DURATION, json.optInt("durationMinutes"))
}

private fun heroMetadata(kind: String, json: JSONObject): String = buildList {
    if (kind == "episode") add("S${json.optInt("season")} E${json.optInt("episode")}")
    json.optInt("year").takeIf { it > 0 }?.let { add(it.toString()) }
    if (kind == "tvShow") {
        val episodes = json.optJSONArray("episodes").toPreviews()
        val count = episodes.mapNotNull { it.season }.distinct().size
        if (count > 0) add("$count ${if (count == 1) "season" else "seasons"}")
        if (episodes.isNotEmpty()) add("${episodes.size} ${if (episodes.size == 1) "episode" else "episodes"}")
    } else json.optInt("durationMinutes").takeIf { it > 0 }?.let { add("${it}m") }
    formatRating(json.optNullableString("rating"))?.let(::add)
    json.optNullableString("hdVideoQuality")?.let(::add)
    json.optJSONArray("genre").toStrings().takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" · ")) }
    if (kind == "collection") add("${json.optInt("numberOfItems")} titles")
}.joinToString("   •   ")

private fun formatRating(value: String?): String? {
    if (value == null) return null
    val first = value.indexOf('|')
    if (first < 0) return null
    val second = value.indexOf('|', first + 1)
    return if (second > first + 1) value.substring(first + 1, second) else null
}

@Composable
private fun Metadata(json: JSONObject, kind: String) {
    val facts = buildList {
        json.optNullableString("releaseDate")?.let { add("Released" to it) }
        json.optNullableString("studio")?.let { add("Studio" to it) }
        json.optNullableString("network")?.let { add("Network" to it) }
        json.optJSONArray("networks").toStrings().takeIf { it.isNotEmpty() }?.let { add("Networks" to it.joinToString()) }
        json.optJSONArray("languages").toStrings().takeIf { it.isNotEmpty() }?.let { add("Languages" to it.joinToString()) }
        if (kind == "movie" || kind == "episode") add("Playback" to "${json.optInt("progressMinutes")} of ${json.optInt("durationMinutes")} min")
    }.filter { it.second.isNotBlank() }
    if (facts.isEmpty()) return
    Column(Modifier.padding(horizontal = 38.dp, vertical = 10.dp)) {
        SectionTitle("Details", 0.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(34.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            facts.forEach { (label, value) -> Column(Modifier.width(150.dp)) {
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                Text(value.substringBefore('T'), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            } }
        }
    }
}

@Composable
private fun Credits(credits: JSONObject?, open: (Route) -> Unit) {
    if (credits == null) return
    val groups = listOf(
        "Cast" to credits.optJSONArray("cast").toStrings(), "Directors" to credits.optJSONArray("directors").toStrings(),
        "Co-directors" to credits.optJSONArray("coDirectors").toStrings(), "Writers" to credits.optJSONArray("screenwriters").toStrings(),
        "Producers" to credits.optJSONArray("producers").toStrings(), "Executive producers" to credits.optJSONArray("executiveProducers").toStrings(),
        "Composer" to listOfNotNull(credits.optNullableString("composer")),
    ).filter { it.second.isNotEmpty() }
    Column(Modifier.padding(top = 8.dp)) {
        SectionTitle("Credits")
        groups.forEach { (role, people) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 38.dp, vertical = 5.dp)) {
                Text(role, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f), fontSize = 13.sp, modifier = Modifier.padding(bottom = 5.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person -> OutlinedButton(onClick = { open(Route.Person(person)) }) { Text(person, fontSize = 13.sp) } }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String, horizontalPadding: Dp = 38.dp) {
    Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp))
}

@Composable
fun AddToCollectionDialog(api: ApiClient, mediaId: String, onDismiss: () -> Unit, onDone: () -> Unit) {
    var collections by remember { mutableStateOf<List<MediaPreview>>(emptyList()) }
    var create by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { api.mediaPage(BrowseSource.Collections, 1, perPage = 200).items }.onSuccess { collections = it }.onFailure { error = it.message } }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(620.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Add to collection", style = MaterialTheme.typography.headlineMedium)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            LazyColumn(Modifier.height(340.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(collections) { collection -> OutlinedButton(onClick = { scope.launch { runCatching { api.updateCollection(collection.id, add = listOf(mediaId)) }.onSuccess { onDone() }.onFailure { error = it.message } } }, modifier = Modifier.fillMaxWidth()) { Text(collection.name, Modifier.fillMaxWidth()) } }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { OutlinedButton(onClick = onDismiss) { Text("Cancel") }; Spacer(Modifier.width(10.dp)); Button(onClick = { create = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(7.dp)); Text("New") } }
        }
    }
    if (create) CreateCollectionDialog(api, mediaId, { create = false }) { create = false; onDone() }
}

@Composable
fun PersonScreen(api: ApiClient, downloads: DownloadStore, online: Boolean, name: String, tvShowDownloadCount: Int, open: (Route) -> Unit, onMediaChanged: () -> Unit) {
    var person by remember(name) { mutableStateOf<JSONObject?>(null) }
    var error by remember(name) { mutableStateOf<String?>(null) }
    var contextItem by remember { mutableStateOf<MediaPreview?>(null) }
    LaunchedEffect(name, online, downloads.revision) {
        runCatching { if (online) api.person(name) else downloads.person(name) }.onSuccess { person = it }.onFailure { error = it.message }
    }
    val value = person
    if (value == null) {
        if (error != null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(error!!) }
        return
    }
    val roles = value.optJSONArray("roles").toStrings()
    val grouped = buildList {
        add("Movies" to value.optJSONArray("creditedMovies").toPreviews()); add("Episodes" to value.optJSONArray("creditedEpisodes").toPreviews())
        value.optJSONObject("credits")?.let { credits ->
            listOf("Cast" to "cast", "Directed" to "directors", "Co-directed" to "coDirectors", "Written" to "screenwriters", "Produced" to "producers", "Executive produced" to "executiveProducers", "Composed" to "composer").forEach { (label, key) -> add(label to credits.optJSONArray(key).toPreviews()) }
        }
    }.filter { it.second.isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 38.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(value.optString("name"), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Text(roles.joinToString("  •  "), color = MaterialTheme.colorScheme.primary) }
        grouped.forEach { (label, media) -> item { Text(label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp)); ThreeColumnMediaGrid(media, api, downloads, { open(Route.Detail(it)) }, { contextItem = it }) } }
    }
    contextItem?.let { item ->
        MediaContextMenu(
            api, downloads, online, item, { contextItem = null }, { contextItem = null; onMediaChanged() },
            tvShowDownloadCount = tvShowDownloadCount,
        )
    }
}

private fun JSONArray?.toStrings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }.filter(String::isNotBlank)
private fun JSONArray?.toPreviews(): List<MediaPreview> = if (this == null) emptyList() else List(length()) { MediaPreview.from(getJSONObject(it)) }
