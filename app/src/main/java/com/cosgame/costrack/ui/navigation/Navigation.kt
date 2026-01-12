package com.cosgame.costrack.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations for the app.
 */
sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : Screen(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    object Sensors : Screen(
        route = "sensors",
        title = "Sensors",
        selectedIcon = Icons.Filled.PlayArrow,
        unselectedIcon = Icons.Outlined.PlayArrow
    )

    object Learn : Screen(
        route = "learn",
        title = "Learn",
        selectedIcon = Icons.Filled.Star,
        unselectedIcon = Icons.Outlined.Star
    )

    object Classifiers : Screen(
        route = "classifiers",
        title = "Activity",
        selectedIcon = Icons.Filled.PlayArrow,
        unselectedIcon = Icons.Outlined.PlayArrow
    )

    object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    companion object {
        val bottomNavItems = listOf(Home, Learn, Classifiers, Settings)

        // Non-bottom-nav routes
        const val ACTIVE_MISSION = "active_mission/{missionId}/{category}"
        const val TRAINING = "training"
        const val TEST = "test"
        const val DATA_BROWSER = "data_browser"
        const val ACTIVITY_DATA_BROWSER = "activity_data_browser"
        const val TRAIN_TOUCH = "train_touch"
        const val TRAIN_MOVEMENT = "train_movement"
        const val CATEGORIES = "categories"

        fun activeMissionRoute(missionId: String, category: String = "") = "active_mission/$missionId/$category"
    }
}
