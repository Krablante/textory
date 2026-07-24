package mom.cosmism.textory.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object TextoryPalette {
    val Canvas = Color(0xFFFCFCFA)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF202320)
    val InkMuted = Color(0xFF747A74)
    val Border = Color(0xFFE7EAE6)
    val Green = Color(0xFF48785A)
    val GreenHighlight = Color(0xFFE2F2E4)
    val GreenBlock = Color(0xFFF0F7F0)
    val GreenDetail = Color(0xFFD1E8D4)
    val Red = Color(0xFF985F63)
    val RedHighlight = Color(0xFFF8E9E9)
    val RedDetail = Color(0xFFF1D4D5)
    val Toolbar = Color(0xFFF8F9F7)
}

private val colorScheme = lightColorScheme(
    primary = TextoryPalette.Green,
    onPrimary = Color.White,
    background = TextoryPalette.Canvas,
    onBackground = TextoryPalette.Ink,
    surface = TextoryPalette.Surface,
    onSurface = TextoryPalette.Ink,
    surfaceVariant = TextoryPalette.Toolbar,
    onSurfaceVariant = TextoryPalette.InkMuted,
    outline = TextoryPalette.Border,
    error = TextoryPalette.Red,
)

@Composable
fun TextoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colorScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes.copy(
            small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
            large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        ),
        content = content,
    )
}
