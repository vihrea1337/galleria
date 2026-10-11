package io.github.vihrea1337.galleria.ui.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.ui.common.displayTitle
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val MAX_SCALE = 5f
private const val DOUBLE_TAP_SCALE = 2.5f
private const val DISMISS_DISTANCE = 220f

/**
 * Полноэкранный просмотр: щипок — зум (до 5×), сдвиг пальцем — по увеличенной картинке
 * (не дальше её краёв), двойной тап — приблизить в точку / вернуть, свайп вниз или вверх
 * без зума — закрыть (фон при этом светлеет, как в галерее телефона).
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

        // Сдвиг не дальше краёв увеличенной картинки.
        fun clamp(o: Offset, s: Float): Offset {
            val maxX = size.width * (s - 1) / 2
            val maxY = size.height * (s - 1) / 2
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
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val moved = event.changes.any { it.positionChanged() }
                            if (zoom != 1f || scale > 1.01f) {
                                val newScale = (scale * zoom).coerceIn(1f, MAX_SCALE)
                                // Зум вокруг точки между пальцами.
                                val centroid = event.calculateCentroid() - Offset(size.width / 2f, size.height / 2f)
                                val zoomed = (offset - centroid) * (newScale / scale) + centroid
                                offset = clamp(zoomed + pan, newScale)
                                scale = newScale
                            } else if (event.changes.size == 1 && moved) {
                                scope.launch { drag.snapTo(drag.value + pan.y) }
                            }
                            if (moved) event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
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
            IconButton(
                onClick = onDismiss,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp),
            ) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.close))
            }
        }
    }
}
