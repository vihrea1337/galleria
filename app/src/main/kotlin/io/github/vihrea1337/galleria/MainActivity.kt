package io.github.vihrea1337.galleria

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import io.github.vihrea1337.galleria.ui.navigation.GalleriaNavHost
import io.github.vihrea1337.galleria.ui.theme.GalleriaTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Картина, которую надо открыть (нажали на виджет «Картина дня»); null — просто лента. */
    private val artworkToOpen = mutableStateOf<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            GalleriaTheme {
                GalleriaNavHost(
                    artworkToOpen = artworkToOpen.value,
                    onArtworkOpened = { artworkToOpen.value = null },
                )
            }
        }
    }

    // singleTop: приложение уже открыто — виджет приходит сюда, а не новым экземпляром.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.getLongExtra(EXTRA_ARTWORK_ID, -1L)?.takeIf { it > 0 }?.let { artworkToOpen.value = it }
    }

    companion object {
        const val EXTRA_ARTWORK_ID = "artwork_id"
    }
}
