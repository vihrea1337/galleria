package io.github.vihrea1337.galleria.data

import io.github.vihrea1337.galleria.data.remote.ArtistDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    private fun params(q: SearchQuery, page: Int = 2, limit: Int = 30): JsonObject =
        Json.parseToJsonElement(q.toParams(page, limit, "id,title")).jsonObject

    private fun filters(json: JsonObject) = json.getValue("query").jsonObject.getValue("bool").jsonObject.getValue("filter").jsonArray

    @Test
    fun `empty query is public domain only`() {
        val json = params(SearchQuery())
        assertNull(json["q"])
        assertEquals(2, json.getValue("page").jsonPrimitive.int)
        assertEquals(30, json.getValue("limit").jsonPrimitive.int)
        assertEquals(listOf("id", "title"), json.getValue("fields").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("""[{"term":{"is_public_domain":true}}]""", filters(json).toString())
    }

    @Test
    fun `text is trimmed, blank text is no text`() {
        assertEquals("monet", params(SearchQuery(text = "  monet ")).getValue("q").jsonPrimitive.content)
        assertNull(params(SearchQuery(text = "   "))["q"])
    }

    @Test
    fun `artist type and exclusion become filters`() {
        val json = params(SearchQuery(artistId = 35809, type = "Painting", excludeId = 16568))
        val f = filters(json).toString()
        assertTrue(f.contains(""""artist_id":35809"""))
        assertTrue(f.contains(""""artwork_type_title.keyword":"Painting""""))
        val mustNot = json.getValue("query").jsonObject.getValue("bool").jsonObject.getValue("must_not").toString()
        assertEquals("""[{"term":{"id":16568}}]""", mustNot)
    }

    @Test
    fun `red wraps around zero degrees as two hue ranges`() {
        val f = filters(params(SearchQuery(color = ColorFilter.Red))).toString()
        assertTrue(f.contains(""""color.h":{"gte":345,"lte":360}"""))
        assertTrue(f.contains(""""color.h":{"gte":0,"lte":12}"""))
        assertTrue(f.contains(""""minimum_should_match":1"""))
        assertTrue(f.contains(""""color.s":{"gte":35}"""))
    }

    @Test
    fun `isEmpty ignores only blank text`() {
        assertTrue(SearchQuery(text = " ").isEmpty)
        assertFalse(SearchQuery(color = ColorFilter.Blue).isEmpty)
        assertFalse(SearchQuery(text = "cat").isEmpty)
    }

    @Test
    fun `nearest color filter for a painting`() {
        assertEquals(ColorFilter.Blue, ColorFilter.nearest(h = 210, s = 50, l = 40))
        assertEquals(ColorFilter.Red, ColorFilter.nearest(h = 355, s = 60, l = 40))
        assertEquals(ColorFilter.Red, ColorFilter.nearest(h = 5, s = 60, l = 40))
        assertEquals(ColorFilter.Dark, ColorFilter.nearest(h = 200, s = 60, l = 10))
        assertNull("почти серое — похожих по цвету не ищем", ColorFilter.nearest(h = 30, s = 8, l = 50))
    }

    @Test
    fun `artist years and bio`() {
        val monet = ArtistDto(id = 1, title = "Claude Monet", birthDate = 1840, deathDate = 1926, description = "<p>Painter &amp; pioneer.</p>").toArtist()
        assertEquals("1840–1926", monet.years)
        assertEquals("Painter & pioneer.", monet.bio)
        assertEquals("1952–", ArtistDto(id = 2, birthDate = 1952).toArtist().years)
        assertNull(ArtistDto(id = 3).toArtist().years)
        assertNull(ArtistDto(id = 3, description = "  ").toArtist().bio)
    }
}
