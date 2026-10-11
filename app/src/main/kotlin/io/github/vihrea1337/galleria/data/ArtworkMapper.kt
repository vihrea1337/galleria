package io.github.vihrea1337.galleria.data

import io.github.vihrea1337.galleria.data.remote.ArtistDto
import io.github.vihrea1337.galleria.data.remote.ArtworkDto
import io.github.vihrea1337.galleria.data.remote.ColorDto
import io.github.vihrea1337.galleria.model.Artist
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.model.ArtworkDetails
import io.github.vihrea1337.galleria.model.Hsl
import java.util.Base64
import kotlin.math.abs
import kotlin.math.roundToInt

// Перевод ответов API в модель — чистые функции без Android: тестируются обычным JUnit.

/** Адреса картинок — стандарт IIIF: /iiif/2/{id}/full/{ширина},/0/default.jpg. */
fun iiifUrl(imageId: String, width: Int): String = "$IIIF_BASE/$imageId/full/$width,/0/default.jpg"

private const val IIIF_BASE = "https://www.artic.edu/iiif/2"
private const val THUMB_WIDTH = 400
private const val FULL_WIDTH = 843
private const val ZOOM_WIDTH = 1686
private const val DEFAULT_ASPECT = 0.8f

/** null — работа без картинки (в ленте их не показываем). */
fun ArtworkDto.toArtwork(): Artwork? {
    val image = imageId ?: return null
    val w = thumbnail?.width
    val h = thumbnail?.height
    return Artwork(
        id = id,
        // Пусто — «Без названия» подставит UI на языке устройства.
        title = title?.trim().orEmpty(),
        artist = artistTitle,
        date = dateDisplay,
        thumbUrl = iiifUrl(image, THUMB_WIDTH),
        imageUrl = iiifUrl(image, FULL_WIDTH),
        zoomUrl = iiifUrl(image, ZOOM_WIDTH),
        aspectRatio = if (w != null && h != null && w > 0 && h > 0) {
            // Слишком вытянутые (свитки, панорамы) ограничиваем — иначе плитка в ленте нелепая.
            (w.toFloat() / h).coerceIn(MIN_ASPECT, MAX_ASPECT)
        } else {
            DEFAULT_ASPECT
        },
        placeholder = thumbnail?.lqip?.let(::decodeDataUri),
        dominantColor = color?.let(::hslToArgb),
        altText = thumbnail?.altText,
        imageId = image,
        fullWidth = w?.takeIf { it > 0 },
        fullHeight = h?.takeIf { it > 0 },
    )
}

private const val MIN_ASPECT = 0.45f
private const val MAX_ASPECT = 2.2f

fun ArtworkDto.toDetails(): ArtworkDetails? {
    val artwork = toArtwork() ?: return null
    return ArtworkDetails(
        artwork = artwork,
        artistId = artistId,
        color = color?.let { Hsl(it.h, it.s, it.l) },
        type = artworkType,
        onViewIn = galleryTitle?.takeIf { isOnView == true },
        artistFull = artistDisplay,
        medium = mediumDisplay,
        dimensions = dimensions,
        origin = placeOfOrigin,
        style = styleTitle,
        department = departmentTitle,
        credit = creditLine,
        description = description?.let(::htmlToText)?.takeIf { it.isNotBlank() },
    )
}

fun ArtistDto.toArtist() = Artist(
    id = id,
    name = title?.trim().orEmpty(),
    years = when {
        birthDate != null && deathDate != null -> "$birthDate–$deathDate"
        birthDate != null -> "$birthDate–"
        deathDate != null -> "–$deathDate"
        else -> null
    },
    bio = description?.let(::htmlToText)?.takeIf { it.isNotBlank() },
)

/** `data:image/gif;base64,AAAA` → байты картинки; что-то другое или битое — null. */
fun decodeDataUri(uri: String): ByteArray? {
    val comma = uri.indexOf(',')
    if (!uri.startsWith("data:") || comma < 0 || !uri.substring(0, comma).endsWith(";base64")) return null
    return runCatching { Base64.getDecoder().decode(uri.substring(comma + 1)) }.getOrNull()
}

/**
 * HSL (h 0–360, s/l 0–100) → ARGB (непрозрачный). Формула из CSS Color 4,
 * без android.graphics — чтобы проверять обычным JVM-тестом.
 */
@Suppress("MagicNumber")
fun hslToArgb(c: ColorDto): Long {
    val s = c.s.coerceIn(0, 100) / 100f
    val l = c.l.coerceIn(0, 100) / 100f
    val chroma = (1 - abs(2 * l - 1)) * s
    val hPrime = ((c.h % 360 + 360) % 360) / 60f
    val x = chroma * (1 - abs(hPrime % 2 - 1))
    val (r1, g1, b1) = when {
        hPrime < 1 -> Triple(chroma, x, 0f)
        hPrime < 2 -> Triple(x, chroma, 0f)
        hPrime < 3 -> Triple(0f, chroma, x)
        hPrime < 4 -> Triple(0f, x, chroma)
        hPrime < 5 -> Triple(x, 0f, chroma)
        else -> Triple(chroma, 0f, x)
    }
    val m = l - chroma / 2
    fun channel(v: Float) = ((v + m) * 255).roundToInt().coerceIn(0, 255).toLong()
    return (0xFFL shl 24) or (channel(r1) shl 16) or (channel(g1) shl 8) or channel(b1)
}

/** Описание музея приходит HTML: абзацы → пустые строки, теги прочь, сущности — в символы. */
fun htmlToText(html: String): String =
    html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>\\s*"), "\n\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&rsquo;", "’")
        .replace("&lsquo;", "‘")
        .replace("&ldquo;", "“")
        .replace("&rdquo;", "”")
        .replace("&mdash;", "—")
        .replace("&ndash;", "–")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .lines().joinToString("\n") { it.trim() }
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()
