package com.kurozetsubou.animeroll

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay

/** ExoPlayer full screen; reports progress every 15 s (the NAS syncs AniList at 85%). */
@Composable
fun VideoPlayer(url: String, resumeAt: Double?, onProgress: (Double, Double?) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            resumeAt?.takeIf { it > 5 }?.let { seekTo((it * 1000).toLong()) }
            playWhenReady = true
        }
    }
    fun report() {
        if (player.currentPosition > 1000) onProgress(player.currentPosition / 1000.0, player.duration.takeIf { it > 0 }?.div(1000.0))
    }
    LaunchedEffect(player) { while (true) { delay(15_000); if (player.isPlaying) report() } }
    DisposableEffect(player) { onDispose { report(); player.release() } }
    BackHandler { onClose() }
    AndroidView({ PlayerView(it).apply { this.player = player } }, Modifier.fillMaxSize().background(Color.Black))
}
