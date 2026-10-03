package com.photangralenphie.mymedia.androidtv.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import com.photangralenphie.mymedia.androidtv.data.ApiClient
import com.photangralenphie.mymedia.androidtv.data.DownloadStore
import com.photangralenphie.mymedia.androidtv.data.MediaCredits
import com.photangralenphie.mymedia.androidtv.data.MediaDetail
import com.photangralenphie.mymedia.androidtv.data.MediaPreview
import com.photangralenphie.mymedia.androidtv.data.Route

@Composable
internal fun PlaybackSourceDialog(onDismiss: () -> Unit, onStream: () -> Unit, onDownload: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(470.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Choose playback source", style = MaterialTheme.typography.headlineSmall)
            Button(onClick = onStream, modifier = Modifier.fillMaxWidth()) {
                Text("Stream from server", Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text("Play downloaded file", Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", Modifier.fillMaxWidth())
            }
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
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text("Next $nextCount unwatched episodes", Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = onAll, modifier = Modifier.fillMaxWidth()) {
                Text("All episodes", Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel", Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
internal fun DetailPrimaryAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
internal fun DetailSecondaryAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
internal fun ThreeColumnMediaGrid(
    items: List<MediaPreview>,
    api: ApiClient,
    downloads: DownloadStore,
    onClick: (MediaPreview) -> Unit,
    onContextMenu: (MediaPreview) -> Unit,
) {
    val downloadedIds = remember(downloads.revision) { downloads.downloadedIds() }
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 38.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items.chunked(3).forEach { rowItems ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                rowItems.forEach { item ->
                    MediaCard(
                        item = item,
                        api = api,
                        onClick = { onClick(item) },
                        modifier = Modifier.weight(1f),
                        onContextMenu = { onContextMenu(item) },
                        downloaded = item.id in downloadedIds,
                    )
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
internal fun SeasonDialog(seasons: List<Int>, selected: Int, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.width(420.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium).padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text("Select season", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(seasons) { season ->
                    if (season == selected) {
                        Button(onClick = { onSelect(season) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Season $season", Modifier.fillMaxWidth())
                        }
                    } else {
                        OutlinedButton(onClick = { onSelect(season) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Season $season", Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

internal fun heroMetadata(detail: MediaDetail): String = buildList {
    if (detail.kind == "episode") add("S${detail.season ?: 0} E${detail.episode ?: 0}")
    detail.year?.let { add(it.toString()) }
    if (detail.kind == "tvShow") {
        val episodes = detail.children
        val count = episodes.mapNotNull { it.season }.distinct().size
        if (count > 0) add("$count ${if (count == 1) "season" else "seasons"}")
        if (episodes.isNotEmpty()) add("${episodes.size} ${if (episodes.size == 1) "episode" else "episodes"}")
    } else {
        detail.durationMinutes.takeIf { it > 0 }?.let { add("${it}m") }
    }
    formatRating(detail.rating)?.let(::add)
    detail.videoQuality?.let(::add)
    detail.genres.takeIf { it.isNotEmpty() }?.let { add(it.joinToString(" · ")) }
    if (detail.kind == "collection") add("${detail.numberOfItems ?: detail.children.size} titles")
}.joinToString("   •   ")

private fun formatRating(value: String?): String? {
    if (value == null) return null
    val first = value.indexOf('|')
    if (first < 0) return null
    val second = value.indexOf('|', first + 1)
    return if (second > first + 1) value.substring(first + 1, second) else null
}

@Composable
internal fun Metadata(detail: MediaDetail) {
    val facts = buildList {
        detail.releaseDate?.let { add("Released" to it) }
        detail.studio?.let { add("Studio" to it) }
        detail.network?.let { add("Network" to it) }
        detail.networks.takeIf { it.isNotEmpty() }?.let { add("Networks" to it.joinToString()) }
        detail.languages.takeIf { it.isNotEmpty() }?.let { add("Languages" to it.joinToString()) }
        if (detail.kind == "movie" || detail.kind == "episode") {
            add("Playback" to "${detail.progressMinutes} of ${detail.durationMinutes} min")
        }
    }.filter { it.second.isNotBlank() }
    if (facts.isEmpty()) return
    Column(Modifier.padding(horizontal = 38.dp, vertical = 10.dp)) {
        SectionTitle("Details", 0.dp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(34.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            facts.forEach { (label, value) ->
                Column(Modifier.width(150.dp)) {
                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .5f))
                    Text(value.substringBefore('T'), fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun Credits(credits: MediaCredits, open: (Route) -> Unit) {
    val groups = listOf(
        "Cast" to credits.cast,
        "Directors" to credits.directors,
        "Co-directors" to credits.coDirectors,
        "Writers" to credits.screenwriters,
        "Producers" to credits.producers,
        "Executive producers" to credits.executiveProducers,
        "Composer" to listOfNotNull(credits.composer),
    ).filter { it.second.isNotEmpty() }
    Column(Modifier.padding(top = 8.dp)) {
        SectionTitle("Credits")
        groups.forEach { (role, people) ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 38.dp, vertical = 5.dp)) {
                Text(
                    role,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 5.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    people.forEach { person ->
                        OutlinedButton(onClick = { open(Route.Person(person)) }) { Text(person, fontSize = 13.sp) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SectionTitle(text: String, horizontalPadding: Dp = 38.dp) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 12.dp),
    )
}
