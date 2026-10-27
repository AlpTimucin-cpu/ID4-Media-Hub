package tr.id4.mediahub.nativecockpit.presentation.cockpit

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val Ink = Color(0xFF0A0F1A)
internal val Navy = Color(0xFF101F31)
internal val GlassLine = Color(0x6655B2D1)
internal val NeonGreen = Color(0xFF00E676)
internal val Turquoise = Color(0xFF00B0FF)
internal val SoftText = Color(0xFFBCD1DC)

private val Palette = darkColorScheme(
    primary = NeonGreen,
    secondary = Turquoise,
    background = Ink,
    surface = Navy,
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun EyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, content = content)
}
