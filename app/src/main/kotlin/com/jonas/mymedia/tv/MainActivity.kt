package com.jonas.mymedia.tv

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.jonas.mymedia.tv.data.ApiClient
import com.jonas.mymedia.tv.data.Appearance
import com.jonas.mymedia.tv.data.DownloadStore
import com.jonas.mymedia.tv.ui.MyMediaApp
import com.jonas.mymedia.tv.ui.MyMediaTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { Root() }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

private sealed interface BootState {
    data object Checking : BootState
    data class Running(
        val api: ApiClient,
        val appearance: Appearance,
        val online: Boolean,
        val message: String? = null,
    ) : BootState
}

@Composable
private fun Root() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("mymedia_settings", Context.MODE_PRIVATE) }
    val downloads = remember { DownloadStore.get(context) }
    var generation by remember { mutableStateOf(0) }
    var state by remember { mutableStateOf<BootState>(BootState.Checking) }
    var playEpisodesDirectly by remember { mutableStateOf(prefs.getBoolean("play_episodes_directly", true)) }
    var tvShowDownloadCount by remember { mutableStateOf(prefs.getInt("tv_download_count", 3).coerceIn(1, 50)) }

    LaunchedEffect(generation) {
        val host = prefs.getString("host", "").orEmpty().trim()
        val port = prefs.getInt("port", 8080)
        state = BootState.Checking
        val base = endpoint(host.ifBlank { "127.0.0.1" }, port)
        val api = ApiClient(base)
        val cachedAppearance = Appearance(
            isDark = prefs.getBoolean("appearance_dark", true),
            accentHex = prefs.getString("appearance_accent", "#FF981F") ?: "#FF981F",
        )
        var online = host.isNotBlank() && api.health()
        var appearance = if (online) runCatching { api.appearance() }.getOrDefault(cachedAppearance) else cachedAppearance
        if (online) prefs.edit().putBoolean("appearance_dark", appearance.isDark).putString("appearance_accent", appearance.accentHex).apply()
        state = BootState.Running(
            api = api,
            appearance = appearance,
            online = online,
            message = if (host.isBlank()) "Configure your MyMedia server." else if (!online) "MyMedia could not be reached at $base" else null,
        )
        while (true) {
            delay(15_000)
            val nowOnline = host.isNotBlank() && api.health()
            if (nowOnline != online) {
                online = nowOnline
                if (online) {
                    appearance = runCatching { api.appearance() }.getOrDefault(appearance)
                    prefs.edit().putBoolean("appearance_dark", appearance.isDark).putString("appearance_accent", appearance.accentHex).apply()
                }
                state = BootState.Running(
                    api,
                    appearance,
                    online,
                    if (online) null else "MyMedia could not be reached at $base",
                )
            }
        }
    }

    when (val current = state) {
        BootState.Checking -> MyMediaTheme(Appearance()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(18.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .94f), RoundedCornerShape(28.dp))
                        .padding(11.dp)
                        .size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp,
                )
            }
        }
        is BootState.Running -> MyMediaTheme(current.appearance) {
            MyMediaApp(
                api = current.api,
                downloads = downloads,
                online = current.online,
                offlineMessage = current.message,
                initialHost = prefs.getString("host", "").orEmpty(),
                initialPort = prefs.getInt("port", 8080),
                playEpisodesDirectly = playEpisodesDirectly,
                tvShowDownloadCount = tvShowDownloadCount,
                onSaveEndpoint = { host, port ->
                    prefs.edit().putString("host", host.trim()).putInt("port", port).apply()
                    generation++
                },
                onEpisodeBehaviorChange = { enabled ->
                    playEpisodesDirectly = enabled
                    prefs.edit().putBoolean("play_episodes_directly", enabled).apply()
                },
                onTvShowDownloadCountChange = { count ->
                    tvShowDownloadCount = count.coerceIn(1, 50)
                    prefs.edit().putInt("tv_download_count", tvShowDownloadCount).apply()
                },
            )
        }
    }
}

private fun endpoint(host: String, port: Int): String {
    val cleaned = host.trim().trimEnd('/')
    if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
        val authority = cleaned.substringAfter("://").substringBefore('/')
        return if (authority.substringAfterLast(':', "").all(Char::isDigit) && ':' in authority) cleaned else "$cleaned:$port"
    }
    return "http://$cleaned:$port"
}

@Composable
private fun ConnectionScreen(initialHost: String, initialPort: Int, message: String, onSave: (String, Int) -> Unit) {
    var host by remember(initialHost) { mutableStateOf(initialHost) }
    var port by remember(initialPort) { mutableStateOf(initialPort.toString()) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(Modifier.width(620.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Text("Connect to MyMedia", style = MaterialTheme.typography.displaySmall)
            Text(message, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledField(
                    "IP address or host",
                    host,
                    { host = it },
                    Modifier.weight(1f),
                    keyboardType = KeyboardType.Number,
                    onSubmit = {},
                    imeAction = ImeAction.Next,
                )
                LabeledField(
                    "Port",
                    port,
                    { port = it.filter(Char::isDigit) },
                    Modifier.width(140.dp),
                    keyboardType = KeyboardType.Number,
                    onSubmit = {},
                    imeAction = ImeAction.Next,
                )
            }
            Button(onClick = { port.toIntOrNull()?.let { onSave(host, it) } }, enabled = host.isNotBlank() && port.toIntOrNull() != null) {
                Text("Connect")
            }
        }
    }
}

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
        if (imeAction == ImeAction.Next) focusManager.moveFocus(FocusDirection.Next)
        else {
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
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = imeAction,
            ),
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
                        key.action == android.view.KeyEvent.ACTION_UP &&
                        (key.keyCode == android.view.KeyEvent.KEYCODE_ENTER || key.keyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
                    ) {
                        submit()
                        true
                    } else false
                }
                .fillMaxWidth().height(52.dp).background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small).padding(14.dp),
        )
    }
}
