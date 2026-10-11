package io.github.vihrea1337.galleria.data

import androidx.paging.PagingConfig
import androidx.paging.PagingSource.LoadResult
import androidx.paging.testing.TestPager
import io.github.vihrea1337.galleria.data.remote.ArticApi
import io.github.vihrea1337.galleria.data.remote.ArtworkDto
import io.github.vihrea1337.galleria.data.remote.ArtworkPageDto
import io.github.vihrea1337.galleria.data.remote.ArtworkResponseDto
import io.github.vihrea1337.galleria.data.remote.PaginationDto
import io.github.vihrea1337.galleria.model.Artwork
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

/** Фейковый API: `total` работ, у каждой есть картинка; можно заставить падать. */
class FakeArticApi(private val total: Int) : ArticApi {
    var failWith: Exception? = null
    val requests = mutableListOf<Triple<String?, Int, Int>>()

    override suspend fun search(query: String?, page: Int, limit: Int, fields: String, publicDomain: Boolean): ArtworkPageDto {
        requests += Triple(query, page, limit)
        failWith?.let { throw it }
        val from = (page - 1) * limit
        val ids = (from until minOf(from + limit, total)).map { it.toLong() + 1 }
        return ArtworkPageDto(PaginationDto(total = total, limit = limit, currentPage = page), ids.map { ArtworkDto(id = it, title = "#$it", imageId = "img$it") })
    }

    override suspend fun artwork(id: Long, fields: String) = ArtworkResponseDto(ArtworkDto(id = id, title = "#$id", imageId = "img$id"))
}

class ArtworkPagingSourceTest {

    private val config = PagingConfig(pageSize = 30, initialLoadSize = 30, enablePlaceholders = false)

    private fun pager(api: ArticApi, query: String? = null) = TestPager(config, ArtworkPagingSource(api, query))

    @Test
    fun `first page starts at 1 and points to the next`() = runTest {
        val api = FakeArticApi(total = 100)
        val page = pager(api).refresh() as LoadResult.Page<Int, Artwork>
        assertEquals(30, page.data.size)
        assertEquals(1L, page.data.first().id)
        assertNull(page.prevKey)
        assertEquals(2, page.nextKey)
    }

    @Test
    fun `pages do not overlap`() = runTest {
        val pager = pager(FakeArticApi(total = 100))
        pager.refresh()
        val second = pager.append() as LoadResult.Page<Int, Artwork>
        assertEquals(31L, second.data.first().id)
    }

    @Test
    fun `ends when results run out`() = runTest {
        val pager = pager(FakeArticApi(total = 45))
        pager.refresh()
        val last = pager.append() as LoadResult.Page<Int, Artwork>
        assertEquals(15, last.data.size)
        assertNull(last.nextKey)
    }

    @Test
    fun `ends before the API 1000-result ceiling`() = runTest {
        val api = FakeArticApi(total = 60_000)
        val pager = pager(api)
        pager.refresh()
        var last: LoadResult.Page<Int, Artwork>
        do {
            last = pager.append() as LoadResult.Page<Int, Artwork>
        } while (last.nextKey != null)
        // 30 × 33 = 990 — страница 34 перешла бы потолок в 1000 и вернула бы ошибку.
        assertTrue(api.requests.all { (_, page, limit) -> page * limit <= ArticApi.MAX_RESULTS })
        assertEquals(33, api.requests.last().second)
    }

    @Test
    fun `blank query is sent as no query`() = runTest {
        val api = FakeArticApi(total = 10)
        pager(api, query = "  ").refresh()
        assertNull(api.requests.single().first)
    }

    @Test
    fun `empty response is the end`() = runTest {
        val page = pager(FakeArticApi(total = 0)).refresh() as LoadResult.Page<Int, Artwork>
        assertTrue(page.data.isEmpty())
        assertNull(page.nextKey)
    }

    @Test
    fun `network and http failures become load errors`() = runTest {
        val api = FakeArticApi(total = 100)
        api.failWith = IOException("offline")
        assertTrue((pager(api).refresh() as LoadResult.Error).throwable is IOException)
        api.failWith = HttpException(Response.error<Any>(503, "".toResponseBody()))
        assertTrue((pager(api).refresh() as LoadResult.Error).throwable is HttpException)
    }
}
