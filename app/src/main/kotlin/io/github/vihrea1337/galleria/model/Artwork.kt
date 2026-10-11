package io.github.vihrea1337.galleria.model

/** Картина для экранов — уже без особенностей API: готовые адреса, цвет, пропорции. */
data class Artwork(
    val id: Long,
    val title: String,
    val artist: String?,
    val date: String?,
    /** Картинка для плитки ленты (ширина ~400 px). */
    val thumbUrl: String,
    /** Картинка для экрана деталей (ширина 843 px — рекомендованный музеем размер). */
    val imageUrl: String,
    /** Ширина / высота: плитка ленты сразу нужной высоты, без прыжков при загрузке. */
    val aspectRatio: Float,
    /** Размытое превью (PNG/GIF-байты) — пока грузится картинка. */
    val placeholder: ByteArray?,
    /** Преобладающий цвет картины, ARGB. */
    val dominantColor: Long?,
    val altText: String?,
) {
    // ByteArray сравнивается по ссылке — для data class переопределяем по id.
    override fun equals(other: Any?) = other is Artwork && other.id == id && other.title == title
    override fun hashCode() = id.hashCode()
}

/** Подробности для экрана картины. */
data class ArtworkDetails(
    val artwork: Artwork,
    val artistFull: String?,
    val medium: String?,
    val dimensions: String?,
    val origin: String?,
    val style: String?,
    val department: String?,
    val credit: String?,
    /** Описание музея — уже обычный текст (из HTML). */
    val description: String?,
)
