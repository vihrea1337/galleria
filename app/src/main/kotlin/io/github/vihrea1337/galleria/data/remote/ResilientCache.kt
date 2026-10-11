package io.github.vihrea1337.galleria.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/*
 * HTTP-кэш ответов API музея — чтобы демо не зависело от того, как музей отвечает прямо сейчас.
 *
 * API присылает `Cache-Control: no-cache`, поэтому OkHttp сам ничего не кэширует. Два перехватчика:
 * 1. [CacheApiResponses] (сетевой) — помечает ответы API как свежие на [FRESH_SECONDS]: повторный
 *    запрос той же страницы (возврат в ленту, поворот, «Повторить») — из кэша, мгновенно.
 * 2. [StaleIfError] (обычный) — если сети нет или музей ответил 5xx (у него бывают 504 под нагрузкой),
 *    отдаёт последний сохранённый ответ возрастом до [STALE_DAYS] дней. Открывал раньше — откроется
 *    и сейчас; не открывал — честная ошибка в UI.
 */

private const val FRESH_SECONDS = 10 * 60
private const val STALE_DAYS = 7

/** Сетевой перехватчик: ответы API (не картинки — их кэширует Coil) хранить [FRESH_SECONDS]. */
class CacheApiResponses(private val apiHost: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (chain.request().url.host != apiHost || !response.isSuccessful) return response
        return response.newBuilder()
            .header("Cache-Control", "public, max-age=$FRESH_SECONDS")
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .build()
    }
}

/** Обычный перехватчик: при ошибке сети или 5xx — устаревший ответ из кэша, если он есть. */
class StaleIfError : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.method != "GET") return chain.proceed(request)
        val fresh = try {
            chain.proceed(request)
        } catch (e: IOException) {
            return fromCache(chain) ?: throw e
        }
        if (fresh.code < 500) return fresh
        // Новый запрос по цепочке нельзя, пока открыт прошлый ответ: читаем тело ошибки
        // в память (оно маленькое), закрываем — и уже потом спрашиваем кэш.
        val error = fresh.newBuilder()
            .body(fresh.body!!.use { it.bytes().toResponseBody(it.contentType()) })
            .build()
        return fromCache(chain) ?: error
    }

    private fun fromCache(chain: Interceptor.Chain): Response? {
        val cacheOnly = chain.request().newBuilder()
            .cacheControl(
                CacheControl.Builder()
                    .onlyIfCached()
                    .maxStale(STALE_DAYS, TimeUnit.DAYS)
                    .build(),
            )
            .build()
        val cached = try {
            chain.proceed(cacheOnly)
        } catch (e: IOException) {
            return null
        }
        // Нет в кэше — OkHttp отвечает искусственным 504.
        if (cached.code == 504) {
            cached.close()
            return null
        }
        return cached
    }
}
