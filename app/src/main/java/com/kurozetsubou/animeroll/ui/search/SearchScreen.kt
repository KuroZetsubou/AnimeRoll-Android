package com.kurozetsubou.animeroll.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurozetsubou.animeroll.Media
import com.kurozetsubou.animeroll.Series
import com.kurozetsubou.animeroll.ui.AppState
import com.kurozetsubou.animeroll.ui.components.Poster
import com.kurozetsubou.animeroll.ui.components.Thumb
import com.kurozetsubou.animeroll.ui.theme.AR
import kotlinx.coroutines.delay

/** Cerca: results as you type (300 ms debounce); NAS series first, then AniList. */
@Composable
fun SearchScreen(state: AppState) {
    var q by rememberSaveableQuery()
    var nas by remember { mutableStateOf(emptyList<Series>()) }
    var al by remember { mutableStateOf(emptyList<Media>()) }
    var trending by remember { mutableStateOf(state.home?.trending.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // Opening a result hides the keyboard first.
    fun open(aniListId: Int?, seriesId: Int?) { focusManager.clearFocus(); keyboard?.hide(); state.openDetail(aniListId, seriesId) }

    LaunchedEffect(Unit) {
        focus.requestFocus()
        if (trending.isEmpty()) trending = runCatching { state.api.trending() }.getOrDefault(emptyList())
    }
    LaunchedEffect(q) {
        if (q.isBlank()) { nas = emptyList(); al = emptyList(); return@LaunchedEffect }
        delay(300)
        busy = true
        nas = runCatching { state.api.librarySearch(q) }.getOrDefault(nas)
        al = if (q.length >= 2) runCatching { state.api.search(q) }.getOrDefault(al) else emptyList()
        busy = false
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, end = AR.side, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ state.pop() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
            Row(Modifier.weight(1f).clip(AR.buttonShape).background(AR.surfaceStrong).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Search, null, tint = AR.textSecondary, modifier = Modifier.size(20.dp))
                Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    if (q.isEmpty()) Text("Cerca serie, film, generi", color = AR.textSecondary, fontSize = 16.sp)
                    BasicTextField(q, { q = it }, singleLine = true, cursorBrush = SolidColor(AR.accent),
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp), modifier = Modifier.fillMaxWidth().focusRequester(focus))
                }
                if (q.isNotEmpty()) Icon(Icons.Default.Close, "Cancella", tint = AR.textSecondary, modifier = Modifier.size(20.dp).clickable { q = "" })
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = AR.accent, trackColor = Color.Transparent)

        if (q.isBlank()) {
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                item { Text("Di tendenza", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = AR.side, vertical = 8.dp)) }
                items(trending, key = { it.id }) { m ->
                    Row(Modifier.fillMaxWidth().clickable { open(m.id, null) }.padding(horizontal = AR.side, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Thumb(m.bannerUrl ?: m.coverUrl, Modifier.width(140.dp))
                        Text(m.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                        Icon(Icons.Default.PlayCircleOutline, "Apri", Modifier.size(32.dp), tint = Color.White)
                    }
                }
            }
        } else {
            val nasIds = nas.mapNotNull { it.aniListId }.toSet()
            val alOnly = al.filter { it.id !in nasIds }
            LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(AR.side),
                horizontalArrangement = Arrangement.spacedBy(AR.posterGap), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (nas.isNotEmpty()) item(span = { GridItemSpan(3) }) { Header("Sul tuo NAS") }
                items(nas, key = { "n${it.id}" }) { s -> Poster(s.title, s.posterUrl, "${s.episodeCount} ep", width = null) { open(s.aniListId, s.id) } }
                if (alOnly.isNotEmpty()) item(span = { GridItemSpan(3) }) { Header("Su AniList") }
                items(alOnly, key = { "a${it.id}" }) { m -> Poster(m.title, m.coverUrl, width = null) { open(m.id, null) } }
                if (!busy && nas.isEmpty() && alOnly.isEmpty()) item(span = { GridItemSpan(3) }) {
                    Text("Nessun risultato per “$q”.", color = AR.textSecondary, modifier = Modifier.padding(top = 24.dp))
                }
            }
        }
    }
}

@Composable
private fun Header(t: String) = Text(t, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))

@Composable
private fun rememberSaveableQuery() = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
