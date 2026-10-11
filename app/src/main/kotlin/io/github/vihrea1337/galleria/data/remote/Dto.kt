package io.github.vihrea1337.galleria.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Ответы API ровно в той форме, в какой они приходят (snake_case — через @SerialName).
// В UI идут не они, а модель Artwork (ArtworkMapper): там уже готовые адреса картинок,
// цвет и пропорции.

@Serializable
data class ArtworkPageDto(
    val pagination: PaginationDto,
    val data: List<ArtworkDto>,
)

@Serializable
data class PaginationDto(
    val total: Int,
    val limit: Int,
    @SerialName("current_page") val currentPage: Int = 1,
)

@Serializable
data class ArtworkResponseDto(val data: ArtworkDto)

@Serializable
data class ArtworkDto(
    val id: Long,
    val title: String? = null,
    @SerialName("artist_title") val artistTitle: String? = null,
    @SerialName("artist_display") val artistDisplay: String? = null,
    @SerialName("date_display") val dateDisplay: String? = null,
    @SerialName("image_id") val imageId: String? = null,
    val thumbnail: ThumbnailDto? = null,
    val color: ColorDto? = null,
    @SerialName("medium_display") val mediumDisplay: String? = null,
    val dimensions: String? = null,
    @SerialName("place_of_origin") val placeOfOrigin: String? = null,
    @SerialName("credit_line") val creditLine: String? = null,
    /** HTML от музея (абзацы, курсив, ссылки). */
    val description: String? = null,
    @SerialName("style_title") val styleTitle: String? = null,
    @SerialName("department_title") val departmentTitle: String? = null,
    @SerialName("artist_id") val artistId: Long? = null,
    @SerialName("artwork_type_title") val artworkType: String? = null,
    @SerialName("is_on_view") val isOnView: Boolean? = null,
    @SerialName("gallery_title") val galleryTitle: String? = null,
)

@Serializable
data class ArtistResponseDto(val data: ArtistDto)

@Serializable
data class ArtistDto(
    val id: Long,
    val title: String? = null,
    @SerialName("birth_date") val birthDate: Int? = null,
    @SerialName("death_date") val deathDate: Int? = null,
    /** HTML. */
    val description: String? = null,
)

@Serializable
data class ThumbnailDto(
    /** Крошечное размытое превью: `data:image/gif;base64,…` — показываем, пока грузится картинка. */
    val lqip: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    @SerialName("alt_text") val altText: String? = null,
)

/** Преобладающий цвет картины в HSL: h 0–360, s и l — проценты 0–100. */
@Serializable
data class ColorDto(val h: Int, val s: Int, val l: Int)
