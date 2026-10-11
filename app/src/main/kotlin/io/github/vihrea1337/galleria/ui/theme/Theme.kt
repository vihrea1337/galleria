package io.github.vihrea1337.galleria.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// «Музейная» палитра: тёплая бумага и глубокий бордовый, без dynamic color —
// у приложения свой узнаваемый вид, а цвет каждой картины подмешивается на её экране.
private val Paper = Color(0xFFF7F4EF)
private val Ink = Color(0xFF1D1B19)
private val Burgundy = Color(0xFF8C2F39)
private val NightPaper = Color(0xFF121110)
private val Blush = Color(0xFFE6B8AE)

private val LightColors = lightColorScheme(
    primary = Burgundy,
    onPrimary = Color.White,
    secondary = Color(0xFF6B5E57),
    // Выбранный чипс и индикатор вкладки — бледно-бордовый, а не сиреневый по умолчанию.
    secondaryContainer = Color(0xFFF2DCD8),
    onSecondaryContainer = Color(0xFF3B1418),
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEAE4DC),
    onSurfaceVariant = Color(0xFF5C554F),
    surfaceContainer = Color(0xFFEFEAE3),
    surfaceContainerHigh = Color(0xFFE9E3DB),
    outline = Color(0xFFB8AFA6),
)

private val DarkColors = darkColorScheme(
    primary = Blush,
    onPrimary = Color(0xFF3B1418),
    secondary = Color(0xFFCDBFB6),
    secondaryContainer = Color(0xFF4A2A2D),
    onSecondaryContainer = Color(0xFFF2DCD8),
    background = NightPaper,
    onBackground = Color(0xFFEDE7E1),
    surface = NightPaper,
    onSurface = Color(0xFFEDE7E1),
    surfaceVariant = Color(0xFF2B2826),
    onSurfaceVariant = Color(0xFFB9B0A8),
    surfaceContainer = Color(0xFF1C1A19),
    surfaceContainerHigh = Color(0xFF262321),
    outline = Color(0xFF5A534E),
)

/** Заголовки — с засечками, как подписи в музее; основной текст — обычный. */
private val Serif = FontFamily.Serif
private val base = Typography()
private val GalleriaTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontFamily = Serif, fontWeight = FontWeight.Medium),
    headlineMedium = base.headlineMedium.copy(fontFamily = Serif, fontWeight = FontWeight.Medium),
    headlineSmall = base.headlineSmall.copy(fontFamily = Serif, fontWeight = FontWeight.Medium),
    titleLarge = base.titleLarge.copy(fontFamily = Serif, fontWeight = FontWeight.Medium),
    titleMedium = base.titleMedium.copy(fontFamily = Serif),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun GalleriaTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = GalleriaTypography,
        content = content,
    )
}
