package io.github.vihrea1337.galleria.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API Чикагского института искусств (https://api.artic.edu/docs/). Бесплатный, без ключа;
 * музей просит представляться заголовком `AIC-User-Agent` — его добавляет OkHttp (di/AppModule).
 *
 * Лента и поиск — один эндпоинт `/artworks/search`: только работы в общественном достоянии
 * (`query[term][is_public_domain]=true`) — у всех есть картинка, и их можно показывать.
 * Ограничение API: не глубже 1000 результатов на одну выдачу (страница × размер ≤ 1000),
 * дальше — 403 «Invalid number of results». Это учитывает [ArtworkPagingSource].
 */
interface ArticApi {
    @GET("artworks/search")
    suspend fun search(
        @Query("q") query: String?,
        @Query("page") page: Int,
        @Query("limit") limit: Int,
        @Query("fields") fields: String = LIST_FIELDS,
        @Query("query[term][is_public_domain]") publicDomain: Boolean = true,
    ): ArtworkPageDto

    @GET("artworks/{id}")
    suspend fun artwork(
        @Path("id") id: Long,
        @Query("fields") fields: String = DETAIL_FIELDS,
    ): ArtworkResponseDto

    companion object {
        const val BASE_URL = "https://api.artic.edu/api/v1/"
        /** Сколько работ на страницу ленты. */
        const val PAGE_SIZE = 30
        /** Потолок API на одну выдачу поиска. */
        const val MAX_RESULTS = 1000

        // Просим только нужные поля — ответ в разы меньше.
        const val LIST_FIELDS = "id,title,artist_title,date_display,image_id,thumbnail,color"
        const val DETAIL_FIELDS = LIST_FIELDS +
            ",artist_display,medium_display,dimensions,place_of_origin,credit_line,description,style_title,department_title"
    }
}
