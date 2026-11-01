package com.kurozetsubou.animeroll.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kurozetsubou.animeroll.ui.theme.AR
import com.kurozetsubou.animeroll.ui.theme.tick

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Poster(title: String?, url: String?, badge: String? = null, width: Dp? = 120.dp, meta: String? = null, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Column((if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).combinedClickable(onLongClick = onLongClick, onClick = onClick)) {
        Box {
            AsyncImage(url, title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(AR.posterShape).background(AR.surface))
            badge?.let { Badge(it, Modifier.padding(8.dp)) }
        }
        title?.let {
            Text(it, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 8.dp))
        }
        meta?.let { Text(it, fontSize = 12.sp, color = AR.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
    }
}

@Composable
fun Badge(text: String, modifier: Modifier = Modifier, color: Color = AR.accent, textColor: Color = AR.onAccent) {
    Text(text.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = AR.display, letterSpacing = 0.9.sp, color = textColor,
        modifier = modifier.background(color, AR.badgeShape).padding(horizontal = 8.dp, vertical = 2.dp))
}

/** 16:9 thumbnail with an optional progress line at the bottom. */
@Composable
fun Thumb(url: String?, modifier: Modifier = Modifier, progress: Float? = null, overlay: @Composable BoxScope.() -> Unit = {}) {
    Box(modifier.aspectRatio(16f / 9f).clip(AR.posterShape).background(AR.surface)) {
        AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        overlay()
        progress?.takeIf { it > 0f }?.let {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .25f)))
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(it.coerceIn(0f, 1f)).height(3.dp).background(AR.accent))
        }
    }
}

@Composable
fun ShelfTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = AR.display, modifier = modifier.padding(horizontal = AR.side).padding(bottom = 12.dp))
}

@Composable
fun Shelf(title: String, content: LazyListScope.() -> Unit) {
    Column(Modifier.padding(bottom = AR.shelfGap)) {
        if (title.isNotBlank()) ShelfTitle(title)
        LazyRow(contentPadding = PaddingValues(horizontal = AR.side), horizontalArrangement = Arrangement.spacedBy(AR.posterGap), content = content)
    }
}

/** Top 10 item: big rank number (orange outline) on the left, poster in front. */
@Composable
fun RankedPoster(rank: Int, url: String?, title: String, onClick: () -> Unit) {
    val posterW = 118.dp
    val numberW = 36.dp
    val numberSize = with(LocalDensity.current) { 72.dp.toSp() }
    Column(Modifier.width(numberW + posterW).clickable(onClick = onClick)) {
        Box {
            Box(Modifier.align(Alignment.BottomStart).offset(x = (-4).dp, y = 36.dp)) {
                Text("$rank", style = TextStyle(fontSize = numberSize, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, color = AR.background))
                Text("$rank", style = TextStyle(fontSize = numberSize, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, color = AR.accent, drawStyle = Stroke(width = 4f)))
            }
            AsyncImage(url, title, contentScale = ContentScale.Crop,
                modifier = Modifier.padding(start = numberW).width(posterW).aspectRatio(2f / 3f).clip(AR.posterShape).background(AR.surface))
        }
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 16.sp,
            modifier = Modifier.padding(start = numberW).padding(top = 8.dp))
    }
}

/** Primary pill button (accent, dark text). */
@Composable
fun PrimaryButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, enabled: Boolean = true, small: Boolean = false, onClick: () -> Unit) {
    val view = LocalView.current
    Row(modifier.height(if (small) 36.dp else 46.dp).clip(AR.buttonShape).background(if (enabled) AR.accent else AR.accent.copy(alpha = .4f))
        .clickable(enabled) { view.tick(); onClick() }.padding(horizontal = if (small) 14.dp else 20.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icon?.let { Icon(it, null, tint = AR.onAccent, modifier = Modifier.size(if (small) 16.dp else 20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = AR.onAccent, fontWeight = FontWeight.Bold, fontFamily = AR.display, fontSize = if (small) 13.sp else 15.sp, maxLines = 1)
    }
}

/** Secondary pill button (raised surface); optional download progress fill. */
@Composable
fun SecondaryButton(text: String, icon: ImageVector?, modifier: Modifier = Modifier, progress: Float? = null, small: Boolean = false, ghost: Boolean = false, onClick: () -> Unit) {
    Box(modifier.height(if (small) 36.dp else 46.dp).clip(AR.buttonShape)
        .background(if (ghost) Color.Transparent else AR.surfaceStrong)
        .border(1.dp, if (ghost) AR.borderStrong else AR.hairline, AR.buttonShape).clickable(onClick = onClick)) {
        progress?.let { Box(Modifier.matchParentSize().fillMaxWidth(it.coerceIn(0f, 1f)).background(AR.accent.copy(alpha = .35f))) }
        Row(Modifier.align(Alignment.Center).padding(horizontal = if (small) 14.dp else 20.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, null, tint = Color.White, modifier = Modifier.size(if (small) 16.dp else 20.dp)); Spacer(Modifier.width(8.dp)) }
            Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = AR.display, fontSize = if (small) 13.sp else 15.sp, maxLines = 1)
        }
    }
}

@Composable
fun ArChip(label: String, selected: Boolean = false, trailing: ImageVector? = null, small: Boolean = false, onClick: () -> Unit) {
    val view = LocalView.current
    Row(Modifier.height(if (small) 32.dp else 36.dp).clip(AR.chipShape).background(if (selected) AR.accent else Color.Transparent)
        .border(1.dp, if (selected) AR.accent else AR.borderStrong, AR.chipShape)
        .clickable { view.tick(); onClick() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = if (small) 13.sp else 14.sp, fontWeight = FontWeight.SemiBold, color = if (selected) AR.onAccent else Color.White, maxLines = 1)
        trailing?.let { Spacer(Modifier.width(4.dp)); Icon(it, null, Modifier.size(16.dp), tint = if (selected) AR.onAccent else Color.White) }
    }
}

/** Pill segmented control (Episodi / Simili, Sul NAS / Su questo dispositivo). */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, onSelect: (Int) -> Unit) {
    val view = LocalView.current
    Row(modifier.height(40.dp).clip(AR.chipShape).background(AR.surface).border(1.dp, AR.hairline, AR.chipShape).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            Box(Modifier.weight(1f).fillMaxHeight().clip(AR.chipShape).background(if (i == selected) AR.surfaceStrong else Color.Transparent)
                .clickable { if (i != selected) { view.tick(); onSelect(i) } }, Alignment.Center) {
                Text(o, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (i == selected) Color.White else AR.textTertiary, maxLines = 1)
            }
        }
    }
}

/** Glass search pill ("Cerca nella libreria e su AniList") at the top of Home. */
@Composable
fun GlassPill(text: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(modifier.fillMaxWidth().height(48.dp).clip(AR.buttonShape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .14f), Color.White.copy(alpha = .06f))))
        .border(1.dp, Color.White.copy(alpha = .14f), AR.buttonShape).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = AR.textSecondary, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(10.dp))
        Text(text, color = AR.textSecondary, fontSize = 15.sp, maxLines = 1)
    }
}

/** Uppercase caption above a settings group. */
@Composable
fun GroupTitle(text: String) {
    Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = AR.textTertiary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp, top = 24.dp))
}

/** Numbered step label of the guided Fonti sheet. */
@Composable
fun StepLabel(n: Int, text: String) {
    Row(Modifier.padding(top = 18.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(22.dp).clip(CircleShape).background(AR.accent), Alignment.Center) {
            Text("$n", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, color = AR.onAccent)
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

/** Icon with a small label under it (detail action row). */
@Composable
fun ActionIcon(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = Color.White, onClick: () -> Unit) {
    Column(modifier.clip(AR.buttonShape).clickable(onClick = onClick).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 12.sp, color = AR.textSecondary, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, fontFamily = AR.display, modifier = modifier)
}

/** Pushed page header: back arrow + title + optional actions. */
@Composable
fun PageHeader(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Indietro") }
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        actions()
    }
}

@Composable
fun OfflineBanner(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(AR.cardShape).background(AR.accentSoft).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.WifiOff, null, tint = AR.accent, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("NAS non raggiungibile: puoi guardare gli episodi salvati sul dispositivo.", fontSize = 13.sp, color = AR.accent)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(AR.surface), Alignment.Center) { Icon(icon, null, Modifier.size(30.dp), tint = AR.textSecondary) }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.padding(top = 12.dp))
        Text(text, color = AR.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

fun genresLine(genres: List<String>) = genres.take(3).joinToString(" • ")
