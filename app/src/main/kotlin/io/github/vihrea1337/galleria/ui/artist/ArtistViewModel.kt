package io.github.vihrea1337.galleria.ui.artist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vihrea1337.galleria.data.ArtworkRepository
import io.github.vihrea1337.galleria.data.SearchQuery
import io.github.vihrea1337.galleria.model.Artist
import io.github.vihrea1337.galleria.model.Artwork
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ArtistUiState(
    val artist: Artist? = null,
    val loading: Boolean = true,
    val error: Throwable? = null,
)

/** Художник: годы жизни и биография (из `/agents`) + все его работы лентой (тот же Paging, фильтр по artist_id). */
@HiltViewModel
class ArtistViewModel @Inject constructor(
    private val repository: ArtworkRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val id: Long = checkNotNull(savedState.get<Long>("id")) { "нет id художника" }

    private val _state = MutableStateFlow(ArtistUiState())
    val state: StateFlow<ArtistUiState> = _state.asStateFlow()

    val works: Flow<PagingData<Artwork>> = repository.artworks(SearchQuery(artistId = id)).cachedIn(viewModelScope)

    init {
        load()
    }

    fun load() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = try {
                ArtistUiState(artist = repository.artist(id), loading = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ArtistUiState(loading = false, error = e)
            }
        }
    }

    fun onArtworkOpen(artwork: Artwork) = repository.remember(artwork)
}
