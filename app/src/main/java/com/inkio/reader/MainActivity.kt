package com.inkio.reader

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.inkio.reader.data.AppTheme
import com.inkio.reader.reminders.ReminderScheduler
import com.inkio.reader.ui.components.EinkRefreshFlash
import com.inkio.reader.ui.components.paperBackground
import com.inkio.reader.ui.library.LibraryScreen
import com.inkio.reader.ui.reader.ReaderScreen
import com.inkio.reader.ui.settings.SettingsScreen
import com.inkio.reader.ui.theme.InkioTheme
import com.inkio.reader.ui.welcome.WelcomeScreen
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** Volume-key page turns: +1 next page, -1 previous page. */
object KeyBus {
    @Volatile var active = false
    val events = MutableSharedFlow<Int>(extraBufferCapacity = 8)
}

class MainActivity : ComponentActivity() {
    val pendingOpen = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { InkioRoot(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra(ReminderScheduler.EXTRA_OPEN_BOOK)?.let { pendingOpen.value = it }
        intent?.removeExtra(ReminderScheduler.EXTRA_OPEN_BOOK)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val app = application as InkioApp
        if (KeyBus.active && app.settings.current.volumeKeys &&
            (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                KeyBus.events.tryEmit(if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) 1 else -1)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

@Composable
private fun InkioRoot(activity: MainActivity) {
    val app = activity.application as InkioApp
    val settings by app.settings.settings.collectAsStateWithLifecycle()
    val eink = settings.theme == AppTheme.EINK

    LaunchedEffect(eink) {
        val transparent = android.graphics.Color.TRANSPARENT
        activity.enableEdgeToEdge(
            statusBarStyle = if (eink) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent),
            navigationBarStyle = if (eink) SystemBarStyle.light(transparent, transparent) else SystemBarStyle.dark(transparent),
        )
    }

    InkioTheme(settings.theme) {
        val nav = rememberNavController()
        val start = remember { if (settings.onboarded) "library" else "welcome" }
        val pending by activity.pendingOpen.collectAsStateWithLifecycle()
        LaunchedEffect(pending) {
            val id = pending ?: return@LaunchedEffect
            if (app.settings.current.onboarded && app.library.book(id) != null) {
                nav.navigate("reader/$id") { launchSingleTop = true }
            }
            activity.pendingOpen.value = null
        }
        val route = nav.currentBackStackEntryAsState().value?.destination?.route

        Box(Modifier.fillMaxSize().paperBackground()) {
            NavHost(
                navController = nav,
                startDestination = start,
                enterTransition = { if (eink) EnterTransition.None else fadeIn(tween(260)) },
                exitTransition = { if (eink) ExitTransition.None else fadeOut(tween(200)) },
                popEnterTransition = { if (eink) EnterTransition.None else fadeIn(tween(260)) },
                popExitTransition = { if (eink) ExitTransition.None else fadeOut(tween(200)) },
            ) {
                composable("welcome") {
                    WelcomeScreen(onDone = {
                        nav.navigate("library") { popUpTo("welcome") { inclusive = true } }
                    })
                }
                composable("library") {
                    LibraryScreen(
                        onOpen = { nav.navigate("reader/$it") },
                        onSettings = { nav.navigate("settings") },
                    )
                }
                composable("reader/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    ReaderScreen(bookId = it.arguments?.getString("id").orEmpty(), onBack = { nav.popBackStack() })
                }
                composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
            }
            EinkRefreshFlash(trigger = route, enabled = eink)
        }
    }
}
