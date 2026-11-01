package com.kurozetsubou.animeroll.ui.explore

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurozetsubou.animeroll.Media
import com.kurozetsubou.animeroll.ui.AppState
import com.kurozetsubou.animeroll.ui.components.*
import com.kurozetsubou.animeroll.ui.theme.AR
import java.util.Calendar

private val SEASONS = listOf("Inverno" to "WINTER", "Primavera" to "SPRING", "Estate" to "SUMMER", "Autunno" to "FALL")
private val GENRES = listOf("Action", "Adventure", "Comedy", "Drama", "Fantasy", "Horror", "Mecha", "Music", "Mystery", "Psychological",
    "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural", "Thriller")

private fun currentSeason(): Pair<Int, Int> {
    val c = Calendar.getInstance()
    return c.get(Calendar.MONTH) / 3 to c.get(Calendar.YEAR)
}

/** Esplora: AniList titles by season and genre. */
@Composable
fun ExploreScreen(state: AppState) {
    val now = remember { currentSeason() }
    var seasonIdx by rememberSaveable { mutableIntStateOf(now.first) }
    var year by rememberSaveable { mutableIntStateOf(now.second) }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<Media>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var picker by remember { mutableStateOf(false) }
    val label = "${SEASONS[seasonIdx].first} $year"
    val isNow = seasonIdx == now.first && year == now.second

    LaunchedEffect(seasonIdx, year, genre) {
        items = null; error = null
        runCatching { state.api.browse(SEASONS[seasonIdx].second, year, genre) }.onSuccess { items = it }.onFailure { error = it.message; items = emptyList() }
    }
    val nasIds = remember(state.library) { state.library.mapNotNull { it.aniListId }.toSet() }

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(GridCells.Fixed(3), contentPadding = PaddingValues(start = AR.side, end = AR.side, bottom = 128.dp),
            horizontalArrangement = Arrangement.spacedBy(AR.posterGap), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item(span = { GridItemSpan(3) }) {
                Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                    ScreenTitle("Esplora")
                    Text("Stagioni da AniList", fontSize = 13.sp, color = AR.textTertiary, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 16.dp).fillMaxWidth().height(52.dp).clip(AR.chipShape).background(AR.surface)
                        .border(1.dp, AR.borderStrong, AR.chipShape).clickable { picker = true }.padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = AR.accent, modifier = Modifier.size(20.dp))
                        Text(label, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = AR.display, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        if (isNow) Badge("In corso", color = AR.teal)
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = AR.textSecondary, modifier = Modifier.padding(start = 8.dp))
                    }
                    Row(Modifier.padding(top = 12.dp).fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ArChip("Tutti", selected = genre == null, small = true) { genre = null }
                        GENRES.forEach { g -> ArChip(g, selected = genre == g, small = true) { genre = g } }
                    }
                    Row(Modifier.padding(top = 24.dp, bottom = 4.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFamily = AR.display, modifier = Modifier.weight(1f))
                        items?.let { Text("${it.size} titoli", fontSize = 13.sp, color = AR.textTertiary) }
                    }
                }
            }
            val list = items
            when {
                list == null -> item(span = { GridItemSpan(3) }) { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator(color = AR.accent) } }
                list.isEmpty() -> item(span = { GridItemSpan(3) }) {
                    EmptyState(Icons.Default.EventBusy, error ?: "Nessun titolo", "Nessun titolo di questa stagione" + (genre?.let { " per “$it”" } ?: ""))
                }
                else -> items(list, key = { it.id }) { m ->
                    Poster(m.title, m.coverUrl, badge = if (m.id in nasIds) "NAS" else null, width = null,
                        meta = m.genres.take(2).joinToString(" · ")) { state.openDetail(m.id, null) }
                }
            }
        }
        if (picker) SeasonPicker(seasonIdx, year, onClose = { picker = false }) { s, y -> seasonIdx = s; year = y; picker = false }
    }
}

/** Full-screen picker with two columns: Stagione and Anno. */
@Composable
private fun SeasonPicker(season: Int, year: Int, onClose: () -> Unit, onApply: (Int, Int) -> Unit) {
    BackHandler { onClose() }
    var s by remember { mutableIntStateOf(season) }
    var y by remember { mutableIntStateOf(year) }
    val years = remember { val cur = Calendar.getInstance().get(Calendar.YEAR); (cur + 1 downTo cur - 4).toList() }
    Column(Modifier.fillMaxSize().background(AR.background).statusBarsPadding().navigationBarsPadding().clickable(enabled = false) {}.padding(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Scegli stagione", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, modifier = Modifier.weight(1f))
            IconButton(onClose) { Icon(Icons.Default.Close, "Chiudi") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PickerColumn("Stagione", SEASONS.map { it.first }, SEASONS[s].first, Modifier.weight(1f)) { s = SEASONS.indexOfFirst { n -> n.first == it } }
            PickerColumn("Anno", years.map { "$it" }, "$y", Modifier.weight(1f)) { y = it.toInt() }
        }
        Spacer(Modifier.weight(1f))
        PrimaryButton("Mostra ${SEASONS[s].first} $y", null, Modifier.fillMaxWidth()) { onApply(s, y) }
    }
}

@Composable
private fun PickerColumn(title: String, options: List<String>, selected: String, modifier: Modifier, onPick: (String) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AR.textTertiary, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
        options.forEach { o ->
            val on = o == selected
            Row(Modifier.fillMaxWidth().height(52.dp).clip(AR.cardShape).background(if (on) AR.accentSoft else AR.surface)
                .border(1.dp, if (on) AR.accent else AR.hairline, AR.cardShape).clickable { onPick(o) }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(o, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = if (on) AR.accent else Color.White, modifier = Modifier.weight(1f))
                if (on) Icon(Icons.Default.Check, null, tint = AR.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}
