package com.photangralenphie.mymedia.androidtv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerScope
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.photangralenphie.mymedia.androidtv.R
import com.photangralenphie.mymedia.androidtv.data.ApiActivity
import com.photangralenphie.mymedia.androidtv.data.ApiClient
import com.photangralenphie.mymedia.androidtv.data.BrowseSource
import com.photangralenphie.mymedia.androidtv.data.DownloadStore
import com.photangralenphie.mymedia.androidtv.data.MediaFilters
import com.photangralenphie.mymedia.androidtv.data.MediaPreview
import com.photangralenphie.mymedia.androidtv.data.Route
import com.photangralenphie.mymedia.androidtv.data.SettingsPage

private data class MenuItem(val label: String, val icon: ImageVector, val route: Route)

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
    val pinned = remember(mediaRevision) { PagedMediaState() }
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
