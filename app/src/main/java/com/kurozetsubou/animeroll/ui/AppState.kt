package com.kurozetsubou.animeroll.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kurozetsubou.animeroll.Api
import com.kurozetsubou.animeroll.ApiException
import com.kurozetsubou.animeroll.Entry
import com.kurozetsubou.animeroll.Episode
import com.kurozetsubou.animeroll.Home
import com.kurozetsubou.animeroll.Media
import com.kurozetsubou.animeroll.OfflineStore
import com.kurozetsubou.animeroll.Series
import com.kurozetsubou.animeroll.Subtitle
import com.kurozetsubou.animeroll.User
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

/** Full-screen pages pushed over the tabs. */
sealed interface Screen {
    data object Search : Screen
}

enum class Tab(val label: String) { Home("Home"), Explore("Esplora"), Library("Libreria"), Downloads("Download"), Profile("Profilo") }

/** Title page target: an AniList id, a NAS series id, or both. */
data class DetailTarget(val aniListId: Int?, val seriesId: Int?)

/** What the player shows: NAS stream or a file saved on the device. */
data class PlayRequest(
    val episode: Episode, val series: Series, val playlist: List<Episode>,
    val url: String, val resumeAt: Double?, val subtitles: List<Subtitle>, val local: Boolean,
) {
    val title get() = "${series.title} · E${episode.label}${episode.title?.let { " $it" } ?: ""}"
    val next get() = playlist.sortedBy { it.number }.firstOrNull { it.number > episode.number }
}

/** App-wide state: session, cached data, navigation and the shared actions (play, add to list...). */
class AppState(val api: Api, val offline: OfflineStore, val scope: CoroutineScope, private val onSignedOut: () -> Unit) {
    var user by mutableStateOf<User?>(null)
    var isOffline by mutableStateOf(false)
    var home by mutableStateOf<Home?>(null)
    var homeError by mutableStateOf<String?>(null)
    var library by mutableStateOf<List<Series>>(emptyList())
    var planning by mutableStateOf<List<Entry>>(emptyList())
    /** AniList media ids already in one of the user's lists (✓ Lista). */
    val inList = mutableStateListOf<Int>()

    var tab by mutableStateOf(Tab.Home)
    val stack = mutableStateListOf<Screen>()
    var detail by mutableStateOf<DetailTarget?>(null)
    var playing by mutableStateOf<PlayRequest?>(null)
    var genre by mutableStateOf<String?>(null)
    val snackbar = SnackbarHostState()

    fun push(s: Screen) { stack.add(s) }
    fun pop() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
    fun openDetail(aniListId: Int?, seriesId: Int?) { detail = DetailTarget(aniListId, seriesId) }
    fun toast(msg: String?) { msg?.let { scope.launch { snackbar.showSnackbar(it) } } }

    fun refresh() = scope.launch {
        runCatching { api.home() }
            .onSuccess { h -> home = h; homeError = null; isOffline = false; h.aniListWatching.forEach { if (it.media.id !in inList) inList.add(it.media.id) } }
            .onFailure { homeError = it.message; if (it is IOException) isOffline = true; if (it is ApiException && it.code == 401) { logout(); return@launch } }
        runCatching { api.library() }.onSuccess { library = it }
        if (user?.aniListConnected == true) runCatching { api.aniListList("PLANNING") }.onSuccess { l ->
            planning = l; l.forEach { if (it.media.id !in inList) inList.add(it.media.id) }
        }
    }

    suspend fun refreshUser() { runCatching { api.me() }.onSuccess { user = it; api.cachedUser = it } }

    /** "+ Lista": puts the title in the AniList "planning" list. */
    fun addToList(mediaId: Int?) {
        if (mediaId == null) return toast("Titolo non collegato ad AniList")
        if (user?.aniListConnected != true) return toast("Collega AniList dalle Impostazioni")
        if (mediaId in inList) return toast("Già nella tua lista AniList")
        scope.launch {
            runCatching { api.setAniListStatus(mediaId, "PLANNING") }
                .onSuccess { inList.add(mediaId); toast("Aggiunto a La mia lista"); runCatching { planning = api.aniListList("PLANNING") } }
                .onFailure { toast(it.message) }
        }
    }

    /** Plays one episode: from the device when it is saved there, else from the NAS. */
    fun play(ep: Episode, series: Series, playlist: List<Episode>) = scope.launch {
        val saved = offline.item(ep.id)?.takeIf { offline.state(it).done }
        if (saved != null) {
            val f: File = offline.file(saved)
            var subs = offline.subtitles(ep.id)
            if (subs.isEmpty()) {
                // Saved before subtitles were kept: fetch them now if the NAS answers (quickly), for this and the next plays.
                runCatching { kotlinx.coroutines.withTimeoutOrNull(3_000) { api.stream(ep.id).subtitles } }.getOrNull()
                    ?.takeIf { it.isNotEmpty() }?.let { offline.saveSubtitles(ep.id, it); subs = offline.subtitles(ep.id) }
            }
            playing = PlayRequest(ep, series, playlist, android.net.Uri.fromFile(f).toString(), ep.positionSeconds, subs, local = true)
            return@launch
        }
        runCatching { api.stream(ep.id) }
            .onSuccess { playing = PlayRequest(ep, series, playlist, it.url, it.resumeAt, it.subtitles, local = false) }
            .onFailure { toast(if (it is IOException) "NAS non raggiungibile" else it.message) }
    }

    /** Next episode to watch: the one in progress, else the first after the last one seen. */
    fun nextEpisode(eps: List<Episode>): Episode? {
        val sorted = eps.sortedWith(compareBy({ it.isExtra }, { it.number }))
        sorted.firstOrNull { (it.positionSeconds ?: 0.0) > 5 && !it.completed }?.let { return it }
        val regular = sorted.filter { !it.isExtra }
        val lastDone = regular.indexOfLast { it.completed }
        return regular.getOrNull(lastDone + 1) ?: sorted.firstOrNull()
    }

    fun playSeries(series: Series) = scope.launch {
        val eps = runCatching { api.episodes(series.id) }.getOrElse { e ->
            // Offline: whatever is saved on the device.
            offline.items.filter { it.series.id == series.id }.map { it.episode }.ifEmpty { toast(e.message); return@launch }
        }
        nextEpisode(eps)?.let { play(it, series, eps) } ?: openDetail(series.aniListId, series.id)
    }

    /** Hero / Novità "Riproduci": plays from the NAS when the series is there, otherwise opens the detail. */
    fun playMedia(m: Media) = scope.launch {
        val s = library.firstOrNull { it.aniListId == m.id } ?: runCatching { api.seriesByAniList(m.id) }.getOrNull()
        if (s != null && s.episodeCount > 0) playSeries(s) else openDetail(m.id, null)
    }

    /** "Esci" (or an expired token): forget the session and every cached screen. */
    fun logout() {
        stack.clear(); detail = null; playing = null; tab = Tab.Home; genre = null
        home = null; homeError = null; library = emptyList(); planning = emptyList(); inList.clear()
        isOffline = false; user = null
        onSignedOut()
    }
}
