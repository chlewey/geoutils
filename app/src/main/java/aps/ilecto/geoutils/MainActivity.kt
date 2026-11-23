package net.interlecto.geoutils

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import net.interlecto.geoutils.ui.MainViewModel
import net.interlecto.geoutils.ui.navigation.GeoUtilsNavHost
import net.interlecto.geoutils.ui.theme.GeoUtilsTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash screen up until startup work (database, assets) is done.
        installSplashScreen().setKeepOnScreenCondition { !viewModel.isReady.value }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GeoUtilsTheme {
                GeoUtilsNavHost()
            }
        }
    }
}
