package com.codemate.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

/**
 * Navigation graph for CodeMate application
 * Defines all navigation routes and their corresponding composable screens
 */
@Composable
fun CodeMateNavigation(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = CodeMateScreens.Home.route
    ) {
        composable(
            route = CodeMateScreens.Home.route
        ) {
            // Home screen composable
            // HomeScreen(navController = navController)
        }
        
        composable(
            route = CodeMateScreens.Editor.route
        ) {
            // Editor screen composable
            // EditorScreen(navController = navController)
        }
        
        composable(
            route = CodeMateScreens.Files.route
        ) {
            // Files screen composable
            // FilesScreen(navController = navController)
        }
        
        composable(
            route = CodeMateScreens.Settings.route
        ) {
            // Settings screen composable
            // SettingsScreen(navController = navController)
        }
        
        composable(
            route = CodeMateScreens.About.route
        ) {
            // About screen composable
            // AboutScreen(navController = navController)
        }
        
        // Additional navigation routes can be added here
    }
}