package mom.cosmism.textory.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import mom.cosmism.textory.data.AppTheme

@Immutable
data class TextoryColors(
    val canvas: Color,
    val surface: Color,
    val ink: Color,
    val inkMuted: Color,
    val border: Color,
    val accent: Color,
    val accentHighlight: Color,
    val green: Color,
    val onAccent: Color,
    val greenHighlight: Color,
    val greenBlock: Color,
    val greenDetail: Color,
    val red: Color,
    val redHighlight: Color,
    val redDetail: Color,
    val toolbar: Color,
)

internal val LightTextoryColors = TextoryColors(
    canvas = Color(0xFFFCFCFA),
    surface = Color(0xFFFFFFFF),
    ink = Color(0xFF202320),
    inkMuted = Color(0xFF6E746E),
    border = Color(0xFFE7EAE6),
    accent = Color(0xFF48785A),
    accentHighlight = Color(0xFFE2F2E4),
    green = Color(0xFF48785A),
    onAccent = Color.White,
    greenHighlight = Color(0xFFE2F2E4),
    greenBlock = Color(0xFFF0F7F0),
    greenDetail = Color(0xFFD1E8D4),
    red = Color(0xFF985F63),
    redHighlight = Color(0xFFF8E9E9),
    redDetail = Color(0xFFF1D4D5),
    toolbar = Color(0xFFF8F9F7),
)

internal val SepiaTextoryColors = TextoryColors(
    canvas = Color(0xFFF3E8D6),
    surface = Color(0xFFFFF8EC),
    ink = Color(0xFF49392E),
    inkMuted = Color(0xFF756455),
    border = Color(0xFFDCC9AF),
    accent = Color(0xFF8A603D),
    accentHighlight = Color(0xFFEAD9BE),
    green = Color(0xFF58704F),
    onAccent = Color(0xFFFFF8EC),
    greenHighlight = Color(0xFFDFE8D7),
    greenBlock = Color(0xFFEEF3E8),
    greenDetail = Color(0xFFD7E5D0),
    red = Color(0xFF98534B),
    redHighlight = Color(0xFFF1DDD5),
    redDetail = Color(0xFFE8C8BE),
    toolbar = Color(0xFFF6EDDF),
)

internal val DarkTextoryColors = TextoryColors(
    canvas = Color(0xFF101512),
    surface = Color(0xFF181E1A),
    ink = Color(0xFFE5ECE7),
    inkMuted = Color(0xFFA8B5AC),
    border = Color(0xFF344039),
    accent = Color(0xFF82C995),
    accentHighlight = Color(0xFF24452D),
    green = Color(0xFF82C995),
    onAccent = Color(0xFF102418),
    greenHighlight = Color(0xFF24452D),
    greenBlock = Color(0xFF1C2C21),
    greenDetail = Color(0xFF2C5738),
    red = Color(0xFFE3A0A4),
    redHighlight = Color(0xFF4A292D),
    redDetail = Color(0xFF64353A),
    toolbar = Color(0xFF141A16),
)

internal fun textoryColors(theme: AppTheme): TextoryColors = when (theme) {
    AppTheme.LIGHT -> LightTextoryColors
    AppTheme.SEPIA -> SepiaTextoryColors
    AppTheme.DARK -> DarkTextoryColors
}

private val LocalTextoryColors = staticCompositionLocalOf { LightTextoryColors }

object TextoryPalette {
    val current: TextoryColors
        @Composable
        @ReadOnlyComposable
        get() = LocalTextoryColors.current

    val Canvas: Color @Composable @ReadOnlyComposable get() = current.canvas
    val Surface: Color @Composable @ReadOnlyComposable get() = current.surface
    val Ink: Color @Composable @ReadOnlyComposable get() = current.ink
    val InkMuted: Color @Composable @ReadOnlyComposable get() = current.inkMuted
    val Border: Color @Composable @ReadOnlyComposable get() = current.border
    val Accent: Color @Composable @ReadOnlyComposable get() = current.accent
    val AccentHighlight: Color @Composable @ReadOnlyComposable get() = current.accentHighlight
    val Green: Color @Composable @ReadOnlyComposable get() = current.green
    val OnAccent: Color @Composable @ReadOnlyComposable get() = current.onAccent
    val GreenHighlight: Color @Composable @ReadOnlyComposable get() = current.greenHighlight
    val GreenBlock: Color @Composable @ReadOnlyComposable get() = current.greenBlock
    val GreenDetail: Color @Composable @ReadOnlyComposable get() = current.greenDetail
    val Red: Color @Composable @ReadOnlyComposable get() = current.red
    val RedHighlight: Color @Composable @ReadOnlyComposable get() = current.redHighlight
    val RedDetail: Color @Composable @ReadOnlyComposable get() = current.redDetail
    val Toolbar: Color @Composable @ReadOnlyComposable get() = current.toolbar
}

private fun materialColorScheme(colors: TextoryColors, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = colors.accent,
        onPrimary = colors.onAccent,
        primaryContainer = colors.accentHighlight,
        onPrimaryContainer = colors.ink,
        secondary = colors.green,
        onSecondary = colors.onAccent,
        secondaryContainer = colors.greenHighlight,
        onSecondaryContainer = colors.ink,
        background = colors.canvas,
        onBackground = colors.ink,
        surface = colors.surface,
        onSurface = colors.ink,
        surfaceVariant = colors.toolbar,
        onSurfaceVariant = colors.inkMuted,
        outline = colors.border,
        outlineVariant = colors.border,
        error = colors.red,
        onError = colors.onAccent,
        errorContainer = colors.redHighlight,
        onErrorContainer = colors.ink,
    )
}

@Composable
fun TextoryTheme(
    theme: AppTheme = AppTheme.LIGHT,
    content: @Composable () -> Unit,
) {
    val colors = textoryColors(theme)
    CompositionLocalProvider(LocalTextoryColors provides colors) {
        MaterialTheme(
            colorScheme = materialColorScheme(colors, dark = theme.isDark),
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes.copy(
                small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
                large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
            ),
            content = content,
        )
    }
}
