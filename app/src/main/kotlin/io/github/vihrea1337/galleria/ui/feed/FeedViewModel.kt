package io.github.vihrea1337.galleria.ui.feed

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.data.ArtworkRepository
import io.github.vihrea1337.galleria.model.Artwork
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Подборки над лентой: каждая — заранее заданный поисковый запрос к музею (null — без запроса). */
enum class Collection(@StringRes val title: Int, val query: String?) {
    All(R.string.collection_all, null),
    Impressionism(R.string.collection_impressionism, "impressionism"),
    Japan(R.string.collection_japan, "japanese woodblock"),
    Portraits(R.string.collection_portraits, "portrait"),
    Landscapes(R.string.collection_landscapes, "landscape"),
    StillLife(R.string.collection_still_life, "still life"),
    Antiquity(R.string.collection_antiquity, "ancient greek roman"),
}

/**
 * Лента: поле поиска + выбранная подборка → поисковый запрос → PagingData.
 *
 * - Текст поиска уходит в запрос через [SEARCH_DEBOUNCE_MS] после последней буквы; очистка —
 *   сразу (ждать нечего). Старый запрос отменяется `flatMapLatest` — результаты устаревшего
 *   поиска в ленту не попадут.
 * - Поиск и подборка хранятся в SavedStateHandle — переживают поворот и смерть процесса.
 * - `cachedIn(viewModelScope)` — при повороте лента не грузится заново.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: ArtworkRepository,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    val searchText: StateFlow<String> = savedState.getStateFlow(KEY_SEARCH, "")
    val collection: StateFlow<Collection> = savedState.getStateFlow(KEY_COLLECTION, Collection.All)

    /** Запрос, который реально уходит в API: поиск, если он есть, иначе запрос подборки. */
    val activeQuery: StateFlow<String?> = combine(
        searchText.map { it.trim() }.distinctUntilChanged().debounce { if (it.isEmpty()) 0 else SEARCH_DEBOUNCE_MS },
        collection,
    ) { search, collection -> search.ifEmpty { null } ?: collection.query }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, collection.value.query)

    val artworks: Flow<PagingData<Artwork>> = activeQuery
        .flatMapLatest { repository.artworks(it) }
        .cachedIn(viewModelScope)

    fun onSearchChange(text: String) {
        savedState[KEY_SEARCH] = text
    }

    /** Выбор подборки сбрасывает поиск — иначе чипс выбран, а лента про другое. */
    fun onCollectionSelect(value: Collection) {
        savedState[KEY_SEARCH] = ""
        savedState[KEY_COLLECTION] = value
    }

    fun onArtworkOpen(artwork: Artwork) = repository.remember(artwork)

    companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
        private const val KEY_SEARCH = "search"
        private const val KEY_COLLECTION = "collection"
    }
}
