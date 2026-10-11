package io.github.vihrea1337.galleria.ui

import androidx.paging.PagingData
import io.github.vihrea1337.galleria.data.ArtworkRepository
import io.github.vihrea1337.galleria.data.SearchQuery
import io.github.vihrea1337.galleria.model.Artist
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.model.ArtworkDetails
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

fun artwork(id: Long, title: String = "Artwork $id") = Artwork(
    id = id, title = title, artist = "Artist", date = "1900",
    thumbUrl = "thumb/$id", imageUrl = "image/$id", zoomUrl = "zoom/$id",
    aspectRatio = 1f, placeholder = null, dominantColor = null, altText = null,
)

/** Репозиторий в памяти: запоминает запросы ленты, детали можно «задержать» или уронить. */
class FakeRepository : ArtworkRepository {
    val queries = mutableListOf<SearchQuery>()
    var previews: (SearchQuery) -> List<Artwork> = { emptyList() }
    var suggestionsFor: (String) -> List<String> = { listOf("$it suggestion") }
    var daily: Artwork? = artwork(100, "Daily")
    private val favoritesState = MutableStateFlow<List<Artwork>>(emptyList())
    private val recent = mutableMapOf<Long, Artwork>()
    var detailsResult: (Long) -> ArtworkDetails = { ArtworkDetails(artwork(it), "Artist (1850–1900)", "Oil", null, null, null, null, null, "About") }
    var detailsGate: CompletableDeferred<Unit>? = null

    override fun artworks(query: SearchQuery): Flow<PagingData<Artwork>> {
        queries += query
        return flowOf(PagingData.empty())
    }

    override suspend fun preview(query: SearchQuery, limit: Int): List<Artwork> = previews(query)
    override suspend fun artist(id: Long) = Artist(id, "Artist $id", "1840–1926", "Bio")
    override suspend fun suggestions(text: String): List<String> = suggestionsFor(text)
    override suspend fun artworkOfTheDay(dayOfEpoch: Long): Artwork? = daily

    override suspend fun details(id: Long): ArtworkDetails {
        detailsGate?.await()
        return detailsResult(id)
    }

    override fun favorites(): Flow<List<Artwork>> = favoritesState
    override fun isFavorite(id: Long): Flow<Boolean> = favoritesState.map { list -> list.any { it.id == id } }

    override suspend fun setFavorite(artwork: Artwork, favorite: Boolean) {
        favoritesState.value = if (favorite) listOf(artwork) + favoritesState.value.filter { it.id != artwork.id } else favoritesState.value.filter { it.id != artwork.id }
    }

    override fun remember(artwork: Artwork) {
        recent[artwork.id] = artwork
    }

    override fun remembered(id: Long): Artwork? = recent[id]
}
