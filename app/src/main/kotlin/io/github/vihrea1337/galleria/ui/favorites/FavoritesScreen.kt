package io.github.vihrea1337.galleria.ui.favorites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.ui.common.ArtworkTile
import io.github.vihrea1337.galleria.ui.common.MessageBlock
import io.github.vihrea1337.galleria.ui.feed.GridCells

@Composable
fun FavoritesRoute(
    onOpen: (Artwork) -> Unit,
    contentPadding: PaddingValues,
    gridState: LazyStaggeredGridState = rememberLazyStaggeredGridState(),
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val favorites by viewModel.favorites.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val removedText = stringResource(R.string.favorite_removed)
    val undoText = stringResource(R.string.undo)

    LaunchedEffect(viewModel) {
        viewModel.removed.collect { artwork ->
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(removedText, actionLabel = undoText, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) viewModel.undoRemove(artwork)
        }
    }

    Box(Modifier.fillMaxSize()) {
        FavoritesScreen(
            favorites = favorites,
            onOpen = {
                viewModel.onArtworkOpen(it)
                onOpen(it)
            },
            onRemove = viewModel::remove,
            gridState = gridState,
            contentPadding = contentPadding,
        )
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(bottom = contentPadding.calculateBottomPadding()),
        )
    }
}

@Composable
fun FavoritesScreen(
    favorites: List<Artwork>?,
    onOpen: (Artwork) -> Unit,
    onRemove: (Artwork) -> Unit,
    gridState: LazyStaggeredGridState,
    contentPadding: PaddingValues,
) {
    Column(Modifier.fillMaxSize()) {
        Text(
            stringResource(R.string.tab_favorites),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        )
        when {
            favorites == null -> Unit
            favorites.isEmpty() -> MessageBlock(
                icon = Icons.Outlined.FavoriteBorder,
                title = stringResource(R.string.favorites_empty_title),
                text = stringResource(R.string.favorites_empty_hint),
                modifier = Modifier.testTag("favorites-empty"),
            )
            else -> LazyVerticalStaggeredGrid(
                columns = GridCells,
                state = gridState,
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalItemSpacing = 12.dp,
                modifier = Modifier.fillMaxSize().testTag("favorites"),
            ) {
                items(favorites, key = { it.id }) { artwork ->
                    ArtworkTile(
                        artwork = artwork,
                        onClick = { onOpen(artwork) },
                        modifier = Modifier.animateItem(),
                        overlay = {
                            Box(Modifier.matchParentSize(), contentAlignment = Alignment.TopEnd) {
                                FilledIconButton(
                                    onClick = { onRemove(artwork) },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.35f),
                                        contentColor = Color.White,
                                    ),
                                    modifier = Modifier.padding(6.dp),
                                ) {
                                    Icon(Icons.Filled.Favorite, contentDescription = stringResource(R.string.favorite_remove))
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
