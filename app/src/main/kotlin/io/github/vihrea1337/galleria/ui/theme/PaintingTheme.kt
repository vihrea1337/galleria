package io.github.vihrea1337.galleria.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Акцентный цвет из картины: тот же оттенок, но с насыщенностью и светлотой, при которых
 * кнопки и ♥ читаются на фоне темы (тёмный акцент на светлом фоне, светлый — на тёмном).
 * Серые картины (почти без насыщенности) — акцент темы по умолчанию.
 */
fun paintingAccent(painting: Color, dark: Boolean): Color? {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(painting.toArgb(), hsl)
    if (hsl[1] < 0.15f) return null
    hsl[1] = hsl[1].coerceIn(0.35f, 0.75f)
    hsl[2] = if (dark) 0.74f else 0.36f
    return Color(ColorUtils.HSLToColor(hsl))
}

/**
 * Экран картины в её цветах: primary (♥, кнопки, ссылки) и secondaryContainer (чипсы)
 * берутся из преобладающего цвета картины. Цвет меняется плавно — когда переходишь
 * между картинами из рядов «Похожие», экран «перекрашивается».
 */
@Composable
fun PaintingTheme(painting: Color?, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val base = MaterialTheme.colorScheme
    val accent = painting?.let { paintingAccent(it, dark) } ?: base.primary
    val primary by animateColorAsState(accent, tween(600), label = "accent")
    val container by animateColorAsState(
        if (painting == null) base.secondaryContainer else accent.copy(alpha = if (dark) 0.28f else 0.16f),
        tween(600),
        label = "container",
    )
    MaterialTheme(
        colorScheme = base.copy(
            primary = primary,
            secondaryContainer = container,
            onSecondaryContainer = if (painting == null) base.onSecondaryContainer else base.onBackground,
        ),
        typography = MaterialTheme.typography,
        content = content,
    )
}
