package net.interlecto.geoutils.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable
import net.interlecto.geoutils.ui.home.HomeScreen

@Serializable
data object HomeRoute

@Composable
fun GeoUtilsNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> {
            HomeScreen()
        }
    }
}
