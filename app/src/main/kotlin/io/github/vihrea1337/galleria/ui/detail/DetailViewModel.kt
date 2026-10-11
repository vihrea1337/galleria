package io.github.vihrea1337.galleria.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vihrea1337.galleria.data.ArtworkRepository
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.model.ArtworkDetails
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    /** Есть сразу, если картину открыли из ленты/избранного; иначе — после загрузки. */
    val artwork: Artwork? = null,
    val details: ArtworkDetails? = null,
    val loading: Boolean = true,
    val error: Throwable? = null,
    val isFavorite: Boolean = false,
)

/**
 * Экран картины. Картинку и название показывает сразу (из ленты — [ArtworkRepository.remembered]),
 * подробности (описание, техника, размеры) догружает из API. Избранное — живой поток из Room:
 * ♥ на этом экране и вкладка «Избранное» всегда согласованы.
 */
@HiltViewModel
class DetailViewModel @Inject constructor(
    private val repository: ArtworkRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    /** Аргумент маршрута `Detail(id)` — Navigation кладёт его в SavedStateHandle по имени поля. */
    private val id: Long = checkNotNull(savedState.get<Long>("id")) { "нет id картины" }

    private val loaded = MutableStateFlow(DetailUiState(artwork = repository.remembered(id)))

    val state: StateFlow<DetailUiState> = combine(loaded, repository.isFavorite(id)) { s, fav -> s.copy(isFavorite = fav) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), loaded.value)

    init {
        load()
    }

    fun load() {
        loaded.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val details = repository.details(id)
                loaded.update { it.copy(artwork = details.artwork, details = details, loading = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loaded.update { it.copy(loading = false, error = e) }
            }
        }
    }

    fun toggleFavorite() {
        val current = state.value
        val artwork = current.artwork ?: return
        viewModelScope.launch { repository.setFavorite(artwork, !current.isFavorite) }
    }
}
