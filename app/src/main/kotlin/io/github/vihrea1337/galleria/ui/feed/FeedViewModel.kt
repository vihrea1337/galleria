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
import io.github.vihrea1337.galleria.data.ColorFilter
import io.github.vihrea1337.galleria.data.SearchQuery
import io.github.vihrea1337.galleria.model.Artwork
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch

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
 * Лента: поле поиска + подборка + цвет → [SearchQuery] → PagingData.
 *
 * Поиск не дёргает API на каждую букву:
 * - короче [MIN_SEARCH_LENGTH] букв — не ищем (по «m» музей вернёт всё подряд);
 * - запрос уходит через [SEARCH_DEBOUNCE_MS] после последней буквы — это дольше паузы между
 *   буквами при обычном наборе на телефоне (400 мс было мало: искало «mo», «mon», «mone»…);
 * - «Найти» на клавиатуре — сразу, без ожидания;
 * - стёр всё — сразу назад к подборке.
 * Устаревший запрос отменяет `flatMapLatest`. Поиск, подборка и цвет — в SavedStateHandle
 * (переживают поворот и смерть процесса), лента — `cachedIn(viewModelScope)`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FeedViewModel @Inject constructor(
    private val repository: ArtworkRepository,
    private val savedState: SavedStateHandle,
    private val clock: Clock,
) : ViewModel() {

    val searchText: StateFlow<String> = savedState.getStateFlow(KEY_SEARCH, "")
    val collection: StateFlow<Collection> = savedState.getStateFlow(KEY_COLLECTION, Collection.All)
    val color: StateFlow<ColorFilter?> = savedState.getStateFlow(KEY_COLOR, null)

    /** «Найти» на клавиатуре: текст уходит в запрос без паузы. */
    private val submitted = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Текст, по которому реально ищем («» — не ищем, показываем подборку). */
    private val effectiveText: Flow<String> = merge(
        searchText.map { it.trim() }.distinctUntilChanged().transformLatest { text ->
            when {
                text.isEmpty() -> emit("")
                // Короткий текст ничего не меняет: лента остаётся какой была (и не сбрасывает
                // короткий запрос, отправленный кнопкой «Найти»).
                text.length < MIN_SEARCH_LENGTH -> Unit
                else -> { delay(SEARCH_DEBOUNCE_MS); emit(text) }
            }
        },
        submitted.map { it.trim() },
    ).distinctUntilChanged()

    /** Запрос, который уходит в API. */
    val activeQuery: StateFlow<SearchQuery> = combine(effectiveText, collection, color) { text, collection, color ->
        SearchQuery(text = text.ifEmpty { null } ?: collection.query, color = color)
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, SearchQuery(text = collection.value.query, color = color.value))

    val artworks: Flow<PagingData<Artwork>> = activeQuery
        .flatMapLatest { repository.artworks(it) }
        .cachedIn(viewModelScope)

    /** Подсказки под полем поиска: с 2 букв, через короткую паузу; ошибка сети — просто без подсказок. */
    val suggestions: StateFlow<List<String>> = searchText
        .map { it.trim() }
        .distinctUntilChanged()
        .mapLatest { text ->
            if (text.length < MIN_SUGGEST_LENGTH) return@mapLatest emptyList()
            delay(SUGGEST_DEBOUNCE_MS)
            try {
                repository.suggestions(text).filterNot { it.equals(text, ignoreCase = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _artworkOfTheDay = MutableStateFlow<Artwork?>(null)

    /** Карточка «Картина дня» над лентой (только без поиска и фильтров). */
    val artworkOfTheDay: StateFlow<Artwork?> = _artworkOfTheDay.asStateFlow()

    init {
        loadArtworkOfTheDay()
    }

    fun loadArtworkOfTheDay() {
        if (_artworkOfTheDay.value != null) return
        viewModelScope.launch {
            _artworkOfTheDay.value = try {
                repository.artworkOfTheDay(LocalDate.now(clock).toEpochDay())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null // без карточки дня лента всё равно работает
            }
        }
    }

    fun onSearchChange(text: String) {
        savedState[KEY_SEARCH] = text
    }

    /** «Найти» на клавиатуре или выбор подсказки — искать сразу. */
    fun onSearchSubmit(text: String = searchText.value) {
        savedState[KEY_SEARCH] = text
        submitted.tryEmit(text)
    }

    /** Выбор подборки сбрасывает поиск — иначе чипс выбран, а лента про другое. */
    fun onCollectionSelect(value: Collection) {
        savedState[KEY_SEARCH] = ""
        submitted.tryEmit("")
        savedState[KEY_COLLECTION] = value
    }

    /** Цвет сочетается с подборкой и поиском («Япония» + синий). Повторное нажатие — снять. */
    fun onColorSelect(value: ColorFilter) {
        savedState[KEY_COLOR] = if (color.value == value) null else value
    }

    fun onArtworkOpen(artwork: Artwork) = repository.remember(artwork)

    companion object {
        const val SEARCH_DEBOUNCE_MS = 650L
        const val MIN_SEARCH_LENGTH = 3
        const val SUGGEST_DEBOUNCE_MS = 250L
        const val MIN_SUGGEST_LENGTH = 2
        private const val KEY_SEARCH = "search"
        private const val KEY_COLLECTION = "collection"
        private const val KEY_COLOR = "color"
    }
}
