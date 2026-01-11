package com.cosgame.costrack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cosgame.costrack.ui.classifiers.ActivityDataBrowserScreen
import com.cosgame.costrack.ui.classifiers.ClassifiersScreen
import com.cosgame.costrack.ui.home.HomeScreen
import com.cosgame.costrack.ui.missions.*
import com.cosgame.costrack.ui.navigation.Screen
import com.cosgame.costrack.ui.touch.TouchMissionsScreen
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
                                // Get current route to check if we're on a nested screen
                                val currentRoute = currentDestination?.route
                                val isOnNestedScreen = currentRoute in listOf(
                                    Screen.ACTIVE_MISSION,
                                    Screen.TRAINING,
                                    Screen.TEST,
                                    Screen.DATA_BROWSER,
                                    Screen.ACTIVITY_DATA_BROWSER,
                                    Screen.TOUCH_MISSIONS
                                ) || currentRoute?.startsWith("active_mission/") == true

                                if (isOnNestedScreen) {
                                    // From nested screens, pop back to the target
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                    }
                                } else {
                                    // Normal bottom nav behavior
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = screen.route != Screen.Home.route
                                        }
                                        launchSingleTop = true
                                        restoreState = screen.route != Screen.Home.route
                                    }
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

                composable(Screen.Missions.route) {
                    MissionsScreen(
                        onStartMission = { mission ->
                            navController.navigate(Screen.activeMissionRoute(mission.id))
                        },
                        onGoToTraining = {
                            navController.navigate(Screen.TRAINING)
                        },
                        onGoToTest = {
                            navController.navigate(Screen.TEST)
                        },
                        onGoToDataBrowser = {
                            navController.navigate(Screen.DATA_BROWSER)
                        },
                        onGoToTouchMissions = {
                            navController.navigate(Screen.TOUCH_MISSIONS)
                        }
                    )
                }

                composable(
                    route = Screen.ACTIVE_MISSION,
                    arguments = listOf(navArgument("missionId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val missionId = backStackEntry.arguments?.getString("missionId") ?: ""
                    ActiveMissionScreen(
                        missionId = missionId,
                        onMissionComplete = {
                            navController.popBackStack()
                        },
                        onCancel = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screen.TRAINING) {
                    TrainingScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screen.TEST) {
                    TestScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screen.DATA_BROWSER) {
                    DataBrowserScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screen.Classifiers.route) {
                    ClassifiersScreen(
                        onGoToActivityData = {
                            navController.navigate(Screen.ACTIVITY_DATA_BROWSER)
                        }
                    )
                }

                composable(Screen.ACTIVITY_DATA_BROWSER) {
                    ActivityDataBrowserScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen()
                }

                composable(Screen.TOUCH_MISSIONS) {
                    TouchMissionsScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}
