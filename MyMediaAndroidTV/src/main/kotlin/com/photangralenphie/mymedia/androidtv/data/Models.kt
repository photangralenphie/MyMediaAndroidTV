package com.photangralenphie.mymedia.androidtv.data

import org.json.JSONArray
import org.json.JSONObject

data class Appearance(val isDark: Boolean = true, val accentHex: String = "#FF981F")

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
        fun fromJson(json: JSONObject) = MediaPreview(
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

data class MediaCredits(
    val cast: List<String> = emptyList(),
    val directors: List<String> = emptyList(),
    val coDirectors: List<String> = emptyList(),
    val screenwriters: List<String> = emptyList(),
    val producers: List<String> = emptyList(),
    val executiveProducers: List<String> = emptyList(),
    val composer: String? = null,
) {
    fun contains(name: String): Boolean = people().any { it.equals(name, ignoreCase = true) }

    fun people(): List<String> = cast + directors + coDirectors + screenwriters + producers + executiveProducers + listOfNotNull(composer)

    fun toJson(): JSONObject = JSONObject()
        .put("cast", JSONArray(cast))
        .put("directors", JSONArray(directors))
        .put("coDirectors", JSONArray(coDirectors))
        .put("screenwriters", JSONArray(screenwriters))
        .put("producers", JSONArray(producers))
        .put("executiveProducers", JSONArray(executiveProducers))
        .putNullable("composer", composer)

    companion object {
        fun fromJson(json: JSONObject) = MediaCredits(
            cast = json.optJSONArray("cast").toStringList(),
            directors = json.optJSONArray("directors").toStringList(),
            coDirectors = json.optJSONArray("coDirectors").toStringList(),
            screenwriters = json.optJSONArray("screenwriters").toStringList(),
            producers = json.optJSONArray("producers").toStringList(),
            executiveProducers = json.optJSONArray("executiveProducers").toStringList(),
            composer = json.optNullableString("composer"),
        )
    }
}

data class MediaDetail(
    val kind: String,
    val id: String,
    val title: String,
    val year: Int? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val durationMinutes: Int = 0,
    val progressMinutes: Int = 0,
    val isWatched: Boolean = false,
    val isFavorite: Boolean = false,
    val isPinned: Boolean = false,
    val artworkPath: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val releaseDate: String? = null,
    val studio: String? = null,
    val network: String? = null,
    val networks: List<String> = emptyList(),
    val languages: List<String> = emptyList(),
    val rating: String? = null,
    val videoQuality: String? = null,
    val credits: MediaCredits = MediaCredits(),
    val children: List<MediaPreview> = emptyList(),
    val parentShow: MediaPreview? = null,
    val numberOfItems: Int? = null,
) {
    fun toPreview(artworkPath: String? = this.artworkPath) = MediaPreview(
        kind, id, title, year, season, episode, numberOfItems, artworkPath,
    )

    fun withUpdate(update: MediaUpdate) = copy(
        progressMinutes = update.progressMinutes ?: progressMinutes,
        isWatched = update.isWatched ?: isWatched,
        isFavorite = update.isFavorite ?: isFavorite,
        isPinned = update.isPinned ?: isPinned,
    )

    fun searchableText(): String = buildList {
        add(kind)
        add(id)
        add(title)
        year?.let { add(it.toString()) }
        description?.let(::add)
        addAll(genres)
        addAll(credits.people())
        releaseDate?.let(::add)
        studio?.let(::add)
        network?.let(::add)
        addAll(networks)
        addAll(languages)
        rating?.let(::add)
        videoQuality?.let(::add)
        parentShow?.name?.let(::add)
        children.mapTo(this) { it.name }
    }.joinToString(" ")

    fun toJson(): JSONObject = JSONObject()
        .put("kind", kind).put("id", id).put("title", title)
        .putNullable("year", year).putNullable("season", season).putNullable("episode", episode)
        .put("durationMinutes", durationMinutes).put("progressMinutes", progressMinutes)
        .put("isWatched", isWatched).put("isFavorite", isFavorite).put("isPinned", isPinned)
        .putNullable("artworkURL", artworkPath).putNullable(descriptionKey(kind), description)
        .put("genre", JSONArray(genres)).putNullable("releaseDate", releaseDate)
        .putNullable("studio", studio).putNullable("network", network)
        .put("networks", JSONArray(networks)).put("languages", JSONArray(languages))
        .putNullable("rating", rating).putNullable("hdVideoQuality", videoQuality)
        .put("credits", credits.toJson()).put(childrenKey(kind), JSONArray(children.map(MediaPreview::toJson)))
        .putNullable("tvShow", parentShow?.toJson()).putNullable("numberOfItems", numberOfItems)

    companion object {
        fun fromJson(json: JSONObject, kindHint: String? = null): MediaDetail {
            val kind = json.optString("kind").ifBlank { kindHint ?: "movie" }
            return MediaDetail(
                kind = kind,
                id = json.optString("id"),
                title = json.optString("title", "Untitled"),
                year = json.optNullableInt("year"),
                season = json.optNullableInt("season"),
                episode = json.optNullableInt("episode"),
                durationMinutes = json.optInt("durationMinutes"),
                progressMinutes = json.optInt("progressMinutes"),
                isWatched = json.optBoolean("isWatched"),
                isFavorite = json.optBoolean("isFavorite"),
                isPinned = json.optBoolean("isPinned"),
                artworkPath = json.optNullableString("artworkURL"),
                description = json.description(kind),
                genres = json.optJSONArray("genre").toStringList(),
                releaseDate = json.optNullableString("releaseDate"),
                studio = json.optNullableString("studio"),
                network = json.optNullableString("network"),
                networks = json.optJSONArray("networks").toStringList(),
                languages = json.optJSONArray("languages").toStringList(),
                rating = json.optNullableString("rating"),
                videoQuality = json.optNullableString("hdVideoQuality"),
                credits = json.optJSONObject("credits")?.let(MediaCredits::fromJson) ?: MediaCredits(),
                children = json.optJSONArray(childrenKey(kind)).toMediaPreviews(),
                parentShow = json.optJSONObject("tvShow")?.let(MediaPreview::fromJson),
                numberOfItems = json.optNullableInt("numberOfItems"),
            )
        }
    }
}

data class MediaUpdate(
    val progressMinutes: Int? = null,
    val isWatched: Boolean? = null,
    val isFavorite: Boolean? = null,
    val isPinned: Boolean? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        progressMinutes?.let { put("progressMinutes", it) }
        isWatched?.let { put("isWatched", it) }
        isFavorite?.let { put("isFavorite", it) }
        isPinned?.let { put("isPinned", it) }
    }
}

data class PersonMediaCredits(
    val cast: List<MediaPreview> = emptyList(),
    val directors: List<MediaPreview> = emptyList(),
    val coDirectors: List<MediaPreview> = emptyList(),
    val screenwriters: List<MediaPreview> = emptyList(),
    val producers: List<MediaPreview> = emptyList(),
    val executiveProducers: List<MediaPreview> = emptyList(),
    val composer: List<MediaPreview> = emptyList(),
) {
    companion object {
        fun fromJson(json: JSONObject) = PersonMediaCredits(
            cast = json.optJSONArray("cast").toMediaPreviews(),
            directors = json.optJSONArray("directors").toMediaPreviews(),
            coDirectors = json.optJSONArray("coDirectors").toMediaPreviews(),
            screenwriters = json.optJSONArray("screenwriters").toMediaPreviews(),
            producers = json.optJSONArray("producers").toMediaPreviews(),
            executiveProducers = json.optJSONArray("executiveProducers").toMediaPreviews(),
            composer = json.optJSONArray("composer").toMediaPreviews(),
        )
    }
}

data class PersonDetail(
    val name: String,
    val roles: List<String> = emptyList(),
    val creditedMovies: List<MediaPreview> = emptyList(),
    val creditedEpisodes: List<MediaPreview> = emptyList(),
    val credits: PersonMediaCredits = PersonMediaCredits(),
) {
    companion object {
        fun fromJson(json: JSONObject) = PersonDetail(
            name = json.optString("name"),
            roles = json.optJSONArray("roles").toStringList(),
            creditedMovies = json.optJSONArray("creditedMovies").toMediaPreviews(),
            creditedEpisodes = json.optJSONArray("creditedEpisodes").toMediaPreviews(),
            credits = json.optJSONObject("credits")?.let(PersonMediaCredits::fromJson) ?: PersonMediaCredits(),
        )
    }
}

data class MediaPage(val items: List<MediaPreview>, val page: Int, val perPage: Int, val totalItems: Int, val totalPages: Int)

data class MediaFilters(
    val minYear: String = "", val maxYear: String = "", val minLength: String = "", val maxLength: String = "",
    val favorite: Boolean? = null, val watched: Boolean? = null, val genres: Set<String> = emptySet(),
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
    data object Movies : BrowseSource { override val title = "All Movies"; override val endpoint = "/api/v1/movies"; override val supportsFilters = true }
    data object TvShows : BrowseSource { override val title = "All TV Shows"; override val endpoint = "/api/v1/tv-shows"; override val supportsFilters = true }
    data class Genre(val genre: String, val kind: String) : BrowseSource {
        override val title = genre
        override val endpoint = "/api/v1/genres/${encodePathSegment(genre)}"
    }
}

sealed interface Route {
    data class Browse(val source: BrowseSource) : Route
    data class Genres(val title: String, val kind: String) : Route
    data object Search : Route
    data class Settings(val page: SettingsPage = SettingsPage.Behaviour) : Route
    data class Detail(val preview: MediaPreview) : Route
    data class Person(val name: String) : Route
}

enum class SettingsPage { Behaviour, Server, Downloads }

private fun MediaPreview.toJson(): JSONObject = JSONObject()
    .put("kind", kind).put("id", id).put("name", name)
    .putNullable("year", year).putNullable("season", season).putNullable("episode", episode)
    .putNullable("numberOfItems", numberOfItems).putNullable("previewArtworkURL", artworkPath)

private fun descriptionKey(kind: String) = when (kind) {
    "movie" -> "longDescription"
    "episode" -> "episodeLongDescription"
    "tvShow" -> "showDescription"
    else -> "collectionDescription"
}

private fun JSONObject.description(kind: String): String? = when (kind) {
    "movie" -> optNullableString("longDescription") ?: optNullableString("shortDescription")
    "episode" -> optNullableString("episodeLongDescription") ?: optNullableString("episodeShortDescription")
    else -> optNullableString(descriptionKey(kind))
}

private fun childrenKey(kind: String) = if (kind == "collection") "items" else "episodes"

fun JSONObject.optNullableString(key: String): String? = if (has(key) && !isNull(key)) optString(key).takeIf(String::isNotBlank) else null
fun JSONObject.optNullableInt(key: String): Int? = if (has(key) && !isNull(key)) optInt(key) else null
private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
fun JSONArray?.toStringList(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }.filter(String::isNotBlank)
fun JSONArray?.toMediaPreviews(): List<MediaPreview> = if (this == null) emptyList() else List(length()) { MediaPreview.fromJson(getJSONObject(it)) }
fun encodePathSegment(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
