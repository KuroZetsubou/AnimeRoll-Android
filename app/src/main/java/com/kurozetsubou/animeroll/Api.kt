package com.kurozetsubou.animeroll

import android.app.Activity
import android.content.Context
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

// ---- Models (same JSON as AnimeRoll.Server) ----
@Serializable data class User(val id: String, val username: String, val aniListConnected: Boolean, val aniListUserName: String? = null, val passkeyCount: Int = 0)
@Serializable data class AuthResponse(val accessToken: String, val user: User)
@Serializable data class Media(
    val id: Int, val title: String, val titleRomaji: String? = null, val description: String? = null,
    val coverUrl: String? = null, val bannerUrl: String? = null, val episodes: Int? = null, val year: Int? = null,
    val format: String? = null, val genres: List<String> = emptyList(), val averageScore: Int? = null,
    val status: String? = null, val nextAiringEpisode: Int? = null,
)
@Serializable data class Entry(val media: Media, val status: String, val progress: Int)
@Serializable data class Series(
    val id: Int, val aniListId: Int? = null, val title: String, val description: String? = null,
    val posterUrl: String? = null, val bannerUrl: String? = null, val totalEpisodes: Int? = null, val episodeCount: Int = 0,
    val titleRomaji: String? = null, val genres: String? = null, val year: Int? = null, val updatedAt: String? = null,
) { val genreList get() = genres.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() } }
@Serializable data class Episode(
    val id: Int, val seriesId: Int, val number: Double, val title: String? = null, val sizeBytes: Long = 0,
    val durationSeconds: Double? = null, val source: String, val positionSeconds: Double? = null, val completed: Boolean = false,
) { val label get() = if (number % 1.0 == 0.0) number.toInt().toString() else number.toString() }
@Serializable data class ContinueItem(val series: Series, val episode: Episode)
@Serializable data class Home(
    val continueWatching: List<ContinueItem>, val recentlyAdded: List<Series>, val aniListWatching: List<Entry>,
    val trending: List<Media>, val seasonal: List<Media>,
)
@Serializable data class Subtitle(val index: Int, val language: String? = null, val label: String, val isDefault: Boolean = false,
    val isForced: Boolean = false, val isSignsOnly: Boolean = false, val url: String)
@Serializable data class StreamInfo(val url: String, val downloadUrl: String, val resumeAt: Double? = null, val subtitles: List<Subtitle> = emptyList())
@Serializable data class Provider(val id: String, val name: String, val enabled: Boolean, val canSearch: Boolean, val canListEpisodes: Boolean, val canStream: Boolean, val hint: String? = null)
@Serializable data class SourceResult(val ref: String, val title: String, val posterUrl: String? = null, val subtitle: String? = null, val extra: Map<String, String>? = null)
@Serializable data class SourceEpisode(val ref: String, val number: Double? = null, val title: String, val extra: Map<String, String>? = null)
@Serializable data class DownloadItem(val sourceRef: String, val episodeNumber: Double? = null)
@Serializable data class DownloadRequest(val source: String, val seriesTitle: String, val aniListId: Int?, val items: List<DownloadItem>)
@Serializable data class Job(val id: String, val source: String, val seriesTitle: String, val episodeNumber: Double? = null, val status: String, val progress: Double, val message: String? = null,
    val priority: Int = 0, val aniListId: Int? = null, val episodeId: Int? = null, val createdAt: String? = null) {
    val isActive get() = status in setOf("Queued", "Running", "PostProcessing")
    val isRunning get() = status == "Running" || status == "PostProcessing"
    val canPause get() = status == "Queued" || status == "Running"
    val canResume get() = status == "Paused"
    val canRetry get() = status == "Failed" || status == "Cancelled"
    val canCancel get() = isActive || status == "Paused"
}
@Serializable data class QueueSettings(val maxConcurrentDownloads: Int = 3)
@Serializable data class PasskeyInfo(val id: Int, val name: String, val createdAt: String? = null, val lastUsedAt: String? = null)
@Serializable data class TelegramStatus(val configured: Boolean, val loggedIn: Boolean)
@Serializable data class TelegramLogin(val next: String? = null, val loggedIn: Boolean = false)
@Serializable data class TelegramChannel(val id: Long, val title: String, val username: String? = null, val enabled: Boolean)

class ApiException(val code: Int, message: String) : Exception(message)

/** OkHttp client for AnimeRoll.Server. Token + server URL are stored in private prefs. */
class Api(context: Context) {
    private val prefs = context.getSharedPreferences("animeroll", Context.MODE_PRIVATE)
    private val http = OkHttpClient()
    val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    var baseUrl: String
        get() = prefs.getString("server", "https://") ?: "https://"
        set(v) = prefs.edit().putString("server", v.trim().trimEnd('/')).apply()
    var token: String?
        get() = prefs.getString("token", null)
        set(v) = prefs.edit().putString("token", v).apply()

    private suspend fun raw(method: String, path: String, body: String? = null, query: Map<String, String> = emptyMap(), auth: Boolean = true): String =
        withContext(Dispatchers.IO) {
            val url = "$baseUrl/$path".toHttpUrl().newBuilder().apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
            val req = Request.Builder().url(url).method(method, body?.toRequestBody("application/json".toMediaType())
                ?: if (method == "GET") null else ByteArray(0).toRequestBody())
            if (auth) token?.let { req.header("Authorization", "Bearer $it") }
            http.newCall(req.build()).execute().use { res ->
                val text = res.body?.string().orEmpty()
                if (!res.isSuccessful) {
                    val msg = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                    throw ApiException(res.code, msg ?: if (res.code == 401) "Credenziali non valide" else "Errore ${res.code}")
                }
                text
            }
        }

    private suspend inline fun <reified T> get(path: String, query: Map<String, String> = emptyMap()): T =
        json.decodeFromString(raw("GET", path, query = query))
    private suspend inline fun <reified B, reified T> send(method: String, path: String, body: B, auth: Boolean = true): T =
        json.decodeFromString(raw(method, path, json.encodeToString(body), auth = auth))

    suspend fun login(u: String, p: String): AuthResponse =
        send<Map<String, String>, AuthResponse>("POST", "api/auth/login", mapOf("username" to u, "password" to p), auth = false).also { token = it.accessToken }
    suspend fun me(): User = get("api/auth/me")
    suspend fun home(): Home = get("api/library/home")
    suspend fun library(): List<Series> = get("api/library/series")
    suspend fun series(id: Int): Series = get("api/library/series/$id")
    suspend fun seriesByAniList(id: Int): Series? = runCatching { get<Series>("api/library/by-anilist/$id") }.getOrNull()
    suspend fun episodes(seriesId: Int): List<Episode> = get("api/library/series/$seriesId/episodes")
    suspend fun stream(episodeId: Int): StreamInfo = get("api/library/episodes/$episodeId/stream")
    suspend fun progress(episodeId: Int, pos: Double, dur: Double?) {
        raw("PUT", "api/library/episodes/$episodeId/progress", json.encodeToString(mapOf("positionSeconds" to pos, "durationSeconds" to dur)))
    }
    suspend fun media(id: Int): Media = get("api/anilist/media/$id")
    suspend fun entry(id: Int): Entry? = runCatching { get<Entry>("api/anilist/entry/$id") }.getOrNull()
    suspend fun search(q: String): List<Media> = get("api/anilist/search", mapOf("q" to q))
    suspend fun providers(): List<Provider> = get("api/sources")
    suspend fun sourceSearch(source: String, q: String): List<SourceResult> = get("api/sources/$source/search", mapOf("q" to q))
    suspend fun sourceEpisodes(source: String, ref: String): List<SourceEpisode> = get("api/sources/$source/episodes", mapOf("ref" to ref))
    suspend fun sendToNas(req: DownloadRequest) { raw("POST", "api/downloads", json.encodeToString(req)) }
    suspend fun jobs(): List<Job> = get("api/downloads")
    suspend fun cancelJob(id: String) { raw("POST", "api/downloads/$id/cancel") }
    suspend fun pauseJob(id: String) { raw("POST", "api/downloads/$id/pause") }
    suspend fun resumeJob(id: String) { raw("POST", "api/downloads/$id/resume") }
    suspend fun retryJob(id: String) { raw("POST", "api/downloads/$id/retry") }
    suspend fun startJobNow(id: String) { raw("POST", "api/downloads/$id/start-now") }
    suspend fun setJobPriority(id: String, priority: Int) { raw("PUT", "api/downloads/$id/priority", """{"priority":$priority}""") }
    suspend fun pauseAll() { raw("POST", "api/downloads/pause-all") }
    suspend fun resumeAll() { raw("POST", "api/downloads/resume-all") }
    suspend fun clearFinished() { raw("DELETE", "api/downloads/finished") }
    suspend fun queueSettings(): QueueSettings = get("api/settings/queue")
    suspend fun setQueueSettings(s: QueueSettings): QueueSettings = send("PUT", "api/settings/queue", s)
    suspend fun librarySearch(q: String): List<Series> = get("api/library/series", mapOf("q" to q))
    suspend fun repairSeries(id: Int) { raw("POST", "api/library/series/$id/repair") }
    suspend fun repairEpisode(id: Int) { raw("POST", "api/library/episodes/$id/repair") }
    suspend fun trending(): List<Media> = get("api/anilist/trending")
    /** Esplora: one AniList season (WINTER, SPRING, SUMMER, FALL) of a year, optionally one genre. */
    suspend fun browse(season: String, year: Int, genre: String?): List<Media> =
        get("api/anilist/browse", buildMap { put("season", season); put("year", year.toString()); genre?.let { put("genre", it) } })
    suspend fun scanInbox() { raw("POST", "api/library/scan-inbox") }
    suspend fun aniListList(status: String): List<Entry> = get("api/anilist/list/$status")
    suspend fun setAniListStatus(mediaId: Int, status: String) { raw("PUT", "api/anilist/entry/$mediaId/status", """{"status":"$status"}""") }
    suspend fun aniListConnectUrl(): String = json.parseToJsonElement(raw("GET", "api/anilist/connect")).jsonObject["url"]!!.jsonPrimitive.content
    suspend fun aniListDisconnect() { raw("DELETE", "api/anilist/connect") }
    suspend fun passkeys(): List<PasskeyInfo> = get("api/auth/passkeys")
    suspend fun deletePasskey(id: Int) { raw("DELETE", "api/auth/passkeys/$id") }
    suspend fun telegramStatus(): TelegramStatus = get("api/telegram/status")
    suspend fun telegramLogin(code: String?): TelegramLogin =
        json.decodeFromString(raw("POST", "api/telegram/login", buildJsonObject { code?.let { put("code", it) } }.toString()))
    suspend fun telegramChannels(): List<TelegramChannel> = get("api/telegram/channels")
    suspend fun saveTelegramChannels(c: List<TelegramChannel>) { raw("PUT", "api/telegram/channels", json.encodeToString(c)) }

    /** Last signed-in user, so the app can open offline (device downloads) when the NAS is unreachable. */
    var cachedUser: User?
        get() = prefs.getString("user", null)?.let { runCatching { json.decodeFromString<User>(it) }.getOrNull() }
        set(v) = prefs.edit().putString("user", v?.let { json.encodeToString(it) }).apply()

    // ---- Passkeys via Credential Manager (needs /.well-known/assetlinks.json on the NAS) ----
    suspend fun passkeyLogin(activity: Activity, username: String?): AuthResponse {
        val begin = json.parseToJsonElement(raw("POST", "api/auth/passkeys/login/begin",
            buildJsonObject { username?.let { put("username", it) } }.toString(), auth = false)).jsonObject
        val cm = CredentialManager.create(activity)
        val result = cm.getCredential(activity, GetCredentialRequest(listOf(GetPublicKeyCredentialOption(begin["options"].toString()))))
        val cred = result.credential as PublicKeyCredential
        val body = buildJsonObject {
            put("ceremonyId", begin["ceremonyId"]!!.jsonPrimitive.content)
            put("response", json.parseToJsonElement(cred.authenticationResponseJson))
        }
        return json.decodeFromString<AuthResponse>(raw("POST", "api/auth/passkeys/login/finish", body.toString(), auth = false))
            .also { token = it.accessToken }
    }

    suspend fun passkeyRegister(activity: Activity, name: String) {
        val begin = json.parseToJsonElement(raw("POST", "api/auth/passkeys/register/begin", "{}")).jsonObject
        val cm = CredentialManager.create(activity)
        val res = cm.createCredential(activity, CreatePublicKeyCredentialRequest(begin["options"].toString())) as CreatePublicKeyCredentialResponse
        val body = buildJsonObject {
            put("ceremonyId", begin["ceremonyId"]!!.jsonPrimitive.content)
            put("name", name)
            put("response", json.parseToJsonElement(res.registrationResponseJson))
        }
        raw("POST", "api/auth/passkeys/register/finish", body.toString())
    }
}

fun String?.stripHtml() = orEmpty().replace(Regex("<br\\s*/?>"), "\n").replace(Regex("<[^>]+>"), "")

