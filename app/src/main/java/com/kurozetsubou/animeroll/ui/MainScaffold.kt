package com.kurozetsubou.animeroll.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kurozetsubou.animeroll.VideoPlayer
import com.kurozetsubou.animeroll.ui.detail.DetailSheet
import com.kurozetsubou.animeroll.ui.downloads.DownloadsScreen
import com.kurozetsubou.animeroll.ui.home.HomeScreen
import com.kurozetsubou.animeroll.ui.explore.ExploreScreen
import com.kurozetsubou.animeroll.ui.library.LibraryScreen
import com.kurozetsubou.animeroll.ui.mine.ProfileScreen
import com.kurozetsubou.animeroll.ui.search.SearchScreen
import com.kurozetsubou.animeroll.ui.theme.AR
import com.kurozetsubou.animeroll.ui.theme.tick
import kotlinx.coroutines.launch

val BottomBarHeight = 96.dp

@Composable
fun MainScaffold(state: AppState) {
    LaunchedEffect(Unit) { state.refresh() }
    val view = LocalView.current
    val density = LocalDensity.current

    // Tab bar: slides away after ~90 dp of scrolling down, comes back on scroll up.
    var barVisible by remember { mutableStateOf(true) }
    val hideConnection = remember {
        object : NestedScrollConnection {
            var acc = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val dy = consumed.y
                if (dy < 0) { acc += -dy; if (acc > with(density) { 90.dp.toPx() }) barVisible = false }
                else if (dy > 0) { acc = 0f; barVisible = true }
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(state.tab) { barVisible = true }

    val sheetOpen = state.detail != null
    val sheetP by animateFloatAsState(if (sheetOpen) 1f else 0f, AR.motion(), label = "sheet")

    BackHandler(state.stack.isNotEmpty() && !sheetOpen && state.playing == null) { state.pop() }
    BackHandler(state.stack.isEmpty() && state.tab != Tab.Home && !sheetOpen && state.playing == null) { state.tab = Tab.Home }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Everything behind the detail sheet: scales to 0.92 and dims while it is open.
        Box(Modifier.fillMaxSize().graphicsLayer {
            val s = 1f - 0.08f * sheetP; scaleX = s; scaleY = s
            clip = sheetP > 0f; shape = AR.heroShape
        }.background(AR.background)) {
            Box(Modifier.fillMaxSize().nestedScroll(hideConnection)) {
                AnimatedContent(state.tab, transitionSpec = { fadeIn(AR.motion(300)) togetherWith fadeOut(AR.motion(300)) }, label = "tab") { tab ->
                    when (tab) {
                        Tab.Home -> HomeScreen(state)
                        Tab.Explore -> ExploreScreen(state)
                        Tab.Library -> LibraryScreen(state)
                        Tab.Downloads -> DownloadsScreen(state)
                        Tab.Profile -> ProfileScreen(state)
                    }
                }
            }
            AnimatedVisibility(barVisible && state.stack.isEmpty(), Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(AR.motion()) { it }, exit = slideOutVertically(AR.motion()) { it }) {
                BottomBar(state) { if (it != state.tab) { view.tick(); state.tab = it } }
            }

            // Pushed pages (Cerca, Download, Impostazioni, ...).
            AnimatedContent(state.stack.lastOrNull(), transitionSpec = {
                if (targetState != null)
                    slideInHorizontally(AR.motion()) { it / 3 } + fadeIn(AR.motion()) togetherWith fadeOut(AR.motion(250))
                else fadeIn(AR.motion(250)) togetherWith slideOutHorizontally(AR.motion()) { it / 3 } + fadeOut(AR.motion())
            }, label = "stack") { screen ->
                if (screen != null) Box(Modifier.fillMaxSize().background(AR.background)) {
                    when (screen) {
                        Screen.Search -> SearchScreen(state)
                    }
                }
            }
        }
        if (sheetP > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f * sheetP))
            .clickable(remember { MutableInteractionSource() }, null) { state.detail = null })

        AnimatedVisibility(sheetOpen, enter = slideInVertically(AR.motion(400)) { it }, exit = slideOutVertically(AR.motion(350)) { it }) {
            // Keep showing the last target while the sheet slides away.
            var last by remember { mutableStateOf(state.detail) }
            state.detail?.let { last = it }
            last?.let { DetailSheet(state, it) { state.detail = null } }
        }

        SnackbarHost(state.snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = BottomBarHeight))

        state.playing?.let { req ->
            key(req.url) {
                VideoPlayer(req,
                    onProgress = { pos, dur -> if (!req.local) state.scope.launch { runCatching { state.api.progress(req.episode.id, pos, dur) } } },
                    onNext = req.next?.let { n -> { state.play(n, req.series, req.playlist) } },
                    onClose = { state.playing = null; state.refresh() })
            }
        }
    }
}

private fun Tab.icon(): ImageVector = when (this) {
    Tab.Home -> Icons.Filled.Home
    Tab.Explore -> Icons.Filled.Explore
    Tab.Library -> Icons.Filled.VideoLibrary
    Tab.Downloads -> Icons.Filled.FileDownload
    Tab.Profile -> Icons.Filled.Person
}

/** Floating glass pill: translucent gradient, bright top hairline and a lens sliding under the active tab. */
@Composable
private fun BottomBar(state: AppState, onSelect: (Tab) -> Unit) {
    val shape = RoundedCornerShape(34.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 12.dp).height(68.dp)
        .shadow(18.dp, shape, ambientColor = Color.Black, spotColor = Color.Black)
        .clip(shape).background(AR.sunken.copy(alpha = .94f))
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .05f))))
        .border(1.dp, Color.White.copy(alpha = .16f), shape).padding(6.dp)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val itemW = maxWidth / Tab.entries.size
            val lens by animateDpAsState(itemW * state.tab.ordinal, AR.motion(420), label = "lens")
            Box(Modifier.offset(x = lens).width(itemW).fillMaxHeight().clip(RoundedCornerShape(28.dp)).background(Color.White.copy(alpha = .16f)))
            Row(Modifier.fillMaxSize()) {
                Tab.entries.forEach { t ->
                    val selected = state.tab == t
                    val tint = if (selected) AR.accent else Color.White.copy(alpha = .75f)
                    Column(Modifier.weight(1f).fillMaxHeight().clickable(remember { MutableInteractionSource() }, null) { onSelect(t) },
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(t.icon(), null, tint = tint, modifier = Modifier.size(22.dp))
                        Text(t.label, fontSize = 10.sp, color = tint, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp), maxLines = 1)
                    }
                }
            }
        }
    }
}
