package io.github.vihrea1337.galleria.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IiifTest {

    @Test
    fun `factor matches screen density`() {
        assertEquals(1, tileFactor(1f)) // пиксель в пиксель — оригинал
        assertEquals(1, tileFactor(3f)) // сильнее оригинала — всё равно уровень 1
        assertEquals(2, tileFactor(0.5f))
        assertEquals(2, tileFactor(0.4f)) // 2.5 → 2: тайл не мельче экрана
        assertEquals(8, tileFactor(0.12f))
        assertEquals(128, tileFactor(0.0001f)) // глубже уровней у музея нет
    }

    @Test
    fun `only visible tiles, clipped at image edges`() {
        // Оригинал 1000×600, уровень 1 (шаг 256): видна область 300..700 × 0..300.
        val tiles = visibleTiles(1000, 600, FullRect(300f, 0f, 700f, 300f), factor = 1)
        assertEquals(listOf(256, 512), tiles.map { it.x }.distinct())
        assertEquals(listOf(0, 256), tiles.map { it.y }.distinct())
        assertEquals(4, tiles.size)
        // Правый нижний край картины: тайл короче.
        val edge = visibleTiles(1000, 600, FullRect(900f, 550f, 2000f, 2000f), factor = 1).single()
        assertEquals(Tile(768, 512, 232, 88, 1), edge)
    }

    @Test
    fun `view outside the image gives nothing`() {
        assertTrue(visibleTiles(1000, 600, FullRect(-500f, -500f, -1f, -1f), 1).isEmpty())
    }

    @Test
    fun `higher factor - bigger regions downscaled to 256 px`() {
        val tile = visibleTiles(8808, 8460, FullRect(0f, 0f, 10f, 10f), factor = 4).single()
        assertEquals(Tile(0, 0, 1024, 1024, 4), tile)
        assertEquals(
            "https://www.artic.edu/iiif/2/abc/0,0,1024,1024/256,/0/default.jpg",
            tileUrl("abc", tile),
        )
    }
}
