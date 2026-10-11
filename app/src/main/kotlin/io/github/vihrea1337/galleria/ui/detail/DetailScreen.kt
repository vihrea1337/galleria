package io.github.vihrea1337.galleria.ui.detail

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Museum
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.model.ArtworkDetails
import io.github.vihrea1337.galleria.ui.common.ArtworkRow
import io.github.vihrea1337.galleria.ui.common.MessageBlock
import io.github.vihrea1337.galleria.ui.common.ORIGIN_ARTIST_ROW
import io.github.vihrea1337.galleria.ui.common.ORIGIN_COLOR_ROW
import io.github.vihrea1337.galleria.ui.common.ORIGIN_TILE
import io.github.vihrea1337.galleria.ui.common.TileShape
import io.github.vihrea1337.galleria.ui.common.displayTitle
import io.github.vihrea1337.galleria.ui.common.message
import io.github.vihrea1337.galleria.ui.common.rememberPlaceholder
import io.github.vihrea1337.galleria.ui.common.sharedArtworkImage
import io.github.vihrea1337.galleria.ui.feed.title
import io.github.vihrea1337.galleria.ui.theme.PaintingTheme

@Composable
fun DetailRoute(
    onBack: () -> Unit,
    onOpen: (id: Long, origin: String) -> Unit,
    onArtist: (id: Long, name: String) -> Unit,
    origin: String = ORIGIN_TILE,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    DetailScreen(
        state = state,
        origin = origin,
        onBack = onBack,
        onToggleFavorite = viewModel::toggleFavorite,
        onRetry = viewModel::load,
        onShare = { artwork ->
            val text = listOfNotNull(artwork.title.ifBlank { null }, artwork.artist).joinToString(" — ") +
                "\nhttps://www.artic.edu/artworks/${artwork.id}"
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, null))
        },
        onOpen = { artwork, from ->
            viewModel.onArtworkOpen(artwork)
            onOpen(artwork.id, from)
        },
        onArtist = onArtist,
    )
}

@Composable
fun DetailScreen(
    state: DetailUiState,
    onBack: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRetry: () -> Unit,
    onShare: (Artwork) -> Unit,
    origin: String = ORIGIN_TILE,
    onOpen: (Artwork, String) -> Unit = { _, _ -> },
    onArtist: (Long, String) -> Unit = { _, _ -> },
) {
    val artwork = state.artwork
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    val painting = artwork?.dominantColor?.let { Color(it) }

    PaintingTheme(painting) {
        // Фон — мягкий градиент от преобладающего цвета картины к фону темы. Доля цвета
        // небольшая, поэтому текст темы остаётся контрастным и в светлой, и в тёмной теме.
        val base = MaterialTheme.colorScheme.background
        val top = lerp(base, painting ?: base, if (isSystemInDarkTheme()) 0.38f else 0.32f)
        val scroll = rememberScrollState()

        // У экрана нет Surface (фон — свой градиент), поэтому цвет текста и значков задаём сами:
        // иначе Compose берёт чёрный по умолчанию, и в тёмной теме заголовок не виден.
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to top, 0.55f to base))) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(scroll)
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    TopBar(state, onBack, onToggleFavorite, onShare)
                    if (artwork != null) {
                        ArtworkImage(artwork, origin, scroll, onClick = { fullscreen = true })
                        Info(artwork, state.details, onArtist)
                    }
                    when {
                        state.details != null -> Description(state.details)
                        state.loading -> DescriptionSkeleton()
                        state.error != null -> MessageBlock(
                            icon = Icons.Outlined.CloudOff,
                            title = stringResource(R.string.details_error),
                            text = state.error.message(),
                            action = stringResource(R.string.retry),
                            onAction = onRetry,
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    val artistName = artwork?.artist
                    ArtworkRow(
                        title = artistName?.let { stringResource(R.string.more_by_artist, it) } ?: stringResource(R.string.more_by_artist_unknown),
                        artworks = state.moreByArtist,
                        origin = ORIGIN_ARTIST_ROW,
                        onOpen = { onOpen(it, ORIGIN_ARTIST_ROW) },
                        action = stringResource(R.string.all_works).takeIf { state.details?.artistId != null && artistName != null },
                        onAction = { state.details?.artistId?.let { id -> onArtist(id, artistName.orEmpty()) } },
                        modifier = Modifier.padding(bottom = 20.dp),
                    )
                    state.similarColor?.let { color ->
                        ArtworkRow(
                            title = stringResource(R.string.similar_in_color, stringResource(color.title()).lowercase()),
                            artworks = state.similar,
                            origin = ORIGIN_COLOR_ROW,
                            onOpen = { onOpen(it, ORIGIN_COLOR_ROW) },
                            leading = {
                                Box(
                                    Modifier.padding(end = 10.dp).size(14.dp).clip(CircleShape)
                                        .background(Color(color.swatch)),
                                )
                            },
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.source_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    )
                }
            }
        }

        if (fullscreen && artwork != null) {
            FullscreenViewer(artwork, onDismiss = { fullscreen = false })
        }
    }
}

@Composable
private fun TopBar(state: DetailUiState, onBack: () -> Unit, onToggleFavorite: () -> Unit, onShare: (Artwork) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Spacer(Modifier.weight(1f))
        val artwork = state.artwork ?: return@Row
        IconButton(onClick = { onShare(artwork) }) {
            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share))
        }
        // ♥ «подпрыгивает», когда картину добавили, — пружиной от увеличенного размера.
        // При первом показе (уже в избранном) не прыгает: сравниваем с прошлым значением.
        val scale = remember { Animatable(1f) }
        var previous by remember { mutableStateOf(state.isFavorite) }
        LaunchedEffect(state.isFavorite) {
            if (state.isFavorite && !previous) {
                scale.snapTo(1.4f)
                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
            previous = state.isFavorite
        }
        IconButton(onClick = onToggleFavorite, modifier = Modifier.testTag("favorite")) {
            Icon(
                if (state.isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(if (state.isFavorite) R.string.favorite_remove else R.string.favorite_add),
                tint = if (state.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.scale(scale.value),
            )
        }
    }
}

/**
 * Картина с параллаксом: при прокрутке уезжает медленнее текста и чуть гаснет — текст «наезжает»
 * на неё. Значок лупы подсказывает, что по нажатию — полноэкранный зум.
 */
@Composable
private fun ArtworkImage(artwork: Artwork, origin: String, scroll: ScrollState, onClick: () -> Unit) {
    // Пока грузится крупная картинка — показываем уже скачанную превью из ленты
    // (она в кэше памяти Coil по своему адресу), а не пустое место.
    val request = ImageRequest.Builder(LocalPlatformContext.current)
        .data(artwork.imageUrl)
        .memoryCacheKey(artwork.imageUrl)
        .placeholderMemoryCacheKey(artwork.thumbUrl)
        .build()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .graphicsLayer {
                translationY = scroll.value * 0.45f
                alpha = 1f - (scroll.value / 1400f).coerceIn(0f, 0.6f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Box {
            AsyncImage(
                model = request,
                contentDescription = artwork.altText ?: artwork.displayTitle(),
                placeholder = rememberPlaceholder(artwork),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .heightIn(max = 560.dp)
                    .aspectRatio(artwork.aspectRatio)
                    .sharedArtworkImage(artwork.id, origin)
                    .clip(TileShape)
                    .clickable(role = Role.Image, onClickLabel = stringResource(R.string.open_fullscreen), onClick = onClick)
                    .testTag("artwork-image"),
            )
            Icon(
                Icons.Outlined.ZoomIn,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
    }
}

@Composable
private fun Info(artwork: Artwork, details: ArtworkDetails?, onArtist: (Long, String) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(artwork.displayTitle(), style = MaterialTheme.typography.headlineSmall)
        val artistText = details?.artistFull ?: artwork.artist
        val artistId = details?.artistId
        val artistName = artwork.artist
        if (artistText != null) {
            // Художник — ссылка на его экран (если музей знает, кто это).
            val link = if (artistId != null && artistName != null) ({ onArtist(artistId, artistName) }) else null
            Row(
                if (link != null) Modifier.clip(TileShape).clickable(role = Role.Button, onClick = link) else Modifier,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    artistText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textDecoration = if (link != null) TextDecoration.Underline else null,
                    modifier = Modifier.weight(1f, fill = false).testTag("artist-link"),
                )
                if (link != null) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                    )
                }
            }
        }
        if (details?.artistFull == null) {
            artwork.date?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        details?.onViewIn?.let { gallery ->
            AssistChip(
                onClick = {},
                label = { Text(stringResource(R.string.on_view, gallery)) },
                leadingIcon = { Icon(Icons.Outlined.Museum, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                border = null,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun Description(details: ArtworkDetails) {
    val facts = listOf(
        R.string.fact_medium to details.medium,
        R.string.fact_dimensions to details.dimensions,
        R.string.fact_origin to details.origin,
        R.string.fact_style to details.style,
        R.string.fact_department to details.department,
        R.string.fact_credit to details.credit,
    ).filter { !it.second.isNullOrBlank() }

    Column(Modifier.padding(horizontal = 20.dp)) {
        if (facts.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            facts.forEach { (label, value) ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        stringResource(label).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(112.dp).padding(top = 2.dp),
                    )
                    Text(value.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        details.description?.let {
            HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
            Text(stringResource(R.string.about_artwork), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.widthIn(max = 640.dp))
        }
    }
}

/** Пока грузятся подробности — серые строки на месте фактов и описания. */
@Composable
private fun DescriptionSkeleton() {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0.6f, 0.75f, 0.5f, 1f, 0.95f, 0.85f).forEach { w ->
            Box(Modifier.fillMaxWidth(w).height(14.dp).clip(TileShape).background(color))
        }
    }
}
