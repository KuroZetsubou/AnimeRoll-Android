package com.kurozetsubou.animeroll.ui.theme

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kurozetsubou.animeroll.R

/** AnimeRoll design system tokens (colors, type, radii, motion). Fonts: Outfit (display) + Figtree (body). */
@OptIn(ExperimentalTextApi::class)
object AR {
    // Brand + ink surfaces
    val accent = Color(0xFFFF6A1F)
    val accentHover = Color(0xFFFF8A4C)
    val accentPress = Color(0xFFE5530A)
    val accentSoft = Color(0xFFFF6A1F).copy(alpha = 0.16f)
    val background = Color(0xFF0A0A0D)
    val sunken = Color(0xFF111116)
    val surface = Color(0xFF1A1A21)          // cards, placeholders, chips
    val surfaceStrong = Color(0xFF24242D)    // raised: secondary buttons, selected segment
    val ink600 = Color(0xFF32323E)
    val textSecondary = Color(0xFFA8A8B6)
    val textTertiary = Color(0xFF7A7A8A)
    val onAccent = Color(0xFF0A0A0D)
    val hairline = Color.White.copy(alpha = 0.08f)
    val borderStrong = Color.White.copy(alpha = 0.18f)
    val teal = Color(0xFF2BD4BD)
    val yellow = Color(0xFFFFC93C)
    val danger = Color(0xFFFF4D7E)
    val success = Color(0xFF3DDC84)
    val scrimBottom = Brush.verticalGradient(0f to Color.Transparent, 0.55f to Color(0xFF0A0A0D).copy(alpha = .6f), 1f to Color(0xFF0A0A0D).copy(alpha = .97f))

    // Radii: 6 badge, 10 poster, 14 card, 20 hero/sheet, pill
    val badgeShape = RoundedCornerShape(6.dp)
    val posterShape = RoundedCornerShape(10.dp)
    val cardShape = RoundedCornerShape(14.dp)
    val heroShape = RoundedCornerShape(20.dp)
    val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val buttonShape = RoundedCornerShape(50)
    val chipShape = RoundedCornerShape(50)

    val side = 16.dp
    val posterGap = 12.dp
    val shelfGap = 28.dp

    val display = FontFamily(
        Font(R.font.outfit, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.outfit, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
        Font(R.font.outfit, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
    )
    val body = FontFamily(
        Font(R.font.figtree, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
        Font(R.font.figtree, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.figtree, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
        Font(R.font.figtree, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

    val easeOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    /** 120 / 220 / 420 ms ease-out. */
    fun <T> motion(ms: Int = 220) = tween<T>(ms, easing = easeOut)
}

private fun bodyTypography(): Typography {
    val t = Typography()
    val f = AR.body
    return Typography(
        displayLarge = t.displayLarge.copy(fontFamily = AR.display), displayMedium = t.displayMedium.copy(fontFamily = AR.display),
        displaySmall = t.displaySmall.copy(fontFamily = AR.display), headlineLarge = t.headlineLarge.copy(fontFamily = AR.display),
        headlineMedium = t.headlineMedium.copy(fontFamily = AR.display), headlineSmall = t.headlineSmall.copy(fontFamily = AR.display),
        titleLarge = t.titleLarge.copy(fontFamily = AR.display), titleMedium = t.titleMedium.copy(fontFamily = f),
        titleSmall = t.titleSmall.copy(fontFamily = f), bodyLarge = t.bodyLarge.copy(fontFamily = f),
        bodyMedium = t.bodyMedium.copy(fontFamily = f), bodySmall = t.bodySmall.copy(fontFamily = f),
        labelLarge = t.labelLarge.copy(fontFamily = f), labelMedium = t.labelMedium.copy(fontFamily = f), labelSmall = t.labelSmall.copy(fontFamily = f),
    )
}

@Composable
fun AnimeRollTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AR.accent, onPrimary = AR.onAccent, background = AR.background, surface = AR.background,
            surfaceContainer = AR.surface, surfaceContainerHigh = AR.surfaceStrong, surfaceContainerHighest = AR.ink600,
            secondaryContainer = AR.surfaceStrong, onSecondaryContainer = Color.White, error = AR.danger,
        ),
        typography = bodyTypography(),
    ) {
        // Surface provides the content color (white on this dark scheme): without it Text defaults to black.
        androidx.compose.material3.Surface(color = AR.background, contentColor = Color.White, content = content)
    }
}

/** Light haptic tick (tab change, Play, add to device, chip selection). */
fun View.tick() { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
