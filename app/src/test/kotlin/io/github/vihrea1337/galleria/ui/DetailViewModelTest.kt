package io.github.vihrea1337.galleria.ui

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import io.github.vihrea1337.galleria.model.Artwork
import io.github.vihrea1337.galleria.model.ArtworkDetails
import io.github.vihrea1337.galleria.ui.detail.DetailViewModel
import io.github.vihrea1337.galleria.ui.favorites.FavoritesViewModel
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DetailViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val repository = FakeRepository()

    private fun viewModel(id: Long = 7) = DetailViewModel(repository, SavedStateHandle(mapOf("id" to id)))

    @Test
    fun `artwork opened from the feed is shown before details arrive`() = runTest(main.dispatcher) {
        repository.remember(artwork(7, "Water Lilies"))
        repository.detailsGate = CompletableDeferred()
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        runCurrent()

        assertEquals("Water Lilies", vm.state.value.artwork?.title)
        assertTrue(vm.state.value.loading)
        assertNull(vm.state.value.details)

        repository.detailsGate!!.complete(Unit)
        advanceUntilIdle()
        assertFalse(vm.state.value.loading)
        assertEquals("Oil", vm.state.value.details?.medium)
    }

    @Test
    fun `error then retry`() = runTest(main.dispatcher) {
        repository.detailsResult = { throw IOException("offline") }
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        advanceUntilIdle()
        assertTrue(vm.state.value.error is IOException)
        assertFalse(vm.state.value.loading)

        repository.detailsResult = { id -> ArtworkDetails(artwork(id), null, "Ink", null, null, null, null, null, null) }
        vm.load()
        advanceUntilIdle()
        assertNull(vm.state.value.error)
        assertEquals("Ink", vm.state.value.details?.medium)
    }

    @Test
    fun `heart toggles favorite and favorites screen sees it`() = runTest(main.dispatcher) {
        val vm = viewModel()
        val favorites = FavoritesViewModel(repository)
        backgroundScope.launch { vm.state.collect {} }
        backgroundScope.launch { favorites.favorites.collect {} }
        advanceUntilIdle()
        assertFalse(vm.state.value.isFavorite)

        vm.toggleFavorite()
        advanceUntilIdle()
        assertTrue(vm.state.value.isFavorite)
        assertEquals(listOf(7L), favorites.favorites.value?.map { it.id })

        vm.toggleFavorite()
        advanceUntilIdle()
        assertFalse(vm.state.value.isFavorite)
        assertEquals(emptyList<Long>(), favorites.favorites.value?.map { it.id })
    }

    @Test
    fun `removing from favorites can be undone`() = runTest(main.dispatcher) {
        val favorites = FavoritesViewModel(repository)
        backgroundScope.launch { favorites.favorites.collect {} }
        val item = artwork(3)
        repository.setFavorite(item, true)
        advanceUntilIdle()

        // Событие «убрано» — для снекбара с «Отменить».
        favorites.removed.test {
            favorites.remove(item)
            assertEquals(3L, awaitItem().id)
        }
        advanceUntilIdle()
        assertEquals(emptyList<Artwork>(), favorites.favorites.value)

        favorites.undoRemove(item)
        advanceUntilIdle()
        assertNotNull(favorites.favorites.value?.singleOrNull { it.id == 3L })
    }
}
