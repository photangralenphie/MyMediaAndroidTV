package com.photangralenphie.mymedia.androidtv.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.photangralenphie.mymedia.androidtv.VideoActivity
import com.photangralenphie.mymedia.androidtv.data.ApiClient
import com.photangralenphie.mymedia.androidtv.data.BrowseSource
import com.photangralenphie.mymedia.androidtv.data.DownloadStore
import com.photangralenphie.mymedia.androidtv.data.MediaDetail
import com.photangralenphie.mymedia.androidtv.data.MediaPreview
import com.photangralenphie.mymedia.androidtv.data.MediaUpdate
import com.photangralenphie.mymedia.androidtv.data.PersonDetail
import com.photangralenphie.mymedia.androidtv.data.Route
import kotlinx.coroutines.launch
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
    var detail by remember(preview) { mutableStateOf<MediaDetail?>(null) }
    var error by remember(preview) { mutableStateOf<String?>(null) }
    var refresh by remember(preview) { mutableIntStateOf(0) }
    LaunchedEffect(preview, refresh, online, downloads.revision) {
        runCatching {
            if (online) api.detail(preview) else downloads.detail(preview.id) ?: error("This title is not available offline")
        }.onSuccess { detail = it; error = null }.onFailure { error = it.message }
    }
    val loadedDetail = detail
    when {
        loadedDetail != null -> DetailContent(api, downloads, online, preview.kind, loadedDetail, playEpisodesDirectly, tvShowDownloadCount, { detail = it }, open, onMediaChanged, goBack) { refresh++ }
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
    detail: MediaDetail,
    playEpisodesDirectly: Boolean,
    tvShowDownloadCount: Int,
    setDetail: (MediaDetail) -> Unit,
    open: (Route) -> Unit,
    onMediaChanged: () -> Unit,
    onDeletedOffline: () -> Unit,
    refresh: () -> Unit,
) {
    val title = detail.title
    val id = detail.id
    // Detail schemas provide artworkURL without maxSize, which is the API's largest image.
    val artwork = if (online) api.absoluteUrl(detail.artworkPath) else downloads.localArtwork(id)?.toURI()?.toString()
    val description = detail.description
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primaryFocus = remember(id) { FocusRequester() }
    val listState = rememberLazyListState()
    var actionError by remember { mutableStateOf<String?>(null) }
    var showCollections by remember { mutableStateOf(false) }
    var selectedSeason by remember(id) { mutableIntStateOf(0) }
    var showSeasonSelector by remember { mutableStateOf(false) }
    var contextItem by remember { mutableStateOf<MediaPreview?>(null) }
    var playbackChoice by remember { mutableStateOf<MediaDetail?>(null) }
    var showTvDownloadDialog by remember { mutableStateOf(false) }

    suspend fun resetHeroPosition() {
        // Focus restoration may issue a bring-into-view request on a later frame.
        repeat(2) { withFrameNanos { } }
        listState.scrollToItem(0, 0)
    }

    val playback = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh(); onMediaChanged()
    }
    fun launchPlayback(value: MediaDetail, useDownload: Boolean) {
        val local = downloads.localVideo(value.id)
        val url = if (useDownload) local?.toURI()?.toString() else api.videoUrl(value.id)
        if (url == null) actionError = "This video has not been downloaded."
        else playback.launch(videoIntent(context, api, online, value, url))
    }
    fun play(value: MediaDetail) {
        val hasDownload = downloads.isDownloaded(value.id)
        when {
            !online -> launchPlayback(value, useDownload = true)
            hasDownload -> playbackChoice = value
            else -> launchPlayback(value, useDownload = false)
        }
    }
    fun playEpisode(item: MediaPreview) {
        scope.launch {
            runCatching { if (online) api.detail(item) else downloads.detail(item.id) ?: error("Episode is not downloaded") }
                .onSuccess(::play).onFailure { actionError = it.message }
        }
    }
    fun mutate(update: MediaUpdate) {
        scope.launch {
            val result = runCatching {
                if (kind == "collection") api.updateCollection(id, pinned = update.isPinned)
                else api.updateMedia(kind, id, update)
            }.onSuccess { setDetail(it); downloads.updateMetadata(it); onMediaChanged() }.onFailure { actionError = it.message }
            if (result.isSuccess) resetHeroPosition()
        }
    }
    val allChildren = when (kind) {
        "tvShow" -> detail.children.let { episodes ->
            if (online) episodes else episodes.mapNotNull { downloads.previewFor(it.id)?.takeIf { local -> downloads.isDownloaded(local.id) } }
        }
        "collection" -> detail.children
        else -> emptyList()
    }
    val seasons = if (kind == "tvShow") allChildren.mapNotNull { it.season }.distinct().sorted() else emptyList()
    fun playNextEpisode() {
        scope.launch {
            actionError = null
            val episodes = allChildren.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
            var next: MediaDetail? = null
            for (episode in episodes) {
                val episodeDetail = runCatching {
                    if (online) api.detail(episode) else downloads.detail(episode.id) ?: error("Episode is not downloaded")
                }.getOrElse {
                    actionError = it.message
                    return@launch
                }
                if (!episodeDetail.isWatched) {
                    next = episodeDetail
                    break
                }
            }
            next?.let(::play) ?: run { actionError = "All episodes are watched." }
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
                    if (kind == "episode") detail.parentShow?.let { Text(it.name, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                    Text(title, fontSize = 38.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(heroMetadata(detail), fontSize = 14.sp, color = Color.White.copy(alpha = .75f), maxLines = 1)
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
                                label = if (detail.progressMinutes > 0) "Resume" else "Play",
                                onClick = { play(detail) },
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
                                        else downloads.startMediaDownload(api, detail)
                                    }
                                },
                            )
                        }
                        if (online && kind != "collection") {
                            val favorite = detail.isFavorite
                            DetailSecondaryAction(Icons.Default.Star, if (favorite) "Unfavorite" else "Favorite", { mutate(MediaUpdate(isFavorite = !favorite)) })
                        }
                        if (online) {
                            val pinned = detail.isPinned
                            DetailSecondaryAction(AppIcons.Pin, if (pinned) "Unpin" else "Pin", { mutate(MediaUpdate(isPinned = !pinned)) }, if (kind == "collection") Modifier.focusRequester(primaryFocus) else Modifier)
                        }
                        if (online && kind != "collection") {
                            val watched = detail.isWatched
                            DetailSecondaryAction(if (watched) AppIcons.Eye else AppIcons.EyeSlash, if (watched) "Mark unwatched" else "Mark watched", { mutate(MediaUpdate(isWatched = !watched)) })
                            DetailSecondaryAction(AppIcons.CollectionStack, "Add to collection", { showCollections = true })
                        }
                    }
                    actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    downloads.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                }
            }
        }
            if (kind != "collection") item { Metadata(detail) }
            if (kind == "episode") detail.parentShow?.let { parent -> item {
                SectionTitle("TV show")
                val parentItem = if (online) parent else downloads.previewFor(parent.id) ?: parent
                Row(Modifier.padding(horizontal = 38.dp).fillMaxWidth()) {
                    MediaCard(parentItem, api, { open(Route.Detail(parentItem)) }, Modifier.weight(1f), downloaded = downloads.hasDownload(parentItem.id))
                    Spacer(Modifier.weight(2f))
                }
            } }
            if (kind == "movie" || kind == "episode") item { Credits(detail.credits, open) }
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

    playbackChoice?.let { value ->
        PlaybackSourceDialog(
            onDismiss = { playbackChoice = null },
            onStream = { playbackChoice = null; launchPlayback(value, useDownload = false) },
            onDownload = { playbackChoice = null; launchPlayback(value, useDownload = true) },
        )
    }
    if (showTvDownloadDialog) TvShowDownloadDialog(
        nextCount = tvShowDownloadCount,
        onDismiss = { showTvDownloadDialog = false },
        onAll = { showTvDownloadDialog = false; downloads.startTvShowDownload(api, detail, null) },
        onNext = { showTvDownloadDialog = false; downloads.startTvShowDownload(api, detail, tvShowDownloadCount) },
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
                runCatching { api.updateCollection(id, remove = listOf(child.id)) }.onSuccess { setDetail(it); onMediaChanged() }.onFailure { actionError = it.message }
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

private fun videoIntent(context: Context, api: ApiClient, online: Boolean, detail: MediaDetail, url: String) = Intent(context, VideoActivity::class.java).apply {
    putExtra(VideoActivity.EXTRA_URL, url); putExtra(VideoActivity.EXTRA_TITLE, detail.title)
    if (online) putExtra(VideoActivity.EXTRA_BASE_URL, api.baseUrl)
    putExtra(VideoActivity.EXTRA_KIND, detail.kind); putExtra(VideoActivity.EXTRA_ID, detail.id)
    putExtra(VideoActivity.EXTRA_PROGRESS, detail.progressMinutes); putExtra(VideoActivity.EXTRA_DURATION, detail.durationMinutes)
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
    var person by remember(name) { mutableStateOf<PersonDetail?>(null) }
    var error by remember(name) { mutableStateOf<String?>(null) }
    var contextItem by remember { mutableStateOf<MediaPreview?>(null) }
    LaunchedEffect(name, online, downloads.revision) {
        runCatching { if (online) api.person(name) else downloads.person(name) }.onSuccess { person = it }.onFailure { error = it.message }
    }
    val value = person
    if (value == null) {
        error?.let { message ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
        }
        return
    }
    val roles = value.roles
    val grouped = buildList {
        add("Movies" to value.creditedMovies)
        add("Episodes" to value.creditedEpisodes)
        add("Cast" to value.credits.cast)
        add("Directed" to value.credits.directors)
        add("Co-directed" to value.credits.coDirectors)
        add("Written" to value.credits.screenwriters)
        add("Produced" to value.credits.producers)
        add("Executive produced" to value.credits.executiveProducers)
        add("Composed" to value.credits.composer)
    }.filter { it.second.isNotEmpty() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 38.dp, vertical = 30.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(value.name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Text(roles.joinToString("  •  "), color = MaterialTheme.colorScheme.primary) }
        grouped.forEach { (label, media) -> item { Text(label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(vertical = 8.dp)); ThreeColumnMediaGrid(media, api, downloads, { open(Route.Detail(it)) }, { contextItem = it }) } }
    }
    contextItem?.let { item ->
        MediaContextMenu(
            api, downloads, online, item, { contextItem = null }, { contextItem = null; onMediaChanged() },
            tvShowDownloadCount = tvShowDownloadCount,
        )
    }
}
