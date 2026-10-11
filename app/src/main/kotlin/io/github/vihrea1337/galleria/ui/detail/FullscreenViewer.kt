package io.github.vihrea1337.galleria.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.data.FullRect
import io.github.vihrea1337.galleria.data.Tile
import io.github.vihrea1337.galleria.data.tileFactor
import io.github.vihrea1337.galleria.data.tileUrl
import io.github.vihrea1337.galleria.data.visibleTiles
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.ui.common.displayTitle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

private const val DOUBLE_TAP_SCALE = 3f
private const val DISMISS_DISTANCE = 220f
/** Не держать в памяти больше стольких тайлов (256×256 ≈ 256 КБ каждый). */
private const val MAX_TILES = 160

/**
 * Полноэкранный просмотр с глубоким зумом.
 *
 * - Базовый слой — картинка 1686 px (сразу, из кэша экрана картины).
 * - Приблизили сильнее, чем позволяет базовый слой, — поверх догружаются **IIIF-тайлы оригинала**
 *   нужного уровня, только видимые (data/Iiif.kt). Можно дойти до пикселя в пиксель с оригиналом:
 *   у «Водяных лилий» это ×16 — видны мазки кисти.
 * - Щипок — зум вокруг точки между пальцами, сдвиг — не дальше краёв картины, двойной тап — приблизить
 *   в точку / вернуть, без зума свайп вверх-вниз — закрыть (фон при этом светлеет).
 * - Внизу на время жеста — «детализация»: какая доля разрешения оригинала сейчас на экране.
 */
@Composable
fun FullscreenViewer(artwork: Artwork, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val scope = rememberCoroutineScope()
        var size by remember { mutableStateOf(IntSize.Zero) }
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        val drag = remember { Animatable(0f) }
        var showLevel by remember { mutableStateOf(false) }

        // Размер оригинала: известен — можно приближать до пикселя в пиксель; нет — до 5×.
        val fullW = artwork.fullWidth ?: 0
        val fullH = artwork.fullHeight ?: 0
        val deep = artwork.imageId != null && fullW > 0 && fullH > 0
        val aspect = if (deep) fullW.toFloat() / fullH else artwork.aspectRatio

        // Картинка при scale = 1 вписана в экран (ContentScale.Fit): её размер на экране.
        fun fitSize(): Pair<Float, Float> {
            if (size == IntSize.Zero) return 0f to 0f
            val w = size.width.toFloat()
            val h = size.height.toFloat()
            return if (w / h > aspect) h * aspect to h else w to w / aspect
        }
        val maxScale = if (deep && size.width > 0) max(5f, 2f * fullW / fitSize().first) else 5f

        // Сдвиг не дальше краёв увеличенной картинки (пока картинка уже экрана — по центру).
        fun clamp(o: Offset, s: Float): Offset {
            val (fw, fh) = fitSize()
            val maxX = max(0f, (fw * s - size.width) / 2)
            val maxY = max(0f, (fh * s - size.height) / 2)
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }

        val backdrop = (1f - abs(drag.value) / (DISMISS_DISTANCE * 3)).coerceIn(0.35f, 1f)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = backdrop))
                .onSizeChanged { size = it }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            val from = scale
                            val to = if (scale > 1.01f) 1f else DOUBLE_TAP_SCALE
                            val startOffset = offset
                            // Приближаем в точку тапа: она остаётся под пальцем.
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val target = if (to == 1f) Offset.Zero else clamp((center - tap) * (to - 1), to)
                            scope.launch {
                                animate(0f, 1f) { t, _ ->
                                    scale = from + (to - from) * t
                                    offset = startOffset + (target - startOffset) * t
                                }
                            }
                        },
                        onTap = { if (scale <= 1.01f) onDismiss() },
                    )
                }
                .pointerInput(maxScale) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val moved = event.changes.any { it.positionChanged() }
                            // Когда пальцы уже подняты, центр жеста не определён (NaN) — такой кадр пропускаем,
                            // иначе NaN попадёт в сдвиг и в координаты тайлов.
                            val rawCentroid = event.calculateCentroid()
                            if (!rawCentroid.isSpecified) continue
                            if (zoom != 1f || scale > 1.01f) {
                                showLevel = true
                                val newScale = (scale * zoom).coerceIn(1f, maxScale)
                                // Зум вокруг точки между пальцами.
                                val centroid = rawCentroid - Offset(size.width / 2f, size.height / 2f)
                                val zoomed = (offset - centroid) * (newScale / scale) + centroid
                                offset = clamp(zoomed + pan, newScale)
                                scale = newScale
                            } else if (event.changes.size == 1 && moved) {
                                scope.launch { drag.snapTo(drag.value + pan.y) }
                            }
                            if (moved) event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                        showLevel = false
                        // Палец отпущен: далеко утащили — закрыть, нет — вернуть на место.
                        if (abs(drag.value) > DISMISS_DISTANCE) onDismiss() else scope.launch { drag.animateTo(0f) }
                    }
                }
                .testTag("fullscreen"),
        ) {
            val request = ImageRequest.Builder(LocalPlatformContext.current)
                .data(artwork.zoomUrl)
                .placeholderMemoryCacheKey(artwork.imageUrl)
                .build()
            AsyncImage(
                model = request,
                contentDescription = artwork.altText ?: artwork.displayTitle(),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y + drag.value
                    },
            )
            if (deep && size != IntSize.Zero) {
                val (fw, fh) = fitSize()
                DeepZoomTiles(
                    imageId = artwork.imageId!!,
                    fullWidth = fullW,
                    fullHeight = fullH,
                    viewSize = size,
                    // Где сейчас картина на экране: левый верхний угол и пикселей экрана на пиксель оригинала.
                    imageLeft = { size.width / 2f + offset.x - fw * scale / 2 },
                    imageTop = { size.height / 2f + offset.y - fh * scale / 2 },
                    density = { fw * scale / fullW },
                )
            }
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
            }
            if (deep && size != IntSize.Zero) {
                val percent = (fitSize().first * scale / fullW * 100).roundToInt().coerceAtMost(200)
                AnimatedVisibility(
                    visible = showLevel,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp),
                ) {
                    Text(
                        stringResource(R.string.zoom_detail, percent),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.55f))
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("zoom-level"),
                    )
                }
            }
        }
    }
}

/**
 * Слой тайлов поверх базовой картинки. Когда жест успокоился (collectLatest отменяет устаревшие
 * загрузки), считает видимые тайлы нужного уровня и грузит недостающие через Coil — по 6 одновременно,
 * в общий кэш картинок. Пока тайл не пришёл, под ним видна базовая картинка, — никаких дыр.
 */
@Composable
private fun DeepZoomTiles(
    imageId: String,
    fullWidth: Int,
    fullHeight: Int,
    viewSize: IntSize,
    imageLeft: () -> Float,
    imageTop: () -> Float,
    density: () -> Float,
) {
    val context = LocalPlatformContext.current
    val loaded = remember(imageId) { mutableStateMapOf<Tile, ImageBitmap>() }
    // Базовый слой — 1686 px по ширине; тайлы нужны, только когда приблизили сильнее него.
    val baseDensity = 1686f / fullWidth

    LaunchedEffect(imageId, viewSize) {
        val permits = Semaphore(6)
        snapshotFlow { Triple(imageLeft(), imageTop(), density()) }
            .collectLatest { (left, top, d) ->
                if (d <= baseDensity * 1.15f) return@collectLatest
                val factor = tileFactor(d)
                val visible = FullRect(
                    left = (0f - left) / d,
                    top = (0f - top) / d,
                    right = (viewSize.width - left) / d,
                    bottom = (viewSize.height - top) / d,
                )
                val needed = visibleTiles(fullWidth, fullHeight, visible, factor).filter { it !in loaded }
                // Старые уровни вытесняем, если тайлов накопилось много.
                if (loaded.size > MAX_TILES) {
                    loaded.keys.filter { it.factor != factor }.take(loaded.size - MAX_TILES / 2).forEach { loaded.remove(it) }
                }
                coroutineScope {
                    needed.forEach { tile ->
                        launch {
                            permits.withPermit {
                                val result = SingletonImageLoader.get(context).execute(
                                    ImageRequest.Builder(context)
                                        .data(tileUrl(imageId, tile))
                                        .size(Size.ORIGINAL)
                                        .allowHardware(false)
                                        .build(),
                                )
                                if (result is SuccessResult) loaded[tile] = result.image.toBitmap().asImageBitmap()
                            }
                        }
                    }
                }
            }
    }

    Canvas(Modifier.fillMaxSize()) {
        val d = density()
        val left = imageLeft()
        val top = imageTop()
        if (d.isNaN() || left.isNaN() || top.isNaN() || d <= baseDensity * 1.15f) return@Canvas
        val current = tileFactor(d)
        // Сначала более грубые уровни (они под), сверху — текущий, самый чёткий.
        loaded.entries.sortedByDescending { it.key.factor }.forEach { (tile, bitmap) ->
            if (tile.factor < current) return@forEach
            val x0 = (left + tile.x * d).roundToInt()
            val y0 = (top + tile.y * d).roundToInt()
            val x1 = (left + (tile.x + tile.width) * d).roundToInt()
            val y1 = (top + (tile.y + tile.height) * d).roundToInt()
            if (x1 < 0 || y1 < 0 || x0 > size.width || y0 > size.height) return@forEach
            drawImage(
                image = bitmap,
                dstOffset = IntOffset(x0, y0),
                dstSize = IntSize(max(1, x1 - x0), max(1, y1 - y0)),
            )
        }
    }
}
