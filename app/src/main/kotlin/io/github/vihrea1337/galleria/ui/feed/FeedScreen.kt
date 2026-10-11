package io.github.vihrea1337.galleria.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Slideshow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.data.ColorFilter
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.ui.common.ArtworkTile
import io.github.vihrea1337.galleria.ui.common.ORIGIN_DAILY
import io.github.vihrea1337.galleria.ui.common.ORIGIN_TILE
import io.github.vihrea1337.galleria.ui.common.MessageBlock
import io.github.vihrea1337.galleria.ui.common.TileShape
import io.github.vihrea1337.galleria.ui.common.backdrop
import io.github.vihrea1337.galleria.ui.common.displayTitle
import io.github.vihrea1337.galleria.ui.common.message
import io.github.vihrea1337.galleria.ui.common.rememberPlaceholder
import io.github.vihrea1337.galleria.ui.common.sharedArtworkImage
import io.github.vihrea1337.galleria.widget.canPinWidget
import io.github.vihrea1337.galleria.widget.requestPinWidget

/** Плитки шириной от 160 dp: 2 колонки на телефоне, 3–5 на планшете — по ширине окна. */
val GridCells = StaggeredGridCells.Adaptive(160.dp)

/** Всё, что лента показывает, кроме самих картин. */
data class FeedUiState(
    val search: String = "",
    /** null — выбран не чипс, а поиск. */
    val collection: Collection? = Collection.All,
    val color: ColorFilter? = null,
    val suggestions: List<String> = emptyList(),
    /** Показывается только на «чистой» ленте — без поиска и фильтров. */
    val artworkOfTheDay: Artwork? = null,
)

@Composable
fun FeedRoute(
    onOpen: (Artwork, String) -> Unit,
    onImmersive: (text: String?, color: String?) -> Unit,
    contentPadding: PaddingValues,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val search by viewModel.searchText.collectAsStateWithLifecycle()
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    val color by viewModel.color.collectAsStateWithLifecycle()
    val query by viewModel.activeQuery.collectAsStateWithLifecycle()
    val suggestions by viewModel.suggestions.collectAsStateWithLifecycle()
    val daily by viewModel.artworkOfTheDay.collectAsStateWithLifecycle()
    val items = viewModel.artworks.collectAsLazyPagingItems()

    // Новый запрос — к началу ленты.
    LaunchedEffect(query) { gridState.scrollToItem(0) }

    FeedScreen(
        state = FeedUiState(
            search = search,
            collection = collection.takeIf { search.isBlank() },
            color = color,
            suggestions = suggestions,
            artworkOfTheDay = daily.takeIf { query.isEmpty },
        ),
        onSearchChange = viewModel::onSearchChange,
        onSearchSubmit = viewModel::onSearchSubmit,
        onImmersive = { onImmersive(query.text, query.color?.name) },
        onCollectionSelect = viewModel::onCollectionSelect,
        onColorSelect = viewModel::onColorSelect,
        items = items,
        onOpen = { artwork, origin ->
            viewModel.onArtworkOpen(artwork)
            onOpen(artwork, origin)
        },
        gridState = gridState,
        contentPadding = contentPadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: FeedUiState,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onCollectionSelect: (Collection) -> Unit,
    onColorSelect: (ColorFilter) -> Unit,
    items: LazyPagingItems<Artwork>,
    onOpen: (Artwork, String) -> Unit,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
    onImmersive: () -> Unit = {},
) {
    val refresh = items.loadState.refresh
    val empty = items.itemCount == 0
    // Новый запрос при уже показанной ленте: не прячем её и не крутим большой кружок —
    // тонкая полоска под поиском, а старые плитки приглушаются до прихода новых.
    val reloading = refresh is LoadState.Loading && !empty
    // Кружок pull-to-refresh — только если человек сам потянул ленту вниз.
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(refresh) { if (refresh !is LoadState.Loading) pulled = false }
    val dim by animateFloatAsState(if (reloading && !pulled) 0.45f else 1f, tween(250), label = "dim")

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(state, onSearchChange, onSearchSubmit, onImmersive)
        Box(Modifier.fillMaxWidth().height(3.dp)) {
            if (reloading && !pulled) {
                LinearProgressIndicator(Modifier.fillMaxWidth().testTag("reloading"))
            }
        }

        PullToRefreshBox(
            isRefreshing = pulled && reloading,
            onRefresh = {
                pulled = true
                items.refresh()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalStaggeredGrid(
                columns = GridCells,
                state = gridState,
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 4.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                modifier = Modifier.fillMaxSize().testTag("feed"),
            ) {
                item(key = "collections", span = StaggeredGridItemSpan.FullLine) {
                    Collections(state.collection, onCollectionSelect)
                }
                item(key = "colors", span = StaggeredGridItemSpan.FullLine) {
                    Colors(state.color, onColorSelect)
                }
                state.artworkOfTheDay?.let { daily ->
                    item(key = "daily-${daily.id}", span = StaggeredGridItemSpan.FullLine) {
                        ArtworkOfTheDay(daily, onClick = { onOpen(daily, ORIGIN_DAILY) }, modifier = Modifier.animateItem())
                    }
                }
                when {
                    empty && refresh is LoadState.Loading -> skeleton()
                    empty && refresh is LoadState.Error -> item(key = "error", span = StaggeredGridItemSpan.FullLine) {
                        MessageBlock(
                            icon = Icons.Outlined.CloudOff,
                            title = stringResource(R.string.feed_error_title),
                            text = refresh.error.message(),
                            action = stringResource(R.string.retry),
                            onAction = items::retry,
                        )
                    }
                    empty && refresh is LoadState.NotLoading -> item(key = "empty", span = StaggeredGridItemSpan.FullLine) {
                        MessageBlock(
                            icon = Icons.Outlined.SearchOff,
                            title = stringResource(R.string.feed_empty_title),
                            text = stringResource(R.string.feed_empty_hint),
                        )
                    }
                }
                items(count = items.itemCount, key = items.itemKey { it.id }) { index ->
                    items[index]?.let { artwork ->
                        ArtworkTile(
                            artwork,
                            onClick = { onOpen(artwork, ORIGIN_TILE) },
                            modifier = Modifier.animateItem().graphicsLayer { alpha = dim },
                        )
                    }
                }
                if (!empty) item(key = "footer", span = StaggeredGridItemSpan.FullLine) { Footer(items) }
            }
        }
    }
}

@Composable
private fun Header(state: FeedUiState, onSearchChange: (String) -> Unit, onSearchSubmit: (String) -> Unit, onImmersive: () -> Unit) {
    val focus = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    // На широком экране (планшет, альбомная) название и поиск — в одну строку: высота дороже.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val submit = { text: String ->
        onSearchSubmit(text)
        focus.clearFocus()
    }
    val field: @Composable (Modifier) -> Unit = { modifier ->
        TextField(
            value = state.search,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (state.search.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit(state.search) }),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = modifier.onFocusChanged { focused = it.isFocused }.testTag("search"),
        )
    }
    val title = @Composable { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium) }
    // «Погружение» — та же выдача во весь экран со слайд-шоу.
    val immersive = @Composable {
        FilledTonalIconButton(onClick = onImmersive, modifier = Modifier.testTag("immersive-button")) {
            Icon(Icons.Outlined.Slideshow, contentDescription = stringResource(R.string.immersive))
        }
    }
    Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = if (wide) 8.dp else 12.dp, bottom = 4.dp)) {
        if (wide) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                title()
                field(Modifier.weight(1f))
                immersive()
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                title()
                Spacer(Modifier.weight(1f))
                immersive()
            }
            field(Modifier.fillMaxWidth().padding(top = 12.dp))
        }
        // Подсказки музея (художники, названия) — пока поле в фокусе.
        AnimatedVisibility(
            visible = focused && state.suggestions.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .testTag("suggestions"),
            ) {
                state.suggestions.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { submit(s) }
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                            .testTag("suggestion-$s"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Text(s, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 14.dp))
                        Icon(Icons.Outlined.NorthWest, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Collections(selected: Collection?, onSelect: (Collection) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
        items(Collection.entries, key = { it.name }) { c ->
            FilterChip(
                selected = c == selected,
                onClick = { onSelect(c) },
                label = { Text(stringResource(c.title)) },
            )
        }
    }
}

/** Поиск по цвету: кружки-образцы; выбранный — с галочкой и обводкой. */
@Composable
private fun Colors(selected: ColorFilter?, onSelect: (ColorFilter) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        item {
            Text(
                stringResource(R.string.by_color),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        items(ColorFilter.entries, key = { it.name }) { c ->
            val isSelected = c == selected
            val scale by animateFloatAsState(if (isSelected) 1.15f else 1f, label = "swatch")
            val name = stringResource(c.title())
            Box(
                Modifier
                    .size(34.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(CircleShape)
                    .background(Color(c.swatch))
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        shape = CircleShape,
                    )
                    .clickable(role = Role.Checkbox) { onSelect(c) }
                    .semantics {
                        contentDescription = name
                        this.selected = isSelected
                    }
                    .testTag("color-${c.name}"),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Outlined.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

fun ColorFilter.title(): Int = when (this) {
    ColorFilter.Red -> R.string.color_red
    ColorFilter.Orange -> R.string.color_orange
    ColorFilter.Yellow -> R.string.color_yellow
    ColorFilter.Green -> R.string.color_green
    ColorFilter.Teal -> R.string.color_teal
    ColorFilter.Blue -> R.string.color_blue
    ColorFilter.Purple -> R.string.color_purple
    ColorFilter.Dark -> R.string.color_dark
}

/**
 * «Картина дня» — большая карточка над лентой: картинка во всю ширину, снизу затемнение
 * и подпись. Тот же shared element, что у плитки: картина «перелетает» на свой экран.
 */
@Composable
private fun ArtworkOfTheDay(artwork: Artwork, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp)
            .clip(shape)
            .clickable(onClick = onClick)
            .testTag("artwork-of-the-day"),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current)
                .data(artwork.imageUrl)
                .memoryCacheKey(artwork.imageUrl)
                .build(),
            contentDescription = artwork.altText ?: artwork.displayTitle(),
            placeholder = rememberPlaceholder(artwork),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(artwork.aspectRatio.coerceIn(1.1f, 1.8f))
                .sharedArtworkImage(artwork.id, ORIGIN_DAILY)
                .clip(shape)
                .background(artwork.backdrop(MaterialTheme.colorScheme.surfaceVariant)),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.75f))),
        )
        // «На рабочий стол» — та же картина каждый день в виджете.
        val context = LocalContext.current
        if (remember { canPinWidget(context) }) {
            FilledTonalIconButton(
                onClick = { requestPinWidget(context) },
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.Black.copy(alpha = 0.4f), contentColor = Color.White),
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).testTag("pin-widget"),
            ) {
                Icon(Icons.Outlined.AddToHomeScreen, contentDescription = stringResource(R.string.widget_add))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
            Text(
                stringResource(R.string.artwork_of_the_day).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                artwork.displayTitle(),
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            artwork.artist?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f)) }
        }
    }
}

@Composable
private fun Footer(items: LazyPagingItems<Artwork>) {
    Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        when (val append = items.loadState.append) {
            is LoadState.Loading -> CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
            is LoadState.Error -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(append.error.message(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = items::retry) { Text(stringResource(R.string.retry)) }
            }
            is LoadState.NotLoading -> if (append.endOfPaginationReached) {
                Text(
                    stringResource(R.string.feed_end),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
    }
}

/** Скелет первой загрузки: плитки разной высоты мягко «дышат». */
private fun LazyStaggeredGridScope.skeleton() {
    val ratios = listOf(0.8f, 1.2f, 0.7f, 1f, 1.3f, 0.75f, 0.9f, 1.1f)
    items(ratios.size, key = { "skeleton-$it" }) { i ->
        val pulse = rememberInfiniteTransition(label = "skeleton")
        val alpha by pulse.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
        Column(Modifier.graphicsLayer { this.alpha = alpha }) {
            Box(Modifier.fillMaxWidth().aspectRatio(ratios[i]).clip(TileShape).background(MaterialTheme.colorScheme.surfaceVariant))
            Box(Modifier.padding(top = 8.dp, start = 4.dp).fillMaxWidth(0.7f).height(12.dp).clip(TileShape).background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}
