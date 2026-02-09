package com.codemate.features.compiler.domain.usecases

import android.util.Log
import com.codemate.features.compiler.core.analyzer.ResultAnalyzer
import com.codemate.features.compiler.core.bridge.TermuxBridge
import com.codemate.features.compiler.core.manager.*
import com.codemate.features.compiler.domain.entities.*
import com.codemate.features.compiler.domain.repositories.CompilerRepository
import kotlinx.coroutines.*

/**
 * 编译用例实现类
 * 封装具体的编译业务逻辑
 */
class CompileUseCases(
    private val termuxBridge: TermuxBridge,
    private val taskManager: CompileTaskManager,
    private val toolchainManager: ToolchainManager,
    private val cacheManager: CacheManager,
    private val historyManager: HistoryManager,
    private val logParser: LogParser,
    private val resultAnalyzer: ResultAnalyzer,
    private val repository: CompilerRepository
) {
    companion object {
        private const val TAG = "CompileUseCases"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * 执行编译用例
     */
    suspend fun executeCompile(
        projectPath: String,
        sourceFiles: List<String>,
        language: Language,
        priority: TaskPriority = TaskPriority.NORMAL
    ): CompileExecutionResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting compile execution for $language")
            
            // 1. 检查工具链是否可用
            val toolchainInfo = toolchainManager.getToolchainInfo(language)
            if (!toolchainInfo.isInstalled) {
                return@withContext CompileExecutionResult(
                    success = false,
                    error = "Toolchain for $language is not installed"
                )
            }
            
            // 2. 创建编译器配置
            val compilerConfig = createCompilerConfig(language)
            
            // 3. 创建编译任务
            val task = CompileTask(
                id = "", // 将在添加时生成
                projectPath = projectPath,
                sourceFiles = sourceFiles,
                targetLanguage = language,
                compilerConfig = compilerConfig,
                priority = priority,
                environmentVariables = getEnvironmentVariables(language)
            )
            
            // 4. 检查缓存
            val cacheKey = cacheManager.generateCacheKey(
                sourceFiles, compilerConfig, task.environmentVariables
            )
            
            val cachedResult = cacheManager.getCachedResult(cacheKey)
            if (cachedResult != null) {
                Log.d(TAG, "Cache hit for compilation")
                return@withContext CompileExecutionResult(
                    success = true,
                    result = cachedResult,
                    fromCache = true
                )
            }
            
            // 5. 添加到任务管理器
            val taskId = taskManager.addTask(task)
            
            // 6. 等待任务完成
            val finalResult = waitForTaskCompletion(taskId)
            
            // 7. 如果成功，分析结果
            val analyzedResult = if (finalResult.success) {
                analyzeCompileResult(finalResult, task, sourceFiles)
            } else {
                finalResult
            }
            
            // 8. 保存到缓存
            if (analyzedResult.success) {
                cacheManager.saveCache(
                    cacheKey, analyzedResult, emptyList(), 
                    sourceFiles.associateWith { "hash" }
                )
            }
            
            // 9. 保存历史记录
            saveCompileHistory(task, analyzedResult)
            
            CompileExecutionResult(
                success = analyzedResult.success,
                result = analyzedResult,
                taskId = taskId,
                fromCache = false
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Compile execution failed", e)
            CompileExecutionResult(
                success = false,
                error = "Compilation failed: ${e.message}"
            )
        }
    }

    /**
     * 批量编译用例
     */
    suspend fun executeBatchCompile(
        projects: List<BatchCompileProject>,
        maxConcurrent: Int = 3
    ): BatchCompileResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting batch compilation for ${projects.size} projects")
            
            val results = mutableListOf<CompileExecutionResult>()
            val semaphore = Semaphore(maxConcurrent)
            
            projects.map { project ->
                async(Dispatchers.IO) {
                    semaphore.acquire()
                    try {
                        val result = executeCompile(
                            project.projectPath,
                            project.sourceFiles,
                            project.language,
                            project.priority
                        )
                        project.id to result
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll().forEach { (projectId, result) ->
                results.add(result)
            }
            
            val successCount = results.count { it.success }
            val totalTime = results.maxOfOrNull { it.result?.executionTime ?: 0L } ?: 0L
            
            BatchCompileResult(
                totalProjects = projects.size,
                successCount = successCount,
                failureCount = projects.size - successCount,
                results = results.associateBy { it.taskId ?: "" },
                totalExecutionTime = totalTime
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Batch compilation failed", e)
            BatchCompileResult(
                totalProjects = projects.size,
                successCount = 0,
                failureCount = projects.size,
                results = emptyMap(),
                totalExecutionTime = 0
            )
        }
    }

    /**
     * 安装工具链用例
     */
    suspend fun installToolchain(language: Language): InstallationResult {
        return toolchainManager.installToolchain(language)
    }

    /**
     * 检查工具链更新用例
     */
    suspend fun checkToolchainUpdates(): List<ToolchainUpdate> {
        return toolchainManager.checkForUpdates()
    }

    /**
     * 获取编译统计用例
     */
    suspend fun getCompilationStatistics(): CompileStatistics {
        return historyManager.getCompileStatistics()
    }

    /**
     * 获取项目统计用例
     */
    suspend fun getProjectStatistics(projectPath: String): ProjectStatistics {
        return historyManager.getProjectStatistics(projectPath)
    }

    /**
     * 清理缓存用例
     */
    suspend fun clearCache(): Boolean {
        return cacheManager.clearAllCache()
    }

    /**
     * 清理历史用例
     */
    suspend fun clearHistory(): Boolean {
        return historyManager.clearAllHistory()
    }

    /**
     * 导出数据用例
     */
    suspend fun exportData(format: ExportFormat): ExportResult {
        return historyManager.exportHistoryData(format)
    }

    /**
     * 取消编译任务用例
     */
    suspend fun cancelCompileTask(taskId: String): Boolean {
        return taskManager.cancelTask(taskId)
    }

    /**
     * 获取编译建议用例
     */
    suspend fun getCompilationRecommendations(
        projectPath: String
    ): List<CompilationRecommendation> = withContext(Dispatchers.IO) {
        try {
            val projectStats = getProjectStatistics(projectPath)
            val recommendations = mutableListOf<CompilationRecommendation>()
            
            // 基于项目统计生成建议
            if (projectStats.successRate < 0.7) {
                recommendations.add(
                    CompilationRecommendation(
                        type = RecommendationType.QUALITY,
                        priority = Priority.HIGH,
                        title = "Improve Compilation Success Rate",
                        description = "Your project has a low success rate (${(projectStats.successRate * 100).toInt()}%). Consider fixing common errors.",
                        impact = Impact.HIGH,
                        effort = Effort.MEDIUM
                    )
                )
            }
            
            if (projectStats.averageExecutionTime > 30000) {
                recommendations.add(
                    CompilationRecommendation(
                        type = RecommendationType.PERFORMANCE,
                        priority = Priority.MEDIUM,
                        title = "Optimize Compilation Speed",
                        description = "Compilation is taking an average of ${projectStats.averageExecutionTime / 1000} seconds.",
                        impact = Impact.MEDIUM,
                        effort = Effort.MEDIUM
                    )
                )
            }
            
            recommendations
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get compilation recommendations", e)
            emptyList()
        }
    }

    // 私有辅助方法
    private suspend fun waitForTaskCompletion(taskId: String): CompileResult = withTimeout(300000L) {
        while (true) {
            val task = taskManager.getTaskState(taskId)
            if (task?.isCompleted() == true) {
                return@withTimeout when {
                    task.isSuccessful() -> CompileResult(
                        taskId = taskId,
                        success = true,
                        executionTime = task.getExecutionTime(),
                        outputFiles = listOf(), // 需要从实际结果获取
                        performanceMetrics = PerformanceMetrics(
                            compilationTime = task.getExecutionTime(),
                            fileCount = task.sourceFiles.size,
                            linesProcessed = task.sourceFiles.size * 100, // 估算
                            modulesCount = 1,
                            compilationSpeed = 1000.0 // 估算
                        )
                    )
                    task.isFailed() -> CompileResult(
                        taskId = taskId,
                        success = false,
                        executionTime = task.getExecutionTime(),
                        errors = listOf(CompileError(
                            line = 0,
                            column = 0,
                            message = task.errorOutput,
                            severity = ErrorSeverity.ERROR
                        ))
                    )
                    else -> CompileResult(
                        taskId = taskId,
                        success = false,
                        executionTime = task.getExecutionTime()
                    )
                }
            }
            delay(100)
        }
    }

    private suspend fun analyzeCompileResult(
        result: CompileResult,
        task: CompileTask,
        sourceFiles: List<String>
    ): CompileResult {
        return try {
            val analysis = resultAnalyzer.analyzeResult(result, task, sourceFiles)
            val report = resultAnalyzer.generateReport(analysis, task, result)
            
            // 更新结果，添加分析信息
            result.copy(
                performanceMetrics = result.performanceMetrics.copy(
                    compilationSpeed = analysis.performanceAnalysis.compilationSpeed
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to analyze compile result", e)
            result
        }
    }

    private suspend fun saveCompileHistory(task: CompileTask, result: CompileResult) {
        try {
            val deviceInfo = DeviceInfo(
                androidVersion = "Android",
                apiLevel = 30,
                deviceModel = "Unknown",
                cpuArchitecture = "arm64",
                availableProcessors = Runtime.getRuntime().availableProcessors(),
                totalMemory = Runtime.getRuntime().totalMemory(),
                availableMemory = Runtime.getRuntime().freeMemory()
            )
            
            historyManager.saveCompileHistory(
                task = task,
                result = result,
                deviceInfo = deviceInfo,
                environmentHash = "env_hash",
                fileHashes = task.sourceFiles.associateWith { "hash" }
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save compile history", e)
        }
    }

    private fun createCompilerConfig(language: Language): CompilerConfig {
        return when (language) {
            Language.JAVA -> CompilerConfig(
                compilerCommand = "javac",
                compilerArgs = listOf("-cp", "."),
                optimizationLevel = OptimizationLevel.MORE,
                debugSymbols = false
            )
            Language.JAVASCRIPT -> CompilerConfig(
                compilerCommand = "node",
                compilerArgs = listOf("-c"),
                optimizationLevel = OptimizationLevel.MORE
            )
            Language.PYTHON -> CompilerConfig(
                compilerCommand = "python3",
                compilerArgs = listOf("-m", "py_compile"),
                optimizationLevel = OptimizationLevel.MORE
            )
            Language.CPP -> CompilerConfig(
                compilerCommand = "g++",
                compilerArgs = listOf("-std=c++17"),
                optimizationLevel = OptimizationLevel.MORE,
                debugSymbols = false
            )
            Language.C -> CompilerConfig(
                compilerCommand = "gcc",
                compilerArgs = listOf("-std=c11"),
                optimizationLevel = OptimizationLevel.MORE,
                debugSymbols = false
            )
            Language.RUST -> CompilerConfig(
                compilerCommand = "rustc",
                compilerArgs = listOf(),
                optimizationLevel = OptimizationLevel.MORE,
                debugSymbols = false
            )
            Language.GO -> CompilerConfig(
                compilerCommand = "go",
                compilerArgs = listOf("build"),
                optimizationLevel = OptimizationLevel.MORE
            )
        }
    }

    private fun getEnvironmentVariables(language: Language): Map<String, String> {
        return when (language) {
            Language.JAVA -> mapOf(
                "JAVA_HOME" to "/usr/lib/jvm/default-java",
                "PATH" to "\$JAVA_HOME/bin:\$PATH"
            )
            Language.PYTHON -> mapOf(
                "PYTHONPATH" to ".",
                "PATH" to ".:\$PATH"
            )
            else -> emptyMap()
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        scope.cancel()
    }
}

/**
 * 编译执行结果
 */
data class CompileExecutionResult(
    val success: Boolean,
    val result: CompileResult? = null,
    val taskId: String? = null,
    val fromCache: Boolean = false,
    val error: String? = null
)

/**
 * 批量编译项目
 */
data class BatchCompileProject(
    val id: String,
    val projectPath: String,
    val sourceFiles: List<String>,
    val language: Language,
    val priority: TaskPriority = TaskPriority.NORMAL
)

/**
 * 批量编译结果
 */
data class BatchCompileResult(
    val totalProjects: Int,
    val successCount: Int,
    val failureCount: Int,
    val results: Map<String, CompileExecutionResult>,
    val totalExecutionTime: Long
)

/**
 * 编译建议
 */
data class CompilationRecommendation(
    val type: RecommendationType,
    val priority: Priority,
    val title: String,
    val description: String,
    val impact: Impact,
    val effort: Effort
)

enum class RecommendationType {
    QUALITY, PERFORMANCE, DEPENDENCY, GENERAL
}

enum class Priority {
    HIGH, MEDIUM, LOW
}

enum class Impact {
    HIGH, MEDIUM, LOW
}

enum class Effort {
    HIGH, MEDIUM, LOW
}