package com.kurozetsubou.animeroll.ui.home

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kurozetsubou.animeroll.ContinueItem
import com.kurozetsubou.animeroll.Media
import com.kurozetsubou.animeroll.Series
import com.kurozetsubou.animeroll.ui.AppState
import com.kurozetsubou.animeroll.ui.Screen
import com.kurozetsubou.animeroll.ui.Tab
import com.kurozetsubou.animeroll.ui.components.*
import com.kurozetsubou.animeroll.ui.theme.AR
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import kotlin.math.abs

private fun Media.hasGenre(g: String?) = g == null || genres.any { it.equals(g, true) }
private fun Series.hasGenre(g: String?) = g == null || genreList.any { it.equals(g, true) }

@Composable
fun HomeScreen(state: AppState) {
    val home = state.home
    val g = state.genre
    val allGenres = remember(home, state.library) {
        (state.library.flatMap { it.genreList } + home?.trending.orEmpty().flatMap { it.genres } + home?.seasonal.orEmpty().flatMap { it.genres })
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(12)
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 128.dp)) {
        item {
            Column(Modifier.statusBarsPadding().padding(horizontal = AR.side).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                GlassPill("Cerca nella libreria e su AniList", Icons.Default.Search) { state.push(Screen.Search) }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ArChip("Tutti", selected = g == null) { state.genre = null }
                    allGenres.forEach { ArChip(it, selected = it.equals(g, true)) { state.genre = it } }
                }
            }
        }
        if (state.isOffline) item { OfflineBanner(Modifier.padding(horizontal = AR.side).padding(top = 12.dp).clickable { state.tab = Tab.Downloads }) }
        if (home == null) {
            item {
                Box(Modifier.fillMaxWidth().height(300.dp), Alignment.Center) {
                    if (state.homeError != null) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.homeError ?: "", color = AR.textSecondary)
                        TextButton({ state.refresh() }) { Text("Riprova", color = AR.accent) }
                    } else CircularProgressIndicator(color = AR.accent)
                }
            }
            return@LazyColumn
        }
        val watching = home.aniListWatching.filter { it.media.hasGenre(g) }
        val featured = (watching.map { it.media } + home.trending.filter { it.hasGenre(g) }).distinctBy { it.id }.take(5)
        if (featured.isNotEmpty()) item(key = "hero") { HeroCarousel(state, featured) }

        val cont = home.continueWatching.filter { it.series.hasGenre(g) }
        if (cont.isNotEmpty()) item(key = "cont") {
            Shelf("Continua a guardare") { items(cont, key = { it.episode.id }) { ContinueCard(state, it) } }
        }
        if (watching.isNotEmpty()) item(key = "watching") {
            Shelf("Stai guardando su AniList") {
                items(watching, key = { it.media.id }) { e ->
                    Poster(e.media.title, e.media.coverUrl, meta = "${e.progress}/${e.media.episodes ?: "?"} episodi") { state.openDetail(e.media.id, null) }
                }
            }
        }
        val planned = state.planning.filter { it.media.hasGenre(g) }
        if (planned.isNotEmpty()) item(key = "planned") {
            Shelf("Pianificati da vedere") {
                items(planned, key = { it.media.id }) { e ->
                    Poster(e.media.title, e.media.coverUrl, meta = listOfNotNull(e.media.episodes?.let { "$it episodi" }, e.media.genres.firstOrNull()).joinToString(" · ")) { state.openDetail(e.media.id, null) }
                }
            }
        }
        val nas = home.recentlyAdded.filter { it.hasGenre(g) }
        if (nas.isNotEmpty()) item(key = "nas") {
            Shelf("Sul tuo NAS") { items(nas, key = { it.id }) { s -> Poster(s.title, s.posterUrl, "${s.episodeCount} ep", meta = s.year?.toString()) { state.openDetail(s.aniListId, s.id) } } }
        }
        val seasonal = home.seasonal.filter { it.hasGenre(g) }
        if (seasonal.isNotEmpty()) item(key = "season") {
            Shelf("Questa stagione") { items(seasonal, key = { it.id }) { m -> Poster(m.title, m.coverUrl, meta = m.genres.firstOrNull()) { state.openDetail(m.id, null) } } }
        }
        val top = home.trending.filter { it.hasGenre(g) }.take(10)
        if (top.isNotEmpty()) item(key = "top") {
            Shelf("Di tendenza") {
                itemsIndexed(top, key = { _, m -> m.id }) { i, m -> RankedPoster(i + 1, m.coverUrl, m.title) { state.openDetail(m.id, null) } }
            }
        }
    }
}

/** Featured cards: swipe left / right between them; auto-advances every 4.5 s, pausing after a swipe. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(state: AppState, items: List<Media>) {
    val pager = androidx.compose.foundation.pager.rememberPagerState { items.size }
    LaunchedEffect(items.size) {
        while (items.size > 1) {
            delay(4500)
            if (!pager.isScrollInProgress) pager.animateScrollToPage((pager.currentPage + 1) % items.size, animationSpec = AR.motion(420))
        }
    }
    Box(Modifier.padding(top = 16.dp, bottom = AR.shelfGap)) {
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth(), beyondViewportPageCount = 1, key = { items[it].id }) { page ->
            HeroCard(state, items[page])
        }
        if (items.size > 1) Row(Modifier.align(Alignment.BottomEnd).padding(end = AR.side + 18.dp, bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items.indices.forEach { i ->
                Box(Modifier.height(6.dp).width(if (i == pager.currentPage) 18.dp else 6.dp).clip(CircleShape).background(if (i == pager.currentPage) AR.accent else Color.White.copy(alpha = .4f)))
            }
        }
    }
}

@Composable
private fun HeroCard(state: AppState, m: Media) {
    val tilt by rememberTilt()
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "heroPress")
    Box(Modifier.padding(horizontal = AR.side).fillMaxWidth().height(320.dp)
        .graphicsLayer { scaleX = scale; scaleY = scale }.clip(AR.heroShape).background(AR.surface)
        .clickable(press, null) { state.openDetail(m.id, null) }) {
        AsyncImage(m.bannerUrl ?: m.coverUrl, m.title, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer {
                scaleX = 1.08f; scaleY = 1.08f; translationX = tilt.x.dp.toPx(); translationY = tilt.y.dp.toPx()
            })
        Box(Modifier.fillMaxSize().background(AR.scrimBottom))
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(16.dp)
            .graphicsLayer { translationX = -tilt.x.dp.toPx() * 0.35f; translationY = -tilt.y.dp.toPx() * 0.35f }) {
            Badge("In evidenza")
            Text(m.title, fontSize = 32.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
            Text(listOfNotNull(m.year?.toString(), m.genres.take(3).joinToString(" · ").ifBlank { null }, m.episodes?.let { "$it episodi" }).joinToString(" · "),
                fontSize = 13.sp, color = AR.textSecondary, modifier = Modifier.padding(bottom = 14.dp))
            PrimaryButton("Scopri", Icons.Outlined.Info, small = true) { state.openDetail(m.id, null) }
        }
    }
}

@Composable
private fun ContinueCard(state: AppState, c: ContinueItem) {
    val dur = c.episode.durationSeconds ?: 0.0
    val p = if (dur > 0) ((c.episode.positionSeconds ?: 0.0) / dur).toFloat() else 0f
    Column(Modifier.width(220.dp)) {
        Thumb(c.series.bannerUrl ?: c.series.posterUrl, Modifier.fillMaxWidth().clickable {
            state.scope.launch {
                val eps = runCatching { state.api.episodes(c.series.id) }.getOrDefault(listOf(c.episode))
                state.play(c.episode, c.series, eps)
            }
        }, progress = p) {
            Box(Modifier.align(Alignment.Center).size(44.dp).clip(CircleShape).background(Color.Black.copy(alpha = .5f)), Alignment.Center) {
                Icon(Icons.Default.PlayArrow, "Riproduci", tint = Color.White)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                Text(c.series.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Episodio ${c.episode.label}", fontSize = 12.sp, color = AR.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton({ state.openDetail(c.series.aniListId, c.series.id) }, Modifier.size(36.dp)) {
                Icon(Icons.Outlined.Info, "Dettagli", tint = AR.textSecondary)
            }
        }
    }
}

/**
 * Device tilt for the hero parallax (game rotation vector, no magnetometer: cheap on battery).
 * Returns an offset in dp, max ±8; zero when animations are turned off (reduce motion).
 */
@Composable
fun rememberTilt(): State<Offset> {
    val context = LocalContext.current
    val tilt = remember { mutableStateOf(Offset.Zero) }
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    DisposableEffect(reduceMotion) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (reduceMotion || sensor == null) return@DisposableEffect onDispose { }
        val rot = FloatArray(9); val ori = FloatArray(3)
        var basePitch = Float.NaN; var baseRoll = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val pitch = ori[1]; val roll = ori[2]
                if (basePitch.isNaN()) { basePitch = pitch; baseRoll = roll }
                // The rest position drifts slowly, so the image re-centres when the phone is held differently.
                basePitch += (pitch - basePitch) * 0.02f; baseRoll += (roll - baseRoll) * 0.02f
                val k = 8f / 0.35f   // ~20° of tilt = full 8 dp
                val target = Offset(((roll - baseRoll) * k).coerceIn(-8f, 8f), ((pitch - basePitch) * k).coerceIn(-8f, 8f))
                val cur = tilt.value
                val next = cur + (target - cur) * 0.2f
                if (abs(next.x - cur.x) > 0.05f || abs(next.y - cur.y) > 0.05f) tilt.value = next
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { sm.unregisterListener(listener) }
    }
    return tilt
}
