package com.photangralenphie.mymedia.androidtv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Call
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

data class DownloadEntry(
    val id: String,
    val kind: String,
    val detail: MediaDetail,
    val directory: File,
    val artwork: File?,
    val video: File?,
    val parentId: String?,
    val sizeBytes: Long,
) {
    val title: String get() = detail.title
}

private data class ActiveDownload(
    val title: String,
    val progress: String,
)

private data class DownloadStatus(
    val active: Map<String, ActiveDownload> = emptyMap(),
    val lastError: String? = null,
)

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
    private var catalog by mutableStateOf<List<DownloadEntry>>(emptyList())
    private var status by mutableStateOf(DownloadStatus())

    val activeIds: Set<String> get() = status.active.keys
    val activeTitles: Map<String, String> get() = status.active.mapValues { it.value.title }
    val activeProgress: Map<String, String> get() = status.active.mapValues { it.value.progress }
    val lastError: String? get() = status.lastError

    init {
        applicationScope.launch { refreshCatalog() }
    }

    fun entries(): List<DownloadEntry> = catalog

    fun playableEntries(): List<DownloadEntry> = entries().filter { it.video?.isFile == true }

    fun previews(source: BrowseSource? = null, filters: MediaFilters = MediaFilters()): List<MediaPreview> {
        val all = entries()
        val playable = all.filter { it.video?.isFile == true }
        val visible = when (source) {
            BrowseSource.Movies -> playable.filter { it.kind == "movie" }
            BrowseSource.TvShows -> all.filter { entry ->
                entry.kind == "tvShow" && playable.any { it.parentId == entry.id }
            }
            BrowseSource.Favorites -> playable.filter { it.detail.isFavorite }
            BrowseSource.Pinned -> playable.filter { it.detail.isPinned }
            BrowseSource.Unwatched -> playable.filterNot { it.detail.isWatched }
            BrowseSource.Collections -> emptyList()
            BrowseSource.Downloads -> all.filter { entry ->
                (entry.kind == "movie" && entry.video != null) ||
                    (entry.kind == "tvShow" && playable.any { it.parentId == entry.id })
            }
            is BrowseSource.Genre -> all.filter { entry ->
                (entry.video != null || entry.kind == "tvShow") &&
                    entry.detail.genres.any { it.equals(source.genre, ignoreCase = true) }
            }
            else -> playable
        }
        return visible.filter { entry ->
            val year = entry.detail.year ?: 0
            val duration = entry.detail.durationMinutes
            (filters.minYear.toIntOrNull()?.let { year >= it } ?: true) &&
                (filters.maxYear.toIntOrNull()?.let { year <= it } ?: true) &&
                (filters.minLength.toIntOrNull()?.let { duration >= it } ?: true) &&
                (filters.maxLength.toIntOrNull()?.let { duration <= it } ?: true) &&
                (filters.favorite?.let { entry.detail.isFavorite == it } ?: true) &&
                (filters.watched?.let { entry.detail.isWatched == it } ?: true) &&
                (filters.genres.isEmpty() || entry.detail.genres.any { it in filters.genres })
        }.map { preview(it, all) }
    }

    fun genres(kind: String): List<String> = entries().asSequence()
        .filter { it.video != null || it.kind == "tvShow" }
        .filter { kind == "both" || (kind == "movies" && it.kind == "movie") || (kind == "tvShows" && it.kind == "tvShow") }
        .flatMap { it.detail.genres.asSequence() }
        .distinct().sorted().toList()

    fun search(query: String): List<MediaPreview> {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) return emptyList()
        return entries().filter { entry ->
            if (entry.video == null && entry.kind != "tvShow") return@filter false
            entry.detail.searchableText().lowercase().contains(needle)
        }.let { matches -> matches.map { preview(it, entries()) } }
    }

    fun person(name: String): PersonDetail {
        val all = entries()
        val matched = all.filter { it.video?.isFile == true && it.detail.credits.contains(name) }
        return PersonDetail(
            name = name,
            creditedMovies = matched.filter { it.kind == "movie" }.map { preview(it, all) },
            creditedEpisodes = matched.filter { it.kind == "episode" }.map { preview(it, all) },
        )
    }

    fun detail(id: String): MediaDetail? = entries().firstOrNull { it.id == id }?.detail

    fun previewFor(id: String): MediaPreview? = entries().let { all -> all.firstOrNull { it.id == id }?.let { preview(it, all) } }

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

    fun startMediaDownload(api: ApiClient, detail: MediaDetail, parent: MediaDetail? = null) {
        val id = detail.id
        if (!beginDownload(id, detail.title)) return
        launchDownload(id, "Download failed") {
            downloadMedia(api, detail, parent, id)
        }
    }

    fun startTvShowDownload(api: ApiClient, show: MediaDetail, episodeLimit: Int?) {
        val showId = show.id
        if (!beginDownload(showId, show.title)) return
        launchDownload(showId, "TV show download failed") {
            saveMetadataAndArtwork(api, "tvShow", show, null, showId)
            val previews = show.children
                .sortedWith(compareBy({ it.season ?: 0 }, { it.episode ?: 0 }))
            val selected = if (episodeLimit == null) {
                previews.filterNot { isDownloaded(it.id) }.map { it to api.detail(it) }
            } else {
                val unwatched = mutableListOf<Pair<MediaPreview, MediaDetail>>()
                for (preview in previews) {
                    if (isDownloaded(preview.id)) continue
                    val episode = api.detail(preview)
                    if (!episode.isWatched) unwatched += preview to episode
                    if (unwatched.size >= episodeLimit) break
                }
                unwatched
            }
            // Each episode is finalized independently, so completed episodes remain when
            // the user cancels a series download.
            selected.forEachIndexed { index, (_, episode) ->
                downloadMedia(
                    api, episode,
                    show,
                    showId,
                    progressStart = index.toFloat() / selected.size.coerceAtLeast(1),
                    progressShare = 1f / selected.size.coerceAtLeast(1),
                )
            }
        }
    }

    fun cancel(id: String) {
        activeJobs.remove(id)?.cancel()
        activeCalls.remove(id)?.cancel()
        removeActiveDownload(id)
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

        // A show download uses the show's id for its active job. An episode delete must
        // stop that parent job as well, otherwise it can recreate the episode files.
        val downloadIds = targets.flatMap { listOfNotNull(it.id, it.parentId) }.plus(id).toSet()
        cancelAndJoin(downloadIds)
        targets.forEach { it.directory.deleteRecursively() }
        refreshCatalog()
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        cancelAndJoin(activeJobs.keys.toSet())
        root.listFiles().orEmpty().forEach(File::deleteRecursively)
        refreshCatalog()
    }

    fun updatePlayback(id: String, progressMinutes: Int, watched: Boolean) {
        applicationScope.launch {
            val entry = entries().firstOrNull { it.id == id } ?: return@launch
            val updated = entry.detail.withUpdate(MediaUpdate(progressMinutes = progressMinutes, isWatched = watched))
            writeMetadata(entry.directory, updated, entry.parentId)
            refreshCatalog()
        }
    }

    fun updateMetadata(value: MediaDetail) {
        applicationScope.launch {
            val entry = entries().firstOrNull { it.id == value.id } ?: return@launch
            writeMetadata(entry.directory, value.copy(kind = entry.kind), entry.parentId)
            refreshCatalog()
        }
    }

    private suspend fun downloadMedia(
        api: ApiClient,
        detail: MediaDetail,
        parent: MediaDetail?,
        progressId: String,
        progressStart: Float = 0f,
        progressShare: Float = 1f,
    ) {
        val parentDetail = if (detail.kind == "episode") {
            parent ?: detail.parentShow?.let { api.detail(it) }
        } else null
        if (parentDetail != null) saveMetadataAndArtwork(api, "tvShow", parentDetail, null, progressId)
        val directory = saveMetadataAndArtwork(api, detail.kind, detail, parentDetail?.id, progressId)
        downloadBinary(
            api.downloadVideoUrl(detail.id),
            directory,
            "video",
            useDispositionExtension = true,
            activeId = progressId,
            reportProgress = true,
            progressStart = progressStart,
            progressShare = progressShare,
        )
    }

    private suspend fun saveMetadataAndArtwork(api: ApiClient, kind: String, value: MediaDetail, parentId: String?, activeId: String): File {
        val id = value.id
        if (id.isBlank()) throw IOException("The server returned metadata without an id")
        val directory = directory(kind, id).apply { mkdirs() }
        writeMetadata(directory, value.copy(kind = kind), parentId)
        api.absoluteUrl(value.artworkPath)?.let { url ->
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
        val temporary = File(directory, "$stem.part")
        val call = http.newCall(Request.Builder().url(url).get().build())
        activeCalls[activeId] = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Download returned HTTP ${response.code}")
                val extension = if (useDispositionExtension) {
                    response.header("Content-Disposition")?.substringAfter("filename=", "")
                        ?.trim('"', ' ', '\'')?.substringAfterLast('.', "")
                        ?.takeIf { it.matches(FILE_EXTENSION_PATTERN) }
                        ?: response.body?.contentType()?.subtype?.substringAfterLast('+')
                            ?.takeIf { it.matches(FILE_EXTENSION_PATTERN) }
                        ?: DEFAULT_VIDEO_EXTENSION
                } else {
                    DEFAULT_ARTWORK_EXTENSION
                }
                directory.listFiles().orEmpty()
                    .filter { it.name.startsWith("$stem.") || it.name == stem }
                    .forEach(File::delete)
                val destination = File(directory, "$stem.$extension")
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
                                val progress = if (total > 0) {
                                    val fraction = progressStart + (received.toFloat() / total.toFloat()) * progressShare
                                    "${(fraction * 100).toInt().coerceIn(0, 100)}%"
                                } else {
                                    formatByteCount(received)
                                }
                                updateProgress(activeId, progress)
                            }
                        }
                    }
                }
                if (!temporary.renameTo(destination)) throw IOException("Could not save ${destination.name}")
                if (reportProgress) {
                    val completed = ((progressStart + progressShare) * 100).toInt().coerceIn(0, 100)
                    updateProgress(activeId, "$completed%")
                }
            }
        } finally {
            activeCalls.remove(activeId, call)
            // Each transfer owns its own partial file. This prevents cancelling one
            // download from deleting the in-progress files of another download.
            temporary.delete()
        }
    }

    @Synchronized
    private fun beginDownload(id: String, title: String): Boolean {
        if (id.isBlank() || id in status.active) return false
        status = status.copy(
            active = status.active + (id to ActiveDownload(title = title, progress = "0%")),
            lastError = null,
        )
        return true
    }

    private fun launchDownload(id: String, fallbackError: String, block: suspend () -> Unit) {
        val job = applicationScope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError(error.message ?: fallbackError)
            } finally {
                finishDownload(id)
            }
        }
        activeJobs[id] = job
        job.start()
    }

    @Synchronized
    private fun updateProgress(id: String, progress: String) {
        val download = status.active[id] ?: return
        status = status.copy(active = status.active + (id to download.copy(progress = progress)))
    }

    @Synchronized
    private fun reportError(message: String) {
        status = status.copy(lastError = message)
    }

    @Synchronized
    private fun removeActiveDownload(id: String) {
        status = status.copy(active = status.active - id)
    }

    private fun finishDownload(id: String) {
        synchronized(this) {
            activeJobs.remove(id)
            activeCalls.remove(id)
            status = status.copy(active = status.active - id)
        }
        refreshCatalog()
    }

    private suspend fun cancelAndJoin(ids: Set<String>) {
        val jobs = ids.mapNotNull { id -> activeJobs.remove(id) }
        jobs.forEach(Job::cancel)
        ids.forEach { id ->
            activeCalls.remove(id)?.cancel()
            removeActiveDownload(id)
        }
        jobs.joinAll()
    }

    private fun readEntry(directory: File): DownloadEntry? = runCatching {
        val stored = JSONObject(metadataFile(directory).readText())
        val kind = stored.optString("kind")
        val detail = MediaDetail.fromJson(stored, kind)
        if (detail.id.isBlank() || kind.isBlank()) return null
        val files = directory.listFiles().orEmpty()
        DownloadEntry(
            id = detail.id,
            kind = kind,
            detail = detail,
            directory = directory,
            artwork = files.firstOrNull { it.name.startsWith("artwork.") },
            video = files.firstOrNull { it.name.startsWith("video.") },
            parentId = stored.optNullableString("_downloadParentId"),
            sizeBytes = directory.walkTopDown().filter(File::isFile).sumOf(File::length),
        )
    }.getOrNull()

    private fun preview(entry: DownloadEntry, all: List<DownloadEntry>): MediaPreview = entry.detail.toPreview(
        artworkPath = entry.artwork?.toURI()?.toString(),
    ).copy(
        numberOfItems = if (entry.kind == "tvShow") all.count { it.parentId == entry.id && it.video != null } else entry.detail.numberOfItems,
    )

    @Synchronized
    private fun refreshCatalog() {
        catalog = root.listFiles().orEmpty().mapNotNull(::readEntry)
            .sortedWith(compareBy({ it.kind }, { it.title.lowercase() }))
        revision++
    }

    private fun writeMetadata(directory: File, detail: MediaDetail, parentId: String?) {
        val stored = detail.toJson()
        if (!parentId.isNullOrBlank()) stored.put("_downloadParentId", parentId)
        metadataFile(directory).writeText(stored.toString())
    }

    private fun directory(kind: String, id: String) = File(root, "${kind}_${id.replace(Regex("[^A-Za-z0-9-]"), "_")}")
    private fun metadataFile(directory: File) = File(directory, "metadata.json")

    companion object {
        private val FILE_EXTENSION_PATTERN = Regex("[A-Za-z0-9]{1,6}")
        private const val DEFAULT_VIDEO_EXTENSION = "mp4"
        private const val DEFAULT_ARTWORK_EXTENSION = "jpg"

        @Volatile private var instance: DownloadStore? = null
        fun get(context: Context): DownloadStore = instance ?: synchronized(this) {
            instance ?: DownloadStore(context.applicationContext).also { instance = it }
        }
    }
}
