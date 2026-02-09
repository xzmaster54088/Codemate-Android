package com.codemate

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main application class for CodeMate
 * Initializes Hilt dependency injection and other core components
 */
@HiltAndroidApp
class CodeMateApplication : Application() {
    
    companion object {
        lateinit var instance: CodeMateApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize any app-wide components here
        initializeComponents()
    }
    
    private fun initializeComponents() {
        // TODO: Initialize global components
        // - Analytics
        // - Crash reporting
        // - Logger
        // - Configuration
    }
    
    override fun onTerminate() {
        super.onTerminate()
        // Clean up any resources
        cleanup()
    }
    
    private fun cleanup() {
        // TODO: Clean up resources
        // - Close databases
        // - Cancel ongoing operations
        // - Clear temporary files
    }
}