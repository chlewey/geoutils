package net.interlecto.geoutils.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import net.interlecto.geoutils.ui.map.MapScreen
import net.interlecto.geoutils.ui.settings.SettingsScreen

@Serializable
data object MapRoute

@Serializable
data object SettingsRoute

@Composable
fun GeoUtilsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = MapRoute) {
        composable<MapRoute> {
            MapScreen(onOpenSettings = { navController.navigate(SettingsRoute) })
        }
        composable<SettingsRoute> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
