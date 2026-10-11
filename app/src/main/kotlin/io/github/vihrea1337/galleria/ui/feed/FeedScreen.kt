package io.github.vihrea1337.galleria.ui.feed

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.ui.common.ArtworkTile
import io.github.vihrea1337.galleria.ui.common.MessageBlock
import io.github.vihrea1337.galleria.ui.common.TileShape
import io.github.vihrea1337.galleria.ui.common.message

/** Плитки шириной от 160 dp: 2 колонки на телефоне, 3–5 на планшете — по ширине окна. */
val GridCells = StaggeredGridCells.Adaptive(160.dp)

@Composable
fun FeedRoute(
    onOpen: (Artwork) -> Unit,
    contentPadding: PaddingValues,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    viewModel: FeedViewModel = hiltViewModel(),
) {
    val search by viewModel.searchText.collectAsStateWithLifecycle()
    val collection by viewModel.collection.collectAsStateWithLifecycle()
    val query by viewModel.activeQuery.collectAsStateWithLifecycle()
    val items = viewModel.artworks.collectAsLazyPagingItems()

    // Новый запрос — к началу ленты.
    LaunchedEffect(query) { gridState.scrollToItem(0) }

    FeedScreen(
        search = search,
        onSearchChange = viewModel::onSearchChange,
        collection = collection.takeIf { search.isBlank() },
        onCollectionSelect = viewModel::onCollectionSelect,
        items = items,
        onOpen = {
            viewModel.onArtworkOpen(it)
            onOpen(it)
        },
        gridState = gridState,
        contentPadding = contentPadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    search: String,
    onSearchChange: (String) -> Unit,
    collection: Collection?,
    onCollectionSelect: (Collection) -> Unit,
    items: LazyPagingItems<Artwork>,
    onOpen: (Artwork) -> Unit,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(search, onSearchChange)

        val refresh = items.loadState.refresh
        val empty = items.itemCount == 0
        PullToRefreshBox(
            // Крутилка — только когда уже есть что показывать; первая загрузка — скелет.
            isRefreshing = refresh is LoadState.Loading && !empty,
            onRefresh = { items.refresh() },
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
                    Collections(collection, onCollectionSelect)
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
                        ArtworkTile(artwork, onClick = { onOpen(artwork) }, modifier = Modifier.animateItem())
                    }
                }
                if (!empty) item(key = "footer", span = StaggeredGridItemSpan.FullLine) { Footer(items) }
            }
        }
    }
}

@Composable
private fun Header(search: String, onSearchChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        TextField(
            value = search,
            onValueChange = onSearchChange,
            placeholder = { Text(stringResource(R.string.search_hint)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (search.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.search_clear))
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag("search"),
        )
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
