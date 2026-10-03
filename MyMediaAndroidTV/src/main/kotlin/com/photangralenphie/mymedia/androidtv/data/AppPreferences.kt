package com.photangralenphie.mymedia.androidtv.data

import android.content.Context

data class AppSettings(
    val host: String,
    val port: Int,
    val appearance: Appearance,
    val playEpisodesDirectly: Boolean,
    val tvShowDownloadCount: Int,
)

class AppPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings = AppSettings(
        host = preferences.getString(KEY_HOST, "").orEmpty(),
        port = preferences.getInt(KEY_PORT, DEFAULT_PORT),
        appearance = Appearance(
            isDark = preferences.getBoolean(KEY_APPEARANCE_DARK, true),
            accentHex = preferences.getString(KEY_APPEARANCE_ACCENT, DEFAULT_ACCENT) ?: DEFAULT_ACCENT,
        ),
        playEpisodesDirectly = preferences.getBoolean(KEY_PLAY_EPISODES_DIRECTLY, true),
        tvShowDownloadCount = preferences.getInt(KEY_TV_DOWNLOAD_COUNT, DEFAULT_TV_DOWNLOAD_COUNT)
            .coerceIn(MIN_TV_DOWNLOAD_COUNT, MAX_TV_DOWNLOAD_COUNT),
    )

    fun saveEndpoint(host: String, port: Int) {
        preferences.edit().putString(KEY_HOST, host.trim()).putInt(KEY_PORT, port).apply()
    }

    fun saveAppearance(appearance: Appearance) {
        preferences.edit()
            .putBoolean(KEY_APPEARANCE_DARK, appearance.isDark)
            .putString(KEY_APPEARANCE_ACCENT, appearance.accentHex)
            .apply()
    }

    fun savePlayEpisodesDirectly(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_PLAY_EPISODES_DIRECTLY, enabled).apply()
    }

    fun saveTvShowDownloadCount(count: Int) {
        preferences.edit().putInt(KEY_TV_DOWNLOAD_COUNT, count.coerceIn(MIN_TV_DOWNLOAD_COUNT, MAX_TV_DOWNLOAD_COUNT)).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "mymedia_settings"
        const val KEY_HOST = "host"
        const val KEY_PORT = "port"
        const val KEY_APPEARANCE_DARK = "appearance_dark"
        const val KEY_APPEARANCE_ACCENT = "appearance_accent"
        const val KEY_PLAY_EPISODES_DIRECTLY = "play_episodes_directly"
        const val KEY_TV_DOWNLOAD_COUNT = "tv_download_count"

        const val DEFAULT_PORT = 8080
        const val DEFAULT_ACCENT = "#FF981F"
        const val DEFAULT_TV_DOWNLOAD_COUNT = 3
        const val MIN_TV_DOWNLOAD_COUNT = 1
        const val MAX_TV_DOWNLOAD_COUNT = 50
    }
}

fun serverEndpoint(host: String, port: Int): String {
    val cleaned = host.trim().trimEnd('/')
    if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
        val authority = cleaned.substringAfter("://").substringBefore('/')
        return if (authority.substringAfterLast(':', "").all(Char::isDigit) && ':' in authority) cleaned else "$cleaned:$port"
    }
    return "http://$cleaned:$port"
}
