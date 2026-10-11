package io.github.vihrea1337.galleria.data

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 * Глубокий зум по стандарту IIIF: оригинал (у «Водяных лилий» Моне — 8808×8460 px) нарезан музеем на
 * тайлы 256×256 на уровнях масштаба 1, 2, 4 … 128. На уровне f один тайл — это квадрат оригинала
 * 256·f × 256·f, уменьшенный до 256 px. При приближении грузим только видимые тайлы того уровня,
 * где 1 пиксель тайла ≈ 1 пикселю экрана, — до мазков кисти, не скачивая 30-мегапиксельный файл.
 * Чистые функции — проверяются обычным JUnit.
 */

const val TILE_SIZE = 256
private const val MAX_FACTOR = 128

/** Прямоугольник в пикселях оригинала. */
data class FullRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

/** Тайл: область оригинала и уровень (во сколько раз уменьшена). */
data class Tile(val x: Int, val y: Int, val width: Int, val height: Int, val factor: Int)

/**
 * Уровень тайлов для текущего масштаба: [screenPxPerFullPx] — сколько пикселей экрана приходится
 * на пиксель оригинала. Берём наибольший уровень, где тайл ещё не мельче экрана (иначе будет мыльно).
 */
fun tileFactor(screenPxPerFullPx: Float): Int {
    if (screenPxPerFullPx >= 1f) return 1
    val ideal = 1f / screenPxPerFullPx
    val power = floor(ln(ideal) / ln(2f)).toInt().coerceAtLeast(0)
    return min(2f.pow(power).toInt(), MAX_FACTOR)
}

/** Тайлы уровня [factor], которые пересекают видимую область [visible] (в пикселях оригинала). */
fun visibleTiles(fullWidth: Int, fullHeight: Int, visible: FullRect, factor: Int): List<Tile> {
    val step = TILE_SIZE * factor
    val left = max(visible.left, 0f)
    val top = max(visible.top, 0f)
    val right = min(visible.right, fullWidth.toFloat())
    val bottom = min(visible.bottom, fullHeight.toFloat())
    if (right <= left || bottom <= top) return emptyList()
    val cols = (floor(left / step).toInt())..(ceil(right / step).toInt() - 1)
    val rows = (floor(top / step).toInt())..(ceil(bottom / step).toInt() - 1)
    return rows.flatMap { row ->
        cols.map { col ->
            val x = col * step
            val y = row * step
            Tile(x, y, min(step, fullWidth - x), min(step, fullHeight - y), factor)
        }
    }
}

/** Адрес тайла: /{x},{y},{w},{h}/{ширина в px},/0/default.jpg. */
fun tileUrl(imageId: String, tile: Tile): String {
    val outWidth = ceil(tile.width.toFloat() / tile.factor).toInt()
    return iiifRegionUrl(imageId, tile.x, tile.y, tile.width, tile.height, outWidth)
}

fun iiifRegionUrl(imageId: String, x: Int, y: Int, w: Int, h: Int, outWidth: Int) =
    "https://www.artic.edu/iiif/2/$imageId/$x,$y,$w,$h/$outWidth,/0/default.jpg"
