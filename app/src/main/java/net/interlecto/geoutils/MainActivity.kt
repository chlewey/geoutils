package net.interlecto.geoutils

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import net.interlecto.geoutils.data.settings.ThemeMode
import net.interlecto.geoutils.ui.MainViewModel
import net.interlecto.geoutils.ui.navigation.GeoUtilsNavHost
import net.interlecto.geoutils.ui.theme.GeoUtilsTheme
import net.interlecto.geoutils.ui.theme.isDark

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash screen up until startup work (database, assets, settings) is done.
        installSplashScreen().setKeepOnScreenCondition { !viewModel.isReady.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val themeMode = settings?.themeMode ?: ThemeMode.SYSTEM
            val dark = themeMode.isDark(isSystemInDarkTheme())
            // The system bars follow the app's theme, not only the system's.
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(LIGHT_SCRIM, DARK_SCRIM) { dark },
                )
            }
            GeoUtilsTheme(themeMode) {
                GeoUtilsNavHost()
            }
        }
    }

    private companion object {
        // The scrims enableEdgeToEdge() uses by default for the navigation bar.
        val LIGHT_SCRIM = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DARK_SCRIM = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
