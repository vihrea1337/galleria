package io.github.vihrea1337.galleria.data

import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.vihrea1337.galleria.data.remote.ArticApi
import io.github.vihrea1337.galleria.model.Artwork
import java.io.IOException
import retrofit2.HttpException

/**
 * Страницы ленты для Paging 3: ключ — номер страницы API (с 1).
 *
 * Paging сам решает, когда звать [load]: долистали почти до конца — следующая страница;
 * ошибка — показывает её в ленте (в UI кнопка «Повторить» зовёт `retry()`).
 *
 * Конец ленты — когда кончились результаты ИЛИ упёрлись в потолок API (1000 работ на
 * выдачу): глубже музей отвечает ошибкой, поэтому туда даже не просим.
 */
class ArtworkPagingSource(
    private val api: ArticApi,
    private val query: String?,
) : PagingSource<Int, Artwork>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Artwork> {
        val page = params.key ?: 1
        // Размер страницы постоянный (initialLoadSize = pageSize в ArtworkRepository):
        // ключ — номер страницы, и разный размер сдвинул бы страницы друг относительно друга.
        val limit = params.loadSize.coerceAtMost(MAX_PAGE)
        return try {
            val response = api.search(query = query?.trim()?.takeIf { it.isNotEmpty() }, page = page, limit = limit)
            val items = response.data.mapNotNull { it.toArtwork() }
            val loaded = page * limit
            val reachedEnd = response.data.isEmpty() ||
                loaded >= response.pagination.total ||
                (page + 1) * limit > ArticApi.MAX_RESULTS
            LoadResult.Page(
                data = items,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (reachedEnd) null else page + 1,
            )
        } catch (e: IOException) {
            LoadResult.Error(e)
        } catch (e: HttpException) {
            LoadResult.Error(e)
        }
    }

    /** После обновления (смена запроса, pull-to-refresh) — начать с той же области ленты. */
    override fun getRefreshKey(state: PagingState<Int, Artwork>): Int? =
        state.anchorPosition?.let { pos ->
            state.closestPageToPosition(pos)?.let { it.prevKey?.plus(1) ?: it.nextKey?.minus(1) }
        }

    private companion object {
        const val MAX_PAGE = 100
    }
}
