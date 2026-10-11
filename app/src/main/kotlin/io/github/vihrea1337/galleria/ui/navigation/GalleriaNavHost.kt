package io.github.vihrea1337.galleria.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import io.github.vihrea1337.galleria.R
import io.github.vihrea1337.galleria.ui.common.LocalNavAnimatedVisibilityScope
import io.github.vihrea1337.galleria.ui.common.LocalSharedTransitionScope
import io.github.vihrea1337.galleria.ui.common.ORIGIN_TILE
import io.github.vihrea1337.galleria.ui.artist.ArtistScreenRoute
import io.github.vihrea1337.galleria.ui.detail.DetailRoute
import io.github.vihrea1337.galleria.ui.favorites.FavoritesRoute
import io.github.vihrea1337.galleria.ui.feed.FeedRoute
import kotlinx.serialization.Serializable

/** Главный экран с вкладками. */
@Serializable
data object Home

/**
 * Экран картины. Имя поля `id` читает DetailViewModel из SavedStateHandle; `origin` — откуда
 * открыли (часть ключа shared element, см. ui/common/SharedElements.kt).
 */
@Serializable
data class Detail(val id: Long, val origin: String = ORIGIN_TILE)

/** Экран художника: биография и все его работы. `id` читает ArtistViewModel. */
@Serializable
data class ArtistRoute(val id: Long, val name: String)

enum class Tab(@StringRes val label: Int, val icon: ImageVector, val selectedIcon: ImageVector) {
    Gallery(R.string.tab_gallery, Icons.Outlined.PhotoLibrary, Icons.Filled.PhotoLibrary),
    Favorites(R.string.tab_favorites, Icons.Outlined.FavoriteBorder, Icons.Filled.Favorite),
}

/**
 * Два уровня: Home (вкладки «Галерея · Избранное») и Detail (картина) — во весь экран, без
 * панели вкладок. Вкладки — не отдельные пункты навигации, а состояние Home: так обе ленты
 * живут в одной области анимации и shared element «плитка → картина» работает из любой.
 *
 * SharedTransitionLayout вокруг NavHost — общая сцена перехода; области видимости раздаются
 * экранам через CompositionLocal (ui/common/SharedElements.kt).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleriaNavHost() {
    val navController = rememberNavController()
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            NavHost(navController, startDestination = Home) {
                composable<Home> {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        HomeScreen(onOpen = { id, origin -> navController.navigate(Detail(id, origin)) })
                    }
                }
                composable<Detail> { entry ->
                    val route = entry.toRoute<Detail>()
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        DetailRoute(
                            origin = route.origin,
                            onBack = { navController.popBackStack() },
                            onOpen = { id, origin -> navController.navigate(Detail(id, origin)) },
                            onArtist = { id, name -> navController.navigate(ArtistRoute(id, name)) },
                        )
                    }
                }
                composable<ArtistRoute> { entry ->
                    val route = entry.toRoute<ArtistRoute>()
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this) {
                        ArtistScreenRoute(
                            name = route.name,
                            onBack = { navController.popBackStack() },
                            onOpen = { id, origin -> navController.navigate(Detail(id, origin)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(onOpen: (Long, String) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(Tab.Gallery) }
    // Позиции лент — здесь, а не во вкладках: переключился туда-обратно — лента на месте.
    val feedGrid = rememberLazyStaggeredGridState()
    val favoritesGrid = rememberLazyStaggeredGridState()

    // «Назад» из «Избранного» — сначала в «Галерею», и только потом выход.
    BackHandler(enabled = tab != Tab.Gallery) { tab = Tab.Gallery }

    // Телефон — нижняя панель, планшет и альбомная — боковая (rail). Правило по умолчанию
    // оставляет нижнюю панель на телефоне в альбомной (там мала высота) — а высоты как раз
    // и не хватает, поэтому решаем по ширине окна: от 600 dp — rail. Под rail контент
    // доходит до низа экрана, и отступ от системной навигации ему нужен свой.
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val layout = if (wide) NavigationSuiteType.NavigationRail else NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfo())
    val bottomInset = if (layout == NavigationSuiteType.NavigationBar) PaddingValues() else WindowInsets.navigationBars.asPaddingValues()

    NavigationSuiteScaffold(
        layoutType = layout,
        containerColor = MaterialTheme.colorScheme.background,
        navigationSuiteItems = {
            Tab.entries.forEach { t ->
                item(
                    selected = t == tab,
                    onClick = { tab = t },
                    icon = { Icon(if (t == tab) t.selectedIcon else t.icon, contentDescription = null) },
                    label = { Text(stringResource(t.label)) },
                )
            }
        },
    ) {
        when (tab) {
            Tab.Gallery -> FeedRoute(onOpen = { artwork, origin -> onOpen(artwork.id, origin) }, contentPadding = bottomInset, gridState = feedGrid)
            Tab.Favorites -> FavoritesRoute(onOpen = { onOpen(it.id, ORIGIN_TILE) }, contentPadding = bottomInset, gridState = favoritesGrid)
        }
    }
}
