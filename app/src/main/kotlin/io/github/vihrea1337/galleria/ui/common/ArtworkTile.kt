package io.github.vihrea1337.galleria.ui.common

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.model.Artwork

val TileShape = RoundedCornerShape(12.dp)

/** Название для показа: пустое из API → «Без названия» на языке устройства. */
@Composable
fun Artwork.displayTitle(): String = title.ifBlank { stringResource(R.string.untitled) }

/**
 * Размытая заглушка из `lqip` (картинка ~5×5 px от музея): растянутая с билинейной
 * фильтрацией, она выглядит как мягкое пятно цветов картины, пока грузится настоящая.
 */
@Composable
fun rememberPlaceholder(artwork: Artwork): Painter? = remember(artwork.id) {
    artwork.placeholder
        ?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
        ?.let { BitmapPainter(it.asImageBitmap(), filterQuality = FilterQuality.Low) }
}

/** Фон под картинкой, пока нет даже заглушки, — преобладающий цвет картины. */
fun Artwork.backdrop(fallback: Color): Color = dominantColor?.let { Color(it) } ?: fallback

/**
 * Плитка ленты: картинка нужной высоты сразу (пропорции из API — лента не прыгает при
 * загрузке), под ней название с засечками и автор.
 */
@Composable
fun ArtworkTile(
    artwork: Artwork,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    overlay: (@Composable BoxScope.() -> Unit)? = null,
) {
    Column(
        modifier
            .clip(TileShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(bottom = 6.dp),
    ) {
        Box {
            // Явный ключ кэша памяти: по нему экран картины найдёт эту превью как заглушку
                // (ключ по умолчанию у Coil 3 содержит ещё и размер — по одному адресу не найти).
            AsyncImage(
                model = ImageRequest.Builder(LocalPlatformContext.current)
                    .data(artwork.thumbUrl)
                    .memoryCacheKey(artwork.thumbUrl)
                    .build(),
                contentDescription = artwork.altText ?: artwork.displayTitle(),
                placeholder = rememberPlaceholder(artwork),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(artwork.aspectRatio)
                    .sharedArtworkImage(artwork.id)
                    .clip(TileShape)
                    .background(artwork.backdrop(MaterialTheme.colorScheme.surfaceVariant)),
            )
            overlay?.invoke(this)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            artwork.displayTitle(),
            style = MaterialTheme.typography.titleSmall.copy(fontFamily = MaterialTheme.typography.titleMedium.fontFamily),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        artwork.artist?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
