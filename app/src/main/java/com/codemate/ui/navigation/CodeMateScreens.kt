package com.codemate.ui.navigation

/**
 * Screen routes for CodeMate application navigation
 */
sealed class CodeMateScreens(val route: String) {
    
    // Main navigation screens
    object Home : CodeMateScreens("home")
    object Editor : CodeMateScreens("editor")
    object Files : CodeMateScreens("files")
    object Settings : CodeMateScreens("settings")
    object About : CodeMateScreens("about")
    
    // Editor specific screens
    object NewFile : CodeMateScreens("new_file")
    object OpenFile : CodeMateScreens("open_file")
    object SaveFile : CodeMateScreens("save_file")
    object FileProperties : CodeMateScreens("file_properties")
    
    // Project management screens
    object NewProject : CodeMateScreens("new_project")
    object OpenProject : CodeMateScreens("open_project")
    object ProjectSettings : CodeMateScreens("project_settings")
    
    // Settings screens
    object EditorSettings : CodeMateScreens("editor_settings")
    object ThemeSettings : CodeMateScreens("theme_settings")
    object ShortcutsSettings : CodeMateScreens("shortcuts_settings")
    object LanguageSettings : CodeMateScreens("language_settings")
    
    // Search and replace screens
    object SearchReplace : CodeMateScreens("search_replace")
    object SearchInFiles : CodeMateScreens("search_in_files")
    
    // Dialog screens
    object CreateFileDialog : CodeMateScreens("create_file_dialog")
    object CreateProjectDialog : CodeMateScreens("create_project_dialog")
    object OpenFileDialog : CodeMateScreens("open_file_dialog")
    object SaveAsDialog : CodeMateScreens("save_as_dialog")
    
    // Utility screens
    object Help : CodeMateScreens("help")
    object License : CodeMateScreens("license")
    object Changelog : CodeMateScreens("changelog")
    object Feedback : CodeMateScreens("feedback")
    
    // Helper function to get all main navigation routes
    companion object {
        val mainNavRoutes = listOf(
            Home.route,
            Editor.route,
            Files.route,
            Settings.route,
            About.route
        )
    }
}