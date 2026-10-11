package io.github.vihrea1337.galleria

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient

/**
 * Coil (загрузка картинок) настраивается здесь один раз для всего приложения: тот же
 * OkHttp, что у API (заголовок музея, общий пул соединений), плавное появление картинок.
 * Кэш в памяти и на диске Coil ведёт сам — повторно картина не скачивается.
 */
@HiltAndroidApp
class GalleriaApp : Application(), SingletonImageLoader.Factory {
    @Inject lateinit var okHttp: OkHttpClient

    /** Клиент картинок: общий пул соединений, но без HTTP-кэша OkHttp — у Coil свой дисковый кэш. */
    private val imageClient by lazy { okHttp.newBuilder().cache(null).build() }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageClient })) }
            .crossfade(true)
            .build()
}
