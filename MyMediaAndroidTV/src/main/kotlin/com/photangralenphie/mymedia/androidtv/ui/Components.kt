package com.photangralenphie.mymedia.androidtv.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Border
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.photangralenphie.mymedia.androidtv.data.ApiClient
import com.photangralenphie.mymedia.androidtv.data.ApiActivity
import com.photangralenphie.mymedia.androidtv.data.MediaPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import java.io.File
import java.net.URI

@Composable
fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onSubmit: (() -> Unit)? = null,
    imeAction: ImeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Done,
    focusRequester: FocusRequester? = null,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun submit() {
        if (imeAction == ImeAction.Next) {
            focusManager.moveFocus(FocusDirection.Next)
        } else {
            keyboard?.hide()
            if (onSubmit == null) focusManager.clearFocus(force = true)
        }
        onSubmit?.invoke()
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(label, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { submit() },
                onSearch = { submit() },
                onDone = { submit() },
            ),
            modifier = Modifier
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                .onPreviewKeyEvent { event ->
                    val key = event.nativeKeyEvent
                    if (
                        onSubmit != null &&
                        key.action == AndroidKeyEvent.ACTION_UP &&
                        (key.keyCode == AndroidKeyEvent.KEYCODE_ENTER || key.keyCode == AndroidKeyEvent.KEYCODE_NUMPAD_ENTER)
                    ) {
                        submit()
                        true
                    } else {
                        false
                    }
                }
                .fillMaxWidth()
                .height(52.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .padding(14.dp),
        )
    }
}

private object ArtworkLoader {
    private const val CACHE_SIZE_BYTES = 32 * 1024 * 1024
    private val cache = object : LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }
    private val client = OkHttpClient.Builder().readTimeout(20, TimeUnit.SECONDS).build()

    suspend fun load(url: String): Bitmap? = cache.get(url) ?: withContext(Dispatchers.IO) {
        val bitmap = if (url.startsWith("file:")) {
            runCatching { BitmapFactory.decodeFile(File(URI(url)).absolutePath) }.getOrNull()
        } else runCatching {
            ApiActivity.begin()
            try {
                client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    response.body?.byteStream()?.use(BitmapFactory::decodeStream)
                }
            } finally {
                ApiActivity.end()
            }
        }.getOrNull()
        bitmap?.also { cache.put(url, it) }
    }
}

@Composable
fun NetworkArtwork(url: String?, contentDescription: String?, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = null
        value = url?.let { ArtworkLoader.load(it) }
    }
    Box(modifier.background(Color(0xFF20242B)), contentAlignment = Alignment.Center) {
        val artwork = bitmap
        if (artwork != null) {
            Image(artwork.asImageBitmap(), contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
        } else {
            Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = .24f), modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
fun MediaCard(
    item: MediaPreview,
    api: ApiClient,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onContextMenu: (() -> Unit)? = null,
    downloaded: Boolean = false,
) {
    val cardShape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                val keyEvent = event.nativeKeyEvent
                if (
                    onContextMenu != null &&
                    keyEvent.action == AndroidKeyEvent.ACTION_UP &&
                    (keyEvent.keyCode == AndroidKeyEvent.KEYCODE_MENU || keyEvent.keyCode == AndroidKeyEvent.KEYCODE_INFO)
                ) {
                    onContextMenu()
                    true
                } else false
            }
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = CardDefaults.shape(shape = cardShape),
        scale = CardDefaults.scale(focusedScale = 1.055f),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = cardShape),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            NetworkArtwork(api.previewArtworkUrl(item.artworkPath), item.name, Modifier.fillMaxSize())
            if (downloaded) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(9.dp).size(30.dp)
                        .background(Color.Black.copy(alpha = .72f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(AppIcons.Download, "Downloaded", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(17.dp))
                }
            }
            Box(
                Modifier.fillMaxWidth().height(112.dp).align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        .48f to Color.Black.copy(alpha = .28f),
                        1f to Color.Black.copy(alpha = .88f),
                    )
                )
            )
            Column(
                Modifier.fillMaxWidth().align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
            ) {
                Text(item.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (item.subtitle.isNotBlank()) {
                    Text(item.subtitle, fontSize = 12.sp, color = Color.White.copy(alpha = .72f), maxLines = 1)
                }
            }
        }
    }
}

private val genreColors = listOf(
    Color(0xFF6457D6), Color(0xFFC74469), Color(0xFF247E78), Color(0xFFB7632D),
    Color(0xFF3972B9), Color(0xFF8B4EA1), Color(0xFF427D3B), Color(0xFF9A4E43),
)

private fun genreIcon(name: String): ImageVector {
    val genre = name.lowercase()
    return when {
        genre == "action" -> Icons.Default.Warning
        genre == "adventure" -> AppIcons.Map
        genre == "animated" || genre == "animation" -> AppIcons.Wand
        genre == "children" -> Icons.Default.Person
        genre == "comedy" -> Icons.Default.Face
        genre == "crime" -> AppIcons.Scope
        genre == "drama" -> AppIcons.TheaterMasks
        genre == "documentary" -> AppIcons.Artwork
        genre == "family" -> Icons.Default.Person
        genre == "fantasy" -> AppIcons.Wand
        genre == "history" -> Icons.Default.DateRange
        genre == "horror" -> Icons.Default.Warning
        genre == "music" || genre == "musical" -> AppIcons.MusicNote
        genre == "mystery" -> Icons.Default.Search
        genre == "nature" -> AppIcons.Leaf
        genre == "romance" || genre == "love" -> Icons.Default.Favorite
        genre == "sci-fi" || genre == "science-fiction" || genre == "science fiction" -> AppIcons.Atom
        genre == "sport" || genre == "sports" -> AppIcons.SportsBall
        genre == "thriller" -> AppIcons.Scope
        genre == "trash" -> Icons.Default.Delete
        genre == "war" -> AppIcons.Scope
        genre == "western" -> AppIcons.Map
        else -> AppIcons.CollectionStack
    }
}

@Composable
fun GenreCard(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val color = genreColors[(name.hashCode() and Int.MAX_VALUE) % genreColors.size]
    val cardShape = RoundedCornerShape(10.dp)
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f),
        shape = CardDefaults.shape(shape = cardShape),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(
            focusedBorder = Border(border = BorderStroke(3.dp, MaterialTheme.colorScheme.primary), shape = cardShape),
        ),
    ) {
        Row(
            Modifier.fillMaxSize().background(Brush.linearGradient(listOf(color, color.copy(alpha = .58f)))).padding(22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Icon(genreIcon(name), null, tint = Color.White, modifier = Modifier.size(46.dp))
            Text(name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 2)
        }
    }
}
