package com.photangralenphie.mymedia.androidtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.MaterialTheme
import com.photangralenphie.mymedia.androidtv.data.ApiClient
import com.photangralenphie.mymedia.androidtv.data.AppPreferences
import com.photangralenphie.mymedia.androidtv.data.AppSettings
import com.photangralenphie.mymedia.androidtv.data.Appearance
import com.photangralenphie.mymedia.androidtv.data.DownloadStore
import com.photangralenphie.mymedia.androidtv.data.serverEndpoint
import com.photangralenphie.mymedia.androidtv.ui.MyMediaApp
import com.photangralenphie.mymedia.androidtv.ui.MyMediaTheme
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
    val preferences = remember { AppPreferences(context) }
    val downloads = remember { DownloadStore.get(context) }
    var settings by remember { mutableStateOf(preferences.load()) }
    var generation by remember { mutableIntStateOf(0) }
    var state by remember { mutableStateOf<BootState>(BootState.Checking) }

    LaunchedEffect(generation) {
        val host = settings.host.trim()
        val port = settings.port
        state = BootState.Checking
        val base = serverEndpoint(host.ifBlank { "127.0.0.1" }, port)
        val api = ApiClient(base)
        val cachedAppearance = settings.appearance
        var online = host.isNotBlank() && api.health()
        var appearance = if (online) runCatching { api.appearance() }.getOrDefault(cachedAppearance) else cachedAppearance
        if (online) updateAppearance(preferences, settings, appearance) { settings = it }
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
                    updateAppearance(preferences, settings, appearance) { settings = it }
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
                initialHost = settings.host,
                initialPort = settings.port,
                playEpisodesDirectly = settings.playEpisodesDirectly,
                tvShowDownloadCount = settings.tvShowDownloadCount,
                onSaveEndpoint = { host, port ->
                    val cleanedHost = host.trim()
                    preferences.saveEndpoint(cleanedHost, port)
                    settings = settings.copy(host = cleanedHost, port = port)
                    generation++
                },
                onEpisodeBehaviorChange = { enabled ->
                    preferences.savePlayEpisodesDirectly(enabled)
                    settings = settings.copy(playEpisodesDirectly = enabled)
                },
                onTvShowDownloadCountChange = { count ->
                    val boundedCount = count.coerceIn(1, 50)
                    preferences.saveTvShowDownloadCount(boundedCount)
                    settings = settings.copy(tvShowDownloadCount = boundedCount)
                },
            )
        }
    }
}

private fun updateAppearance(
    preferences: AppPreferences,
    settings: AppSettings,
    appearance: Appearance,
    updateSettings: (AppSettings) -> Unit,
) {
    preferences.saveAppearance(appearance)
    updateSettings(settings.copy(appearance = appearance))
}
