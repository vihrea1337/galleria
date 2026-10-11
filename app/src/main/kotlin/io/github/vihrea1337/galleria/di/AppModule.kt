package io.github.vihrea1337.galleria.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.vihrea1337.galleria.BuildConfig
import io.github.vihrea1337.galleria.data.ArtworkRepository
import io.github.vihrea1337.galleria.data.ArtworkRepositoryImpl
import io.github.vihrea1337.galleria.data.local.FavoriteDao
import io.github.vihrea1337.galleria.data.local.GalleriaDatabase
import io.github.vihrea1337.galleria.data.remote.ArticApi
import io.github.vihrea1337.galleria.data.remote.CacheApiResponses
import io.github.vihrea1337.galleria.data.remote.StaleIfError
import java.io.File
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Один OkHttp на API и на картинки (Coil — GalleriaApp): общий пул соединений.
     * Музей просит представляться — заголовок AIC-User-Agent с названием и контактом.
     * HTTP-кэш ответов API + «устаревшее при ошибке» — data/remote/ResilientCache.kt.
     */
    @Provides
    @Singleton
    fun okHttp(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // Поиск музея под нагрузкой отвечает по 10+ секунд — не обрываем раньше времени.
        .readTimeout(30, TimeUnit.SECONDS)
        .cache(Cache(File(context.cacheDir, "http-api"), 20L * 1024 * 1024))
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("AIC-User-Agent", "Galleria/${BuildConfig.VERSION_NAME} (github.com/vihrea1337/galleria)")
                    .build(),
            )
        }
        .addInterceptor(StaleIfError())
        .addNetworkInterceptor(CacheApiResponses(apiHost = ArticApi.BASE_URL.toHttpUrl().host))
        .build()

    @Provides
    @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    fun articApi(client: OkHttpClient, json: Json): ArticApi = Retrofit.Builder()
        .baseUrl(ArticApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(ArticApi::class.java)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): GalleriaDatabase =
        Room.databaseBuilder(context, GalleriaDatabase::class.java, "galleria.db").build()

    @Provides
    fun favoriteDao(db: GalleriaDatabase): FavoriteDao = db.favorites()

    @Provides
    fun clock(): Clock = Clock.systemDefaultZone()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    abstract fun repository(impl: ArtworkRepositoryImpl): ArtworkRepository
}
