package io.github.vihrea1337.galleria.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vihrea1337.galleria.data.ArtworkRepository
import io.github.vihrea1337.galleria.model.Artwork
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Избранное из Room — работает без сети. Убрать можно с «Отменить» (событие для снекбара). */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val repository: ArtworkRepository,
) : ViewModel() {

    /** null — ещё читаем базу (чтобы не мигнуть пустым состоянием). */
    val favorites: StateFlow<List<Artwork>?> = repository.favorites()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val removedEvents = Channel<Artwork>(Channel.BUFFERED)

    /** Картина, которую только что убрали: экран показывает «Убрано · Отменить». */
    val removed: Flow<Artwork> = removedEvents.receiveAsFlow()

    fun remove(artwork: Artwork) {
        viewModelScope.launch {
            repository.setFavorite(artwork, false)
            removedEvents.send(artwork)
        }
    }

    fun undoRemove(artwork: Artwork) {
        viewModelScope.launch { repository.setFavorite(artwork, true) }
    }

    fun onArtworkOpen(artwork: Artwork) = repository.remember(artwork)
}
