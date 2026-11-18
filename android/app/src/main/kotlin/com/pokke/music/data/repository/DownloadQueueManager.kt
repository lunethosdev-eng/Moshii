package com.pokke.music.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.pokke.music.data.model.Song
import com.music.bitchord.data.innertube.StreamResolver
import com.music.bitchord.download.Downloader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** Queue/worker based download engine adapted to Pokke's Song and library model. */
class DownloadQueueManager private constructor(context: Context) {
    enum class Status { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
    data class Task(
        val id: Long,
        val song: Song,
        val status: Status,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = -1,
        val error: String? = null,
        val filePath: String? = null,
    ) {
        val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    }

    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val workers = Semaphore(3)
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val paused = ConcurrentHashMap.newKeySet<Long>()
    private val queue = ArrayDeque<Long>()
    private val lock = Any()
    private val nextId = AtomicLong(System.currentTimeMillis())
    private val _tasks = MutableStateFlow<Map<Long, Task>>(emptyMap())
    val tasks: StateFlow<Map<Long, Task>> = _tasks.asStateFlow()
    private val prefs = app.getSharedPreferences("pokke_download_engine_v1", Context.MODE_PRIVATE)

    init { restoreTasks() }

    fun enqueue(song: Song, target: File): Long {
        val existing = _tasks.value.values.firstOrNull {
            it.song.id == song.id && (
                it.status in setOf(Status.QUEUED, Status.RUNNING, Status.PAUSED) ||
                    (it.status == Status.COMPLETED && it.filePath?.let { path -> File(path).isFile } == true)
                )
        }
        if (existing != null) return existing.id
        val id = nextId.incrementAndGet()
        synchronized(lock) {
            _tasks.value = _tasks.value + (id to Task(id, song, Status.QUEUED, filePath = target.absolutePath))
            queue.addLast(id)
            persistTasks()
        }
        runCatching {
            ContextCompat.startForegroundService(app, Intent(app, DownloadForegroundService::class.java))
        }
        dispatch()
        return id
    }

    fun pause(id: Long) {
        paused.add(id)
        jobs[id]?.cancel()
        update(id) { it.copy(status = Status.PAUSED, error = null) }
    }

    fun resume(id: Long) {
        val task = _tasks.value[id] ?: return
        if (task.status != Status.PAUSED && task.status != Status.FAILED) return
        paused.remove(id)
        update(id) { it.copy(status = Status.QUEUED, error = null) }
        synchronized(lock) { if (!queue.contains(id)) queue.addLast(id) }
        dispatch()
    }

    fun cancel(id: Long, deletePartial: Boolean = true) {
        paused.remove(id)
        jobs.remove(id)?.cancel()
        val task = _tasks.value[id]
        if (deletePartial) task?.filePath?.let { File("$it.part").delete() }
        update(id) { it.copy(status = Status.CANCELLED, error = null) }
        synchronized(lock) { queue.remove(id) }
    }

    fun retry(id: Long) = resume(id)

    private fun dispatch() {
        scope.launch {
            while (true) {
                val id = synchronized(lock) { queue.removeFirstOrNull() } ?: break
                if (paused.contains(id)) continue
                val task = _tasks.value[id] ?: continue
                if (task.status != Status.QUEUED) continue
                if (paused.contains(id)) continue
                val job = scope.launch {
                    workers.withPermit {
                        if (!paused.contains(id)) runTask(id)
                    }
                }
                jobs[id] = job
                job.invokeOnCompletion {
                    jobs.remove(id, job)
                    dispatch()
                }
            }
        }
    }

    private suspend fun runTask(id: Long) {
        val task = _tasks.value[id] ?: return
        val youtubeId = task.song.youtubeId?.takeIf { it.isNotBlank() }
        val source = task.song.audioUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        if (youtubeId == null && source == null) {
            update(id) { it.copy(status = Status.FAILED, error = "No hay una URL de audio resoluble para esta canción") }
            return
        }
        val target = task.filePath?.let(::File)
        if (target == null) {
            update(id) { it.copy(status = Status.FAILED, error = "No se definió el destino de descarga") }
            return
        }
        update(id) { it.copy(status = Status.RUNNING, error = null) }
        val partial = File(target.absolutePath + ".part")
        try {
            withContext(Dispatchers.IO) {
                target.parentFile?.mkdirs()
                partial.delete() // BitChord's ranged downloader starts a fresh, identity-bound transfer.
                FileOutputStream(partial, false).use { output ->
                    if (youtubeId != null) {
                        val stream = StreamResolver.resolveForDownload(youtubeId, Int.MAX_VALUE, requireM4a = true)
                        Downloader.fetch(
                            videoId = youtubeId,
                            stream = stream,
                            maxKbps = Int.MAX_VALUE,
                            requireM4a = true,
                            sink = output,
                            onProgress = { written, total ->
                                update(id) { it.copy(downloadedBytes = written, totalBytes = total) }
                            },
                        )
                    } else {
                        Downloader.fetchDirect(
                            url = source!!,
                            headers = emptyMap(),
                            sink = output,
                            onProgress = { written, total ->
                                update(id) { it.copy(downloadedBytes = written, totalBytes = total) }
                            },
                        )
                    }
                }
                if (!partial.renameTo(target)) {
                    partial.copyTo(target, overwrite = true)
                    partial.delete()
                }
            }
            update(id) { it.copy(status = Status.COMPLETED, downloadedBytes = target.length(), totalBytes = target.length(), filePath = target.absolutePath) }
        } catch (cancel: CancellationException) {
            if (paused.contains(id)) update(id) { it.copy(status = Status.PAUSED) }
            else if (_tasks.value[id]?.status != Status.CANCELLED) update(id) { it.copy(status = Status.CANCELLED) }
        } catch (error: Throwable) {
            update(id) { it.copy(status = Status.FAILED, error = error.message ?: "Error de descarga") }
        }
    }

    private inline fun update(id: Long, transform: (Task) -> Task) {
        _tasks.value = _tasks.value.toMutableMap().apply { this[id]?.let { this[id] = transform(it) } }
        persistTasks()
    }

    private fun restoreTasks() {
        val restored = runCatching {
            val array = JSONArray(prefs.getString("tasks", "[]"))
            buildMap<Long, Task> {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val songJson = item.optJSONObject("song") ?: continue
                    val song = Song(
                        id = songJson.optString("id"),
                        title = songJson.optString("title"),
                        artist = songJson.optString("artist"),
                        album = songJson.optString("album").takeIf(String::isNotBlank),
                        coverUrl = songJson.optString("coverUrl").takeIf(String::isNotBlank),
                        animatedCoverUrl = songJson.optString("animatedCoverUrl").takeIf(String::isNotBlank),
                        audioUrl = songJson.optString("audioUrl").takeIf(String::isNotBlank),
                        durationSeconds = songJson.optInt("durationSeconds"),
                        lyricsText = songJson.optString("lyricsText").takeIf(String::isNotBlank),
                        lyricsUrl = songJson.optString("lyricsUrl").takeIf(String::isNotBlank),
                        genre = songJson.optString("genre").takeIf(String::isNotBlank),
                        youtubeId = songJson.optString("youtubeId").takeIf(String::isNotBlank),
                    )
                    val id = item.optLong("id")
                    val oldStatus = runCatching { Status.valueOf(item.optString("status")) }.getOrDefault(Status.QUEUED)
                    val status = if (oldStatus == Status.RUNNING) Status.QUEUED else oldStatus
                    put(id, Task(id, song, status, item.optLong("downloadedBytes"), item.optLong("totalBytes", -1L), item.optString("error").takeIf(String::isNotBlank), item.optString("filePath").takeIf(String::isNotBlank)))
                    if (status == Status.QUEUED) synchronized(lock) { queue.addLast(id) }
                }
            }
        }.getOrDefault(emptyMap())
        _tasks.value = restored
        if (restored.isNotEmpty()) {
            nextId.set(maxOf(nextId.get(), restored.keys.maxOrNull() ?: 0L))
            if (restored.values.any { it.status == Status.QUEUED }) {
                runCatching { ContextCompat.startForegroundService(app, Intent(app, DownloadForegroundService::class.java)) }
            }
            dispatch()
        }
    }

    private fun persistTasks() {
        val snapshot = _tasks.value.values
        val array = JSONArray()
        snapshot.forEach { task ->
            val song = task.song
            val songJson = JSONObject().apply {
                put("id", song.id); put("title", song.title); put("artist", song.artist)
                put("album", song.album); put("coverUrl", song.coverUrl); put("animatedCoverUrl", song.animatedCoverUrl)
                put("audioUrl", song.audioUrl); put("durationSeconds", song.durationSeconds)
                put("lyricsText", song.lyricsText); put("lyricsUrl", song.lyricsUrl); put("genre", song.genre); put("youtubeId", song.youtubeId)
            }
            array.put(JSONObject().apply {
                put("id", task.id); put("song", songJson); put("status", task.status.name)
                put("downloadedBytes", task.downloadedBytes); put("totalBytes", task.totalBytes)
                put("error", task.error); put("filePath", task.filePath)
            })
        }
        prefs.edit().putString("tasks", array.toString()).apply()
    }

    companion object {
        @Volatile private var instance: DownloadQueueManager? = null
        fun get(context: Context): DownloadQueueManager = instance ?: synchronized(this) {
            instance ?: DownloadQueueManager(context).also { instance = it }
        }
    }
}
