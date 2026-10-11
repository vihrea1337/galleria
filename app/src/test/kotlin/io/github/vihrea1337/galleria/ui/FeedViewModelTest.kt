package io.github.vihrea1337.galleria.ui

import androidx.lifecycle.SavedStateHandle
import io.github.vihrea1337.galleria.data.ColorFilter
import io.github.vihrea1337.galleria.ui.feed.Collection
import io.github.vihrea1337.galleria.ui.feed.FeedViewModel
import io.github.vihrea1337.galleria.ui.feed.FeedViewModel.Companion.SEARCH_DEBOUNCE_MS
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FeedViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRepository()
    private val clock = Clock.fixed(Instant.parse("2026-10-11T10:00:00Z"), ZoneOffset.UTC)

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) = FeedViewModel(repository, state, clock)

    /** Какие тексты поиска ушли в API (без фильтров). */
    private fun searchedTexts() = repository.queries.map { it.text }

    @Test
    fun `starts with the All collection - no query`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.artworks.collect {} }
        advanceUntilIdle()
        assertNull(vm.activeQuery.value.text)
        assertEquals(listOf<String?>(null), searchedTexts())
    }

    @Test
    fun `slow typing on a phone does not search after every letter`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.artworks.collect {} }
        runCurrent()

        // Как человек на телефоне: ~0,5 с между буквами (раньше пауза 400 мс срабатывала на каждой).
        for (text in listOf("m", "mo", "mon", "mone", "monet")) {
            vm.onSearchChange(text)
            advanceTimeBy(500)
        }
        assertEquals("пока печатают — ни одного запроса", listOf<String?>(null), searchedTexts())

        advanceTimeBy(SEARCH_DEBOUNCE_MS)
        runCurrent()
        assertEquals(listOf(null, "monet"), searchedTexts())
    }

    @Test
    fun `fewer than 3 letters never search`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.artworks.collect {} }
        vm.onSearchChange("mo")
        advanceUntilIdle()
        assertEquals(listOf<String?>(null), searchedTexts())
    }

    @Test
    fun `search button searches at once - even short text`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.artworks.collect {} }
        runCurrent()
        vm.onSearchChange("ox")
        vm.onSearchSubmit()
        runCurrent()
        assertEquals("ox", vm.activeQuery.value.text)
        // И пауза после этого не сбрасывает отправленный запрос.
        advanceUntilIdle()
        assertEquals(listOf(null, "ox"), searchedTexts())
    }

    @Test
    fun `clearing search returns to the collection immediately`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.artworks.collect {} }
        vm.onCollectionSelect(Collection.Japan)
        vm.onSearchChange("hokusai")
        advanceUntilIdle()
        assertEquals("hokusai", vm.activeQuery.value.text)

        vm.onSearchChange("")
        runCurrent()
        assertEquals(Collection.Japan.query, vm.activeQuery.value.text)
    }

    @Test
    fun `choosing a collection clears search`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onSearchChange("cat")
        vm.onCollectionSelect(Collection.Portraits)
        advanceUntilIdle()
        assertEquals("", vm.searchText.value)
        assertEquals("portrait", vm.activeQuery.value.text)
    }

    @Test
    fun `color combines with collection and toggles off`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onCollectionSelect(Collection.Japan)
        vm.onColorSelect(ColorFilter.Blue)
        advanceUntilIdle()
        assertEquals(Collection.Japan.query, vm.activeQuery.value.text)
        assertEquals(ColorFilter.Blue, vm.activeQuery.value.color)

        vm.onColorSelect(ColorFilter.Blue)
        advanceUntilIdle()
        assertNull(vm.activeQuery.value.color)
    }

    @Test
    fun `same text after trimming is not searched twice`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.artworks.collect {} }
        vm.onSearchChange("monet")
        advanceUntilIdle()
        vm.onSearchChange("monet ")
        advanceUntilIdle()
        assertEquals(listOf(null, "monet"), searchedTexts())
    }

    @Test
    fun `suggestions from 2 letters, without the typed text itself`() = runTest(main.dispatcher) {
        repository.suggestionsFor = { listOf("Claude Monet", "mon", "Piet Mondrian") }
        val vm = viewModel()
        backgroundScope.launch { vm.suggestions.collect {} }
        vm.onSearchChange("m")
        advanceUntilIdle()
        assertEquals(emptyList<String>(), vm.suggestions.value)
        vm.onSearchChange("mon")
        advanceUntilIdle()
        assertEquals(listOf("Claude Monet", "Piet Mondrian"), vm.suggestions.value)
    }

    @Test
    fun `artwork of the day loads, failure is just no card`() = runTest(main.dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(100L, vm.artworkOfTheDay.value?.id)
    }

    @Test
    fun `search, collection and color survive process death`() = runTest(main.dispatcher) {
        val state = SavedStateHandle(mapOf("search" to "", "collection" to Collection.Landscapes, "color" to ColorFilter.Green))
        val vm = viewModel(state)
        advanceUntilIdle()
        assertEquals(Collection.Landscapes, vm.collection.value)
        assertEquals("landscape", vm.activeQuery.value.text)
        assertEquals(ColorFilter.Green, vm.activeQuery.value.color)
    }
}
