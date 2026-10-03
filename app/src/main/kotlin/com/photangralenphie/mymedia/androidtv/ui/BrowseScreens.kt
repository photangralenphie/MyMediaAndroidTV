package com.photangralenphie.mymedia.androidtv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.photangralenphie.mymedia.androidtv.data.ApiClient
import com.photangralenphie.mymedia.androidtv.data.BrowseSource
import com.photangralenphie.mymedia.androidtv.data.DownloadStore
import com.photangralenphie.mymedia.androidtv.data.MediaFilters
import com.photangralenphie.mymedia.androidtv.data.MediaPreview
import com.photangralenphie.mymedia.androidtv.data.Route
import com.photangralenphie.mymedia.androidtv.data.SettingsPage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Stable
class PagedMediaState {
    val items = mutableStateListOf<MediaPreview>()
    var page by mutableIntStateOf(0)
        private set
    var totalPages by mutableIntStateOf(1)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    suspend fun load(api: ApiClient, source: BrowseSource, filters: MediaFilters, reset: Boolean = false) {
        if (loading || (!reset && page >= totalPages)) return
        if (reset) {
            items.clear()
            page = 0
            totalPages = 1
        }
        loading = true
        error = null
        try {
            val result = api.mediaPage(source, page + 1, filters)
            items.addAll(result.items)
            page = result.page
            totalPages = result.totalPages
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load this page"
        } finally {
            loading = false
        }
    }
}

@Composable
fun BrowseScreen(
    api: ApiClient,
    downloads: DownloadStore,
    online: Boolean,
    source: BrowseSource,
    tvShowDownloadCount: Int,
    open: (Route) -> Unit,
    onMediaChanged: () -> Unit,
) {
    var filters by remember(source) { mutableStateOf(MediaFilters()) }
    var showFilters by remember { mutableStateOf(false) }
    var createCollection by remember { mutableStateOf(false) }
    var contextItem by remember { mutableStateOf<MediaPreview?>(null) }
    val scope = rememberCoroutineScope()
    val state = remember(source, filters) { PagedMediaState() }
    val useDownloads = !online || source == BrowseSource.Downloads
    val localItems = remember(downloads.revision, source, filters) { downloads.previews(source, filters) }
    val downloadedIds = remember(downloads.revision) { downloads.downloadedIds() }
    val displayedItems = if (useDownloads) localItems else state.items
    val initialFocus = remember(state, useDownloads) { FocusRequester() }
    val emptyOfflineFocus = remember { FocusRequester() }

    LaunchedEffect(state, useDownloads) {
        if (!useDownloads) state.load(api, source, filters, reset = true)
    }
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
                Button(onClick = { createCollection = true }) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("New collection")
                }
                Spacer(Modifier.width(12.dp))
            }
            if (source.supportsFilters) {
                OutlinedButton(onClick = { showFilters = true }) {
                    Icon(Icons.Default.MoreVert, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Filters${if (filters.activeCount > 0) " (${filters.activeCount})" else ""}")
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        if (displayedItems.isEmpty() && (useDownloads || !state.loading)) {
            Text(
                if (useDownloads) "No downloads yet" else state.error ?: "Nothing here yet",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f),
            )
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
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            itemsIndexed(displayedItems, key = { _, item -> "${item.kind}-${item.id}" }) { index, item ->
                MediaCard(
                    item = item,
                    api = api,
                    onClick = { open(Route.Detail(item)) },
                    modifier = Modifier.fillMaxWidth()
                        .then(if (index == 0) Modifier.focusRequester(initialFocus) else Modifier),
                    onContextMenu = { contextItem = item },
                    downloaded = item.id in downloadedIds,
                )
                if (!useDownloads && item == displayedItems.lastOrNull()) {
                    LaunchedEffect(item.id) { state.load(api, source, filters) }
                }
            }
        }
    }

    if (showFilters) {
        FilterDialog(api, downloads, online, filters, { showFilters = false }) {
            filters = it
            showFilters = false
        }
    }
    if (online && createCollection) {
        CreateCollectionDialog(api, onDismiss = { createCollection = false }) { preview ->
            createCollection = false
            onMediaChanged()
            open(Route.Detail(preview))
        }
    }
    contextItem?.let { item ->
        MediaContextMenu(
            api = api,
            downloads = downloads,
            online = online,
            item = item,
            onDismiss = { contextItem = null },
            onChanged = {
                contextItem = null
                onMediaChanged()
                if (!useDownloads) scope.launch { state.load(api, source, filters, reset = true) }
            },
            tvShowDownloadCount = tvShowDownloadCount,
        )
    }
}

@Composable
fun GenresScreen(
    api: ApiClient,
    downloads: DownloadStore,
    online: Boolean,
    route: Route.Genres,
    open: (Route) -> Unit,
) {
    var genres by remember(route) { mutableStateOf<List<String>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(route, online, downloads.revision) {
        if (online) {
            runCatching { api.genres(route.kind) }
                .onSuccess { genres = it; error = null }
                .onFailure { error = it.message }
        } else {
            genres = downloads.genres(route.kind)
            error = null
        }
    }
    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text(route.title, style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(20.dp))
        error?.let { Text(it) }
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(genres) { genre ->
                GenreCard(
                    name = genre,
                    onClick = { open(Route.Browse(BrowseSource.Genre(genre, route.kind))) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
fun SearchScreen(
    api: ApiClient,
    downloads: DownloadStore,
    online: Boolean,
    tvShowDownloadCount: Int,
    open: (Route) -> Unit,
    onMediaChanged: () -> Unit,
) {
    val searchFocus = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    var searchScope by remember { mutableStateOf("all") }
    var results by remember { mutableStateOf<List<MediaPreview>>(emptyList()) }
    var page by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(1) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showScopes by remember { mutableStateOf(false) }
    var contextItem by remember { mutableStateOf<MediaPreview?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val downloadedIds = remember(downloads.revision) { downloads.downloadedIds() }

    LaunchedEffect(Unit) { runCatching { searchFocus.requestFocus() } }

    fun search(reset: Boolean) {
        if (query.isBlank() || loading || (!reset && page >= totalPages)) return
        if (!online) {
            results = downloads.search(query)
            page = 1
            totalPages = 1
            error = null
            return
        }
        coroutineScope.launch {
            loading = true
            error = null
            try {
                val result = api.search(query.trim(), searchScope, if (reset) 1 else page + 1)
                results = if (reset) result.items else results + result.items
                page = result.page
                totalPages = result.totalPages
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error = failure.message ?: "Search failed"
            } finally {
                loading = false
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(28.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LabeledField(
                label = "Titles, descriptions, or credits",
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                onSubmit = { search(true) },
                focusRequester = searchFocus,
            )
            OutlinedButton(onClick = { showScopes = true }) {
                Text(searchScopeLabel(searchScope))
                Spacer(Modifier.width(8.dp))
                Text("▾")
            }
            Button(onClick = { search(true) }, enabled = query.isNotBlank()) {
                Icon(Icons.Default.Search, null)
                Spacer(Modifier.width(7.dp))
                Text("Search")
            }
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
                    item = item,
                    api = api,
                    onClick = { open(Route.Detail(item)) },
                    modifier = Modifier.fillMaxWidth(),
                    onContextMenu = { contextItem = item },
                    downloaded = item.id in downloadedIds,
                )
                if (online && item == results.lastOrNull()) {
                    LaunchedEffect(item.id) { search(false) }
                }
            }
        }
    }

    if (showScopes) {
        SearchScopeDialog(searchScope, { showScopes = false }) {
            searchScope = it
            showScopes = false
        }
    }
    contextItem?.let { item ->
        MediaContextMenu(
            api = api,
            downloads = downloads,
            online = online,
            item = item,
            onDismiss = { contextItem = null },
            onChanged = {
                contextItem = null
                onMediaChanged()
                search(true)
            },
            tvShowDownloadCount = tvShowDownloadCount,
        )
    }
}

private fun searchScopeLabel(scope: String) = when (scope) {
    "title" -> "Titles"
    "description" -> "Descriptions"
    "credits" -> "Credits"
    else -> "Everything"
}

@Composable
private fun SearchScopeDialog(selected: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(420.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("Search in", style = MaterialTheme.typography.headlineSmall)
            listOf("all", "title", "description", "credits").forEach { value ->
                if (selected == value) {
                    Button(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) {
                        Text(searchScopeLabel(value), Modifier.fillMaxWidth())
                    }
                } else {
                    OutlinedButton(onClick = { onSelect(value) }, modifier = Modifier.fillMaxWidth()) {
                        Text(searchScopeLabel(value), Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterDialog(
    api: ApiClient,
    downloads: DownloadStore,
    online: Boolean,
    initial: MediaFilters,
    onDismiss: () -> Unit,
    onApply: (MediaFilters) -> Unit,
) {
    var value by remember(initial) { mutableStateOf(initial) }
    var genres by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(online, downloads.revision) {
        genres = if (online) runCatching { api.genres("both") }.getOrDefault(emptyList()) else downloads.genres("both")
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(760.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
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
                    val selected = genre in value.genres
                    if (selected) {
                        Button(onClick = { value = value.copy(genres = value.genres - genre) }) {
                            Text(genre, maxLines = 1, fontSize = 12.sp)
                        }
                    } else {
                        OutlinedButton(onClick = { value = value.copy(genres = value.genres + genre) }) {
                            Text(genre, maxLines = 1, fontSize = 12.sp)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = { value = MediaFilters() }) { Text("Clear") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(10.dp))
                Button(onClick = { onApply(value) }) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun TriStateButton(label: String, value: Boolean?, onChange: (Boolean?) -> Unit) {
    val text = "$label: ${when (value) { true -> "Yes"; false -> "No"; null -> "Any" }}"
    OutlinedButton(onClick = { onChange(when (value) { null -> true; true -> false; false -> null }) }) {
        Icon(if (value == null) Icons.Default.MoreVert else Icons.Default.CheckCircle, null)
        Spacer(Modifier.width(7.dp))
        Text(text)
    }
}

@Composable
fun CreateCollectionDialog(
    api: ApiClient,
    initialMediaId: String? = null,
    onDismiss: () -> Unit,
    onCreated: (MediaPreview) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(600.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("New collection", style = MaterialTheme.typography.headlineMedium)
            LabeledField("Title", title, { title = it }, Modifier.fillMaxWidth())
            LabeledField("Description (optional)", description, { description = it }, Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(10.dp))
                Button(
                    enabled = title.isNotBlank() && !saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            try {
                                val created = api.createCollection(title.trim(), description, listOfNotNull(initialMediaId))
                                onCreated(created.toPreview())
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message
                            } finally {
                                saving = false
                            }
                        }
                    },
                ) { Text(if (saving) "Creating…" else "Create") }
            }
        }
    }
}
