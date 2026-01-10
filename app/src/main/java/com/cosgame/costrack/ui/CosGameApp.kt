package com.cosgame.costrack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.cosgame.costrack.ui.classifiers.ClassifiersScreen
import com.cosgame.costrack.ui.home.HomeScreen
import com.cosgame.costrack.ui.navigation.Screen
import com.cosgame.costrack.ui.sensors.SensorsScreen
import com.cosgame.costrack.ui.settings.SettingsScreen
import com.cosgame.costrack.ui.theme.CosGameTheme

/**
 * Main app composable with navigation.
 */
@Composable
fun CosGameApp() {
    CosGameTheme {
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        Scaffold(
            bottomBar = {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == screen.route
                        } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected)
                                        screen.selectedIcon
                                    else
                                        screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    // Pop up to the start destination to avoid
                                    // building up a large stack of destinations
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    // Avoid multiple copies of the same destination
                                    launchSingleTop = true
                                    // Restore state when reselecting a previously selected item
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        onNavigateToSensors = {
                            navController.navigate(Screen.Sensors.route)
                        },
                        onNavigateToClassifiers = {
                            navController.navigate(Screen.Classifiers.route)
                        },
                        onNavigateToSettings = {
                            navController.navigate(Screen.Settings.route)
                        }
                    )
                }

                composable(Screen.Sensors.route) {
                    SensorsScreen()
                }

                composable(Screen.Classifiers.route) {
                    ClassifiersScreen()
                }

                composable(Screen.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}
