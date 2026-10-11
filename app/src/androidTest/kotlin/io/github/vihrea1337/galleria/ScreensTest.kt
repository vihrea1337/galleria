package io.github.vihrea1337.galleria

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.vihrea1337.galleria.data.ColorFilter
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.model.ArtworkDetails
import io.github.vihrea1337.galleria.ui.detail.DetailScreen
import io.github.vihrea1337.galleria.ui.detail.DetailUiState
import io.github.vihrea1337.galleria.ui.favorites.FavoritesScreen
import io.github.vihrea1337.galleria.ui.feed.Collection
import io.github.vihrea1337.galleria.ui.feed.FeedScreen
import io.github.vihrea1337.galleria.ui.feed.FeedUiState
import io.github.vihrea1337.galleria.ui.theme.GalleriaTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI-тесты экранов без сети и Hilt: экраны принимают состояние и колбэки, данные — готовые.
 * Картинки в тестах не грузятся (адреса фиктивные) — проверяем текст, состояния и действия.
 */
@RunWith(AndroidJUnit4::class)
class ScreensTest {

    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun str(id: Int) = context.getString(id)

    private fun artwork(id: Long, title: String, artist: String? = "Claude Monet") = Artwork(
        id = id, title = title, artist = artist, date = "1906",
        thumbUrl = "https://example.invalid/$id.jpg", imageUrl = "https://example.invalid/$id-l.jpg",
        zoomUrl = "https://example.invalid/$id-xl.jpg", aspectRatio = 1.2f,
        placeholder = null, dominantColor = 0xFF3A6B80, altText = null,
    )

    private fun feed(
        items: List<Artwork>,
        onOpen: (Artwork) -> Unit = {},
        onSearch: (String) -> Unit = {},
        onSubmit: (String) -> Unit = {},
        onColor: (ColorFilter) -> Unit = {},
        suggestions: List<String> = emptyList(),
        daily: Artwork? = null,
    ) {
        val notLoading = LoadStates(LoadState.NotLoading(true), LoadState.NotLoading(true), LoadState.NotLoading(true))
        compose.setContent {
            GalleriaTheme {
                val paging = flowOf(PagingData.from(items, sourceLoadStates = notLoading)).collectAsLazyPagingItems()
                var search by remember { mutableStateOf("") }
                FeedScreen(
                    state = FeedUiState(search = search, collection = Collection.All, suggestions = suggestions, artworkOfTheDay = daily),
                    onSearchChange = { search = it; onSearch(it) },
                    onSearchSubmit = onSubmit,
                    onCollectionSelect = {},
                    onColorSelect = onColor,
                    items = paging,
                    onOpen = { artwork, _ -> onOpen(artwork) },
                    gridState = rememberLazyStaggeredGridState(),
                    contentPadding = PaddingValues(),
                )
            }
        }
    }

    @Test
    fun feedShowsTilesAndOpensArtwork() {
        var opened: Artwork? = null
        feed(listOf(artwork(1, "Water Lilies"), artwork(2, "")), onOpen = { opened = it })

        compose.onNodeWithText("Water Lilies").assertIsDisplayed()
        // Пустое название из API — «Без названия» на языке устройства.
        compose.onNodeWithText(str(R.string.untitled)).assertIsDisplayed()
        compose.onNodeWithText("Water Lilies").performClick()
        assertEquals(1L, opened?.id)
    }

    @Test
    fun emptyFeedSaysNothingFound() {
        feed(emptyList())
        compose.onNodeWithText(str(R.string.feed_empty_title)).assertIsDisplayed()
    }

    @Test
    fun searchFieldSendsTextAndCanBeCleared() {
        val typed = mutableListOf<String>()
        feed(listOf(artwork(1, "Water Lilies")), onSearch = { typed += it })

        compose.onNodeWithTag("search").performTextInput("monet")
        compose.onNodeWithContentDescription(str(R.string.search_clear)).performClick()
        assertEquals(listOf("monet", ""), typed)
    }

    @Test
    fun suggestionsAppearWhileTypingAndSearchOnTap() {
        val submitted = mutableListOf<String>()
        feed(listOf(artwork(1, "Water Lilies")), onSubmit = { submitted += it }, suggestions = listOf("Claude Monet", "Piet Mondrian"))
        compose.onNodeWithTag("search").performClick()
        compose.onNodeWithText("Claude Monet").assertIsDisplayed().performClick()
        assertEquals(listOf("Claude Monet"), submitted)
    }

    @Test
    fun colorSwatchSelectsColor() {
        val picked = mutableListOf<ColorFilter>()
        feed(listOf(artwork(1, "Water Lilies")), onColor = { picked += it })
        compose.onNodeWithContentDescription(str(R.string.color_blue)).performClick()
        assertEquals(listOf(ColorFilter.Blue), picked)
    }

    @Test
    fun artworkOfTheDayCardOpensTheArtwork() {
        var opened: Artwork? = null
        feed(listOf(artwork(1, "Water Lilies")), onOpen = { opened = it }, daily = artwork(9, "Paris Street; Rainy Day"))
        compose.onNodeWithText(str(R.string.artwork_of_the_day).uppercase()).assertIsDisplayed()
        compose.onNodeWithTag("artwork-of-the-day").performClick()
        assertEquals(9L, opened?.id)
    }

    @Test
    fun detailShowsFactsAndTogglesFavorite() {
        val art = artwork(1, "Water Lilies")
        val details = ArtworkDetails(art, "Claude Monet (French, 1840–1926)", "Oil on canvas", "89.9 × 94.1 cm", "France", "Impressionism", null, null, "A pond in Giverny.")
        var state by mutableStateOf(DetailUiState(artwork = art, details = details, loading = false))
        compose.setContent {
            GalleriaTheme {
                DetailScreen(state, onBack = {}, onToggleFavorite = { state = state.copy(isFavorite = !state.isFavorite) }, onRetry = {}, onShare = {})
            }
        }

        compose.onNodeWithText("Water Lilies").assertIsDisplayed()
        compose.onNodeWithText("Oil on canvas").assertIsDisplayed()
        compose.onNodeWithText("A pond in Giverny.").assertExists()

        compose.onNodeWithContentDescription(str(R.string.favorite_add)).performClick()
        compose.onNodeWithContentDescription(str(R.string.favorite_remove)).assertIsDisplayed()
    }

    @Test
    fun detailErrorOffersRetry() {
        var retried = false
        compose.setContent {
            GalleriaTheme {
                DetailScreen(
                    DetailUiState(artwork = artwork(1, "Water Lilies"), loading = false, error = java.io.IOException("offline")),
                    onBack = {}, onToggleFavorite = {}, onRetry = { retried = true }, onShare = {},
                )
            }
        }
        compose.onNodeWithText(str(R.string.error_offline)).assertIsDisplayed()
        compose.onNodeWithText(str(R.string.retry)).performClick()
        assertEquals(true, retried)
    }

    @Test
    fun favoritesEmptyStateAndRemove() {
        var favorites by mutableStateOf<List<Artwork>>(emptyList())
        compose.setContent {
            GalleriaTheme {
                FavoritesScreen(
                    favorites = favorites,
                    onOpen = {},
                    onRemove = { removed -> favorites = favorites.filter { it.id != removed.id } },
                    gridState = rememberLazyStaggeredGridState(),
                    contentPadding = PaddingValues(),
                )
            }
        }
        compose.onNodeWithTag("favorites-empty").assertIsDisplayed()

        favorites = listOf(artwork(5, "Stacks of Wheat"))
        compose.onNodeWithText("Stacks of Wheat").assertIsDisplayed()
        compose.onNodeWithContentDescription(str(R.string.favorite_remove)).performClick()
        compose.onNodeWithTag("favorites-empty").assertIsDisplayed()
    }
}
