package com.kurozetsubou.animeroll.ui.downloads

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurozetsubou.animeroll.Job
import com.kurozetsubou.animeroll.OfflineItem
import com.kurozetsubou.animeroll.fileSize
import com.kurozetsubou.animeroll.ui.AppState
import com.kurozetsubou.animeroll.ui.components.*
import com.kurozetsubou.animeroll.ui.theme.AR
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MenuBg = AR.surfaceStrong
private val Red = AR.danger

private fun statusLabel(s: String) = when (s) {
    "Queued" -> "In coda"; "Running" -> "In corso"; "PostProcessing" -> "Post-elaborazione"
    "Completed" -> "Completato"; "Failed" -> "Non riuscito"; "Cancelled" -> "Annullato"; "Paused" -> "In pausa"; else -> s
}
private fun priorityLabel(p: Int) = when { p >= 10 -> "Subito"; p > 0 -> "Alta"; p < 0 -> "Bassa"; else -> null }

/** Two lists: what the NAS is downloading, and what is saved on this device. */
@Composable
fun DownloadsScreen(state: AppState) {
    var scope by rememberSaveable { mutableIntStateOf(if (state.isOffline) 1 else 0) }
    var jobs by remember { mutableStateOf(emptyList<Job>()) }
    var menu by remember { mutableStateOf(false) }
    val cs = rememberCoroutineScope()
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        while (true) {
            runCatching { state.api.jobs() }.onSuccess { jobs = it }
            delay(if (jobs.any { it.isActive }) 3000 else 15000)
        }
    }
    fun act(block: suspend () -> Unit) = cs.launch { runCatching { block() }.onFailure { state.toast(it.message) }; reload++ }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = AR.side, end = 8.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ScreenTitle("Download", Modifier.weight(1f))
            if (scope == 0) Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Altro") }
                DropdownMenu(menu, { menu = false }, Modifier.background(MenuBg)) {
                    DropdownMenuItem({ Text("Metti tutto in pausa") }, { menu = false; act { state.api.pauseAll() } }, leadingIcon = { Icon(Icons.Default.Pause, null) })
                    DropdownMenuItem({ Text("Riprendi tutto") }, { menu = false; act { state.api.resumeAll() } }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) })
                    HorizontalDivider()
                    DropdownMenuItem({ Text("Rimuovi i conclusi") }, { menu = false; act { state.api.clearFinished() } }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) })
                }
            }
        }
        Segmented(listOf("Sul NAS", "Su questo dispositivo"), scope, Modifier.padding(horizontal = AR.side).fillMaxWidth()) { scope = it }
        if (state.isOffline) OfflineBanner(Modifier.padding(horizontal = AR.side).padding(top = 12.dp))
        if (scope == 0) NasList(state, jobs, ::act) else DeviceList(state)
    }
}

@Composable
private fun NasList(state: AppState, jobs: List<Job>, act: (suspend () -> Unit) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)) {
        if (jobs.isEmpty()) item { EmptyState(Icons.Default.Storage, "Nessun download", "Dalla pagina di un anime tocca “Trova episodi” e mandali al NAS.") }
        items(jobs, key = { it.id }) { JobRow(state, it, act) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun JobRow(state: AppState, j: Job, act: (suspend () -> Unit) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val api = state.api
    val stCol = when (j.status) { "Running" -> AR.accent; "PostProcessing" -> AR.teal; "Completed" -> AR.success; "Failed" -> AR.danger; "Paused" -> AR.yellow; else -> AR.textTertiary }
    Box(Modifier.padding(horizontal = AR.side, vertical = 5.dp)) {
        Column(Modifier.fillMaxWidth().clip(AR.cardShape).background(AR.surface).combinedClickable(onLongClick = { menu = true }) { menu = true }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(j.seriesTitle, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val sub = listOfNotNull(j.episodeNumber?.let { "Episodio ${if (it % 1.0 == 0.0) it.toInt() else it}" }, j.source, if (j.isActive || j.status == "Paused") priorityLabel(j.priority) else null)
                    Text(sub.joinToString(" · "), fontSize = 12.sp, color = AR.textTertiary)
                }
                Text(statusLabel(j.status) + if (j.status == "Running") " ${(j.progress * 100).toInt()}%" else "", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = stCol)
            }
            if (j.isRunning || j.status == "Paused" || (j.status == "Queued" && j.progress > 0))
                LinearProgressIndicator({ j.progress.toFloat() }, Modifier.fillMaxWidth().height(4.dp).clip(AR.chipShape),
                    color = if (j.status == "Paused") AR.yellow else AR.accent, trackColor = Color.White.copy(alpha = .16f))
            j.message?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 12.sp, color = if (j.status == "Failed") AR.danger else AR.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            if (j.status != "Completed") Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (j.canRetry) PrimaryButton("Riprova", null, small = true) { act { api.retryJob(j.id) } }
                if (j.canPause) SecondaryButton("Pausa", null, small = true, ghost = true) { act { api.pauseJob(j.id) } }
                if (j.canResume) PrimaryButton("Riprendi", null, small = true) { act { api.resumeJob(j.id) } }
                if (j.canCancel) SecondaryButton("Annulla", null, small = true) { act { api.cancelJob(j.id) } }
            }
        }
        // Every queue action in one place.
        DropdownMenu(menu, { menu = false }, Modifier.background(MenuBg)) {
            fun run(b: suspend () -> Unit) { menu = false; act(b) }
            if (j.status == "Queued" || j.status == "Paused") DropdownMenuItem({ Text("Avvia subito") }, { run { api.startJobNow(j.id) } }, leadingIcon = { Icon(Icons.Default.Bolt, null) })
            if (j.canPause) DropdownMenuItem({ Text("Metti in pausa") }, { run { api.pauseJob(j.id) } }, leadingIcon = { Icon(Icons.Default.Pause, null) })
            if (j.canResume) DropdownMenuItem({ Text("Riprendi") }, { run { api.resumeJob(j.id) } }, leadingIcon = { Icon(Icons.Default.PlayArrow, null) })
            if (j.isActive || j.status == "Paused") {
                HorizontalDivider()
                listOf(1 to "Priorità alta", 0 to "Priorità normale", -1 to "Priorità bassa").forEach { (p, l) ->
                    val cur = j.priority.coerceIn(-1, 1) == p && j.priority < 10
                    DropdownMenuItem({ Text(l, fontWeight = if (cur) FontWeight.Bold else FontWeight.Normal) }, { run { api.setJobPriority(j.id, p) } },
                        leadingIcon = { if (cur) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) })
                }
                HorizontalDivider()
            }
            if (j.canRetry) DropdownMenuItem({ Text("Riprova") }, { run { api.retryJob(j.id) } }, leadingIcon = { Icon(Icons.Default.Refresh, null) })
            if (j.canCancel) DropdownMenuItem({ Text("Annulla", color = Red) }, { run { api.cancelJob(j.id) } }, leadingIcon = { Icon(Icons.Default.Close, null, tint = Red) })
            if (!j.canCancel && !j.canRetry && j.status != "Queued") DropdownMenuItem({ Text("Nessuna azione", color = AR.textSecondary) }, { menu = false })
        }
    }
}

@Composable
private fun DeviceList(state: AppState) {
    val off = state.offline
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1000); tick++ } }
    val items = off.items.sortedWith(compareBy({ it.series.title }, { it.episode.number }))
    LazyColumn(contentPadding = PaddingValues(top = 12.dp, bottom = 120.dp)) {
        if (items.isEmpty()) item { EmptyState(Icons.Default.PhoneAndroid, "Niente offline", "Tocca la freccia accanto a un episodio per salvarlo sul dispositivo.") }
        else item(key = "space") {
            val total = remember(tick / 5) { off.totalBytes() }
            Text("Spazio usato: ${total.fileSize()} · tieni premuto un episodio per aprirlo in un'altra app.", fontSize = 12.sp,
                color = AR.textSecondary, modifier = Modifier.padding(horizontal = AR.side).padding(bottom = 8.dp))
        }
        items(items, key = { it.episode.id }) { DeviceRow(state, it, tick) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceRow(state: AppState, item: OfflineItem, tick: Int) {
    var menu by remember { mutableStateOf(false) }
    val st = remember(item, tick) { state.offline.state(item) }
    Box {
        Row(Modifier.fillMaxWidth().combinedClickable(onLongClick = { menu = true }) {
            if (st.done) {
                val list = state.offline.items.filter { it.series.id == item.series.id }.map { it.episode }.sortedBy { it.number }
                state.play(item.episode, item.series, list)
            } else menu = true
        }.padding(horizontal = AR.side, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(item.series.bannerUrl ?: item.series.posterUrl, Modifier.width(120.dp), progress = if (st.done) null else st.progress) {
                if (st.done) Icon(Icons.Default.PlayCircleOutline, null, Modifier.align(Alignment.Center).size(30.dp), tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(item.series.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val info = when {
                    st.failed -> "Download non riuscito"
                    !st.done -> "Scaricamento… ${(st.progress * 100).toInt()}%"
                    else -> "Episodio ${item.episode.label} · ${st.bytes.fileSize()}"
                }
                Text(info, fontSize = 12.sp, color = if (st.failed) Red else AR.textSecondary)
            }
        }
        DropdownMenu(menu, { menu = false }, Modifier.background(MenuBg)) {
            if (st.done) DropdownMenuItem({ Text("Apri con…") }, { menu = false; state.offline.openWith(item) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) })
            DropdownMenuItem({ Text("Elimina dal dispositivo", color = Red) }, { menu = false; state.offline.delete(item) },
                leadingIcon = { Icon(Icons.Default.Delete, null, tint = Red) })
        }
    }
}
