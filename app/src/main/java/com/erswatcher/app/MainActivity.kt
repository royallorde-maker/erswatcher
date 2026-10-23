package com.erswatcher.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.erswatcher.app.data.Store
import com.erswatcher.app.ui.ErsApp
import com.erswatcher.app.ui.SplashScreen
import com.erswatcher.app.ui.theme.Accent
import com.erswatcher.app.ui.theme.ErsTheme
import com.erswatcher.app.ui.theme.accentOf
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.BLACK))
        val store = Store(applicationContext)
        val prefs = getSharedPreferences("ers_settings", MODE_PRIVATE)

        setContent {
            var accent by rememberSaveable { mutableStateOf(prefs.getString("accent", "VIOLET") ?: "VIOLET") }
            var motion by rememberSaveable { mutableStateOf(prefs.getBoolean("motion", true)) }
            ErsTheme(accent = accentOf(accent)) {
                var splash by remember { mutableStateOf(savedInstanceState == null) }
                LaunchedEffect(Unit) {
                    if (splash) {
                        delay(1250)
                        splash = false
                    }
                }
                Crossfade(targetState = splash, animationSpec = tween(350), label = "splash") { showing ->
                    if (showing) {
                        SplashScreen()
                    } else {
                        ErsApp(
                            store = store,
                            accent = accentOf(accent),
                            onAccentChange = {
                                accent = it.name
                                prefs.edit().putString("accent", it.name).apply()
                            },
                            motion = motion,
                            onMotionChange = {
                                motion = it
                                prefs.edit().putBoolean("motion", it).apply()
                            }
                        )
                    }
                }
            }
        }
    }
}
