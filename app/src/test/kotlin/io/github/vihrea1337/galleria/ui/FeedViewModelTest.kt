package io.github.vihrea1337.galleria.ui

import androidx.lifecycle.SavedStateHandle
import io.github.vihrea1337.galleria.ui.feed.Collection
import io.github.vihrea1337.galleria.ui.feed.FeedViewModel
import io.github.vihrea1337.galleria.ui.feed.FeedViewModel.Companion.SEARCH_DEBOUNCE_MS
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

    private fun viewModel(state: SavedStateHandle = SavedStateHandle()) = FeedViewModel(repository, state)

    @Test
    fun `starts with the All collection - no query`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.artworks.collect {} }
        advanceUntilIdle()
        assertNull(vm.activeQuery.value)
        assertEquals(listOf<String?>(null), repository.queries)
    }

    @Test
    fun `typing is debounced - only the last text goes to the API`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.artworks.collect {} }
        runCurrent()

        vm.onSearchChange("m")
        advanceTimeBy(100)
        vm.onSearchChange("mon")
        advanceTimeBy(100)
        vm.onSearchChange("monet")
        advanceTimeBy(SEARCH_DEBOUNCE_MS - 1)
        runCurrent()
        assertNull("до паузы запрос не уходит", vm.activeQuery.value)

        advanceTimeBy(2)
        runCurrent()
        assertEquals("monet", vm.activeQuery.value)
        assertEquals(listOf(null, "monet"), repository.queries)
    }

    @Test
    fun `clearing search returns to the collection immediately`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.artworks.collect {} }
        vm.onCollectionSelect(Collection.Japan)
        vm.onSearchChange("hokusai")
        advanceUntilIdle()
        assertEquals("hokusai", vm.activeQuery.value)

        vm.onSearchChange("")
        runCurrent()
        assertEquals(Collection.Japan.query, vm.activeQuery.value)
    }

    @Test
    fun `choosing a collection clears search`() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.onSearchChange("cat")
        vm.onCollectionSelect(Collection.Portraits)
        advanceUntilIdle()
        assertEquals("", vm.searchText.value)
        assertEquals("portrait", vm.activeQuery.value)
    }

    @Test
    fun `same text after trimming is not searched twice`() = runTest(main.dispatcher) {
        val vm = viewModel()
        backgroundScope.launch { vm.artworks.collect {} }
        vm.onSearchChange("monet")
        advanceUntilIdle()
        vm.onSearchChange("monet ")
        advanceUntilIdle()
        assertEquals(listOf(null, "monet"), repository.queries)
    }

    @Test
    fun `search and collection survive process death`() = runTest(main.dispatcher) {
        val state = SavedStateHandle(mapOf("search" to "", "collection" to Collection.Landscapes))
        val vm = viewModel(state)
        advanceUntilIdle()
        assertEquals(Collection.Landscapes, vm.collection.value)
        assertEquals("landscape", vm.activeQuery.value)
    }
}
