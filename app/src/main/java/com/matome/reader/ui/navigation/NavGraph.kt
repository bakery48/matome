package com.matome.reader.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.matome.reader.R
import com.matome.reader.data.model.Article
import com.matome.reader.ui.bookmarks.BookmarksScreen
import com.matome.reader.ui.detail.ArticleDetailScreen
import com.matome.reader.ui.home.HomeScreen
import com.matome.reader.ui.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Bookmarks : Screen("bookmarks")
    object Settings : Screen("settings")
    object Detail : Screen("detail")
}

@Composable
fun MatomeNavGraph() {
    val navController = rememberNavController()
    var currentArticle by remember { mutableStateOf<Article?>(null) }
    var isArticleBookmarked by remember { mutableStateOf(false) }

    val bottomNavItems = listOf(
        Triple(Screen.Home, Icons.Default.Home, R.string.home_tab),
        Triple(Screen.Bookmarks, Icons.Default.Bookmark, R.string.bookmarks_tab),
        Triple(Screen.Settings, Icons.Default.Settings, R.string.settings_tab)
    )

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route != Screen.Detail.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { (screen, icon, labelRes) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(stringResource(labelRes)) },
                            selected = currentDestination?.hierarchy?.any {
                                it.route == screen.route
                            } == true,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
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
                    onOpenArticle = { article ->
                        currentArticle = article
                        isArticleBookmarked = article.isBookmarked
                        navController.navigate(Screen.Detail.route)
                    }
                )
            }
            composable(Screen.Bookmarks.route) {
                BookmarksScreen(
                    onOpenArticle = { article ->
                        currentArticle = article
                        isArticleBookmarked = article.isBookmarked
                        navController.navigate(Screen.Detail.route)
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
            composable(Screen.Detail.route) {
                currentArticle?.let { article ->
                    ArticleDetailScreen(
                        article = article,
                        isBookmarked = isArticleBookmarked,
                        onBack = { navController.popBackStack() },
                        onToggleBookmark = {
                            isArticleBookmarked = !isArticleBookmarked
                        }
                    )
                }
            }
        }
    }
}
