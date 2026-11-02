package com.kurozetsubou.animeroll.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurozetsubou.animeroll.*
import com.kurozetsubou.animeroll.ui.components.*
import com.kurozetsubou.animeroll.ui.theme.AR
import kotlinx.coroutines.launch

/** "Trova episodi": guided sheet. 1 Fonte, 2 Cerca (or a manual link), 3 Risultati → episodes → Scarica sul NAS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesSheet(api: Api, title: String, aniListId: Int?, seriesId: Int?, onDownloads: () -> Unit, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var providers by remember { mutableStateOf(emptyList<Provider>()) }
    var provider by remember { mutableStateOf<Provider?>(null) }
    var query by remember { mutableStateOf(title) }
    var searchTick by remember { mutableIntStateOf(0) }
    var results by remember { mutableStateOf(emptyList<SourceResult>()) }
    var picked by remember { mutableStateOf<SourceResult?>(null) }
    var episodes by remember { mutableStateOf(emptyList<SourceEpisode>()) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableIntStateOf(0) }
    var link by remember { mutableStateOf("") }
    var number by remember { mutableStateOf("") }
    var subtitleNote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        providers = runCatching { api.providers() }.getOrDefault(emptyList()).filter { it.enabled }
        provider = providers.firstOrNull()
        if (providers.isEmpty()) error = "Nessuna fonte attiva sul NAS."
    }
    LaunchedEffect(provider, searchTick) {
        val p = provider ?: return@LaunchedEffect
        picked = null; episodes = emptyList()
        if (!p.canSearch) { results = emptyList(); return@LaunchedEffect }
        searching = true
        results = runCatching { api.sourceSearch(p.id, query) }.onFailure { error = it.message }.getOrDefault(emptyList())
        searching = false
    }
    suspend fun send(p: Provider, items: List<DownloadItem>, alHint: Int?) {
        runCatching { api.sendToNas(DownloadRequest(p.id, title, aniListId ?: alHint, items)) }
            .onSuccess { sent = items.size }.onFailure { error = it.message }
    }
    val manual = provider?.canSearch == false

    ModalBottomSheet(onDismiss, containerColor = Color(0xFF15151D).copy(alpha = .98f), shape = AR.sheetShape) {
        Column(Modifier.padding(horizontal = 20.dp).fillMaxHeight(0.88f).verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            if (sent > 0) {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.CheckCircle, null, tint = AR.accent, modifier = Modifier.size(48.dp))
                    Text("Inviati $sent download al NAS", fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = AR.display)
                    Text("Li trovi nella coda in Download.", color = AR.textSecondary, fontSize = 14.sp)
                    PrimaryButton("Vai a Download", null, Modifier.fillMaxWidth()) { onDownloads(); onDismiss() }
                    SecondaryButton("Chiudi", null, Modifier.fillMaxWidth(), ghost = true) { onDismiss() }
                }
                return@Column
            }
            Text("Trova episodi", fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = AR.display)
            Text(title, fontSize = 13.sp, color = AR.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)

            StepLabel(1, "Fonte")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                providers.forEach { p -> ArChip(p.name, p == provider) { provider = p } }
            }
            provider?.hint?.let { Text(it, fontSize = 12.sp, color = AR.textSecondary, modifier = Modifier.padding(top = 8.dp)) }

            if (manual) {
                StepLabel(2, "Link o riferimento")
                Field(link, { link = it }, "https://… o magnet")
                Spacer(Modifier.height(10.dp))
                Field(number, { number = it }, "Numero episodio (facoltativo)")
            } else {
                StepLabel(2, "Cerca")
                OutlinedTextField(query, { query = it }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(50),
                    leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Titolo da cercare") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions { searchTick++ },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AR.accent, unfocusedBorderColor = AR.hairline,
                        focusedContainerColor = AR.surface, unfocusedContainerColor = AR.surface))
                val p = picked
                if (p == null) {
                    StepLabel(3, "Risultati")
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AR.accent, trackColor = AR.surface)
                    else if (results.isEmpty()) Text("Nessun risultato.", color = AR.textSecondary, fontSize = 14.sp)
                    results.forEach { r ->
                        Row(Modifier.fillMaxWidth().clickable {
                            val pr = provider ?: return@clickable
                            if (!pr.canListEpisodes) { scope.launch { send(pr, listOf(DownloadItem(r.ref)), null) }; return@clickable }
                            picked = r; searching = true
                            scope.launch {
                                episodes = runCatching { api.sourceEpisodes(pr.id, r.ref) }.onFailure { error = it.message }.getOrDefault(emptyList())
                                selected = episodes.map { it.ref }.toSet(); searching = false
                            }
                        }.heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                r.subtitle?.let { Text(it, fontSize = 12.sp, color = AR.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = AR.textTertiary)
                        }
                        HorizontalDivider(color = AR.hairline)
                    }
                } else {
                    Row(Modifier.padding(top = 18.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).clip(androidx.compose.foundation.shape.CircleShape).background(AR.accent), Alignment.Center) {
                            Text("3", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, color = AR.onAccent)
                        }
                        Text(p.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Text("Tutti", color = AR.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { selected = episodes.map { it.ref }.toSet() }.padding(8.dp))
                        Text("Nessuno", color = AR.accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { selected = emptySet() }.padding(8.dp))
                    }
                    if (searching) LinearProgressIndicator(Modifier.fillMaxWidth(), color = AR.accent, trackColor = AR.surface)
                    episodes.forEach { e ->
                        val on = e.ref in selected
                        Row(Modifier.fillMaxWidth().clickable { selected = if (on) selected - e.ref else selected + e.ref }.heightIn(min = 50.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(if (on) AR.accent else Color.Transparent)
                                .border(1.5.dp, if (on) AR.accent else AR.borderStrong, RoundedCornerShape(6.dp)), Alignment.Center) {
                                if (on) Icon(Icons.Default.Check, null, tint = AR.onAccent, modifier = Modifier.size(16.dp))
                            }
                            Text(e.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        HorizontalDivider(color = AR.hairline)
                    }
                    TextButton({ picked = null }) { Text("← Risultati", color = AR.textSecondary) }
                }
            }
            error?.let { Text(it, color = AR.danger, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }

            if (!manual && picked != null && provider?.canSubtitles == true && seriesId != null) {
                SecondaryButton("Aggiorna solo i sottotitoli (ASS)", Icons.Default.ClosedCaption, Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    val p = provider ?: return@SecondaryButton
                    val ref = picked?.ref ?: return@SecondaryButton
                    subtitleNote = "Invio…"
                    scope.launch {
                        subtitleNote = runCatching { api.refreshSubtitles(seriesId, p.id, ref) }
                            .map { r -> "${r.queued} episodi in coda per i sottotitoli" + (if (r.notFound.isEmpty()) "" else " · non trovati su ${p.name}: " + r.notFound.joinToString(", ") { n -> if (n % 1.0 == 0.0) n.toInt().toString() else n.toString() }) + ". Segui l'avanzamento in Download." }
                            .getOrElse { it.message }
                    }
                }
                subtitleNote?.let { Text(it, color = AR.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }

            val count = if (manual) (if (link.isNotBlank()) 1 else 0) else if (picked != null) selected.size else 0
            PrimaryButton("Scarica sul NAS ($count)", Icons.Default.Download, Modifier.fillMaxWidth().padding(top = 20.dp), enabled = count > 0) {
                val p = provider ?: return@PrimaryButton
                scope.launch {
                    if (manual) send(p, listOf(DownloadItem(link.trim(), number.replace(",", ".").toDoubleOrNull())), null)
                    else {
                        val items = episodes.filter { it.ref in selected }.map { DownloadItem(it.ref, it.number) }
                        send(p, items, episodes.firstOrNull()?.extra?.get("anilistId")?.toIntOrNull())
                    }
                }
            }
        }
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, hint: String) {
    OutlinedTextField(value, onChange, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = AR.cardShape, placeholder = { Text(hint) },
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = AR.accent, unfocusedBorderColor = AR.hairline,
            focusedContainerColor = AR.surface, unfocusedContainerColor = AR.surface))
}
