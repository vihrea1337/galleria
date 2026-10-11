package io.github.vihrea1337.galleria.ui.immersive

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.ui.common.backdrop
import io.github.vihrea1337.galleria.ui.common.displayTitle
import io.github.vihrea1337.galleria.ui.common.rememberPlaceholder
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Сколько показывать картину в автопрокрутке. */
private const val SLIDE_MS = 8_000L

@Composable
fun ImmersiveRoute(onBack: () -> Unit, onOpen: (Long) -> Unit, viewModel: ImmersiveViewModel = hiltViewModel()) {
    ImmersiveScreen(
        items = viewModel.artworks.collectAsLazyPagingItems(),
        onBack = onBack,
        onOpen = {
            viewModel.onArtworkOpen(it)
            onOpen(it.id)
        },
    )
}

/**
 * Музей в кармане: картины во весь экран, листаются вертикально. Каждая медленно «наезжает»
 * (эффект Кена Бёрнса), за ней — она же, размытая, вместо чёрных полей. Автопрокрутка с полоской
 * прогресса; любое касание листания её не ломает — таймер начинается заново с новой страницы.
 * Системные панели прячутся (свайп от края — вернуть).
 */
@Composable
fun ImmersiveScreen(items: LazyPagingItems<Artwork>, onBack: () -> Unit, onOpen: (Artwork) -> Unit) {
    HideSystemBars()
    val pager = rememberPagerState { items.itemCount }
    var playing by rememberSaveable { mutableStateOf(true) }
    var progress by remember { mutableFloatStateOf(0f) }

    // Автопрокрутка: прогресс от 0 до 1 за SLIDE_MS, затем следующая картина. Ключ — страница:
    // пролистал руками — отсчёт с начала.
    LaunchedEffect(pager.currentPage, playing, items.itemCount) {
        progress = 0f
        if (!playing || items.itemCount == 0) return@LaunchedEffect
        val start = System.currentTimeMillis()
        while (isActive) {
            progress = ((System.currentTimeMillis() - start).toFloat() / SLIDE_MS).coerceAtMost(1f)
            if (progress >= 1f) {
                if (pager.currentPage + 1 < items.itemCount) pager.animateScrollToPage(pager.currentPage + 1)
                break
            }
            delay(16)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("immersive")) {
        if (items.itemCount == 0) {
            if (items.loadState.refresh is LoadState.Loading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            }
        } else {
            VerticalPager(state = pager, beyondViewportPageCount = 1, key = { index -> items.peek(index)?.id ?: index }) { page ->
                items[page]?.let { artwork -> Slide(artwork, active = page == pager.currentPage, onOpen = { onOpen(artwork) }) }
            }
        }

        // Верх: назад, пауза/воспроизведение и полоска прогресса.
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            LinearProgressIndicator(
                progress = { progress },
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                drawStopIndicator = {},
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).height(2.dp),
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                RoundButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
                Spacer(Modifier.weight(1f))
                RoundButton(onClick = { playing = !playing }) {
                    Icon(
                        if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        stringResource(if (playing) R.string.immersive_pause else R.string.immersive_play),
                    )
                }
            }
        }
    }
}

@Composable
private fun Slide(artwork: Artwork, active: Boolean, onOpen: () -> Unit) {
    // Медленный наезд камеры: масштаб 1 → 1.12 и лёгкий сдвиг, туда-обратно, пока картина на экране.
    val camera = rememberInfiniteTransition(label = "ken-burns")
    val zoom by camera.animateFloat(1f, 1.12f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse), label = "zoom")
    val pan by camera.animateFloat(-1f, 1f, infiniteRepeatable(tween(19_000, easing = LinearEasing), RepeatMode.Reverse), label = "pan")
    val context = LocalPlatformContext.current

    Box(Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen)) {
        // Фон — та же картина, размытая (на Android 12+; раньше — затемнённый цвет картины).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(artwork.thumbUrl).memoryCacheKey(artwork.thumbUrl).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp).graphicsLayer { alpha = 0.55f },
            )
        } else {
            Box(Modifier.fillMaxSize().background(artwork.backdrop(Color.DarkGray).copy(alpha = 0.6f)))
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(artwork.imageUrl)
                .memoryCacheKey(artwork.imageUrl)
                .placeholderMemoryCacheKey(artwork.thumbUrl)
                .build(),
            contentDescription = artwork.altText ?: artwork.displayTitle(),
            placeholder = rememberPlaceholder(artwork),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 72.dp)
                .graphicsLayer {
                    val s = if (active) zoom else 1f
                    scaleX = s
                    scaleY = s
                    translationX = if (active) pan * size.width * 0.03f else 0f
                },
        )
        // Низ: подпись на затемнении.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.8f)))
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 24.dp),
        ) {
            Text(artwork.displayTitle(), style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            listOfNotNull(artwork.artist, artwork.date).joinToString(" · ").takeIf { it.isNotEmpty() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 10.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(end = 6.dp))
                Text(stringResource(R.string.immersive_hint), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun RoundButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.35f), contentColor = Color.White),
        content = content,
    )
}

/** Прячет статус- и навигационную панели, пока экран открыт; вернуть — свайпом от края. */
@Composable
private fun HideSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
