package com.erswatcher.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.erswatcher.app.data.Store
import com.erswatcher.app.scan.ScanController
import com.erswatcher.app.ui.theme.Accent
import kotlinx.coroutines.launch

@Composable
fun ErsApp(
    store: Store,
    accent: Accent,
    onAccentChange: (Accent) -> Unit,
    motion: Boolean,
    onMotionChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scan = remember { ScanController(context) }
    DisposableEffect(scan) {
        onDispose { scan.stopAll() }
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var planBuildingId by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun notify(message: String) {
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = !showSettings && tab == 2 && planBuildingId != null) { planBuildingId = null }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            ErsTopBar(scanning = scan.anyScanning, onSettings = { showSettings = true })
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> HomeScreen(
                        store = store,
                        scan = scan,
                        motion = motion,
                        onOpenScanner = { tab = 1 },
                        onOpenLog = { tab = 3 },
                        onOpenPlans = {
                            planBuildingId = null
                            tab = 2
                        },
                        onOpenBuilding = {
                            planBuildingId = it
                            tab = 2
                        }
                    )

                    1 -> ScanScreen(store = store, scan = scan, motion = motion, notify = ::notify)
                    2 -> PlansScreen(
                        store = store,
                        selectedBuildingId = planBuildingId,
                        onOpen = { planBuildingId = it },
                        onBack = { planBuildingId = null },
                        notify = ::notify,
                        motion = motion
                    )
                    else -> LogScreen(store = store, notify = ::notify)
                }
            }
            ErsBottomBar(selected = tab) { next ->
                if (next == 2 && tab == 2) planBuildingId = null
                tab = next
            }
        }

        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                SettingsScreen(
                    store = store,
                    accent = accent,
                    onAccentChange = onAccentChange,
                    motion = motion,
                    onMotionChange = onMotionChange,
                    onBack = { showSettings = false },
                    notify = ::notify
                )
            }
        }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 92.dp, start = 16.dp, end = 16.dp)
            )
        }
    }
}
