package com.kurozetsubou.animeroll.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurozetsubou.animeroll.ui.AppState
import com.kurozetsubou.animeroll.ui.components.*
import com.kurozetsubou.animeroll.ui.theme.AR
import kotlinx.coroutines.launch

/** Libreria: series on the NAS, filter field and Importa (scans /media/_inbox). */
@Composable
fun LibraryScreen(state: AppState) {
    var q by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val list = state.library.filter { q.isBlank() || it.title.contains(q, true) || (it.titleRomaji?.contains(q, true) == true) }
        .sortedBy { it.title.lowercase() }

    LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(start = AR.side, end = AR.side, bottom = 128.dp),
        horizontalArrangement = Arrangement.spacedBy(AR.posterGap), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(3) }) {
            Column(Modifier.statusBarsPadding().padding(top = 16.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ScreenTitle("Libreria", Modifier.weight(1f))
                    SecondaryButton("Importa", Icons.Default.FolderOpen, small = true) {
                        scope.launch {
                            runCatching { state.api.scanInbox() }.onSuccess { state.toast("Importazione avviata"); kotlinx.coroutines.delay(2500); state.refresh() }
                                .onFailure { state.toast(it.message) }
                        }
                    }
                }
                Row(Modifier.padding(top = 16.dp).fillMaxWidth().height(48.dp).clip(AR.chipShape).background(AR.surface)
                    .border(1.dp, AR.hairline, AR.chipShape).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = AR.textTertiary, modifier = Modifier.size(20.dp))
                    Box(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        if (q.isEmpty()) Text("Filtra la libreria", color = AR.textTertiary, fontSize = 15.sp)
                        BasicTextField(q, { q = it }, singleLine = true, cursorBrush = SolidColor(AR.accent),
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp), modifier = Modifier.fillMaxWidth())
                    }
                    if (q.isNotEmpty()) Icon(Icons.Default.Close, "Cancella", tint = AR.textTertiary, modifier = Modifier.size(18.dp).clickableNoRipple { q = "" })
                }
            }
        }
        if (list.isEmpty()) item(span = { GridItemSpan(3) }) {
            EmptyState(Icons.Default.FolderOpen, "Libreria vuota", "Scarica qualcosa sul NAS o metti i file in /media/_inbox e premi Importa")
        }
        items(list, key = { it.id }) { s ->
            Poster(s.title, s.posterUrl, width = null, meta = listOfNotNull(s.year?.toString(), "${s.episodeCount}${s.totalEpisodes?.let { "/$it" } ?: ""} ep").joinToString(" · ")) {
                state.openDetail(s.aniListId, s.id)
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit) = this.then(
    Modifier.clickable(androidx.compose.foundation.interaction.MutableInteractionSource(), null, onClick = onClick))
