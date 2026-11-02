package com.kurozetsubou.animeroll

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** One episode saved on this device. The file lives in the app's Movies folder. */
@Serializable data class OfflineItem(val episode: Episode, val series: Series, val downloadId: Long, val fileName: String)

/** Live state of a device download, read from the system DownloadManager. */
data class OfflineState(val done: Boolean, val failed: Boolean, val progress: Float, val bytes: Long)

/**
 * Device downloads with the system DownloadManager (keeps going in background, shows a notification).
 * The list of saved episodes is kept in private prefs so it works offline.
 */
class OfflineStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("offline", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val items = mutableStateListOf<OfflineItem>().apply {
        addAll(runCatching { json.decodeFromString<List<OfflineItem>>(prefs.getString("items", "[]")!!) }.getOrDefault(emptyList()))
    }

    private fun save() = prefs.edit().putString("items", json.encodeToString(items.toList())).apply()

    fun item(episodeId: Int) = items.firstOrNull { it.episode.id == episodeId }

    fun file(item: OfflineItem) = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), item.fileName)

    fun download(url: String, episode: Episode, series: Series) {
        if (item(episode.id) != null) return
        val name = "${series.title} E${episode.label}"
        val fileName = "${name.replace(Regex("[^\\w\\- ]"), "_")}_${episode.id}.mp4"
        val id = dm.enqueue(DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_MOVIES, fileName))
        items.add(OfflineItem(episode, series, id, fileName))
        save()
    }

    fun state(item: OfflineItem): OfflineState {
        dm.query(DownloadManager.Query().setFilterById(item.downloadId)).use { c ->
            if (c == null || !c.moveToFirst()) {
                val f = file(item)
                return OfflineState(f.exists(), !f.exists(), if (f.exists()) 1f else 0f, f.length())
            }
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            return OfflineState(
                done = status == DownloadManager.STATUS_SUCCESSFUL,
                failed = status == DownloadManager.STATUS_FAILED,
                progress = if (total > 0) done.toFloat() / total else 0f,
                bytes = if (total > 0) total else done,
            )
        }
    }

    fun totalBytes() = items.sumOf { file(it).takeIf { f -> f.exists() }?.length() ?: 0L }

    private val subsDir get() = File(context.filesDir, "offline-subs").also { it.mkdirs() }
    private val subsHttp = okhttp3.OkHttpClient()

    /** Subtitles saved with the episode (WebVTT, plus the original ASS with its fonts), as file:// tracks. Empty if none. */
    fun subtitles(episodeId: Int): List<Subtitle> {
        val index = File(subsDir, "$episodeId.json").takeIf { it.exists() } ?: return emptyList()
        val tracks = runCatching { json.decodeFromString<List<Subtitle>>(index.readText()) }.getOrDefault(emptyList())
        return tracks.mapNotNull { t ->
            val vtt = File(subsDir, "$episodeId.s${t.index}.vtt").takeIf { it.exists() } ?: return@mapNotNull null
            val ass = File(subsDir, "$episodeId.s${t.index}.ass").takeIf { it.exists() }
            t.copy(url = Uri.fromFile(vtt).toString(), assUrl = ass?.let { Uri.fromFile(it).toString() })
        }
    }

    private fun fetch(url: String): ByteArray? = runCatching {
        subsHttp.newCall(okhttp3.Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body?.bytes() else null }
    }.getOrNull()

    /** Small files, fetched right away next to the video: the episode then plays with subtitles without the NAS. */
    suspend fun saveSubtitles(episodeId: Int, tracks: List<Subtitle>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val saved = mutableListOf<Subtitle>()
        for (t in tracks) {
            val vtt = fetch(t.url) ?: continue
            File(subsDir, "$episodeId.s${t.index}.vtt").writeBytes(vtt)
            t.assUrl?.let { a -> fetch(a + (if ('?' in a) "&" else "?") + "embed=1") }?.let { File(subsDir, "$episodeId.s${t.index}.ass").writeBytes(it) }
            saved += t
        }
        if (saved.isNotEmpty()) File(subsDir, "$episodeId.json").writeText(json.encodeToString(saved))
    }

    fun delete(item: OfflineItem) {
        subsDir.listFiles { f -> f.name.startsWith("${item.episode.id}.") }?.forEach { it.delete() }
        dm.remove(item.downloadId)
        file(item).delete()
        items.remove(item)
        save()
    }

    /** "Apri con…": hands the saved file to another player app. */
    fun openWith(item: OfflineItem) {
        val uri = dm.getUriForDownloadedFile(item.downloadId) ?: return
        val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(view, "Apri con…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** "Apri con…" for an episode on the NAS: the signed stream URL to another app. */
fun openUrlWith(context: Context, url: String) {
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*")
    context.startActivity(Intent.createChooser(view, "Apri con…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun Long.fileSize(): String = when {
    this >= 1L shl 30 -> "%.1f GB".format(this / (1L shl 30).toDouble())
    this >= 1L shl 20 -> "%.0f MB".format(this / (1L shl 20).toDouble())
    else -> "${this / 1024} KB"
}
