package com.cosgame.costrack.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.cosgame.costrack.ui.learn.LearnScreen
import com.cosgame.costrack.ui.learn.CategoriesScreen
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
                                val currentRoute = currentDestination?.route
                                val isOnNestedScreen = currentRoute in listOf(
                                    Screen.ACTIVE_MISSION,
                                    Screen.TRAINING,
                                    Screen.TEST,
                                    Screen.DATA_BROWSER,
                                    Screen.ACTIVITY_DATA_BROWSER,
                                    Screen.TRAIN_TOUCH,
                                    Screen.TRAIN_MOVEMENT,
                                    Screen.CATEGORIES
                                ) || currentRoute?.startsWith("active_mission/") == true

                                if (isOnNestedScreen) {
                                    navController.navigate(screen.route) {
                                        popUpTo(Screen.Home.route) {
                                            inclusive = false
                                        }
                                        launchSingleTop = true
                                    }
                                } else {
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

                // Learn screen (main training hub)
                composable(Screen.Learn.route) {
                    LearnScreen(
                        onTrainTouch = {
                            navController.navigate(Screen.TRAIN_TOUCH)
                        },
                        onTrainMovement = {
                            navController.navigate(Screen.TRAIN_MOVEMENT)
                        },
                        onManageCategories = {
                            navController.navigate(Screen.CATEGORIES)
                        }
                    )
                }

                // Categories management
                composable(Screen.CATEGORIES) {
                    CategoriesScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                // Train Touch (Touch Intelligence)
                composable(Screen.TRAIN_TOUCH) {
                    TouchMissionsScreen(
                        onBack = {
                            navController.popBackStack()
                        }
                    )
                }

                // Train Movement (Activity Missions)
                composable(Screen.TRAIN_MOVEMENT) {
                    val missionsViewModel: MissionsViewModel = viewModel()
                    val missionsUiState by missionsViewModel.uiState.collectAsState()

                    MissionsScreen(
                        viewModel = missionsViewModel,
                        onStartMission = { mission ->
                            navController.navigate(Screen.activeMissionRoute(mission.id, missionsUiState.selectedCategory))
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
                            // Not needed anymore, but keep for compatibility
                        }
                    )
                }

                composable(
                    route = Screen.ACTIVE_MISSION,
                    arguments = listOf(
                        navArgument("missionId") { type = NavType.StringType },
                        navArgument("category") { type = NavType.StringType; defaultValue = "" }
                    )
                ) { backStackEntry ->
                    val missionId = backStackEntry.arguments?.getString("missionId") ?: ""
                    val category = backStackEntry.arguments?.getString("category") ?: ""
                    ActiveMissionScreen(
                        missionId = missionId,
                        category = category,
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
            }
        }
    }
}
