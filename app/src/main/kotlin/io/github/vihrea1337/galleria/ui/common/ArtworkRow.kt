package io.github.vihrea1337.galleria.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.vihrea1337.galleria.model.Artwork

private val RowHeight = 168.dp

/**
 * Горизонтальный ряд работ одной высоты («Ещё у художника», «Похожие по цвету»): ширина каждой
 * карточки — по пропорциям картины, поэтому ряд выглядит как развеска в музее.
 */
@Composable
fun ArtworkRow(
    title: String,
    artworks: List<Artwork>,
    /** Часть ключа shared element; у каждого ряда свой — одна работа может быть в двух рядах. */
    origin: String,
    onOpen: (Artwork) -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    if (artworks.isEmpty()) return
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            leading?.invoke()
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            action?.let { TextButton(onClick = onAction) { Text(it) } }
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.testTag("row-$title"),
        ) {
            items(artworks, key = { it.id }) { artwork ->
                val width = RowHeight * artwork.aspectRatio.coerceIn(0.6f, 1.6f)
                Column(Modifier.width(width).clip(TileShape).clickable(role = Role.Button) { onOpen(artwork) }) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalPlatformContext.current)
                            .data(artwork.thumbUrl)
                            .memoryCacheKey(artwork.thumbUrl)
                            .build(),
                        contentDescription = artwork.altText ?: artwork.displayTitle(),
                        placeholder = rememberPlaceholder(artwork),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(width)
                            .height(RowHeight)
                            .sharedArtworkImage(artwork.id, origin)
                            .clip(TileShape)
                            .background(artwork.backdrop(MaterialTheme.colorScheme.surfaceVariant)),
                    )
                    Text(
                        artwork.displayTitle(),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp, bottom = 2.dp),
                    )
                }
            }
        }
    }
}
