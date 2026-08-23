package com.jonas.mymedia.tv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

data class DownloadEntry(
    val id: String,
    val kind: String,
    val title: String,
    val metadata: JSONObject,
    val directory: File,
    val artwork: File?,
    val video: File?,
    val parentId: String?,
) {
    val sizeBytes: Long get() = directory.walkTopDown().filter(File::isFile).sumOf(File::length)
}

class DownloadStore private constructor(context: Context) {
    private val root = File(context.filesDir, "downloads").apply { mkdirs() }
    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()

    var revision by mutableIntStateOf(0)
        private set
    var activeIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var activeTitles by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var activeProgress by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    fun entries(): List<DownloadEntry> = root.listFiles().orEmpty().mapNotNull(::readEntry)
        .sortedWith(compareBy({ it.kind }, { it.title.lowercase() }))

    fun playableEntries(): List<DownloadEntry> = entries().filter { it.video?.isFile == true }

    fun previews(source: BrowseSource? = null, filters: MediaFilters = MediaFilters()): List<MediaPreview> {
        val all = entries()
        val playable = all.filter { it.video?.isFile == true }
        val visible = when (source) {
            BrowseSource.Movies -> playable.filter { it.kind == "movie" }
            BrowseSource.TvShows -> all.filter { entry ->
                entry.kind == "tvShow" && playable.any { it.parentId == entry.id }
            }
            BrowseSource.Favorites -> playable.filter { it.metadata.optBoolean("isFavorite") }
            BrowseSource.Pinned -> playable.filter { it.metadata.optBoolean("isPinned") }
            BrowseSource.Unwatched -> playable.filterNot { it.metadata.optBoolean("isWatched") }
            BrowseSource.Collections -> emptyList()
            BrowseSource.Downloads -> all.filter { entry ->
                entry.kind == "movie" && entry.video != null ||
                    entry.kind == "tvShow" && playable.any { it.parentId == entry.id }
            }
            is BrowseSource.Genre -> all.filter { entry ->
                (entry.video != null || entry.kind == "tvShow") &&
                    entry.metadata.optJSONArray("genre").toStringList().any { it.equals(source.genre, ignoreCase = true) }
            }
            else -> playable
        }
        return visible.filter { entry ->
            val year = entry.metadata.optInt("year")
            val duration = entry.metadata.optInt("durationMinutes")
            (filters.minYear.toIntOrNull()?.let { year >= it } ?: true) &&
                (filters.maxYear.toIntOrNull()?.let { year <= it } ?: true) &&
                (filters.minLength.toIntOrNull()?.let { duration >= it } ?: true) &&
                (filters.maxLength.toIntOrNull()?.let { duration <= it } ?: true) &&
                (filters.favorite?.let { entry.metadata.optBoolean("isFavorite") == it } ?: true) &&
                (filters.watched?.let { entry.metadata.optBoolean("isWatched") == it } ?: true) &&
                (filters.genres.isEmpty() || entry.metadata.optJSONArray("genre").toStringList().any { it in filters.genres })
        }.map(::preview)
    }

    fun genres(kind: String): List<String> = entries().asSequence()
        .filter { it.video != null || it.kind == "tvShow" }
        .filter { kind == "both" || (kind == "movies" && it.kind == "movie") || (kind == "tvShows" && it.kind == "tvShow") }
        .flatMap { it.metadata.optJSONArray("genre").toStringList().asSequence() }
        .distinct().sorted().toList()

    fun search(query: String): List<MediaPreview> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        return entries().filter { entry ->
            if (entry.video == null && entry.kind != "tvShow") return@filter false
            entry.metadata.toString().lowercase().contains(needle)
        }.map(::preview)
    }

    fun person(name: String): JSONObject {
        val matched = playableEntries().filter { entry ->
            val credits = entry.metadata.optJSONObject("credits") ?: return@filter false
            listOf("cast", "directors", "coDirectors", "screenwriters", "producers", "executiveProducers")
                .any { key -> credits.optJSONArray(key).toStringList().any { it.equals(name, ignoreCase = true) } } ||
                credits.optString("composer").equals(name, ignoreCase = true)
        }
        fun previewJson(entry: DownloadEntry): JSONObject {
            val value = preview(entry)
            return JSONObject().put("kind", value.kind).put("id", value.id).put("name", value.name)
                .put("year", value.year ?: JSONObject.NULL).put("season", value.season ?: JSONObject.NULL)
                .put("episode", value.episode ?: JSONObject.NULL).put("previewArtworkURL", value.artworkPath ?: JSONObject.NULL)
        }
        return JSONObject()
            .put("name", name)
            .put("roles", JSONArray())
            .put("creditedMovies", JSONArray(matched.filter { it.kind == "movie" }.map(::previewJson)))
            .put("creditedEpisodes", JSONArray(matched.filter { it.kind == "episode" }.map(::previewJson)))
            .put("credits", JSONObject())
    }

    fun detail(id: String): JSONObject? = entries().firstOrNull { it.id == id }?.metadata?.let { JSONObject(it.toString()) }

    fun previewFor(id: String): MediaPreview? = entries().firstOrNull { it.id == id }?.let(::preview)

    fun localVideo(id: String): File? = entries().firstOrNull { it.id == id }?.video?.takeIf(File::isFile)

    fun localArtwork(id: String): File? = entries().firstOrNull { it.id == id }?.artwork?.takeIf(File::isFile)

    fun isDownloaded(id: String): Boolean = localVideo(id) != null

    fun downloadedIds(): Set<String> = buildSet {
        playableEntries().forEach { entry ->
            add(entry.id)
            entry.parentId?.let(::add)
        }
    }

    fun hasDownload(id: String): Boolean = id in downloadedIds()

    fun startMediaDownload(api: ApiClient, kind: String, detail: JSONObject, parent: JSONObject? = null) {
        val id = detail.optString("id")
        if (id.isBlank() || id in activeIds) return
        activeIds = activeIds + id
        activeTitles = activeTitles + (id to detail.optString("title", "Video"))
        activeProgress = activeProgress + (id to "0%")
        lastError = null
        activeJobs[id] = applicationScope.launch {
            try {
                downloadMedia(api, kind, detail, parent, id)
            } catch (_: CancellationException) {
                // Cancellation is initiated by the user and is not an error.
            } catch (error: Throwable) {
                lastError = error.message ?: "Download failed"
            } finally {
                finishDownload(id)
            }
        }
    }

    fun startTvShowDownload(api: ApiClient, show: JSONObject, episodeLimit: Int?) {
        val showId = show.optString("id")
        if (showId.isBlank() || showId in activeIds) return
        activeIds = activeIds + showId
        activeTitles = activeTitles + (showId to show.optString("title", "TV show"))
        activeProgress = activeProgress + (showId to "0%")
        lastError = null
        activeJobs[showId] = applicationScope.launch {
            try {
                saveMetadataAndArtwork(api, "tvShow", show, null, showId)
                val previews = show.optJSONArray("episodes").let { array ->
                    if (array == null) emptyList() else List(array.length()) { MediaPreview.from(array.getJSONObject(it)) }
                }.sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
                val selected = if (episodeLimit == null) {
                    previews.filterNot { isDownloaded(it.id) }.map { it to api.detail(it) }
                } else {
                    val unwatched = mutableListOf<Pair<MediaPreview, JSONObject>>()
                    for (preview in previews) {
                        if (isDownloaded(preview.id)) continue
                        val episode = api.detail(preview)
                        if (!episode.optBoolean("isWatched")) unwatched += preview to episode
                        if (unwatched.size >= episodeLimit) break
                    }
                    unwatched
                }
                selected.forEachIndexed { index, (_, episode) ->
                    downloadMedia(
                        api,
                        "episode",
                        episode,
                        show,
                        showId,
                        progressStart = index.toFloat() / selected.size.coerceAtLeast(1),
                        progressShare = 1f / selected.size.coerceAtLeast(1),
                    )
                }
            } catch (_: CancellationException) {
                // Completed episodes remain available when a series download is cancelled.
            } catch (error: Throwable) {
                lastError = error.message ?: "TV show download failed"
            } finally {
                finishDownload(showId)
            }
        }
    }

    fun cancel(id: String) {
        activeJobs.remove(id)?.cancel()
        activeCalls.remove(id)?.cancel()
        activeIds = activeIds - id
        activeTitles = activeTitles - id
        activeProgress = activeProgress - id
        applicationScope.launch {
            root.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.forEach(File::delete)
            revision++
        }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) {
        val allEntries = entries()
        val entry = allEntries.firstOrNull { it.id == id }
        val deletesTvShow = entry?.kind == "tvShow" || allEntries.any { it.parentId == id }
        val targets = if (deletesTvShow) {
            allEntries.filter { it.id == id || it.parentId == id }
        } else {
            listOfNotNull(entry)
        }
        if (targets.isEmpty()) return@withContext

        // A show download uses the show's id for its active job. Individually downloaded
        // episodes use their own ids, so stop both forms before removing their files.
        (targets.map { it.id } + id).distinct().filter { it in activeIds }.forEach(::cancel)
        targets.forEach { it.directory.deleteRecursively() }
        revision++
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        root.listFiles().orEmpty().forEach(File::deleteRecursively)
        revision++
    }

    fun updatePlayback(id: String, progressMinutes: Int, watched: Boolean) {
        applicationScope.launch {
            val entry = entries().firstOrNull { it.id == id } ?: return@launch
            val updated = JSONObject(entry.metadata.toString())
                .put("progressMinutes", progressMinutes)
                .put("isWatched", watched)
            metadataFile(entry.directory).writeText(updated.toString())
            revision++
        }
    }

    fun updateMetadata(value: JSONObject) {
        val id = value.optString("id")
        applicationScope.launch {
            val entry = entries().firstOrNull { it.id == id } ?: return@launch
            val updated = JSONObject(value.toString()).put("kind", entry.kind)
            entry.parentId?.let { updated.put("_downloadParentId", it) }
            metadataFile(entry.directory).writeText(updated.toString())
            revision++
        }
    }

    private suspend fun downloadMedia(
        api: ApiClient,
        kind: String,
        detail: JSONObject,
        parent: JSONObject?,
        progressId: String,
        progressStart: Float = 0f,
        progressShare: Float = 1f,
    ) {
        val parentJson = if (kind == "episode") {
            parent ?: detail.optJSONObject("tvShow")?.let { api.detail(MediaPreview.from(it)) }
        } else null
        if (parentJson != null) saveMetadataAndArtwork(api, "tvShow", parentJson, null, progressId)
        val directory = saveMetadataAndArtwork(api, kind, detail, parentJson?.optString("id"), progressId)
        downloadBinary(
            api.downloadVideoUrl(detail.optString("id")),
            directory,
            "video",
            useDispositionExtension = true,
            activeId = progressId,
            reportProgress = true,
            progressStart = progressStart,
            progressShare = progressShare,
        )
    }

    private suspend fun saveMetadataAndArtwork(api: ApiClient, kind: String, value: JSONObject, parentId: String?, activeId: String): File {
        val id = value.optString("id")
        if (id.isBlank()) throw IOException("The server returned metadata without an id")
        val directory = directory(kind, id).apply { mkdirs() }
        val stored = JSONObject(value.toString()).put("kind", kind)
        if (!parentId.isNullOrBlank()) stored.put("_downloadParentId", parentId)
        metadataFile(directory).writeText(stored.toString())
        api.absoluteUrl(value.optNullableString("artworkURL"))?.let { url ->
            if (directory.listFiles().orEmpty().none { it.name.startsWith("artwork.") }) {
                downloadBinary(url, directory, "artwork", useDispositionExtension = false, activeId = activeId)
            }
        }
        return directory
    }

    private suspend fun downloadBinary(
        url: String,
        directory: File,
        stem: String,
        useDispositionExtension: Boolean,
        activeId: String,
        reportProgress: Boolean = false,
        progressStart: Float = 0f,
        progressShare: Float = 1f,
    ) {
        val call = http.newCall(Request.Builder().url(url).get().build())
        activeCalls[activeId] = call
        try {
            call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("Download returned HTTP ${response.code}")
            val extension = if (useDispositionExtension) {
                response.header("Content-Disposition")?.substringAfter("filename=", "")
                    ?.trim('"', ' ', '\'')?.substringAfterLast('.', "")?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,6}")) }
                    ?: response.body?.contentType()?.subtype?.substringAfterLast('+')?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,6}")) }
                    ?: "mp4"
            } else "jpg"
            directory.listFiles().orEmpty().filter { it.name.startsWith("$stem.") || it.name == stem }.forEach(File::delete)
            val destination = File(directory, "$stem.$extension")
            val temporary = File(directory, "$stem.part")
            val body = response.body ?: throw IOException("Download response was empty")
            val total = body.contentLength()
            body.byteStream().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var received = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        if (reportProgress) {
                            activeProgress = activeProgress + (activeId to if (total > 0) {
                                val fraction = progressStart + (received.toFloat() / total.toFloat()) * progressShare
                                "${(fraction * 100).toInt().coerceIn(0, 100)}%"
                            } else formatProgressBytes(received))
                        }
                    }
                }
            }
            if (!temporary.renameTo(destination)) throw IOException("Could not save ${destination.name}")
            if (reportProgress) {
                val completed = ((progressStart + progressShare) * 100).toInt().coerceIn(0, 100)
                activeProgress = activeProgress + (activeId to "$completed%")
            }
            }
        } finally {
            activeCalls.remove(activeId, call)
        }
    }

    private fun finishDownload(id: String) {
        activeJobs.remove(id)
        activeCalls.remove(id)
        activeIds = activeIds - id
        activeTitles = activeTitles - id
        activeProgress = activeProgress - id
        revision++
    }

    private fun formatProgressBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824 -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576 -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private fun readEntry(directory: File): DownloadEntry? = runCatching {
        val metadata = JSONObject(metadataFile(directory).readText())
        val id = metadata.optString("id")
        val kind = metadata.optString("kind")
        if (id.isBlank() || kind.isBlank()) return null
        DownloadEntry(
            id = id,
            kind = kind,
            title = metadata.optString("title", "Untitled"),
            metadata = metadata,
            directory = directory,
            artwork = directory.listFiles().orEmpty().firstOrNull { it.name.startsWith("artwork.") },
            video = directory.listFiles().orEmpty().firstOrNull { it.name.startsWith("video.") },
            parentId = metadata.optNullableString("_downloadParentId"),
        )
    }.getOrNull()

    private fun preview(entry: DownloadEntry) = MediaPreview(
        kind = entry.kind,
        id = entry.id,
        name = entry.title,
        year = entry.metadata.optNullableInt("year"),
        season = entry.metadata.optNullableInt("season"),
        episode = entry.metadata.optNullableInt("episode"),
        numberOfItems = if (entry.kind == "tvShow") entries().count { it.parentId == entry.id && it.video != null } else null,
        artworkPath = entry.artwork?.toURI()?.toString(),
    )

    private fun directory(kind: String, id: String) = File(root, "${kind}_${id.replace(Regex("[^A-Za-z0-9-]"), "_")}")
    private fun metadataFile(directory: File) = File(directory, "metadata.json")

    companion object {
        @Volatile private var instance: DownloadStore? = null
        fun get(context: Context): DownloadStore = instance ?: synchronized(this) {
            instance ?: DownloadStore(context.applicationContext).also { instance = it }
        }
    }
}

private fun org.json.JSONArray?.toStringList(): List<String> =
    if (this == null) emptyList() else List(length()) { optString(it) }.filter(String::isNotBlank)
