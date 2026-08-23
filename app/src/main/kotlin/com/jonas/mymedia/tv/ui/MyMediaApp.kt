package com.jonas.mymedia.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.jonas.mymedia.tv.LabeledField
import com.jonas.mymedia.tv.R
import com.jonas.mymedia.tv.data.ApiClient
import com.jonas.mymedia.tv.data.ApiActivity
import com.jonas.mymedia.tv.data.BrowseSource
import com.jonas.mymedia.tv.data.DownloadEntry
import com.jonas.mymedia.tv.data.DownloadStore
import com.jonas.mymedia.tv.data.MediaFilters
import com.jonas.mymedia.tv.data.MediaPreview
import com.jonas.mymedia.tv.data.Route
import com.jonas.mymedia.tv.data.SettingsPage
import kotlinx.coroutines.launch

private data class MenuItem(val label: String, val icon: ImageVector, val route: Route)

private data class DownloadListItem(
    val entry: DownloadEntry,
    val indented: Boolean = false,
    val subtitle: String,
    val displayedSizeBytes: Long = entry.sizeBytes,
)

@Composable
fun MyMediaApp(
    api: ApiClient,
    downloads: DownloadStore,
    online: Boolean,
    offlineMessage: String?,
    initialHost: String,
    initialPort: Int,
    playEpisodesDirectly: Boolean,
    tvShowDownloadCount: Int,
    onSaveEndpoint: (String, Int) -> Unit,
    onEpisodeBehaviorChange: (Boolean) -> Unit,
    onTvShowDownloadCountChange: (Int) -> Unit,
) {
    val history = remember { mutableStateListOf<Route>(Route.Browse(BrowseSource.Unwatched)) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerFocus = remember { FocusRequester() }
    var mediaRevision by remember { mutableIntStateOf(0) }
    val current = history.last()
    val isGenreResults = current is Route.Browse && current.source is BrowseSource.Genre
    val hasSidebar = (current is Route.Browse && !isGenreResults) || current is Route.Genres || current == Route.Search
    LaunchedEffect(online) {
        if (!online && history.last() !is Route.Detail && history.last() !is Route.Person && history.last() !is Route.Settings) {
            history.clear()
            history.add(Route.Browse(BrowseSource.Downloads))
        }
    }
    fun open(route: Route) {
        drawerState.setValue(DrawerValue.Closed)
        if (route == current) return
        if ((route is Route.Browse && route.source !is BrowseSource.Genre) || route is Route.Genres || route == Route.Search) history.clear()
        history.add(route)
    }
    // A closed sidebar owns Back so the first press reveals the app navigation. Once the
    // sidebar is open, Back is deliberately left to the Activity and exits the app. Full-screen
    // destinations keep their normal navigate-up behavior.
    BackHandler(
        enabled = if (hasSidebar) drawerState.currentValue == DrawerValue.Closed else history.size > 1,
    ) {
        if (hasSidebar) drawerState.setValue(DrawerValue.Open)
        else history.removeAt(history.lastIndex)
    }

    val menu = remember(online) {
        if (!online) listOf(
            "OFFLINE" to listOf(MenuItem("Downloads", AppIcons.Download, Route.Browse(BrowseSource.Downloads))),
        ) else listOf(
            "LIBRARY" to listOf(
                MenuItem("Unwatched", AppIcons.EyeSlash, Route.Browse(BrowseSource.Unwatched)),
                MenuItem("Genres", AppIcons.TheaterMasks, Route.Genres("Library Genres", "both")),
                MenuItem("Collections", AppIcons.CollectionStack, Route.Browse(BrowseSource.Collections)),
                MenuItem("Downloads", AppIcons.Download, Route.Browse(BrowseSource.Downloads)),
                MenuItem("Favorites", Icons.Default.Star, Route.Browse(BrowseSource.Favorites)),
                MenuItem("Search", Icons.Default.Search, Route.Search),
            ),
            "MOVIES" to listOf(
                MenuItem("All Movies", AppIcons.MovieClapper, Route.Browse(BrowseSource.Movies)),
                MenuItem("Genres", AppIcons.TheaterMasks, Route.Genres("Movie Genres", "movies")),
            ),
            "TV SHOWS" to listOf(
                MenuItem("All TV Shows", AppIcons.Tv, Route.Browse(BrowseSource.TvShows)),
                MenuItem("Genres", AppIcons.TheaterMasks, Route.Genres("TV Show Genres", "tvShows")),
            ),
        )
    }
    val pinned = remember(mediaRevision) { PagedState() }
    LaunchedEffect(pinned, online) { if (online) pinned.load(api, BrowseSource.Pinned, MediaFilters(), reset = true) }
    val pinnedItems = if (online) pinned.items else emptyList()

    // Detail destinations use the full canvas, like modern streaming apps. Keeping the
    // drawer out of this composition also prevents its retained focus from reopening it.
    if (current is Route.Detail) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            DetailScreen(api, downloads, online, current.preview, playEpisodesDirectly, tvShowDownloadCount, ::open, { mediaRevision++ }) { history.removeAt(history.lastIndex) }
            StatusIndicators(downloads, online, offlineMessage, onOfflineClick = { open(Route.Settings(SettingsPage.Server)) })
        }
        return
    }
    if (current is Route.Person) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            PersonScreen(api, downloads, online, current.name, tvShowDownloadCount, ::open) { mediaRevision++ }
            StatusIndicators(downloads, online, offlineMessage, onOfflineClick = { open(Route.Settings(SettingsPage.Server)) })
        }
        return
    }
    if (current is Route.Settings) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            SettingsScreen(current.page, downloads, initialHost, initialPort, playEpisodesDirectly, tvShowDownloadCount, onSaveEndpoint, onEpisodeBehaviorChange, onTvShowDownloadCountChange)
            StatusIndicators(downloads, online, offlineMessage, onOfflineClick = { open(Route.Settings(SettingsPage.Server)) })
        }
        return
    }
    if (isGenreResults) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            BrowseScreen(api, downloads, online, (current as Route.Browse).source, tvShowDownloadCount, ::open) { mediaRevision++ }
            StatusIndicators(downloads, online, offlineMessage, onOfflineClick = { open(Route.Settings(SettingsPage.Server)) })
        }
        return
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = { drawerValue ->
            val expanded = drawerValue == DrawerValue.Open
            Column(
                // NavigationDrawerItem's TV defaults are 256 dp open and 56 dp closed.
                // The extra 20 dp accounts for the item's 10 dp horizontal margins.
                Modifier.fillMaxHeight().width(if (expanded) 276.dp else 76.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .98f)).padding(vertical = 18.dp)
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(painterResource(R.drawable.app_icon), null, Modifier.size(42.dp))
                    if (expanded) Text("MyMedia", color = MaterialTheme.colorScheme.primary, fontSize = 20.sp)
                }
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 6.dp)) {
                    menu.forEach { (group, entries) ->
                        if (expanded) item { Text(group, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f), modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 5.dp)) }
                        items(entries) { entry ->
                            DrawerEntry(
                                entry.label,
                                entry.icon,
                                current == entry.route,
                                expanded,
                                if (entry.route == menu.firstOrNull()?.second?.firstOrNull()?.route) Modifier.focusRequester(drawerFocus) else Modifier,
                            ) { open(entry.route) }
                        }
                    }
                    if (expanded && pinnedItems.isNotEmpty()) item { Text("PINNED", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .48f), modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 5.dp)) }
                    items(pinnedItems, key = { "${it.kind}-${it.id}" }) { item ->
                        val icon = when (item.kind) {
                            "movie" -> AppIcons.MovieClapper
                            "tvShow", "episode" -> AppIcons.Tv
                            else -> AppIcons.CollectionStack
                        }
                        DrawerEntry(item.name, icon, false, expanded) { open(Route.Detail(item)) }
                        if (online && item == pinnedItems.lastOrNull()) {
                            LaunchedEffect(item.id) { pinned.load(api, BrowseSource.Pinned, MediaFilters()) }
                        }
                    }
                }
                DrawerEntry("Settings", Icons.Default.Settings, current is Route.Settings, expanded) { open(Route.Settings()) }
            }
        }
    ) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(start = 84.dp)) {
            when (val route = current) {
                is Route.Browse -> BrowseScreen(api, downloads, online, route.source, tvShowDownloadCount, ::open) { mediaRevision++ }
                is Route.Genres -> GenresScreen(api, downloads, online, route, ::open)
                Route.Search -> SearchScreen(api, downloads, online, tvShowDownloadCount, ::open) { mediaRevision++ }
                is Route.Settings -> Unit
                is Route.Detail, is Route.Person -> Unit
            }
            StatusIndicators(
                downloads,
                online,
                offlineMessage,
                { open(Route.Settings(SettingsPage.Server)) },
                drawerFocus,
            )
        }
    }
}

@Composable
private fun BoxScope.StatusIndicators(
    downloads: DownloadStore,
    online: Boolean,
    message: String?,
    onOfflineClick: () -> Unit,
    drawerFocus: FocusRequester? = null,
) {
    Column(
        modifier = Modifier.align(Alignment.TopEnd).padding(18.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        downloads.activeTitles.entries.firstOrNull()?.let { (id, title) ->
            Row(
                Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = .94f), RoundedCornerShape(28.dp)).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(AppIcons.Download, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    buildString {
                        append("Downloading ").append(title)
                        downloads.activeProgress[id]?.let { append("  •  ").append(it) }
                    },
                    maxLines = 1,
                )
            }
        }
        if (ApiActivity.activeRequests > 0) {
            CircularProgressIndicator(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .94f), RoundedCornerShape(28.dp))
                    .padding(11.dp)
                    .size(20.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }
        if (!online) Button(
            onClick = onOfflineClick,
            modifier = if (drawerFocus != null) Modifier.focusProperties { left = drawerFocus } else Modifier,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            colors = ButtonDefaults.colors(
                containerColor = Color(0xFFFFC107),
                contentColor = Color.Black,
                focusedContainerColor = Color(0xFFFFD54F),
                focusedContentColor = Color.Black,
            ),
        ) {
            Icon(Icons.Default.Warning, message ?: "Server unavailable", Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text("Offline", maxLines = 1, fontSize = 13.sp)
        }
    }
}

@Composable
private fun NavigationDrawerScope.DrawerEntry(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    expanded: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        selected = selected,
        onClick = onClick,
        leadingContent = { Icon(icon, null, Modifier.size(24.dp)) },
        modifier = modifier.padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        if (expanded) Text(label, maxLines = 1)
    }
}

@Stable
private class PagedState {
    val items = mutableStateListOf<MediaPreview>()
    var page by mutableIntStateOf(0)
    var totalPages by mutableIntStateOf(1)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    suspend fun load(api: ApiClient, source: BrowseSource, filters: MediaFilters, reset: Boolean = false) {
        if (loading || (!reset && page >= totalPages)) return
        if (reset) { items.clear(); page = 0; totalPages = 1 }
        loading = true; error = null
        runCatching { api.mediaPage(source, page + 1, filters) }
            .onSuccess { result -> items.addAll(result.items); page = result.page; totalPages = result.totalPages }
            .onFailure { error = it.message ?: "Could not load this page" }
        loading = false
    }
}

@Composable
private fun BrowseScreen(api: ApiClient, downloads: DownloadStore, online: Boolean, source: BrowseSource, tvShowDownloadCount: Int, open: (Route) -> Unit, onMediaChanged: () -> Unit) {
    var filters by remember(source) { mutableStateOf(MediaFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    var createCollection by remember { mutableStateOf(false) }
    var contextItem by remember { mutableStateOf<MediaPreview?>(null) }
    val scope = rememberCoroutineScope()
    val state = remember(source, filters) { PagedState() }
    val useDownloads = !online || source == BrowseSource.Downloads
    val localItems = remember(downloads.revision, source, filters) { downloads.previews(source, filters) }
    val downloadedIds = remember(downloads.revision) { downloads.downloadedIds() }
    val displayedItems = if (useDownloads) localItems else state.items
    val initialFocus = remember(state, useDownloads) { FocusRequester() }
    val emptyOfflineFocus = remember { FocusRequester() }
    LaunchedEffect(state, useDownloads) { if (!useDownloads) state.load(api, source, filters, reset = true) }
    LaunchedEffect(displayedItems.firstOrNull()?.id) {
        if (displayedItems.isNotEmpty()) runCatching { initialFocus.requestFocus() }
    }
    LaunchedEffect(online, displayedItems.isEmpty(), useDownloads) {
        if (!online && useDownloads && displayedItems.isEmpty()) runCatching { emptyOfflineFocus.requestFocus() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(source.title, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.weight(1f))
            if (online && source == BrowseSource.Collections) {
                Button(onClick = { createCollection = true }) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("New collection") }
                Spacer(Modifier.width(12.dp))
            }
            if (source.supportsFilters) {
                OutlinedButton(onClick = { showFilters = true }) {
                    Icon(Icons.Default.MoreVert, null); Spacer(Modifier.width(8.dp)); Text("Filters${if (filters.activeCount > 0) " (${filters.activeCount})" else ""}")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (displayedItems.isEmpty() && (useDownloads || !state.loading)) {
            Text(if (useDownloads) "No downloads yet" else state.error ?: "Nothing here yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
            if (!online && useDownloads) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = { open(Route.Settings(SettingsPage.Server)) },
                    modifier = Modifier.focusRequester(emptyOfflineFocus),
                ) { Text("Server settings") }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(top = 12.dp, bottom = 36.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(displayedItems, key = { _, item -> "${item.kind}-${item.id}" }) { index, item ->
                MediaCard(
                    item,
                    api,
                    { open(Route.Detail(item)) },
                    Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(initialFocus) else Modifier),
                    onContextMenu = { contextItem = item },
                    downloaded = item.id in downloadedIds,
                )
                if (!useDownloads && item == displayedItems.lastOrNull()) LaunchedEffect(item.id) { state.load(api, source, filters) }
            }
        }
    }
    if (showFilters) FilterDialog(api, downloads, online, filters, { showFilters = false }) { filters = it; showFilters = false }
    if (online && createCollection) CreateCollectionDialog(api, onDismiss = { createCollection = false }) { preview -> createCollection = false; onMediaChanged(); open(Route.Detail(preview)) }
    contextItem?.let { item ->
        MediaContextMenu(
            api, downloads, online, item, { contextItem = null },
            {
                contextItem = null
                onMediaChanged()
                if (!useDownloads) scope.launch { state.load(api, source, filters, reset = true) }
            },
            tvShowDownloadCount = tvShowDownloadCount,
        )
    }
}

@Composable
private fun GenresScreen(api: ApiClient, downloads: DownloadStore, online: Boolean, route: Route.Genres, open: (Route) -> Unit) {
    var genres by remember(route) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route, online, downloads.revision) {
        if (online) runCatching { api.genres(route.kind) }.onSuccess { genres = it }.onFailure { error = it.message }
        else { genres = downloads.genres(route.kind); error = null }
    }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text(route.title, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))
        if (error != null) Text(error!!)
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(genres) { genre -> GenreCard(genre, { open(Route.Browse(BrowseSource.Genre(genre, route.kind))) }, Modifier.fillMaxWidth()) }
        }
    }
}

@Composable
private fun SearchScreen(api: ApiClient, downloads: DownloadStore, online: Boolean, tvShowDownloadCount: Int, open: (Route) -> Unit, onMediaChanged: () -> Unit) {
    val searchFocus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var scope by remember { mutableStateOf("all") }
    var results by remember { mutableStateOf<List<MediaPreview>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var pages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showScopes by remember { mutableStateOf(false) }
    var contextItem by remember { mutableStateOf<MediaPreview?>(null) }
    val coroutine = rememberCoroutineScope()
    val downloadedIds = remember(downloads.revision) { downloads.downloadedIds() }
    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }
    fun search(reset: Boolean) {
        if (query.isBlank() || loading || (!reset && page >= pages)) return
        if (!online) {
            results = downloads.search(query)
            page = 1
            pages = 1
            error = null
            return
        }
        coroutine.launch {
            loading = true; error = null
            runCatching { api.search(query.trim(), scope, if (reset) 1 else page + 1) }.onSuccess {
                results = if (reset) it.items else results + it.items; page = it.page; pages = it.totalPages
            }.onFailure { error = it.message ?: "Search failed" }
            loading = false
        }
    }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledField(
                "Titles, descriptions, or credits",
                query,
                { query = it },
                Modifier.weight(1f),
                onSubmit = { search(true) },
                focusRequester = searchFocus,
            )
            OutlinedButton(onClick = { showScopes = true }) { Text(scopeLabel(scope)); Spacer(Modifier.width(8.dp)); Text("▾") }
            Button(onClick = { search(true) }, enabled = query.isNotBlank()) { Icon(Icons.Default.Search, null); Spacer(Modifier.width(7.dp)); Text("Search") }
        }
        Spacer(Modifier.height(20.dp))
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 10.dp)) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(results, key = { "${it.kind}-${it.id}" }) { item ->
                MediaCard(
                    item,
                    api,
                    { open(Route.Detail(item)) },
                    Modifier.fillMaxWidth(),
                    onContextMenu = { contextItem = item },
                    downloaded = item.id in downloadedIds,
                )
                if (online && item == results.lastOrNull()) LaunchedEffect(item.id) { search(false) }
            }
        }
    }
    if (showScopes) ScopeDialog(scope, { showScopes = false }) { scope = it; showScopes = false }
    contextItem?.let { item ->
        MediaContextMenu(
            api, downloads, online, item, { contextItem = null },
            { contextItem = null; onMediaChanged(); search(true) },
            tvShowDownloadCount = tvShowDownloadCount,
        )
    }
}

private fun scopeLabel(scope: String) = when (scope) {
    "title" -> "Titles"
    "description" -> "Descriptions"
    "credits" -> "Credits"
    else -> "Everything"
}

@Composable
private fun ScopeDialog(selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(420.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("Search in", style = MaterialTheme.typography.headlineSmall)
            listOf("all", "title", "description", "credits").forEach { value ->
                if (selected == value) Button(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) { Text(scopeLabel(value), Modifier.fillMaxWidth()) }
                else OutlinedButton(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) { Text(scopeLabel(value), Modifier.fillMaxWidth()) }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
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
    var page by remember(initialPage) { mutableStateOf(if (initialPage == SettingsPage.Root) SettingsPage.Behaviour else initialPage) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxHeight().width(270.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .96f)).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(bottom = 16.dp))
            listOf(
                Triple(SettingsPage.Behaviour, "Behaviour", Icons.Default.CheckCircle),
                Triple(SettingsPage.Server, "Server", Icons.Default.Settings),
                Triple(SettingsPage.Downloads, "Downloads", AppIcons.Download),
            ).forEach { (target, label, icon) ->
                OutlinedButton(
                    onClick = { page = target },
                    modifier = Modifier.fillMaxWidth(),
                    colors = if (page == target) ButtonDefaults.colors() else androidx.tv.material3.OutlinedButtonDefaults.colors(),
                    border = if (page == target) ButtonDefaults.border() else androidx.tv.material3.OutlinedButtonDefaults.border(),
                ) {
                    Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(9.dp)); Text(label, Modifier.fillMaxWidth())
                }
            }
        }
        Column(Modifier.fillMaxHeight().width(760.dp).padding(horizontal = 38.dp, vertical = 34.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            when (page) {
            SettingsPage.Root, SettingsPage.Behaviour -> {
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
                    OutlinedButton(onClick = { onTvShowDownloadCountChange((tvShowDownloadCount - 1).coerceAtLeast(1)) }, enabled = tvShowDownloadCount > 1) { Text("−") }
                    Text("$tvShowDownloadCount episodes", modifier = Modifier.width(150.dp))
                    OutlinedButton(onClick = { onTvShowDownloadCountChange((tvShowDownloadCount + 1).coerceAtMost(50)) }, enabled = tvShowDownloadCount < 50) { Text("+") }
                }
            }
            SettingsPage.Server -> {
                Text("Server", style = MaterialTheme.typography.headlineSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabeledField(
                        "IP address or host",
                        host,
                        { host = it },
                        Modifier.weight(1f),
                        keyboardType = KeyboardType.Number,
                        onSubmit = {},
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                    )
                    LabeledField(
                        "Port",
                        port,
                        { port = it.filter(Char::isDigit) },
                        Modifier.width(150.dp),
                        keyboardType = KeyboardType.Number,
                        onSubmit = {},
                        imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                    )
                }
                Button(onClick = { port.toIntOrNull()?.let { onSave(host, it) } }, enabled = host.isNotBlank() && port.toIntOrNull() != null) { Text("Save and reconnect") }
            }
            SettingsPage.Downloads -> {
                val downloadItems = remember(downloads.revision) {
                    val all = downloads.entries()
                    val playable = all.filter { it.video?.isFile == true }
                    val episodesByShow = playable.filter { it.kind == "episode" && it.parentId != null }
                        .groupBy { it.parentId }
                    val shows = all.filter { it.kind == "tvShow" && episodesByShow[it.id].orEmpty().isNotEmpty() }
                    val moviesAndOtherVideos = playable.filter { it.kind != "episode" && it.kind != "tvShow" }
                    val knownShowIds = shows.mapTo(mutableSetOf()) { it.id }
                    val orphanedEpisodes = playable.filter { it.kind == "episode" && it.parentId !in knownShowIds }

                    buildList {
                        moviesAndOtherVideos.forEach { entry ->
                            add(DownloadListItem(entry, subtitle = formatBytes(entry.sizeBytes)))
                        }
                        shows.forEach { show ->
                            val episodes = episodesByShow[show.id].orEmpty()
                                .sortedWith(compareBy({ it.metadata.optInt("season") }, { it.metadata.optInt("episode") }))
                            add(
                                DownloadListItem(
                                    entry = show,
                                    subtitle = "${episodes.size} ${if (episodes.size == 1) "episode" else "episodes"}  •  ${formatBytes(show.sizeBytes + episodes.sumOf { it.sizeBytes })}",
                                    displayedSizeBytes = show.sizeBytes + episodes.sumOf { it.sizeBytes },
                                )
                            )
                            episodes.forEach { episode ->
                                val season = episode.metadata.optInt("season")
                                val number = episode.metadata.optInt("episode")
                                add(
                                    DownloadListItem(
                                        entry = episode,
                                        indented = true,
                                        subtitle = "Season $season  •  Episode $number  •  ${formatBytes(episode.sizeBytes)}",
                                    )
                                )
                            }
                        }
                        orphanedEpisodes.forEach { episode ->
                            val season = episode.metadata.optInt("season")
                            val number = episode.metadata.optInt("episode")
                            add(
                                DownloadListItem(
                                    entry = episode,
                                    subtitle = "Season $season  •  Episode $number  •  ${formatBytes(episode.sizeBytes)}",
                                )
                            )
                        }
                    }
                }
                val topLevelCount = downloadItems.count { !it.indented }
                val totalSize = downloadItems.sumOf { if (it.entry.kind == "tvShow") it.entry.sizeBytes else it.displayedSizeBytes }
                Text("Downloads", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "$topLevelCount ${if (topLevelCount == 1) "title" else "titles"}  •  ${formatBytes(totalSize)}",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
                )
                if (downloadItems.isEmpty()) Text("No downloads yet", color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f))
                LazyColumn(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(downloadItems, key = { "${it.entry.kind}-${it.entry.id}" }) { item ->
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
                            OutlinedButton(onClick = { scope.launch { downloads.delete(entry.id) } }) { Text("Delete") }
                        }
                    }
                }
                Button(onClick = { confirmDeleteAll = true }, enabled = downloadItems.isNotEmpty()) { Text("Delete all downloads") }
            }
        }
        }
    }
    if (confirmDeleteAll) Dialog(onDismissRequest = { confirmDeleteAll = false }) {
        Column(Modifier.width(500.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Delete all downloads?", style = MaterialTheme.typography.headlineSmall)
            Text("Downloaded videos, artwork, and offline metadata will be removed.")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") }
                Spacer(Modifier.width(10.dp))
                Button(onClick = { confirmDeleteAll = false; scope.launch { downloads.deleteAll() } }) { Text("Delete all") }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun FilterDialog(api: ApiClient, downloads: DownloadStore, online: Boolean, initial: MediaFilters, onDismiss: () -> Unit, onApply: (MediaFilters) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(online, downloads.revision) { genres = if (online) runCatching { api.genres("both") }.getOrDefault(emptyList()) else downloads.genres("both") }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(760.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(26.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Filters", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField("From year", value.minYear, { value = value.copy(minYear = it.filter(Char::isDigit)) }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
                LabeledField("To year", value.maxYear, { value = value.copy(maxYear = it.filter(Char::isDigit)) }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
                LabeledField("Min minutes", value.minLength, { value = value.copy(minLength = it.filter(Char::isDigit)) }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
                LabeledField("Max minutes", value.maxLength, { value = value.copy(maxLength = it.filter(Char::isDigit)) }, Modifier.weight(1f), keyboardType = KeyboardType.Number)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                TriStateButton("Favorite", value.favorite) { value = value.copy(favorite = it) }
                TriStateButton("Watched", value.watched) { value = value.copy(watched = it) }
            }
            Text("Genres", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.height(190.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(genres) { genre ->
                    val checked = genre in value.genres
                    if (checked) Button(onClick = { value = value.copy(genres = value.genres - genre) }) { Text(genre, maxLines = 1, fontSize = 12.sp) }
                    else OutlinedButton(onClick = { value = value.copy(genres = value.genres + genre) }) { Text(genre, maxLines = 1, fontSize = 12.sp) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { value = MediaFilters() }) { Text("Clear") }; Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }; Spacer(Modifier.width(10.dp))
                Button(onClick = { onApply(value) }) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun TriStateButton(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    val text = "$label: ${when (value) { true -> "Yes"; false -> "No"; null -> "Any" }}"
    OutlinedButton(onClick = { onChange(when (value) { null -> true; true -> false; false -> null }) }) { Icon(if (value == null) Icons.Default.MoreVert else Icons.Default.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text(text) }
}

@Composable
fun CreateCollectionDialog(api: ApiClient, initialMediaId: String? = null, onDismiss: () -> Unit, onCreated: (MediaPreview) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(600.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(26.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("New collection", style = MaterialTheme.typography.headlineMedium)
            LabeledField("Title", title, { title = it }, Modifier.fillMaxWidth())
            LabeledField("Description (optional)", description, { description = it }, Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }; Spacer(Modifier.width(10.dp))
                Button(enabled = title.isNotBlank() && !saving, onClick = {
                    scope.launch { saving = true; runCatching { api.createCollection(title.trim(), description, listOfNotNull(initialMediaId)) }.onSuccess { onCreated(MediaPreview.from(it)) }.onFailure { error = it.message }; saving = false }
                }) { Text(if (saving) "Creating…" else "Create") }
            }
        }
    }
}
