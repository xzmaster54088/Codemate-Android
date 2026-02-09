package com.codemate.features.compiler.domain.repositories

import com.codemate.features.compiler.domain.entities.*
import kotlinx.coroutines.flow.Flow

/**
 * 编译器仓库接口
 * 定义编译相关的所有数据操作接口
 */
interface CompilerRepository {
    
    // 任务管理相关
    suspend fun addCompileTask(task: CompileTask): String
    suspend fun getCompileTask(taskId: String): CompileTask?
    suspend fun getAllCompileTasks(): List<CompileTask>
    suspend fun updateCompileTask(task: CompileTask): Boolean
    suspend fun deleteCompileTask(taskId: String): Boolean
    
    // 编译结果相关
    suspend fun saveCompileResult(result: CompileResult): Boolean
    suspend fun getCompileResult(taskId: String): CompileResult?
    suspend fun getAllCompileResults(): List<CompileResult>
    
    // 实时状态相关
    suspend fun getTaskStatus(taskId: String): TaskStatus
    suspend fun observeTaskStatus(taskId: String): Flow<TaskStatus>
    suspend fun observeAllTasks(): Flow<List<CompileTask>>
    
    // 缓存相关
    suspend fun getCachedResult(cacheKey: String): CompileResult?
    suspend fun saveCachedResult(cacheKey: String, result: CompileResult, outputFiles: List<String>): Boolean
    suspend fun clearCache(): Boolean
    suspend fun getCacheStatistics(): CacheStatistics
    
    // 历史记录相关
    suspend fun saveCompileHistory(history: CompileHistory): Boolean
    suspend fun getCompileHistory(projectPath: String? = null, language: Language? = null, limit: Int = 50): List<CompileHistory>
    suspend fun getHistoryById(historyId: String): CompileHistory?
    suspend fun deleteHistory(historyId: String): Boolean
    suspend fun clearAllHistory(): Boolean
    
    // 工具链相关
    suspend fun getToolchainInfo(language: Language): ToolchainInfo
    suspend fun getAllToolchains(): List<ToolchainInfo>
    suspend fun installToolchain(language: Language): InstallationResult
    suspend fun uninstallToolchain(language: Language): Boolean
    suspend fun checkToolchainUpdate(language: Language): ToolchainUpdate?
    suspend fun getInstallationStatus(language: Language): InstallationStatus?
    
    // 统计分析相关
    suspend fun getCompileStatistics(): CompileStatistics
    suspend fun getProjectStatistics(projectPath: String): ProjectStatistics
    suspend fun getLanguageStatistics(): Map<Language, LanguageStatistics>
    suspend fun getErrorAnalysis(): ErrorAnalysis
    suspend fun getPerformanceTrends(days: Int = 30): List<PerformanceTrend>
    
    // 数据清理和维护
    suspend fun cleanupOldData(): Boolean
    suspend fun exportData(format: ExportFormat): ExportResult
    suspend fun importData(data: String, format: ExportFormat): Boolean
}