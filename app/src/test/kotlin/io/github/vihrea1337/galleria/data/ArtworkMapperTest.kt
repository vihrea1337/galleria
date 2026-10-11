package io.github.vihrea1337.galleria.data

import io.github.vihrea1337.galleria.data.remote.ArtworkDto
import io.github.vihrea1337.galleria.data.remote.ArtworkPageDto
import io.github.vihrea1337.galleria.data.remote.ArtworkResponseDto
import io.github.vihrea1337.galleria.data.remote.ColorDto
import io.github.vihrea1337.galleria.data.remote.ThumbnailDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkMapperTest {

    private val json = Json { ignoreUnknownKeys = true }
    private fun resource(name: String) = checkNotNull(javaClass.classLoader?.getResource(name)).readText()

    @Test
    fun `iiif url has width and default format`() {
        assertEquals(
            "https://www.artic.edu/iiif/2/abc/full/400,/0/default.jpg",
            iiifUrl("abc", 400),
        )
    }

    @Test
    fun `artwork without image is skipped`() {
        assertNull(ArtworkDto(id = 1, title = "No picture").toArtwork())
    }

    @Test
    fun `blank title becomes empty so UI shows localized Untitled`() {
        assertEquals("", ArtworkDto(id = 1, title = "  ", imageId = "x").toArtwork()!!.title)
        assertEquals("", ArtworkDto(id = 1, imageId = "x").toArtwork()!!.title)
    }

    @Test
    fun `aspect ratio comes from thumbnail and is clamped`() {
        fun ratio(w: Int?, h: Int?) = ArtworkDto(id = 1, imageId = "x", thumbnail = ThumbnailDto(width = w, height = h)).toArtwork()!!.aspectRatio
        assertEquals(1.5f, ratio(300, 200), 0.001f)
        assertEquals(0.45f, ratio(100, 1000), 0.001f) // свиток
        assertEquals(2.2f, ratio(5000, 100), 0.001f) // панорама
        assertEquals(0.8f, ratio(null, 100), 0.001f) // нет размеров — по умолчанию
        assertEquals(0.8f, ratio(0, 0), 0.001f)
    }

    @Test
    fun `hsl to argb matches reference colors`() {
        assertEquals(0xFFFF0000, hslToArgb(ColorDto(0, 100, 50))) // красный
        assertEquals(0xFF00FF00, hslToArgb(ColorDto(120, 100, 50))) // зелёный
        assertEquals(0xFF0000FF, hslToArgb(ColorDto(240, 100, 50))) // синий
        assertEquals(0xFFFFFFFF, hslToArgb(ColorDto(0, 0, 100)))
        assertEquals(0xFF000000, hslToArgb(ColorDto(0, 0, 0)))
        assertEquals(0xFF808080, hslToArgb(ColorDto(0, 0, 50)))
        // Оттенок по кругу: 360 = 0, -120 = 240.
        assertEquals(hslToArgb(ColorDto(0, 100, 50)), hslToArgb(ColorDto(360, 100, 50)))
        assertEquals(hslToArgb(ColorDto(240, 100, 50)), hslToArgb(ColorDto(-120, 100, 50)))
    }

    @Test
    fun `lqip data uri decodes to gif bytes`() {
        val bytes = decodeDataUri("data:image/gif;base64,R0lGODlhAQABAAAAACw=")
        assertNotNull(bytes)
        assertArrayEquals("GIF89a".toByteArray(), bytes!!.copyOfRange(0, 6))
    }

    @Test
    fun `broken or foreign uris give null`() {
        assertNull(decodeDataUri("https://example.com/a.gif"))
        assertNull(decodeDataUri("data:image/gif,plain"))
        assertNull(decodeDataUri("data:image/gif;base64,%%%не base64%%%"))
        assertNull(decodeDataUri("data:image/gif;base64"))
    }

    @Test
    fun `html description becomes plain paragraphs`() {
        val html = "<p>Monet&rsquo;s <em>water</em> garden &amp; pond.</p>\n<p>Second&nbsp;line<br/>wrapped.</p>"
        assertEquals("Monet’s water garden & pond.\n\nSecond line\nwrapped.", htmlToText(html))
    }

    @Test
    fun `real search response parses and maps`() {
        val page = json.decodeFromString<ArtworkPageDto>(resource("search-monet.json"))
        assertEquals(3, page.data.size)
        assertTrue(page.pagination.total > 1000)
        val first = page.data.first().toArtwork()!!
        assertEquals(16568L, first.id)
        assertEquals("Water Lilies", first.title)
        assertEquals("Claude Monet", first.artist)
        assertTrue(first.thumbUrl.endsWith("/full/400,/0/default.jpg"))
        assertTrue(first.zoomUrl.endsWith("/full/1686,/0/default.jpg"))
        assertNotNull(first.placeholder)
        assertNotNull(first.dominantColor)
    }

    @Test
    fun `real detail response maps facts and description`() {
        val details = json.decodeFromString<ArtworkResponseDto>(resource("artwork-detail.json")).data.toDetails()!!
        assertEquals("Oil on canvas", details.medium)
        assertEquals("Impressionism", details.style)
        assertTrue(details.artistFull!!.startsWith("Claude Monet"))
        val description = details.description!!
        assertTrue(description.startsWith("Little did Claude Monet know"))
        assertTrue("<" !in description)
    }
}
