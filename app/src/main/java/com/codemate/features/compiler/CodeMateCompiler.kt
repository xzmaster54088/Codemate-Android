package com.codemate.features.compiler

import android.content.Context
import com.codemate.features.compiler.core.analyzer.ResultAnalyzer
import com.codemate.features.compiler.core.bridge.TermuxBridge
import com.codemate.features.compiler.core.manager.*
import com.codemate.features.compiler.core.parser.LogParser
import com.codemate.features.compiler.data.repositories.CompilerRepositoryImpl
import com.codemate.features.compiler.domain.repositories.CompilerRepository
import com.codemate.features.compiler.domain.usecases.CompileUseCases
import com.codemate.features.compiler.presentation.ui.CompilerViewModel
import com.codemate.features.compiler.presentation.workers.CompilerWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager

/**
 * CodeMate Mobile 编译器模块工厂
 * 负责初始化和管理所有编译相关组件
 */
class CodeMateCompiler private constructor(
    private val context: Context
) {
    
    // 核心组件
    private val termuxBridge: TermuxBridge by lazy { TermuxBridge(context) }
    private val logParser: LogParser by lazy { LogParser() }
    private val resultAnalyzer: ResultAnalyzer by lazy { ResultAnalyzer() }
    
    // 管理器
    private val taskManager: CompileTaskManager by lazy { 
        CompileTaskManager(termuxBridge, maxConcurrentTasks = 3) 
    }
    private val toolchainManager: ToolchainManager by lazy { 
        ToolchainManager(termuxBridge) 
    }
    private val cacheManager: CacheManager by lazy { 
        CacheManager(context) 
    }
    private val historyManager: HistoryManager by lazy { 
        HistoryManager(context) 
    }
    
    // 仓库层
    private val repository: CompilerRepository by lazy { 
        CompilerRepositoryImpl(
            context = context,
            termuxBridge = termuxBridge,
            taskManager = taskManager,
            toolchainManager = toolchainManager,
            cacheManager = cacheManager,
            historyManager = historyManager
        ) 
    }
    
    // 用例层
    private val compileUseCases: CompileUseCases by lazy { 
        CompileUseCases(
            termuxBridge = termuxBridge,
            taskManager = taskManager,
            toolchainManager = toolchainManager,
            cacheManager = cacheManager,
            historyManager = historyManager,
            logParser = logParser,
            resultAnalyzer = resultAnalyzer,
            repository = repository
        ) 
    }
    
    // UI层
    private val viewModel: CompilerViewModel by lazy { 
        CompilerViewModel(
            application = context.applicationContext as android.app.Application,
            repository = repository,
            compileUseCases = compileUseCases
        ) 
    }
    
    // Worker工厂
    private val workerFactory: CompilerWorkerFactory by lazy { 
        CompilerWorkerFactory(
            termuxBridge = termuxBridge,
            taskManager = taskManager,
            toolchainManager = toolchainManager,
            cacheManager = cacheManager,
            historyManager = historyManager,
            logParser = logParser,
            resultAnalyzer = resultAnalyzer,
            compileUseCases = compileUseCases
        ) 
    }
    
    /**
     * 启动编译引擎
     */
    fun start() {
        // 启动任务管理器
        taskManager.start()
        
        // 配置WorkManager
        configureWorkManager()
        
        // 启动缓存预热
        warmupCache()
        
        // 启动定期任务
        schedulePeriodicTasks()
    }
    
    /**
     * 停止编译引擎
     */
    suspend fun stop() {
        // 停止任务管理器
        taskManager.stop()
        
        // 清理管理器
        toolchainManager.cleanup()
        cacheManager.cleanup()
        historyManager.cleanup()
        
        // 清理用例
        compileUseCases.cleanup()
        
        // 关闭Termux桥接
        termuxBridge.shutdown()
    }
    
    /**
     * 获取Termux桥接器
     */
    fun getTermuxBridge(): TermuxBridge = termuxBridge
    
    /**
     * 获取任务管理器
     */
    fun getTaskManager(): CompileTaskManager = taskManager
    
    /**
     * 获取工具链管理器
     */
    fun getToolchainManager(): ToolchainManager = toolchainManager
    
    /**
     * 获取缓存管理器
     */
    fun getCacheManager(): CacheManager = cacheManager
    
    /**
     * 获取历史管理器
     */
    fun getHistoryManager(): HistoryManager = historyManager
    
    /**
     * 获取仓库
     */
    fun getRepository(): CompilerRepository = repository
    
    /**
     * 获取用例
     */
    fun getCompileUseCases(): CompileUseCases = compileUseCases
    
    /**
     * 获取ViewModel
     */
    fun getViewModel(): CompilerViewModel = viewModel
    
    /**
     * 获取Worker工厂
     */
    fun getWorkerFactory(): CompilerWorkerFactory = workerFactory
    
    /**
     * 配置WorkManager
     */
    private fun configureWorkManager() {
        val configuration = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
        
        // WorkManager会自动使用配置
    }
    
    /**
     * 启动缓存预热
     */
    private suspend fun warmupCache() {
        try {
            cacheManager.warmupCache()
        } catch (e: Exception) {
            // 静默处理缓存预热失败
        }
    }
    
    /**
     * 启动定期任务
     */
    private fun schedulePeriodicTasks() {
        try {
            val workManager = WorkManager.getInstance(context)
            
            // 定期清理任务
            val cleanupRequest = CompilerWorkRequests.createPeriodicCleanupRequest()
            workManager.enqueueUniquePeriodicWork(
                "cache_cleanup",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                cleanupRequest
            )
            
            // 定期统计更新
            val statisticsRequest = CompilerWorkRequests.createPeriodicStatisticsUpdateRequest()
            workManager.enqueueUniquePeriodicWork(
                "statistics_update",
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                statisticsRequest
            )
            
        } catch (e: Exception) {
            // 静默处理定期任务设置失败
        }
    }
    
    companion object {
        @Volatile
        private var INSTANCE: CodeMateCompiler? = null
        
        /**
         * 获取编译器实例（单例模式）
         */
 fun getInstance(context: Context): CodeMateCompiler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CodeMateCompiler(context.applicationContext).also { instance ->
                    INSTANCE = instance
                }
            }
        }
        
        /**
         * 初始化编译器（需要在Application中调用）
         */
        fun initialize(context: Context): CodeMateCompiler {
            val instance = getInstance(context)
            instance.start()
            return instance
        }
        
        /**
         * 清理资源（需要在应用退出时调用）
         */
        suspend fun cleanup() {
            INSTANCE?.stop()
            INSTANCE = null
        }
    }
}

/**
 * 编译模块配置
 */
data class CompilerConfig(
    val maxConcurrentTasks: Int = 3,
    val enableCache: Boolean = true,
    val enableHistory: Boolean = true,
    val enableStatistics: Boolean = true,
    val cacheSizeLimit: Long = 100 * 1024 * 1024L, // 100MB
    val historyRetentionDays: Int = 90,
    val enableBackgroundTasks: Boolean = true,
    val enableNotifications: Boolean = true
)

/**
 * 编译模块状态
 */
enum class CompilerModuleState {
    INITIALIZING,
    READY,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR
}

/**
 * 编译模块事件
 */
sealed class CompilerModuleEvent {
    object Initialized : CompilerModuleEvent()
    object Started : CompilerModuleEvent()
    object Stopped : CompilerModuleEvent()
    data class Error(val message: String) : CompilerModuleEvent()
    data class TaskCompleted(val taskId: String) : CompilerModuleEvent()
    data class ToolchainInstalled(val language: Language) : CompilerModuleEvent()
}