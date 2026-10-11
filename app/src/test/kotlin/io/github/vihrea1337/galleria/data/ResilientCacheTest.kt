package io.github.vihrea1337.galleria.data

import io.github.vihrea1337.galleria.data.remote.CacheApiResponses
import io.github.vihrea1337.galleria.data.remote.StaleIfError
import java.io.IOException
import java.nio.file.Files
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/** Кэш ответов API на настоящем OkHttp и локальном сервере: свежий — из кэша, при сбое — устаревший. */
class ResilientCacheTest {

    private val server = MockWebServer()
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server.start()
        client = OkHttpClient.Builder()
            // Без автоповтора: иначе после обрыва OkHttp сам сходит ещё раз и получит пустой ответ MockWebServer.
            .retryOnConnectionFailure(false)
            .cache(Cache(Files.createTempDirectory("http-cache").toFile(), 1024 * 1024))
            .addInterceptor(StaleIfError())
            .addNetworkInterceptor(CacheApiResponses(apiHost = server.hostName))
            .build()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun get(path: String = "/artworks") =
        client.newCall(Request.Builder().url(server.url(path)).build()).execute().use { it.code to it.body!!.string() }

    @Test
    fun `api says no-cache, but a repeat request is served from cache`() {
        server.enqueue(MockResponse().setBody("page 1").addHeader("Cache-Control", "no-cache"))
        assertEquals(200 to "page 1", get())
        assertEquals(200 to "page 1", get())
        assertEquals("второй раз к серверу не ходили", 1, server.requestCount)
    }

    @Test
    fun `Vary star from the server does not disable the cache`() {
        server.enqueue(MockResponse().setBody("page").addHeader("Cache-Control", "no-cache").addHeader("Vary", "*"))
        assertEquals(200 to "page", get())
        assertEquals(200 to "page", get())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `museum 504 - the last saved answer instead of an error`() {
        server.enqueue(MockResponse().setBody("saved"))
        get()
        // Свежесть кэша вышла бы через 10 минут; имитируем «нужно спросить сервер» — другой Cache-Control у запроса.
        server.enqueue(MockResponse().setResponseCode(504).setBody("gateway timeout"))
        val fresh = client.newCall(
            Request.Builder().url(server.url("/artworks")).header("Cache-Control", "no-cache").build(),
        ).execute().use { it.code to it.body!!.string() }
        assertEquals(200 to "saved", fresh)
    }

    @Test
    fun `no network - the last saved answer`() {
        server.enqueue(MockResponse().setBody("saved"))
        val url = server.url("/artworks")
        get()
        server.shutdown() // сервер недоступен — как без сети
        val offline = client.newCall(
            Request.Builder().url(url).header("Cache-Control", "no-cache").build(),
        ).execute().use { it.code to it.body!!.string() }
        assertEquals(200 to "saved", offline)
    }

    @Test
    fun `nothing saved - honest error`() {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(503, get("/never-seen").first)
        val url = server.url("/never-seen-2")
        server.shutdown()
        assertThrows(IOException::class.java) {
            client.newCall(Request.Builder().url(url).build()).execute().close()
        }
    }

    @Test
    fun `other hosts (images) are not marked cacheable`() {
        val other = OkHttpClient.Builder()
            .cache(Cache(Files.createTempDirectory("http-cache-2").toFile(), 1024 * 1024))
            .addNetworkInterceptor(CacheApiResponses(apiHost = "api.artic.edu"))
            .build()
        server.enqueue(MockResponse().setBody("a").addHeader("Cache-Control", "no-cache"))
        server.enqueue(MockResponse().setBody("b").addHeader("Cache-Control", "no-cache"))
        other.newCall(Request.Builder().url(server.url("/img")).build()).execute().close()
        other.newCall(Request.Builder().url(server.url("/img")).build()).execute().close()
        assertEquals(2, server.requestCount)
    }
}
