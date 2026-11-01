package com.kurozetsubou.animeroll

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.kurozetsubou.animeroll.ui.PlayRequest
import com.kurozetsubou.animeroll.ui.components.PrimaryButton
import com.kurozetsubou.animeroll.ui.theme.AR
import com.kurozetsubou.animeroll.ui.theme.tick
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * ExoPlayer full screen with AnimeRoll controls; reports progress every 15 s (the NAS syncs AniList at 85%).
 * Behaviour (subtitles, resume, progress) is the same as before the restyle.
 */
@Composable
fun VideoPlayer(req: PlayRequest, onProgress: (Double, Double?) -> Unit, onNext: (() -> Unit)?, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val player = remember {
        // WebVTT sidecars from the NAS: ExoPlayer keeps them in sync and shows overlapping lines.
        val subtitles = req.subtitles
        val chosen = pickSubtitle(subtitles)
        val tracks = subtitles.map { s ->
            MediaItem.SubtitleConfiguration.Builder(android.net.Uri.parse(s.url))
                .setMimeType(MimeTypes.TEXT_VTT)
                .setLanguage(s.language)
                .setLabel(s.label)
                .setSelectionFlags(if (s == chosen) C.SELECTION_FLAG_DEFAULT else 0)
                .build()
        }
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.Builder().setUri(req.url).setSubtitleConfigurations(tracks).build())
            trackSelectionParameters = trackSelectionParameters.buildUpon()
                .setPreferredTextLanguage(chosen?.language ?: Locale.getDefault().language)
                .setIgnoredTextSelectionFlags(0)
                .build()
            prepare()
            req.resumeAt?.takeIf { it > 5 }?.let { seekTo((it * 1000).toLong()) }
            playWhenReady = true
        }
    }
    fun report() {
        if (player.currentPosition > 1000) onProgress(player.currentPosition / 1000.0, player.duration.takeIf { it > 0 }?.div(1000.0))
    }
    LaunchedEffect(player) { while (true) { delay(15_000); if (player.isPlaying) report() } }
    DisposableEffect(player) { onDispose { report(); player.release() } }
    BackHandler { onClose() }

    // Landscape + immersive while playing.
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val prev = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val ctl = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl?.hide(WindowInsetsCompat.Type.systemBars())
        view.keepScreenOn = true
        onDispose {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = prev ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            view.keepScreenOn = false
        }
    }

    var playing by remember { mutableStateOf(true) }
    var buffering by remember { mutableStateOf(true) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var textTracks by remember { mutableStateOf<List<Tracks.Group>>(emptyList()) }
    var controls by remember { mutableStateOf(true) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var lastTouch by remember { mutableLongStateOf(System.currentTimeMillis()) }

    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying || player.playWhenReady }
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onTracksChanged(tracks: Tracks) { textTracks = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT } }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition; duration = player.duration.coerceAtLeast(0)
            playing = player.playWhenReady
            if (controls && player.isPlaying && System.currentTimeMillis() - lastTouch > 3500) controls = false
            delay(400)
        }
    }
    fun touch() { lastTouch = System.currentTimeMillis() }
    val nearEnd = duration > 0 && (duration - position < 90_000 || position.toFloat() / duration > 0.92f)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ PlayerView(it).apply { this.player = player; useController = false; setShutterBackgroundColor(android.graphics.Color.BLACK) } },
            Modifier.fillMaxSize())
        // Tap anywhere: show / hide the controls.
        Box(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) { controls = !controls; touch() })

        if (buffering) CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = AR.accent)

        AnimatedVisibility(controls, enter = fadeIn(AR.motion(250)), exit = fadeOut(AR.motion(300))) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = .7f), 0.3f to Color.Transparent,
                0.7f to Color.Transparent, 1f to Color.Black.copy(alpha = .8f)))) {
                // Title top-left, subtitles top-right.
                Row(Modifier.fillMaxWidth().safeDrawingPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Chiudi", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text(req.series.title, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Episodio ${req.episode.label}${req.episode.title?.let { " · $it" } ?: ""}${if (req.local) " · sul dispositivo" else ""}",
                            fontSize = 13.sp, color = AR.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (textTracks.isNotEmpty()) SubtitleMenu(player, textTracks) { touch() }
                }
                // Centre: ⏪10 ▶ ⏩10
                Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoundIcon(Icons.Default.Replay10, "Indietro 10 secondi", 56) { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)); touch() }
                    RoundIcon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pausa" else "Riproduci", 76, accent = true) {
                        view.tick(); if (player.playWhenReady) player.pause() else player.play(); playing = player.playWhenReady; touch()
                    }
                    RoundIcon(Icons.Default.Forward10, "Avanti 10 secondi", 56) { player.seekTo(player.currentPosition + 10_000); touch() }
                }
                // Bottom: seek bar in accent.
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    val shown = seeking ?: if (duration > 0) position.toFloat() / duration else 0f
                    Slider(shown, { seeking = it; touch() }, onValueChangeFinished = {
                        seeking?.let { player.seekTo((it * duration).toLong()) }; seeking = null
                    }, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = AR.accent, inactiveTrackColor = Color.White.copy(alpha = .22f)))
                    Row {
                        Text(fmt((shown * duration).toLong()), fontSize = 12.sp, color = Color.White)
                        Spacer(Modifier.weight(1f))
                        Text("-" + fmt((duration - (shown * duration).toLong()).coerceAtLeast(0)), fontSize = 12.sp, color = Color.White)
                    }
                }
            }
        }

        // "Salta sigla" at the start (jumps 85 s), "Prossimo episodio" card near the end.
        val glass = Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .10f)))
        if (position < 150_000 && duration > 300_000) {
            Row(Modifier.align(Alignment.BottomStart).safeDrawingPadding().padding(start = 24.dp, bottom = if (controls) 96.dp else 28.dp)
                .height(40.dp).clip(AR.buttonShape).background(glass).clickable { view.tick(); player.seekTo(player.currentPosition + 85_000); touch() }
                .padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FastForward, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Salta sigla", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = AR.display, fontSize = 14.sp)
            }
        }
        val nxt = req.next
        if (onNext != null && nxt != null && nearEnd) {
            Row(Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(end = 24.dp, bottom = if (controls) 96.dp else 28.dp)
                .clip(AR.cardShape).background(glass).padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.padding(end = 12.dp)) {
                    Text("Prossimo episodio", fontSize = 11.sp, color = AR.textSecondary)
                    Text("E${nxt.label}${nxt.title?.let { " · $it" } ?: ""}", fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 200.dp))
                }
                PrimaryButton("Avvia", null, small = true) { view.tick(); report(); onNext() }
            }
        }
    }
}

@Composable
private fun RoundIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, size: Int, accent: Boolean = false, onClick: () -> Unit) {
    val base = if (accent) Modifier.shadow(20.dp, CircleShape, ambientColor = AR.accent, spotColor = AR.accent).background(AR.accent, CircleShape)
    else Modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .22f), Color.White.copy(alpha = .10f))), CircleShape)
    Box(Modifier.size(size.dp).then(base).clip(CircleShape).clickable(onClick = onClick), Alignment.Center) {
        Icon(icon, label, tint = if (accent) AR.onAccent else Color.White, modifier = Modifier.size((size * (if (accent) 0.5f else 0.55f)).dp))
    }
}

/** Subtitle picker: off, or one of the NAS tracks. */
@Composable
private fun SubtitleMenu(player: ExoPlayer, groups: List<Tracks.Group>, onUse: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton({ open = true; onUse() }) { Icon(Icons.Default.ClosedCaption, "Sottotitoli", tint = Color.White) }
        DropdownMenu(open, { open = false }, Modifier.background(AR.surfaceStrong)) {
            val disabled = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
            DropdownMenuItem({ Text("Disattivati", fontWeight = if (disabled || groups.none { it.isSelected }) FontWeight.Bold else FontWeight.Normal) }, {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                open = false
            })
            groups.forEach { g ->
                val f = g.getTrackFormat(0)
                val selected = !disabled && g.isSelected
                DropdownMenuItem({ Text(f.label ?: f.language ?: "Traccia", fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) }, {
                    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, 0)).build()
                    open = false
                }, leadingIcon = { if (selected) Icon(Icons.Default.Check, null) else Spacer(Modifier.size(24.dp)) })
            }
        }
    }
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Same rule as the Apple apps: device language first, full tracks before "signs & songs". */
fun pickSubtitle(subs: List<Subtitle>): Subtitle? {
    if (subs.isEmpty()) return null
    val full = subs.filter { !it.isSignsOnly }
    fun two(l: String?) = l?.let { runCatching { Locale.forLanguageTag(it).language.take(2) }.getOrNull() }
    val device = Locale.getDefault().language
    return full.firstOrNull { two(it.language) == device || it.language?.startsWith(device) == true }
        ?: full.firstOrNull { two(it.language) == "en" || it.language == "eng" }
        ?: full.firstOrNull { it.isDefault } ?: full.firstOrNull() ?: subs.first()
}
