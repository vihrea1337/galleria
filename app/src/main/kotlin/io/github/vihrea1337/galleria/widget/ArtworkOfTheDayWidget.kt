package io.github.vihrea1337.galleria.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import io.github.vihrea1337.galleria.MainActivity
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.data.ArtworkRepository
import io.github.vihrea1337.galleria.model.Artwork
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/** Виджет создаёт система, а не Hilt, — зависимости берём через EntryPoint. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun repository(): ArtworkRepository
}

/**
 * «Картина дня» на рабочем столе: та же картина, что в карточке над лентой (одна на всех в этот
 * день). Обновляет система раз в сутки (updatePeriodMillis в res/xml/artwork_of_the_day_widget.xml),
 * WorkManager не нужен. Нажатие — открыть картину в приложении.
 *
 * Glance рисует через RemoteViews: никаких Compose-картинок из сети — картинку заранее грузим
 * Coil-ом в Bitmap (через HTTP-кэш: без сети покажется вчерашняя).
 */
class ArtworkOfTheDayWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java).repository()
        val artwork = try {
            repository.artworkOfTheDay(LocalDate.now().toEpochDay())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val bitmap = artwork?.let { loadBitmap(context, it) }
        provideContent { Content(context, artwork, bitmap) }
    }

    private suspend fun loadBitmap(context: Context, artwork: Artwork): Bitmap? {
        val result = SingletonImageLoader.get(context).execute(
            ImageRequest.Builder(context)
                .data(artwork.imageUrl)
                // Виджеты передаются через Binder с лимитом размера — картинку уменьшаем.
                .size(720)
                .allowHardware(false)
                .build(),
        )
        return (result as? SuccessResult)?.image?.toBitmap()
    }

    @Composable
    private fun Content(context: Context, artwork: Artwork?, bitmap: Bitmap?) {
        val open = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply { artwork?.let { putExtra(MainActivity.EXTRA_ARTWORK_ID, it.id) } }
        Box(
            GlanceModifier
                .fillMaxSize()
                .cornerRadius(20.dp)
                .background(ColorProvider(artwork?.dominantColor?.let { Color(it) } ?: Color(0xFF2B2826)))
                .clickable(actionStartActivity(open)),
            contentAlignment = Alignment.BottomStart,
        ) {
            if (bitmap != null) {
                Image(
                    provider = ImageProvider(bitmap),
                    contentDescription = artwork?.title,
                    contentScale = ContentScale.Crop,
                    modifier = GlanceModifier.fillMaxSize(),
                )
            }
            // Подпись на тёмной подложке (градиентов Glance не умеет — полупрозрачная полоса).
            Column(
                GlanceModifier
                    .fillMaxWidth()
                    .background(ColorProvider(Color.Black.copy(alpha = 0.55f)))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    context.getString(R.string.artwork_of_the_day).uppercase(),
                    style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.8f)), fontSize = 10.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    artwork?.title?.ifBlank { null } ?: context.getString(if (artwork == null) R.string.widget_offline else R.string.untitled),
                    maxLines = 2,
                    style = TextStyle(color = ColorProvider(Color.White), fontSize = 15.sp, fontFamily = FontFamily.Serif),
                )
                artwork?.artist?.let {
                    Text(it, maxLines = 1, style = TextStyle(color = ColorProvider(Color.White.copy(alpha = 0.8f)), fontSize = 12.sp))
                }
            }
        }
    }
}

class ArtworkOfTheDayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ArtworkOfTheDayWidget()
}

/** Можно ли предложить «добавить виджет» прямо из приложения (Android 8+, поддерживает лаунчер). */
fun canPinWidget(context: Context): Boolean =
    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
        android.appwidget.AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported

/** Системный диалог «Добавить виджет на главный экран». */
fun requestPinWidget(context: Context) {
    if (!canPinWidget(context)) return
    android.appwidget.AppWidgetManager.getInstance(context).requestPinAppWidget(
        android.content.ComponentName(context, ArtworkOfTheDayWidgetReceiver::class.java),
        null,
        null,
    )
}
