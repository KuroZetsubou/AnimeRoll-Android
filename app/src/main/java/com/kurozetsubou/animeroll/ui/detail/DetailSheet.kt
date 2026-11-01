package com.kurozetsubou.animeroll.ui.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kurozetsubou.animeroll.*
import com.kurozetsubou.animeroll.ui.AppState
import com.kurozetsubou.animeroll.ui.DetailTarget
import com.kurozetsubou.animeroll.ui.components.*
import com.kurozetsubou.animeroll.ui.theme.AR
import com.kurozetsubou.animeroll.ui.theme.tick
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val SheetBg = AR.background

/** Title page, shown as a sheet over Home. Drag down (or right from the left edge) to close. */
@Composable
fun DetailSheet(state: AppState, target: DetailTarget, onClose: () -> Unit) {
    val api = state.api
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val closePx = with(density) { 120.dp.toPx() }

    var media by remember(target) { mutableStateOf<Media?>(null) }
    var series by remember(target) { mutableStateOf<Series?>(null) }
    var entry by remember(target) { mutableStateOf<Entry?>(null) }
    var episodes by remember(target) { mutableStateOf(emptyList<Episode>()) }
    var loading by remember(target) { mutableStateOf(true) }
    var showSources by remember { mutableStateOf(false) }
    var confirmRepair by remember { mutableStateOf(false) }
    var confirmDeleteSeries by remember { mutableStateOf(false) }
    var episodeToDelete by remember { mutableStateOf<Episode?>(null) }
    var tab by remember(target) { mutableIntStateOf(0) }
    var showStatus by remember { mutableStateOf(false) }
    var similar by remember(target) { mutableStateOf<List<Similar>?>(null) }

    suspend fun loadEpisodes() { series?.let { s -> episodes = runCatching { api.episodes(s.id) }.getOrElse {
        state.offline.items.filter { it.series.id == s.id }.map { it.episode }.sortedBy { it.number } } } }

    LaunchedEffect(target) {
        var alId = target.aniListId
        if (target.seriesId != null) {
            series = state.library.firstOrNull { it.id == target.seriesId } ?: runCatching { api.series(target.seriesId) }.getOrNull()
            alId = alId ?: series?.aniListId
        }
        alId?.let { id ->
            media = runCatching { api.media(id) }.getOrNull()
            if (state.user?.aniListConnected == true) entry = api.entry(id)
            if (series == null) series = state.library.firstOrNull { it.aniListId == id } ?: api.seriesByAniList(id)
        }
        loadEpisodes()
        loading = false
    }
    LaunchedEffect(media?.id, series?.aniListId) {
        val id = media?.id ?: series?.aniListId
        if (!loading || id != null) similar = similarTitles(state, id)
    }
    // Refresh progress after watching.
    LaunchedEffect(state.playing) { if (state.playing == null && !loading) loadEpisodes() }

    // Drag to close: vertical via nested scroll at the top of the list, horizontal from the left edge.
    val offY = remember { Animatable(0f) }
    val offX = remember { Animatable(0f) }
    fun settle() = scope.launch {
        if (offY.value > closePx || offX.value > closePx) onClose()
        else { launch { offY.animateTo(0f, AR.motion(300)) }; launch { offX.animateTo(0f, AR.motion(300)) } }
    }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0 && offY.value > 0) {
                    val used = maxOf(available.y, -offY.value)
                    scope.launch { offY.snapTo(offY.value + used) }
                    return Offset(0f, used)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && source == NestedScrollSource.UserInput) {
                    scope.launch { offY.snapTo(offY.value + available.y) }
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offY.value > 0) { settle(); return available }
                return Velocity.Zero
            }
        }
    }
    BackHandler(state.playing == null) { onClose() }

    val title = media?.title ?: series?.title ?: ""
    val genres = media?.genres?.takeIf { it.isNotEmpty() } ?: series?.genreList.orEmpty()
    val next = state.nextEpisode(episodes)
    // Poll device download progress while one is running.
    var tickN by remember { mutableIntStateOf(0) }
    LaunchedEffect(episodes) { while (true) { delay(1000); tickN++ } }

    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 10.dp)
            .offset { IntOffset(offX.value.roundToInt(), offY.value.roundToInt()) }
            .clip(AR.sheetShape).background(SheetBg)) {
            LazyColumn(Modifier.fillMaxSize().nestedScroll(connection), contentPadding = PaddingValues(bottom = 64.dp)) {
                item { Banner(media?.bannerUrl ?: series?.bannerUrl ?: media?.coverUrl ?: series?.posterUrl) }
                item {
                    Column(Modifier.padding(horizontal = AR.side).offset(y = (-56).dp)) {
                        Text(title, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display)
                        media?.titleRomaji?.takeIf { it != title }?.let { Text(it, fontSize = 13.sp, color = AR.textTertiary, modifier = Modifier.padding(top = 4.dp)) }
                        MetaRow(media, series)
                        val alId = media?.id ?: series?.aniListId
                        if (alId != null && (entry != null || state.user?.aniListConnected == true)) {
                            Row(Modifier.padding(bottom = 14.dp).fillMaxWidth().clip(AR.cardShape).background(AR.surface).clickable { showStatus = true }.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Link, null, tint = AR.teal, modifier = Modifier.size(18.dp))
                                Text(entry?.let { "AniList: guardati ${it.progress}/${media?.episodes ?: "?"} · ${alStatusLabel(it.status)}" } ?: "Aggiungi alla lista AniList",
                                    fontSize = 14.sp, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
                                Icon(Icons.Default.ChevronRight, null, tint = AR.textTertiary, modifier = Modifier.size(18.dp))
                            }
                        } else if (state.user?.aniListConnected != true) Text("Collega AniList dal Profilo", fontSize = 13.sp, color = AR.textTertiary, modifier = Modifier.padding(bottom = 14.dp))
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AR.accent, trackColor = AR.surface)
                        else if (next != null && series != null) {
                            val resume = (next.positionSeconds ?: 0.0) > 5 && !next.completed
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrimaryButton(if (resume) "Riprendi E${next.label}" else "Guarda E${next.label}", Icons.Default.PlayArrow, Modifier.weight(1f)) { state.play(next, series!!, episodes) }
                                SecondaryButton("Trova episodi", Icons.Default.Search) { showSources = true }
                            }
                        } else {
                            Text("Non ancora sul NAS", fontSize = 14.sp, color = AR.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp))
                            PrimaryButton("Trova episodi", Icons.Default.Search, Modifier.fillMaxWidth()) { showSources = true }
                        }
                        Description((media?.description ?: series?.description).stripHtml().trim())
                        Segmented(listOf("Episodi", "Simili"), tab) { tab = it }
                    }
                }
                if (tab == 0) {
                    if (episodes.isEmpty() && !loading) item {
                        Text("Nessun episodio sul NAS", color = AR.textTertiary, fontSize = 14.sp, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().offset(y = (-40).dp).padding(AR.side))
                    }
                    if (episodes.isNotEmpty() && series != null) item {
                        Row(Modifier.fillMaxWidth().offset(y = (-48).dp).padding(horizontal = AR.side), horizontalArrangement = Arrangement.End) {
                            SecondaryButton("Ripara serie", Icons.Default.Build, small = true, ghost = true) { confirmRepair = true }
                            Spacer(Modifier.width(8.dp))
                            SecondaryButton("Elimina serie", Icons.Default.Delete, small = true, ghost = true) { confirmDeleteSeries = true }
                            Spacer(Modifier.width(8.dp))
                            SecondaryButton("Scarica tutti sul dispositivo", Icons.Default.Download, small = true, ghost = true) {
                                episodes.filter { state.offline.item(it.id) == null }.forEach { startDeviceDownload(state, it, series!!) }
                            }
                        }
                    }
                    items(episodes, key = { it.id }) { ep -> Box(Modifier.offset(y = (-48).dp)) { EpisodeRow(state, ep, series!!, episodes, tickN, alProgress = entry?.progress ?: 0, onDelete = { episodeToDelete = ep }) } }
                } else {
                    item {
                        Box(Modifier.offset(y = (-40).dp)) {
                            if (similar == null) Box(Modifier.fillMaxWidth().padding(AR.side), Alignment.Center) { CircularProgressIndicator(color = AR.accent) }
                            else if (similar!!.isEmpty()) Text("Nessun titolo simile", color = AR.textTertiary, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(AR.side))
                            else LazyRow(contentPadding = PaddingValues(horizontal = AR.side), horizontalArrangement = Arrangement.spacedBy(AR.posterGap)) {
                                items(similar!!, key = { "${it.aniListId}-${it.seriesId}" }) { s -> Poster(s.title, s.poster) { state.openDetail(s.aniListId, s.seriesId) } }
                            }
                        }
                    }
                }
            }
            // Back chevron top left (glass).
            Box(Modifier.align(Alignment.TopStart).padding(12.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = .55f))
                .clickable(onClick = onClose), Alignment.Center) { Icon(Icons.Default.ChevronLeft, "Indietro", Modifier.size(24.dp), tint = Color.White) }
        }
        // Left edge: drag right to close.
        Box(Modifier.fillMaxHeight().width(20.dp).draggable(rememberDraggableState { d -> scope.launch { offX.snapTo((offX.value + d).coerceAtLeast(0f)) } },
            Orientation.Horizontal, onDragStopped = { settle() }))
    }

    if (showSources) SourcesSheet(api, title, media?.id ?: series?.aniListId, onDownloads = { onClose(); state.tab = com.kurozetsubou.animeroll.ui.Tab.Downloads }) { showSources = false }
    if (showStatus) AniListStatusSheet(entry?.status, onDismiss = { showStatus = false }) { st ->
        showStatus = false
        val id = media?.id ?: series?.aniListId
        if (id != null) scope.launch {
            runCatching { api.setAniListStatus(id, st) }.onSuccess {
                entry = runCatching { api.entry(id) }.getOrNull(); if (id !in state.inList) state.inList.add(id); state.refresh()
            }.onFailure { state.toast(it.message) }
        }
    }
    episodeToDelete?.let { ep ->
        AlertDialog(
            onDismissRequest = { episodeToDelete = null },
            title = { Text("Eliminare l'episodio dal NAS?") },
            text = { Text("Il file e i sottotitoli dell'episodio ${ep.label} vengono cancellati dal NAS e liberano spazio. Per rivederlo dovrai scaricarlo di nuovo.") },
            confirmButton = { TextButton({
                episodeToDelete = null
                scope.launch {
                    runCatching { api.deleteEpisode(ep.id) }
                        .onSuccess { episodes = episodes.filter { it.id != ep.id }; if (episodes.isEmpty()) series = null; state.refresh(); state.toast("Episodio eliminato") }
                        .onFailure { state.toast(it.message) }
                }
            }) { Text("Elimina", color = AR.danger) } },
            dismissButton = { TextButton({ episodeToDelete = null }) { Text("Annulla") } },
            containerColor = AR.surfaceStrong,
        )
    }
    if (confirmDeleteSeries) AlertDialog(
        onDismissRequest = { confirmDeleteSeries = false },
        title = { Text("Eliminare tutta la serie dal NAS?") },
        text = { Text("Tutti gli episodi di “$title” vengono cancellati dal NAS. Questa azione non si può annullare.") },
        confirmButton = { TextButton({
            confirmDeleteSeries = false
            series?.let { s -> scope.launch {
                runCatching { api.deleteSeries(s.id) }
                    .onSuccess { episodes = emptyList(); series = null; state.refresh(); state.toast("Serie eliminata") }
                    .onFailure { state.toast(it.message) }
            } }
        }) { Text("Elimina tutto", color = AR.danger) } },
        dismissButton = { TextButton({ confirmDeleteSeries = false }) { Text("Annulla") } },
        containerColor = AR.surfaceStrong,
    )
    if (confirmRepair) AlertDialog(
        onDismissRequest = { confirmRepair = false },
        title = { Text("Riparare sul NAS?") },
        text = { Text("Il NAS ricontrolla e riconverte tutti gli episodi di “$title”. Lo vedi in Download.") },
        confirmButton = { TextButton({ confirmRepair = false; series?.let { s -> scope.launch {
            runCatching { api.repairSeries(s.id) }.onSuccess { state.toast("Riparazione in coda sul NAS") }.onFailure { state.toast(it.message) } } } }) {
            Text("Ripara", color = AR.accent) } },
        dismissButton = { TextButton({ confirmRepair = false }) { Text("Annulla") } },
        containerColor = AR.surfaceStrong,
    )
}

@Composable
private fun Banner(url: String?) {
    val zoom = remember { Animatable(1f) }
    LaunchedEffect(url) { zoom.animateTo(1.08f, tween(20_000, easing = LinearEasing)) }
    Box(Modifier.fillMaxWidth().height(300.dp)) {
        AsyncImage(url, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = zoom.value; scaleY = zoom.value }.background(AR.surface))
        Box(Modifier.fillMaxSize().background(AR.scrimBottom))
    }
}

@Composable
private fun MetaRow(media: Media?, series: Series?) {
    Row(Modifier.padding(top = 8.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val parts = listOfNotNull(
            (media?.year ?: series?.year)?.toString(), (media?.genres?.takeIf { it.isNotEmpty() } ?: series?.genreList)?.take(3)?.joinToString(" · ")?.ifBlank { null },
            (media?.episodes ?: series?.totalEpisodes)?.let { "$it episodi" }, media?.averageScore?.let { "★ $it%" },
        )
        Text(parts.joinToString(" · "), fontSize = 13.sp, color = AR.textSecondary, modifier = Modifier.weight(1f, false))
        if ((series?.episodeCount ?: 0) > 0) Badge("Sul NAS")
    }
}

private fun alStatusLabel(s: String) = when (s) {
    "CURRENT" -> "In corso"; "PLANNING" -> "Pianificato"; "COMPLETED" -> "Completato"; "PAUSED" -> "In pausa"; "DROPPED" -> "Abbandonato"; else -> s
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AniListStatusSheet(current: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    ModalBottomSheet(onDismiss, containerColor = AR.surfaceStrong, shape = AR.sheetShape) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
            Text("Stato su AniList", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = AR.display, modifier = Modifier.padding(bottom = 8.dp))
            listOf("CURRENT", "PLANNING", "COMPLETED", "PAUSED", "DROPPED").forEach { st ->
                Row(Modifier.fillMaxWidth().height(50.dp).clickable { onPick(st) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(alStatusLabel(st), fontSize = 16.sp, modifier = Modifier.weight(1f))
                    if (st == current) Icon(Icons.Default.Check, null, tint = AR.accent)
                }
                HorizontalDivider(color = AR.hairline)
            }
        }
    }
}

@Composable
private fun Description(text: String) {
    if (text.isBlank()) return
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.animateContentSize(AR.motion()).clickable { expanded = !expanded }) {
        Text(text, fontSize = 14.sp, lineHeight = 20.sp, maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
        if (!expanded) Text("altro", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AR.textSecondary)
    }
}

/** "⬇ Scarica E3" on the device, showing progress while it downloads. */
@Composable
private fun DeviceDownloadButton(state: AppState, ep: Episode, series: Series, tick: Int) {
    val view = LocalView.current
    val item = state.offline.item(ep.id)
    val st = remember(item, tick) { item?.let { state.offline.state(it) } }
    val label = when {
        item == null -> "Scarica E${ep.label}"
        st?.done == true -> "E${ep.label} sul dispositivo"
        st?.failed == true -> "Download non riuscito"
        else -> "Scarico E${ep.label}… ${((st?.progress ?: 0f) * 100).toInt()}%"
    }
    SecondaryButton(label, if (st?.done == true) Icons.Default.DownloadDone else Icons.Default.Download, Modifier.fillMaxWidth(),
        progress = st?.takeIf { !it.done && !it.failed }?.progress) {
        if (item == null) { view.tick(); startDeviceDownload(state, ep, series) }
        else if (st?.failed == true) { state.offline.delete(item); startDeviceDownload(state, ep, series) }
    }
}

fun startDeviceDownload(state: AppState, ep: Episode, series: Series) = state.scope.launch {
    runCatching { state.offline.download(state.api.stream(ep.id).downloadUrl, ep, series) }
        .onSuccess { state.toast("Download sul dispositivo avviato") }.onFailure { state.toast(it.message) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRow(state: AppState, ep: Episode, series: Series, all: List<Episode>, tick: Int, alProgress: Int, onDelete: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var menu by remember { mutableStateOf(false) }
    val item = state.offline.item(ep.id)
    val st = remember(item, tick) { item?.let { state.offline.state(it) } }
    val dur = ep.durationSeconds ?: 0.0
    val p = if (dur > 0) ((ep.positionSeconds ?: 0.0) / dur).toFloat() else 0f
    val seen = ep.completed || ep.number <= alProgress
    Box {
        Row(Modifier.fillMaxWidth().combinedClickable(onLongClick = { menu = true }) { state.play(ep, series, all) }
            .padding(horizontal = AR.side, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(CircleShape).background(if (seen) AR.accentSoft else AR.surface), Alignment.Center) {
                if (seen) Icon(Icons.Default.Check, null, tint = AR.accent, modifier = Modifier.size(18.dp))
                else Text(ep.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = AR.display, color = AR.textSecondary)
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(ep.title ?: "Episodio ${ep.label}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = listOfNotNull(ep.durationSeconds?.let { "${(it / 60).toInt()} min" }, ep.sizeBytes?.takeIf { it > 0 }?.fileSize(), ep.source?.replaceFirstChar { c -> c.uppercase() })
                Text(meta.joinToString(" · "), fontSize = 12.sp, color = AR.textTertiary)
                if (p > 0f && !ep.completed) LinearProgressIndicator({ p.coerceIn(0f, 1f) }, Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(CircleShape),
                    color = AR.accent, trackColor = Color.White.copy(alpha = .16f))
            }
            Box(Modifier.size(40.dp).clip(CircleShape).clickable {
                if (item == null) { view.tick(); startDeviceDownload(state, ep, series) } else menu = true
            }, Alignment.Center) {
                when {
                    item == null -> Icon(Icons.Default.Download, "Scarica sul dispositivo", tint = Color.White)
                    st?.done == true -> Icon(Icons.Default.DownloadDone, "Sul dispositivo", tint = AR.accent)
                    st?.failed == true -> Icon(Icons.Default.ErrorOutline, "Non riuscito", tint = AR.danger)
                    else -> CircularProgressIndicator({ st?.progress ?: 0f }, Modifier.size(24.dp), color = AR.accent, strokeWidth = 2.5.dp, trackColor = AR.surfaceStrong)
                }
            }
        }
        HorizontalDivider(Modifier.align(Alignment.BottomCenter).padding(horizontal = AR.side), color = AR.hairline)
        DropdownMenu(menu, { menu = false }, Modifier.background(AR.surfaceStrong)) {
            DropdownMenuItem({ Text("Ripara sul NAS") }, leadingIcon = { Icon(Icons.Default.Build, null) }, onClick = {
                menu = false
                state.scope.launch { runCatching { state.api.repairEpisode(ep.id) }.onSuccess { state.toast("Riparazione in coda sul NAS") }.onFailure { state.toast(it.message) } }
            })
            DropdownMenuItem({ Text("Apri con…") }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) }, onClick = {
                menu = false
                if (item != null && st?.done == true) state.offline.openWith(item)
                else state.scope.launch { runCatching { openUrlWith(context, state.api.stream(ep.id).url) }.onFailure { state.toast(it.message) } }
            })
            DropdownMenuItem({ Text("Elimina dal NAS…", color = AR.danger) }, leadingIcon = { Icon(Icons.Default.DeleteForever, null, tint = AR.danger) }, onClick = { menu = false; onDelete() })
            if (item != null) DropdownMenuItem({ Text("Elimina dal dispositivo") }, leadingIcon = { Icon(Icons.Default.Delete, null) }, onClick = {
                menu = false; state.offline.delete(item)
            })
        }
    }
}

private data class Similar(val title: String, val poster: String?, val aniListId: Int?, val seriesId: Int?)

/** AniList's recommendations; titles we already have on the NAS open their NAS page. */
private suspend fun similarTitles(state: AppState, aniListId: Int?): List<Similar> {
    if (aniListId == null) return emptyList()
    val rec = runCatching { state.api.recommendations(aniListId) }.getOrDefault(emptyList())
    val nas = state.library.mapNotNull { s -> s.aniListId?.let { it to s } }.toMap()
    return rec.filter { it.id != aniListId }.map { m ->
        nas[m.id]?.let { Similar(it.title, it.posterUrl, it.aniListId, it.id) } ?: Similar(m.title, m.coverUrl, m.id, null)
    }
}
