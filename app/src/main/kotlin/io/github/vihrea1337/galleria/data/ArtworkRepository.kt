package io.github.vihrea1337.galleria.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import io.github.vihrea1337.galleria.data.local.FavoriteDao
import io.github.vihrea1337.galleria.data.local.toArtwork
import io.github.vihrea1337.galleria.data.local.toFavorite
import io.github.vihrea1337.galleria.data.remote.ArticApi
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.model.ArtworkDetails
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Всё, что экранам нужно от данных. Интерфейс — чтобы в тестах ViewModel подставить фейк.
 */
interface ArtworkRepository {
    /** Лента: null или пусто — подборка без поиска. */
    fun artworks(query: String?): Flow<PagingData<Artwork>>
    suspend fun details(id: Long): ArtworkDetails
    fun favorites(): Flow<List<Artwork>>
    fun isFavorite(id: Long): Flow<Boolean>
    suspend fun setFavorite(artwork: Artwork, favorite: Boolean)

    /**
     * Картина, по которой только что нажали в ленте/избранном: экран картины берёт её
     * сразу (картинка, название, цвет — без ожидания сети), а подробности догружает.
     */
    fun remember(artwork: Artwork)
    fun remembered(id: Long): Artwork?
}

@Singleton
class ArtworkRepositoryImpl @Inject constructor(
    private val api: ArticApi,
    private val favoritesDao: FavoriteDao,
    private val clock: Clock,
) : ArtworkRepository {

    override fun artworks(query: String?): Flow<PagingData<Artwork>> =
        Pager(
            config = PagingConfig(
                pageSize = ArticApi.PAGE_SIZE,
                // Ключ — номер страницы API, поэтому первая загрузка ОБЯЗАНА быть того же
                // размера, что и остальные (по умолчанию Paging просит втрое больше —
                // страница 2 тогда перекрылась бы с первой, и в ленте пошли бы дубли).
                initialLoadSize = ArticApi.PAGE_SIZE,
                // Подгружать заранее — пока человек долистывает текущие две страницы.
                prefetchDistance = ArticApi.PAGE_SIZE,
                enablePlaceholders = false,
            ),
            pagingSourceFactory = { ArtworkPagingSource(api, query) },
        ).flow

    override suspend fun details(id: Long): ArtworkDetails =
        api.artwork(id).data.toDetails() ?: error("У работы $id нет изображения")

    override fun favorites(): Flow<List<Artwork>> =
        favoritesDao.observeAll().map { list -> list.map { it.toArtwork() } }

    override fun isFavorite(id: Long): Flow<Boolean> = favoritesDao.observeIsFavorite(id)

    // Несколько последних — хватает на «открыл, вернулся, открыл другую».
    private val recent = object : LinkedHashMap<Long, Artwork>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Artwork>?) = size > RECENT_LIMIT
    }

    override fun remember(artwork: Artwork) {
        synchronized(recent) { recent[artwork.id] = artwork }
    }

    override fun remembered(id: Long): Artwork? = synchronized(recent) { recent[id] }

    override suspend fun setFavorite(artwork: Artwork, favorite: Boolean) {
        if (favorite) favoritesDao.insert(artwork.toFavorite(clock.millis())) else favoritesDao.delete(artwork.id)
    }

    private companion object {
        const val RECENT_LIMIT = 20
    }
}
