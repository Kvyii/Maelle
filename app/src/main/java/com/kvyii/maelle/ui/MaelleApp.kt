package com.kvyii.maelle.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.kvyii.maelle.AppContainer

private sealed class Tab(val route: String, val label: String, val icon: ImageVector) {
    object Library : Tab("library", "Library", Icons.Filled.Book)
    object Search : Tab("search", "Search", Icons.Filled.Search)
    object Settings : Tab("settings", "Settings", Icons.Filled.Settings)
}

private val tabs = listOf(Tab.Library, Tab.Search, Tab.Settings)

@Composable
fun MaelleApp(container: AppContainer) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val showBar = currentRoute in tabs.map { it.route }

    Scaffold(
        bottomBar = {
            if (showBar) {
                NavigationBar {
                    val current = backStackEntry?.destination
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = Tab.Library.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Tab.Library.route) {
                LibraryScreen(container, onOpenSeries = { id ->
                    navController.navigate("series/$id")
                })
            }
            composable(Tab.Search.route) {
                SearchScreen(container, onOpenSeries = { id ->
                    navController.navigate("series/$id")
                })
            }
            composable(Tab.Settings.route) {
                SettingsScreen(onOpen = { route -> navController.navigate(route) })
            }
            composable("settings/preferences") {
                PreferencesScreen(container, onBack = { navController.popBackStack() })
            }
            composable("settings/themes") {
                ThemesScreen(container, onBack = { navController.popBackStack() })
            }
            composable("settings/assistant") {
                AssistantSettingsScreen(container, onBack = { navController.popBackStack() })
            }
            composable("series/{seriesId}") { entry ->
                val seriesId = entry.arguments?.getString("seriesId")?.toLongOrNull() ?: return@composable
                SeriesScreen(
                    container = container,
                    seriesId = seriesId,
                    onBack = { navController.popBackStack() },
                    onOpenChapter = { chapterId ->
                        navController.navigate("reader/$seriesId/$chapterId")
                    },
                )
            }
            composable("reader/{seriesId}/{chapterId}") { entry ->
                val seriesId = entry.arguments?.getString("seriesId")?.toLongOrNull() ?: return@composable
                val chapterId = entry.arguments?.getString("chapterId")?.toLongOrNull() ?: return@composable
                ReaderScreen(
                    container = container,
                    seriesId = seriesId,
                    chapterId = chapterId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
