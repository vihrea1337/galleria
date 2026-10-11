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

/**
 * Картинка картины `id`. Ключ — `origin-id`: одна и та же картина может быть на экране дважды
 * (карточка «Картина дня» и плитка ленты), и экран картины должен «прилететь» из той, по которой
 * нажали. Поэтому маршрут Detail несёт origin.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedArtworkImage(id: Long, origin: String = ORIGIN_TILE): Modifier {
    val transition = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalNavAnimatedVisibilityScope.current ?: return this
    return with(transition) {
        this@sharedArtworkImage.sharedElement(
            sharedContentState = rememberSharedContentState(key = "$origin-$id"),
            animatedVisibilityScope = visibility,
        )
    }
}

/** Откуда открыли картину — часть ключа shared element. */
const val ORIGIN_TILE = "image"
const val ORIGIN_DAILY = "daily"
const val ORIGIN_ARTIST_ROW = "artist-row"
const val ORIGIN_COLOR_ROW = "color-row"
const val ORIGIN_IMMERSIVE = "immersive"
