package io.github.vihrea1337.galleria.ui.artist

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import io.github.vihrea1337.galleria.ui.common.ORIGIN_TILE
import io.github.vihrea1337.galleria.ui.common.TileShape
import io.github.vihrea1337.galleria.ui.common.message
import io.github.vihrea1337.galleria.ui.feed.GridCells

@Composable
fun ArtistScreenRoute(
    name: String,
    onBack: () -> Unit,
    onOpen: (id: Long, origin: String) -> Unit,
    viewModel: ArtistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArtistScreen(
        name = name,
        state = state,
        works = viewModel.works.collectAsLazyPagingItems(),
        onBack = onBack,
        onRetry = viewModel::load,
        onOpen = {
            viewModel.onArtworkOpen(it)
            onOpen(it.id, ORIGIN_TILE)
        },
    )
}

/**
 * Имя приходит в маршруте — шапка видна сразу; годы и биография догружаются. Биография
 * свёрнута до 4 строк, «Читать дальше» раскрывает её с анимацией высоты.
 */
@Composable
fun ArtistScreen(
    name: String,
    state: ArtistUiState,
    works: LazyPagingItems<Artwork>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (Artwork) -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        LazyVerticalStaggeredGrid(
            columns = GridCells,
            state = rememberLazyStaggeredGridState(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalItemSpacing = 12.dp,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .testTag("artist"),
        ) {
            item(key = "header", span = StaggeredGridItemSpan.FullLine) {
                Header(name, state, onBack, onRetry)
            }
            item(key = "works-title", span = StaggeredGridItemSpan.FullLine) {
                Text(
                    stringResource(R.string.artist_works),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                )
            }
            items(count = works.itemCount, key = works.itemKey { it.id }) { index ->
                works[index]?.let { artwork -> ArtworkTile(artwork, onClick = { onOpen(artwork) }) }
            }
            if (works.loadState.refresh is LoadState.Loading || works.loadState.append is LoadState.Loading) {
                item(key = "loading", span = StaggeredGridItemSpan.FullLine) {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(name: String, state: ArtistUiState, onBack: () -> Unit, onRetry: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column {
        IconButton(onClick = onBack, modifier = Modifier.padding(top = 4.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Column(Modifier.padding(horizontal = 8.dp)) {
            Text(name, style = MaterialTheme.typography.displaySmall)
            state.artist?.years?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                state.artist?.bio != null -> {
                    Text(
                        state.artist.bio,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = if (expanded) Int.MAX_VALUE else 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp).animateContentSize().testTag("artist-bio"),
                    )
                    TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(stringResource(if (expanded) R.string.artist_bio_less else R.string.artist_bio_more))
                    }
                }
                state.loading -> Column(Modifier.padding(top = 12.dp)) {
                    listOf(1f, 0.9f, 0.6f).forEach { w ->
                        Box(
                            Modifier.padding(vertical = 4.dp).fillMaxWidth(w).height(14.dp).clip(TileShape)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                        )
                    }
                }
                state.error != null -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Text(state.error.message(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
                }
            }
        }
    }
}
