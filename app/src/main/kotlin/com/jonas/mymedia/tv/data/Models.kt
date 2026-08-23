package com.jonas.mymedia.tv.data

import org.json.JSONObject

data class Appearance(
    val isDark: Boolean = true,
    val accentHex: String = "#FF981F",
)

data class MediaPreview(
    val kind: String,
    val id: String,
    val name: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val numberOfItems: Int? = null,
    val artworkPath: String? = null,
) {
    val subtitle: String
        get() = buildList {
            if (kind == "episode" && season != null && episode != null) add("S%02d E%02d".format(season, episode))
            year?.let { add(it.toString()) }
            numberOfItems?.let { add("$it ${if (it == 1) "title" else "titles"}") }
        }.joinToString("  •  ")

    companion object {
        fun from(json: JSONObject) = MediaPreview(
            kind = json.optString("kind", "movie"),
            id = json.optString("id"),
            name = json.optString("name", "Untitled"),
            year = json.optNullableInt("year"),
            season = json.optNullableInt("season"),
            episode = json.optNullableInt("episode"),
            numberOfItems = json.optNullableInt("numberOfItems"),
            artworkPath = json.optNullableString("previewArtworkURL"),
        )
    }
}

data class MediaPage(
    val items: List<MediaPreview>,
    val page: Int,
    val perPage: Int,
    val totalItems: Int,
    val totalPages: Int,
)

data class MediaFilters(
    val minYear: String = "",
    val maxYear: String = "",
    val minLength: String = "",
    val maxLength: String = "",
    val favorite: Boolean? = null,
    val watched: Boolean? = null,
    val genres: Set<String> = emptySet(),
) {
    val activeCount: Int
        get() = listOf(minYear, maxYear, minLength, maxLength).count { it.isNotBlank() } +
            listOf(favorite, watched).count { it != null } + if (genres.isEmpty()) 0 else 1
}

sealed interface BrowseSource {
    val title: String
    val endpoint: String
    val supportsFilters: Boolean get() = false

    data object Unwatched : BrowseSource { override val title = "Unwatched"; override val endpoint = "/api/v1/unwatched" }
    data object Collections : BrowseSource { override val title = "Collections"; override val endpoint = "/api/v1/collections" }
    data object Favorites : BrowseSource { override val title = "Favorites"; override val endpoint = "/api/v1/favorites" }
    data object Pinned : BrowseSource { override val title = "Pinned"; override val endpoint = "/api/v1/pinned" }
    data object Downloads : BrowseSource { override val title = "Downloads"; override val endpoint = "" }
    data object Movies : BrowseSource {
        override val title = "All Movies"; override val endpoint = "/api/v1/movies"; override val supportsFilters = true
    }
    data object TvShows : BrowseSource {
        override val title = "All TV Shows"; override val endpoint = "/api/v1/tv-shows"; override val supportsFilters = true
    }
    data class Genre(val genre: String, val kind: String) : BrowseSource {
        override val title = genre
        override val endpoint = "/api/v1/genres/${java.net.URLEncoder.encode(genre, "UTF-8").replace("+", "%20")}" 
    }
}

sealed interface Route {
    data class Browse(val source: BrowseSource) : Route
    data class Genres(val title: String, val kind: String) : Route
    data object Search : Route
    data class Settings(val page: SettingsPage = SettingsPage.Root) : Route
    data class Detail(val preview: MediaPreview) : Route
    data class Person(val name: String) : Route
}

enum class SettingsPage { Root, Behaviour, Server, Downloads }

fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

fun JSONObject.optNullableInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null
