package com.codemate.features.compiler.presentation.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.codemate.features.compiler.core.bridge.TermuxBridge
import com.codemate.features.compiler.core.manager.*
import com.codemate.features.compiler.domain.entities.*
import com.codemate.features.compiler.domain.usecases.CompileUseCases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 编译任务工作者
 * 使用WorkManager在后台执行编译任务
 */
class CompileWorker(
    context: Context,
    params: WorkerParameters,
    private val termuxBridge: TermuxBridge,
    private val taskManager: CompileTaskManager,
    private val toolchainManager: ToolchainManager,
    private val cacheManager: CacheManager,
    private val historyManager: HistoryManager,
    private val logParser: LogParser,
    private val resultAnalyzer: ResultAnalyzer,
    private val compileUseCases: CompileUseCases
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CompileWorker"
        const val WORK_NAME = "compile_task"
        const val TASK_ID_KEY = "task_id"
        const val PROJECT_PATH_KEY = "project_path"
        const val SOURCE_FILES_KEY = "source_files"
        const val LANGUAGE_KEY = "language"
        const val PRIORITY_KEY = "priority"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting compile worker")
            
            val taskId = inputData.getString(TASK_ID_KEY) ?: return@withContext Result.failure()
            val projectPath = inputData.getString(PROJECT_PATH_KEY) ?: return@withContext Result.failure()
            val sourceFiles = inputData.getString(SOURCE_FILES_KEY)?.split(",") ?: emptyList()
            val language = Language.valueOf(inputData.getString(LANGUAGE_KEY) ?: return@withContext Result.failure())
            val priority = TaskPriority.valueOf(inputData.getString(PRIORITY_KEY) ?: TaskPriority.NORMAL.name)
            
            Log.d(TAG, "Compiling with taskId: $taskId, language: $language")
            
            // 执行编译
            val result = compileUseCases.executeCompile(
                projectPath = projectPath,
                sourceFiles = sourceFiles,
                language = language,
                priority = priority
            )
            
            // 创建输出数据
            val outputData = Data.Builder()
                .putString(TASK_ID_KEY, taskId)
                .putBoolean("success", result.success)
                .putString("result", result.toString())
                .putString("error", result.error)
                .putLong("execution_time", result.result?.executionTime ?: 0L)
                .build()
            
            if (result.success) {
                Log.d(TAG, "Compilation completed successfully for task: $taskId")
                Result.success(outputData)
            } else {
                Log.w(TAG, "Compilation failed for task: $taskId, error: ${result.error}")
                Result.failure(outputData)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Compile worker failed", e)
            Result.failure()
        }
    }
}

/**
 * 工具链安装工作者
 */
class ToolchainInstallWorker(
    context: Context,
    params: WorkerParameters,
    private val toolchainManager: ToolchainManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ToolchainInstallWorker"
        const val WORK_NAME = "toolchain_install"
        const val LANGUAGE_KEY = "language"
        const val PROGRESS_KEY = "progress"
        const val MESSAGE_KEY = "message"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val language = Language.valueOf(inputData.getString(LANGUAGE_KEY) ?: return@withContext Result.failure())
            
            Log.d(TAG, "Installing toolchain for: $language")
            
            val result = toolchainManager.installToolchain(language)
            
            val outputData = Data.Builder()
                .putString(LANGUAGE_KEY, language.name)
                .putBoolean("success", result.success)
                .putString("message", result.message)
                .putInt("progress", result.state?.progress ?: 0)
                .build()
            
            if (result.success) {
                Log.d(TAG, "Toolchain installation completed for: $language")
                Result.success(outputData)
            } else {
                Log.w(TAG, "Toolchain installation failed for: $language")
                Result.failure(outputData)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Toolchain install worker failed", e)
            Result.failure()
        }
    }
}

/**
 * 数据清理工作者
 */
class DataCleanupWorker(
    context: Context,
    params: WorkerParameters,
    private val cacheManager: CacheManager,
    private val historyManager: HistoryManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "DataCleanupWorker"
        const val WORK_NAME = "data_cleanup"
        const val CLEANUP_TYPE_KEY = "cleanup_type"
        const val CACHE_CLEANUP = "cache"
        const val HISTORY_CLEANUP = "history"
        const val ALL_CLEANUP = "all"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val cleanupType = inputData.getString(CLEANUP_TYPE_KEY) ?: ALL_CLEANUP
            
            Log.d(TAG, "Starting data cleanup: $cleanupType")
            
            var cacheCleaned = false
            var historyCleaned = false
            
            when (cleanupType) {
                CACHE_CLEANUP -> {
                    cacheCleaned = cacheManager.clearAllCache()
                }
                HISTORY_CLEANUP -> {
                    historyCleaned = historyManager.clearAllHistory()
                }
                ALL_CLEANUP -> {
                    cacheCleaned = cacheManager.clearAllCache()
                    historyCleaned = historyManager.clearAllHistory()
                }
            }
            
            val outputData = Data.Builder()
                .putBoolean("cache_cleaned", cacheCleaned)
                .putBoolean("history_cleaned", historyCleaned)
                .putString("cleanup_type", cleanupType)
                .build()
            
            Log.d(TAG, "Data cleanup completed: cache=$cacheCleaned, history=$historyCleaned")
            Result.success(outputData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Data cleanup worker failed", e)
            Result.failure()
        }
    }
}

/**
 * 统计分析工作者
 */
class StatisticsAnalysisWorker(
    context: Context,
    params: WorkerParameters,
    private val historyManager: HistoryManager,
    private val resultAnalyzer: ResultAnalyzer
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "StatisticsAnalysisWorker"
        const val WORK_NAME = "statistics_analysis"
        const val PROJECT_PATH_KEY = "project_path"
        const val ANALYSIS_TYPE_KEY = "analysis_type"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val projectPath = inputData.getString(PROJECT_PATH_KEY)
            val analysisType = inputData.getString(ANALYSIS_TYPE_KEY) ?: "general"
            
            Log.d(TAG, "Starting statistics analysis: $analysisType for project: $projectPath")
            
            val outputData = when (analysisType) {
                "project" -> {
                    if (projectPath != null) {
                        val projectStats = historyManager.getProjectStatistics(projectPath)
                        val report = Data.Builder()
                            .putString("project_path", projectPath)
                            .putString("analysis_type", analysisType)
                            .putString("result", projectStats.toString())
                            .build()
                        Result.success(report)
                    } else {
                        Result.failure()
                    }
                }
                "language" -> {
                    val languageStats = historyManager.getLanguageStatistics()
                    val report = Data.Builder()
                        .putString("analysis_type", analysisType)
                        .putString("result", languageStats.toString())
                        .build()
                    Result.success(report)
                }
                "error" -> {
                    val errorAnalysis = historyManager.getErrorAnalysis()
                    val report = Data.Builder()
                        .putString("analysis_type", analysisType)
                        .putString("result", errorAnalysis.toString())
                        .build()
                    Result.success(report)
                }
                else -> {
                    val generalStats = historyManager.getCompileStatistics()
                    val report = Data.Builder()
                        .putString("analysis_type", analysisType)
                        .putString("result", generalStats.toString())
                        .build()
                    Result.success(report)
                }
            }
            
            Log.d(TAG, "Statistics analysis completed: $analysisType")
            outputData
            
        } catch (e: Exception) {
            Log.e(TAG, "Statistics analysis worker failed", e)
            Result.failure()
        }
    }
}

/**
 * 缓存预热工作者
 */
class CacheWarmupWorker(
    context: Context,
    params: WorkerParameters,
    private val cacheManager: CacheManager
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "CacheWarmupWorker"
        const val WORK_NAME = "cache_warmup"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting cache warmup")
            
            cacheManager.warmupCache()
            
            val outputData = Data.Builder()
                .putString("warmup_status", "completed")
                .build()
            
            Log.d(TAG, "Cache warmup completed")
            Result.success(outputData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Cache warmup worker failed", e)
            Result.failure()
        }
    }
}

/**
 * WorkManager工厂类
 * 创建和管理各种工作者实例
 */
class CompilerWorkerFactory(
    private val termuxBridge: TermuxBridge,
    private val taskManager: CompileTaskManager,
    private val toolchainManager: ToolchainManager,
    private val cacheManager: CacheManager,
    private val historyManager: HistoryManager,
    private val logParser: LogParser,
    private val resultAnalyzer: ResultAnalyzer,
    private val compileUseCases: CompileUseCases
) : WorkerFactory() {

    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        return when (workerClassName) {
            CompileWorker::class.java.name -> {
                CompileWorker(
                    appContext,
                    workerParameters,
                    termuxBridge,
                    taskManager,
                    toolchainManager,
                    cacheManager,
                    historyManager,
                    logParser,
                    resultAnalyzer,
                    compileUseCases
                )
            }
            ToolchainInstallWorker::class.java.name -> {
                ToolchainInstallWorker(
                    appContext,
                    workerParameters,
                    toolchainManager
                )
            }
            DataCleanupWorker::class.java.name -> {
                DataCleanupWorker(
                    appContext,
                    workerParameters,
                    cacheManager,
                    historyManager
                )
            }
            StatisticsAnalysisWorker::class.java.name -> {
                StatisticsAnalysisWorker(
                    appContext,
                    workerParameters,
                    historyManager,
                    resultAnalyzer
                )
            }
            CacheWarmupWorker::class.java.name -> {
                CacheWarmupWorker(
                    appContext,
                    workerParameters,
                    cacheManager
                )
            }
            else -> null
        }
    }
}

/**
 * 工作请求构建器
 */
object CompilerWorkRequests {
    
    /**
     * 创建编译任务请求
     */
    fun createCompileRequest(
        taskId: String,
        projectPath: String,
        sourceFiles: List<String>,
        language: Language,
        priority: TaskPriority = TaskPriority.NORMAL
    ): WorkRequest {
        val inputData = Data.Builder()
            .putString(CompileWorker.TASK_ID_KEY, taskId)
            .putString(CompileWorker.PROJECT_PATH_KEY, projectPath)
            .putString(CompileWorker.SOURCE_FILES_KEY, sourceFiles.joinToString(","))
            .putString(CompileWorker.LANGUAGE_KEY, language.name)
            .putString(CompileWorker.PRIORITY_KEY, priority.name)
            .build()
        
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()
        
        return OneTimeWorkRequestBuilder<CompileWorker>()
            .setInputData(inputData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                WorkRequest.MAX_BACKOFF_MILLIS
            )
            .build()
    }
    
    /**
     * 创建工具链安装请求
     */
    fun createToolchainInstallRequest(language: Language): WorkRequest {
        val inputData = Data.Builder()
            .putString(ToolchainInstallWorker.LANGUAGE_KEY, language.name)
            .build()
        
        return OneTimeWorkRequestBuilder<ToolchainInstallWorker>()
            .setInputData(inputData)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                WorkRequest.MAX_BACKOFF_MILLIS
            )
            .build()
    }
    
    /**
     * 创建数据清理请求
     */
    fun createCleanupRequest(cleanupType: String = DataCleanupWorker.ALL_CLEANUP): WorkRequest {
        val inputData = Data.Builder()
            .putString(DataCleanupWorker.CLEANUP_TYPE_KEY, cleanupType)
            .build()
        
        return OneTimeWorkRequestBuilder<DataCleanupWorker>()
            .setInputData(inputData)
            .build()
    }
    
    /**
     * 创建统计分析请求
     */
    fun createStatisticsAnalysisRequest(
        projectPath: String? = null,
        analysisType: String = "general"
    ): WorkRequest {
        val inputData = Data.Builder()
            .putString(StatisticsAnalysisWorker.PROJECT_PATH_KEY, projectPath)
            .putString(StatisticsAnalysisWorker.ANALYSIS_TYPE_KEY, analysisType)
            .build()
        
        return OneTimeWorkRequestBuilder<StatisticsAnalysisWorker>()
            .setInputData(inputData)
            .build()
    }
    
    /**
     * 创建缓存预热请求
     */
    fun createCacheWarmupRequest(): WorkRequest {
        return OneTimeWorkRequestBuilder<CacheWarmupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
            )
            .build()
    }
    
    /**
     * 创建周期性清理请求
     */
    fun createPeriodicCleanupRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<DataCleanupWorker>(
            24, // 24小时
            java.util.concurrent.TimeUnit.HOURS
        )
        .setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(true)
                .build()
        )
        .build()
    }
    
    /**
     * 创建周期性统计更新请求
     */
    fun createPeriodicStatisticsUpdateRequest(): PeriodicWorkRequest {
        return PeriodicWorkRequestBuilder<StatisticsAnalysisWorker>(
            1, // 1小时
            java.util.concurrent.TimeUnit.HOURS
        )
        .setConstraints(
            Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
        )
        .build()
    }
}