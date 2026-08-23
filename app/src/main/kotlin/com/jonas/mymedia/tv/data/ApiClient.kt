package com.jonas.mymedia.tv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiClient(val baseUrl: String) {
    private val jsonType = "application/json; charset=utf-8".toMediaType()
    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun health(): Boolean = runCatching {
        getObject("/api/v1/health", tracked = false).optString("status") == "ok"
    }.getOrDefault(false)

    suspend fun appearance(): Appearance {
        val json = getObject("/api/v1/appearance")
        return Appearance(json.optString("colorScheme") == "dark", json.optString("accentColor", "#FF981F"))
    }

    suspend fun mediaPage(
        source: BrowseSource,
        page: Int,
        filters: MediaFilters = MediaFilters(),
        perPage: Int = 24,
    ): MediaPage {
        val extra = when (source) {
            is BrowseSource.Genre -> mapOf("kind" to source.kind)
            else -> emptyMap()
        }
        return mediaPage(source.endpoint, page, filters, perPage, extra)
    }

    suspend fun mediaPage(
        endpoint: String,
        page: Int,
        filters: MediaFilters = MediaFilters(),
        perPage: Int = 24,
        extra: Map<String, String> = emptyMap(),
    ): MediaPage {
        val builder = url(endpoint).newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("perPage", perPage.toString())
        if (filters.minYear.isNotBlank() && filters.maxYear.isNotBlank()) {
            builder.addQueryParameter("minYear", filters.minYear).addQueryParameter("maxYear", filters.maxYear)
        }
        filters.minLength.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("minLength", it) }
        filters.maxLength.takeIf(String::isNotBlank)?.let { builder.addQueryParameter("maxLength", it) }
        filters.favorite?.let { builder.addQueryParameter("isFavorite", it.toString()) }
        filters.watched?.let { builder.addQueryParameter("isWatched", it.toString()) }
        if (filters.genres.isNotEmpty()) builder.addQueryParameter("genre", filters.genres.joinToString(","))
        extra.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val json = getObject(builder.build().toString())
        val array = json.optJSONArray("items") ?: JSONArray()
        return MediaPage(
            items = List(array.length()) { MediaPreview.from(array.getJSONObject(it)) },
            page = json.optInt("page", page),
            perPage = json.optInt("perPage", perPage),
            totalItems = json.optInt("totalItems"),
            totalPages = json.optInt("totalPages"),
        )
    }

    suspend fun search(query: String, scope: String, page: Int): MediaPage = mediaPage(
        endpoint = "/api/v1/search",
        page = page,
        extra = mapOf("query" to query, "scope" to scope),
    )

    suspend fun genres(kind: String): List<String> {
        val array = getArray(url("/api/v1/genres").newBuilder().addQueryParameter("kind", kind).build().toString())
        return List(array.length()) { array.getString(it) }
    }

    suspend fun detail(preview: MediaPreview): JSONObject = getObject(
        when (preview.kind) {
            "tvShow" -> "/api/v1/tv-shows/${preview.id}"
            "episode" -> "/api/v1/episodes/${preview.id}"
            "collection" -> "/api/v1/collections/${preview.id}"
            else -> "/api/v1/movies/${preview.id}"
        }
    )

    suspend fun person(name: String): JSONObject = getObject(
        url("/api/v1/people/${java.net.URLEncoder.encode(name, "UTF-8").replace("+", "%20")}").toString()
    )

    suspend fun updateMedia(kind: String, id: String, changes: JSONObject): JSONObject = mutate(
        method = "PATCH",
        endpoint = when (kind) {
            "tvShow" -> "/api/v1/tv-shows/$id"
            "episode" -> "/api/v1/episodes/$id"
            else -> "/api/v1/movies/$id"
        },
        body = changes,
    )

    suspend fun createCollection(title: String, description: String?, mediaIds: List<String>): JSONObject = mutate(
        "POST", "/api/v1/collections", JSONObject().put("title", title)
            .put("collectionDescription", description?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
            .put("mediaItemIDs", JSONArray(mediaIds)),
    )

    suspend fun updateCollection(id: String, add: List<String> = emptyList(), remove: List<String> = emptyList(), pinned: Boolean? = null): JSONObject {
        val body = JSONObject()
        if (add.isNotEmpty()) body.put("add", JSONArray(add))
        if (remove.isNotEmpty()) body.put("remove", JSONArray(remove))
        if (pinned != null) body.put("isPinned", pinned)
        return mutate("PATCH", "/api/v1/collections/$id", body)
    }

    fun absoluteUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://") || path.startsWith("file:")) return path
        return baseUrl.trimEnd('/') + "/" + path.trimStart('/')
    }

    fun previewArtworkUrl(path: String?, maxSize: Int = 960): String? = absoluteUrl(path)?.let { value ->
        runCatching { value.toHttpUrl().newBuilder().setQueryParameter("maxSize", maxSize.toString()).build().toString() }
            .getOrDefault(value)
    }

    fun videoUrl(id: String): String = absoluteUrl("/api/v1/videos/$id")!!
    fun downloadVideoUrl(id: String): String = absoluteUrl("/api/v1/videos/$id/download")!!

    private suspend fun getObject(endpoint: String, tracked: Boolean = true): JSONObject = JSONObject(execute(Request.Builder().url(urlString(endpoint)).get().build(), tracked))
    private suspend fun getArray(endpoint: String): JSONArray = JSONArray(execute(Request.Builder().url(urlString(endpoint)).get().build()))

    private suspend fun mutate(method: String, endpoint: String, body: JSONObject): JSONObject {
        val request = Request.Builder().url(urlString(endpoint)).method(method, body.toString().toRequestBody(jsonType)).build()
        return JSONObject(execute(request))
    }

    private suspend fun execute(request: Request, tracked: Boolean = true): String = withContext(Dispatchers.IO) {
        if (tracked) ApiActivity.begin()
        try {
            http.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw IOException("API returned HTTP ${response.code}${if (text.isBlank()) "" else ": $text"}")
                text
            }
        } finally {
            if (tracked) ApiActivity.end()
        }
    }

    private fun url(endpoint: String) = urlString(endpoint).toHttpUrl()
    private fun urlString(endpoint: String) = if (endpoint.startsWith("http")) endpoint else baseUrl.trimEnd('/') + "/" + endpoint.trimStart('/')
}
