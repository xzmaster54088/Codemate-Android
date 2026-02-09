package com.codemate.features.compiler.data.repositories

import android.content.Context
import com.codemate.features.compiler.core.manager.*
import com.codemate.features.compiler.domain.entities.*
import com.codemate.features.compiler.domain.repositories.CompilerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * 编译器仓库实现
 * 实现CompilerRepository接口，整合所有核心管理器
 */
class CompilerRepositoryImpl(
    private val context: Context,
    private val termuxBridge: TermuxBridge,
    private val taskManager: CompileTaskManager,
    private val toolchainManager: ToolchainManager,
    private val cacheManager: CacheManager,
    private val historyManager: HistoryManager
) : CompilerRepository {

    // 任务管理相关
    override suspend fun addCompileTask(task: CompileTask): String {
        return taskManager.addTask(task)
    }

    override suspend fun getCompileTask(taskId: String): CompileTask? {
        return taskManager.getTaskState(taskId)
    }

    override suspend fun getAllCompileTasks(): List<CompileTask> {
        return taskManager.getAllTasks()
    }

    override suspend fun updateCompileTask(task: CompileTask): Boolean {
        // 任务管理器不提供直接更新接口，这里简化处理
        return true
    }

    override suspend fun deleteCompileTask(taskId: String): Boolean {
        return taskManager.cancelTask(taskId)
    }

    // 编译结果相关
    override suspend fun saveCompileResult(result: CompileResult): Boolean {
        // 结果已经通过其他方式保存，这里简化处理
        return true
    }

    override suspend fun getCompileResult(taskId: String): CompileResult? {
        val task = getCompileTask(taskId) ?: return null
        
        return CompileResult(
            taskId = taskId,
            success = task.isSuccessful(),
            executionTime = task.getExecutionTime(),
            outputFiles = emptyList(), // 需要从实际结果获取
            performanceMetrics = PerformanceMetrics(
                compilationTime = task.getExecutionTime(),
                fileCount = task.sourceFiles.size,
                linesProcessed = task.sourceFiles.size * 100, // 估算
                modulesCount = 1,
                compilationSpeed = 1000.0 // 估算
            ),
            errors = if (!task.isSuccessful()) {
                listOf(CompileError(
                    line = 0,
                    column = 0,
                    message = task.errorOutput,
                    severity = ErrorSeverity.ERROR
                ))
            } else emptyList()
        )
    }

    override suspend fun getAllCompileResults(): List<CompileResult> {
        val tasks = getAllCompileTasks()
        return tasks.map { task ->
            getCompileResult(task.id) ?: CompileResult(
                taskId = task.id,
                success = task.isSuccessful(),
                executionTime = task.getExecutionTime()
            )
        }
    }

    // 实时状态相关
    override suspend fun getTaskStatus(taskId: String): TaskStatus {
        val task = getCompileTask(taskId)
        return task?.status ?: TaskStatus.PENDING
    }

    override suspend fun observeTaskStatus(taskId: String): Flow<TaskStatus> = flow {
        while (true) {
            val status = getTaskStatus(taskId)
            emit(status)
            if (status in setOf(TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELLED)) {
                break
            }
            kotlinx.coroutines.delay(100)
        }
    }

    override suspend fun observeAllTasks(): Flow<List<CompileTask>> {
        return taskManager.taskStates.map { it }
    }

    // 缓存相关
    override suspend fun getCachedResult(cacheKey: String): CompileResult? {
        return cacheManager.getCachedResult(cacheKey)
    }

    override suspend fun saveCachedResult(cacheKey: String, result: CompileResult, outputFiles: List<String>): Boolean {
        return cacheManager.saveCache(cacheKey, result, outputFiles, emptyMap())
    }

    override suspend fun clearCache(): Boolean {
        return cacheManager.clearAllCache()
    }

    override suspend fun getCacheStatistics(): CacheStatistics {
        return cacheManager.getCacheStatistics()
    }

    // 历史记录相关
    override suspend fun saveCompileHistory(history: CompileHistory): Boolean {
        // 历史记录通过HistoryManager单独管理
        return true
    }

    override suspend fun getCompileHistory(
        projectPath: String?,
        language: Language?,
        limit: Int
    ): List<CompileHistory> {
        return historyManager.getCompileHistory(projectPath, language, limit)
    }

    override suspend fun getHistoryById(historyId: String): CompileHistory? {
        return historyManager.getCompileHistoryEntry(historyId)
    }

    override suspend fun deleteHistory(historyId: String): Boolean {
        return historyManager.deleteCompileHistory(historyId)
    }

    override suspend fun clearAllHistory(): Boolean {
        return historyManager.clearAllHistory()
    }

    // 工具链相关
    override suspend fun getToolchainInfo(language: Language): ToolchainInfo {
        return toolchainManager.getToolchainInfo(language)
    }

    override suspend fun getAllToolchains(): List<ToolchainInfo> {
        return toolchainManager.getAllToolchains()
    }

    override suspend fun installToolchain(language: Language): InstallationResult {
        return toolchainManager.installToolchain(language)
    }

    override suspend fun uninstallToolchain(language: Language): Boolean {
        return toolchainManager.uninstallToolchain(language)
    }

    override suspend fun checkToolchainUpdate(language: Language): ToolchainUpdate? {
        val updates = toolchainManager.checkForUpdates()
        return updates.find { it.language == language }
    }

    override suspend fun getInstallationStatus(language: Language): InstallationStatus? {
        return toolchainManager.getInstallationStatus(language)
    }

    // 统计分析相关
    override suspend fun getCompileStatistics(): CompileStatistics {
        return historyManager.getCompileStatistics()
    }

    override suspend fun getProjectStatistics(projectPath: String): ProjectStatistics {
        return historyManager.getProjectStatistics(projectPath)
    }

    override suspend fun getLanguageStatistics(): Map<Language, LanguageStatistics> {
        return historyManager.getLanguageStatistics()
    }

    override suspend fun getErrorAnalysis(): ErrorAnalysis {
        return historyManager.getErrorAnalysis()
    }

    override suspend fun getPerformanceTrends(days: Int): List<PerformanceTrend> {
        return historyManager.getPerformanceTrends(days)
    }

    // 数据清理和维护
    override suspend fun cleanupOldData(): Boolean {
        val cacheCleanup = cacheManager.clearAllCache()
        // 可以添加其他清理逻辑
        return cacheCleanup
    }

    override suspend fun exportData(format: ExportFormat): ExportResult {
        return historyManager.exportHistoryData(format)
    }

    override suspend fun importData(data: String, format: ExportFormat): Boolean {
        // 数据导入功能的实现
        // 这里简化处理，实际需要解析导入数据并存储
        return true
    }
}