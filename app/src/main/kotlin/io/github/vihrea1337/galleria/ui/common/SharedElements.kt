package io.github.vihrea1337.galleria.ui.common

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier

/*
 * Shared element «плитка → экран картины». Обоим экранам нужны две области видимости:
 * SharedTransitionScope (вокруг NavHost) и AnimatedVisibilityScope (анимация конкретного
 * пункта навигации). Чтобы не тащить их параметрами через все composable, они раздаются
 * через CompositionLocal. Нет областей (превью, UI-тесты) — модификатор ничего не делает.
 */

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalNavAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Картинка картины `id` — один и тот же ключ в ленте, избранном и на экране картины. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtworkImage(id: Long): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(transition) {
        this@sharedArtworkImage.sharedElement(
            sharedContentState = rememberSharedContentState(key = "image-$id"),
            animatedVisibilityScope = visibility,
        )
    }
}
