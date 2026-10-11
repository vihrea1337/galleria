package io.github.vihrea1337.galleria.ui.immersive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vihrea1337.galleria.data.ArtworkRepository
import io.github.vihrea1337.galleria.data.ColorFilter
import io.github.vihrea1337.galleria.data.SearchQuery
import io.github.vihrea1337.galleria.model.Artwork
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

/**
 * «Погружение»: та же выдача, что сейчас в ленте (текст и цвет приходят в маршруте), но без
 * гравюр и предметов — только живопись: на весь экран лучше всего смотрятся картины.
 */
@HiltViewModel
class ImmersiveViewModel @Inject constructor(
    private val repository: ArtworkRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val query = SearchQuery(
        text = savedState.get<String>("text"),
        color = savedState.get<String>("color")?.let { name -> ColorFilter.entries.firstOrNull { it.name == name } },
        type = "Painting",
    )

    val artworks: Flow<PagingData<Artwork>> = repository.artworks(query).cachedIn(viewModelScope)

    fun onArtworkOpen(artwork: Artwork) = repository.remember(artwork)
}
