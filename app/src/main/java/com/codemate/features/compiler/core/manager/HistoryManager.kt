package com.codemate.features.compiler.core.manager

import android.content.Context
import android.util.Log
import com.codemate.features.compiler.domain.entities.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 编译历史和统计管理器
 * 负责保存编译记录、性能统计、错误分析等
 * 提供历史查询、趋势分析和数据导出功能
 */
class HistoryManager(private val context: Context) {
    companion object {
        private const val TAG = "HistoryManager"
        private const val HISTORY_DIR = "compile_history"
        private const val STATISTICS_FILE = "statistics.json"
        private const val HISTORY_INDEX_FILE = "history_index.json"
        private const val MAX_HISTORY_ENTRIES = 10000
        private const val CLEANUP_THRESHOLD = 0.8 // 80%时清理
        
        // 统计更新周期
        private const val STATISTICS_UPDATE_INTERVAL = 60000L // 1分钟
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val historyDir: File = File(context.filesDir, HISTORY_DIR)
    private val statisticsFile: File = File(historyDir, STATISTICS_FILE)
    private val indexFile: File = File(historyDir, HISTORY_INDEX_FILE)
    
    // 内存缓存
    private val historyCache = ConcurrentHashMap<String, CompileHistory>()
    private val statisticsCache = AtomicReference<CompileStatistics>()
    
    // 统计计数器
    private val entryCounter = AtomicLong(0)
    private val lastStatisticsUpdate = AtomicLong(0)
    
    // JSON序列化器
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
    }

    init {
        // 确保历史目录存在
        historyDir.mkdirs()
        scope.launch {
            loadStatistics()
            loadHistoryIndex()
            cleanupOldEntries()
        }
    }

    /**
     * 保存编译历史记录
     */
    suspend fun saveCompileHistory(
        task: CompileTask,
        result: CompileResult,
        deviceInfo: DeviceInfo,
        environmentHash: String,
        fileHashes: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val historyId = generateHistoryId()
            val timestamp = System.currentTimeMillis()
            
            val historyEntry = CompileHistory(
                id = historyId,
                task = task,
                result = result,
                timestamp = java.util.Date(timestamp),
                deviceInfo = deviceInfo,
                environmentHash = environmentHash,
                fileHashes = fileHashes
            )
            
            // 保存到内存缓存
            historyCache[historyId] = historyEntry
            
            // 保存到磁盘
            val historyFile = getHistoryFile(historyId)
            saveHistoryEntry(historyEntry, historyFile)
            
            // 更新索引
            updateHistoryIndex(historyId, timestamp, task.targetLanguage)
            
            // 更新统计信息
            updateStatistics(historyEntry)
            
            // 检查是否需要清理
            if (shouldCleanupHistory()) {
                scope.launch {
                    cleanupHistory()
                }
            }
            
            Log.d(TAG, "Saved compile history: $historyId")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save compile history", e)
            false
        }
    }

    /**
     * 获取编译历史
     */
    suspend fun getCompileHistory(
        projectPath: String? = null,
        language: Language? = null,
        limit: Int = 50,
        offset: Int = 0
    ): List<CompileHistory> = withContext(Dispatchers.IO) {
        try {
            var history = historyCache.values.toList()
            
            // 应用过滤器
            if (projectPath != null) {
                history = history.filter { it.task.projectPath == projectPath }
            }
            
            if (language != null) {
                history = history.filter { it.task.targetLanguage == language }
            }
            
            // 按时间排序
            history = history.sortedByDescending { it.timestamp }
            
            // 应用分页
            history.drop(offset).take(limit)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get compile history", e)
            emptyList()
        }
    }

    /**
     * 获取单个编译历史记录
     */
    suspend fun getCompileHistoryEntry(historyId: String): CompileHistory? = withContext(Dispatchers.IO) {
        // 先检查内存缓存
        historyCache[historyId]?.let { return@withContext it }
        
        // 从磁盘加载
        val historyFile = getHistoryFile(historyId)
        if (historyFile.exists()) {
            try {
                val historyJson = historyFile.readText()
                val historyEntry = json.decodeFromString<CompileHistory>(historyJson)
                historyCache[historyId] = historyEntry
                return@withContext historyEntry
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load history entry: $historyId", e)
            }
        }
        
        null
    }

    /**
     * 删除编译历史记录
     */
    suspend fun deleteCompileHistory(historyId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 从内存缓存移除
            historyCache.remove(historyId)
            
            // 从磁盘删除
            val historyFile = getHistoryFile(historyId)
            if (historyFile.exists()) {
                historyFile.delete()
            }
            
            // 从索引移除
            removeFromHistoryIndex(historyId)
            
            Log.d(TAG, "Deleted compile history: $historyId")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete compile history: $historyId", e)
            false
        }
    }

    /**
     * 清除所有编译历史
     */
    suspend fun clearAllHistory(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 清除内存缓存
            historyCache.clear()
            
            // 清除磁盘文件
            historyDir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    file.delete()
                }
            }
            
            // 重置统计
            statisticsCache.set(CompileStatistics())
            entryCounter.set(0)
            
            // 重新创建索引文件
            indexFile.writeText(json.encodeToString(HistoryIndex(emptyMap())))
            
            Log.i(TAG, "Cleared all compile history")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all history", e)
            false
        }
    }

    /**
     * 获取编译统计信息
     */
    suspend fun getCompileStatistics(): CompileStatistics = withContext(Dispatchers.IO) {
        // 检查是否需要更新统计
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastStatisticsUpdate.get() > STATISTICS_UPDATE_INTERVAL) {
            updateStatisticsFromCache()
        }
        
        statisticsCache.get() ?: CompileStatistics()
    }

    /**
     * 获取项目统计
     */
    suspend fun getProjectStatistics(projectPath: String): ProjectStatistics = withContext(Dispatchers.IO) {
        try {
            val projectHistory = historyCache.values
                .filter { it.task.projectPath == projectPath }
            
            if (projectHistory.isEmpty()) {
                return@withContext ProjectStatistics.empty(projectPath)
            }
            
            val totalTasks = projectHistory.size.toLong()
            val successfulTasks = projectHistory.count { it.result.success }.toLong()
            val failedTasks = totalTasks - successfulTasks
            
            val avgExecutionTime = projectHistory.map { it.result.executionTime }.average()
            val totalExecutionTime = projectHistory.sumOf { it.result.executionTime }
            
            val lastUsed = projectHistory.maxOf { it.timestamp }
            
            val languageUsage = projectHistory
                .groupBy { it.task.targetLanguage }
                .mapValues { (_, tasks) -> tasks.size.toLong() }
            
            val errorTypes = projectHistory
                .flatMap { it.result.errors }
                .groupBy { it.message }
                .mapValues { (_, errors) -> errors.size.toLong() }
            
            ProjectStatistics(
                projectPath = projectPath,
                totalTasks = totalTasks,
                successfulTasks = successfulTasks,
                failedTasks = failedTasks,
                successRate = if (totalTasks > 0) successfulTasks.toDouble() / totalTasks else 0.0,
                averageExecutionTime = avgExecutionTime.toLong(),
                totalExecutionTime = totalExecutionTime,
                lastUsed = java.util.Date(lastUsed),
                languageUsage = languageUsage,
                errorTypes = errorTypes,
                averageMemoryUsage = projectHistory.map { it.result.peakMemoryUsage }.average(),
                averageLinesProcessed = projectHistory.map { it.result.performanceMetrics.linesProcessed }.average()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get project statistics for: $projectPath", e)
            ProjectStatistics.empty(projectPath)
        }
    }

    /**
     * 获取语言使用统计
     */
    suspend fun getLanguageStatistics(): Map<Language, LanguageStatistics> = withContext(Dispatchers.IO) {
        try {
            val languageGroups = historyCache.values.groupBy { it.task.targetLanguage }
            
            languageGroups.mapValues { (_, history) ->
                val totalTasks = history.size.toLong()
                val successfulTasks = history.count { it.result.success }.toLong()
                val avgExecutionTime = history.map { it.result.executionTime }.average()
                
                val mostUsedProject = history
                    .groupBy { it.task.projectPath }
                    .maxByOrNull { it.value.size }?.key ?: ""
                
                LanguageStatistics(
                    language = history.first().task.targetLanguage,
                    totalTasks = totalTasks,
                    successfulTasks = successfulTasks,
                    successRate = if (totalTasks > 0) successfulTasks.toDouble() / totalTasks else 0.0,
                    averageExecutionTime = avgExecutionTime.toLong(),
                    mostUsedProject = mostUsedProject,
                    mostActiveDay = calculateMostActiveDay(history),
                    peakUsageHour = calculatePeakUsageHour(history)
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get language statistics", e)
            emptyMap()
        }
    }

    /**
     * 获取错误分析
     */
    suspend fun getErrorAnalysis(): ErrorAnalysis = withContext(Dispatchers.IO) {
        try {
            val allErrors = historyCache.values
                .flatMap { it.result.errors }
            
            if (allErrors.isEmpty()) {
                return@withContext ErrorAnalysis.empty()
            }
            
            val errorCounts = allErrors
                .groupingBy { it.message }
                .eachCount()
                .mapValues { it.value.toLong() }
            
            val errorByLanguage = allErrors
                .groupBy { it.task.targetLanguage }
                .mapValues { (_, errors) -> errors.size.toLong() }
            
            val errorBySeverity = allErrors
                .groupingBy { it.severity }
                .eachCount()
                .mapValues { it.value.toLong() }
            
            val topErrors = errorCounts.entries
                .sortedByDescending { it.value }
                .take(10)
                .map { (error, count) -> TopError(error, count) }
            
            val mostProblematicFiles = allErrors
                .filter { it.file.isNotEmpty() }
                .groupingBy { it.file }
                .eachCount()
                .mapValues { it.value.toLong() }
                .entries
                .sortedByDescending { it.value }
                .take(10)
                .map { (file, count) -> MostProblematicFile(file, count) }
            
            ErrorAnalysis(
                totalErrors = allErrors.size.toLong(),
                uniqueErrors = errorCounts.size,
                errorCounts = errorCounts,
                errorByLanguage = errorByLanguage,
                errorBySeverity = errorBySeverity,
                topErrors = topErrors,
                mostProblematicFiles = mostProblematicFiles,
                errorRate = allErrors.size.toDouble() / max(1, historyCache.size),
                mostCommonSeverity = errorBySeverity.maxByOrNull { it.value }?.key ?: ErrorSeverity.INFO
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get error analysis", e)
            ErrorAnalysis.empty()
        }
    }

    /**
     * 获取性能趋势
     */
    suspend fun getPerformanceTrends(days: Int = 30): List<PerformanceTrend> = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (days * 24 * 60 * 60 * 1000L)
            
            val recentHistory = historyCache.values
                .filter { it.timestamp.time >= cutoffTime }
                .sortedBy { it.timestamp }
            
            if (recentHistory.isEmpty()) {
                return@withContext emptyList()
            }
            
            // 按天分组
            val dailyGroups = recentHistory.groupBy { 
                val cal = java.util.Calendar.getInstance().apply { 
                    time = it.timestamp 
                }
                "${cal.get(java.util.Calendar.YEAR)}-${cal.get(java.util.Calendar.MONTH) + 1}-${cal.get(java.util.Calendar.DATE)}"
            }
            
            dailyGroups.map { (date, history) ->
                val avgTime = history.map { it.result.executionTime }.average()
                val taskCount = history.size.toLong()
                val successRate = history.count { it.result.success }.toDouble() / history.size
                
                PerformanceTrend(
                    date = java.util.Date(date.toLongOrNull() ?: System.currentTimeMillis()),
                    averageTime = avgTime.toLong(),
                    taskCount = taskCount,
                    successRate = successRate
                )
            }.sortedBy { it.date }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get performance trends", e)
            emptyList()
        }
    }

    /**
     * 导出历史数据
     */
    suspend fun exportHistoryData(format: ExportFormat): ExportResult = withContext(Dispatchers.IO) {
        try {
            val exportData = when (format) {
                ExportFormat.JSON -> exportToJson()
                ExportFormat.CSV -> exportToCsv()
                ExportFormat.TXT -> exportToText()
            }
            
            val exportFile = File(historyDir, "export_${System.currentTimeMillis()}.${format.extension}")
            exportFile.writeText(exportData)
            
            Log.i(TAG, "Exported history data to: ${exportFile.name}")
            ExportResult(
                success = true,
                filePath = exportFile.absolutePath,
                fileSize = exportFile.length(),
                recordCount = historyCache.size,
                message = "Successfully exported ${historyCache.size} records"
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export history data", e)
            ExportResult(
                success = false,
                message = "Export failed: ${e.message}"
            )
        }
    }

    /**
     * 获取缓存使用情况
     */
    suspend fun getHistoryUsage(): HistoryUsage = withContext(Dispatchers.IO) {
        try {
            val totalFiles = historyDir.listFiles()?.size ?: 0
            val totalSize = historyDir.walkTopDown().sumOf { it.length() }
            val currentEntries = historyCache.size
            
            HistoryUsage(
                entryCount = currentEntries,
                maxEntries = MAX_HISTORY_ENTRIES,
                totalSize = totalSize,
                fileCount = totalFiles,
                usagePercentage = (currentEntries * 100.0 / MAX_HISTORY_ENTRIES).toInt()
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get history usage", e)
            HistoryUsage(0, MAX_HISTORY_ENTRIES, 0, 0, 0)
        }
    }

    /**
     * 清理管理器资源
     */
    fun cleanup() {
        scope.cancel()
        saveStatistics()
    }

    // 私有方法
    private fun generateHistoryId(): String {
        return "hist_${entryCounter.incrementAndGet()}_${System.currentTimeMillis()}"
    }

    private fun getHistoryFile(historyId: String): File {
        return File(historyDir, "$historyId.json")
    }

    private fun saveHistoryEntry(entry: CompileHistory, file: File) {
        try {
            file.parentFile?.mkdirs()
            val jsonString = json.encodeToString(entry)
            file.writeText(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save history entry to: ${file.name}", e)
        }
    }

    private fun updateStatistics(historyEntry: CompileHistory) {
        val currentStats = statisticsCache.get() ?: CompileStatistics()
        
        val updatedStats = currentStats.copy(
            totalTasks = currentStats.totalTasks + 1,
            successfulTasks = if (historyEntry.result.success) {
                currentStats.successfulTasks + 1
            } else currentStats.successfulTasks,
            failedTasks = if (!historyEntry.result.success) {
                currentStats.failedTasks + 1
            } else currentStats.failedTasks,
            totalExecutionTime = currentStats.totalExecutionTime + historyEntry.result.executionTime,
            averageExecutionTime = if (currentStats.totalTasks > 0) {
                (currentStats.totalExecutionTime + historyEntry.result.executionTime).toDouble() / (currentStats.totalTasks + 1)
            } else historyEntry.result.executionTime.toDouble(),
            languagesUsed = currentStats.languagesUsed + 
                (historyEntry.task.targetLanguage to (currentStats.languagesUsed[historyEntry.task.targetLanguage] ?: 0) + 1)
        )
        
        statisticsCache.set(updatedStats)
        lastStatisticsUpdate.set(System.currentTimeMillis())
    }

    private suspend fun updateStatisticsFromCache() = withContext(Dispatchers.IO) {
        try {
            // 从磁盘加载所有历史记录并重新计算统计
            val allHistory = historyCache.values.toList()
            
            if (allHistory.isEmpty()) {
                statisticsCache.set(CompileStatistics())
                return@withContext
            }
            
            val stats = CompileStatistics(
                totalTasks = allHistory.size.toLong(),
                successfulTasks = allHistory.count { it.result.success }.toLong(),
                failedTasks = allHistory.count { !it.result.success }.toLong(),
                totalExecutionTime = allHistory.sumOf { it.result.executionTime },
                averageExecutionTime = allHistory.map { it.result.executionTime }.average(),
                languagesUsed = allHistory.groupBy { it.task.targetLanguage }
                    .mapValues { it.value.size.toLong() },
                cacheHitCount = 0, // 需要从其他地方获取
                cacheMissCount = 0,
                errorsByLanguage = allHistory
                    .groupBy { it.task.targetLanguage }
                    .mapValues { (_, history) ->
                        history.flatMap { it.result.errors }
                            .groupBy { it.message }
                            .mapValues { it.size.toLong() }
                    },
                performanceTrends = getPerformanceTrends(),
                popularProjects = calculatePopularProjects(allHistory),
                memoryUsage = calculateMemoryStats(allHistory)
            )
            
            statisticsCache.set(stats)
            lastStatisticsUpdate.set(System.currentTimeMillis())
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update statistics from cache", e)
        }
    }

    private fun updateHistoryIndex(historyId: String, timestamp: Long, language: Language) {
        try {
            val currentIndex = loadHistoryIndex()
            val updatedEntries = currentIndex.entries.toMutableMap()
            
            updatedEntries[historyId] = HistoryIndexEntry(
                timestamp = timestamp,
                language = language,
                projectPath = "" // 需要从历史记录获取
            )
            
            // 保持索引大小在合理范围内
            if (updatedEntries.size > MAX_HISTORY_ENTRIES) {
                val sortedEntries = updatedEntries.entries.sortedBy { it.value.timestamp }
                val entriesToRemove = sortedEntries.take(updatedEntries.size - MAX_HISTORY_ENTRIES)
                entriesToRemove.forEach { (id, _) ->
                    updatedEntries.remove(id)
                    // 同时删除对应的历史文件
                    getHistoryFile(id).delete()
                }
            }
            
            val newIndex = HistoryIndex(updatedEntries)
            indexFile.writeText(json.encodeToString(newIndex))
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update history index", e)
        }
    }

    private fun removeFromHistoryIndex(historyId: String) {
        try {
            val currentIndex = loadHistoryIndex()
            val updatedEntries = currentIndex.entries.toMutableMap()
            updatedEntries.remove(historyId)
            
            val newIndex = HistoryIndex(updatedEntries)
            indexFile.writeText(json.encodeToString(newIndex))
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove from history index", e)
        }
    }

    private suspend fun loadStatistics() = withContext(Dispatchers.IO) {
        try {
            if (statisticsFile.exists()) {
                val statsJson = statisticsFile.readText()
                val stats = json.decodeFromString<CompileStatistics>(statsJson)
                statisticsCache.set(stats)
                lastStatisticsUpdate.set(System.currentTimeMillis())
            } else {
                statisticsCache.set(CompileStatistics())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load statistics", e)
            statisticsCache.set(CompileStatistics())
        }
    }

    private fun saveStatistics() {
        try {
            statisticsCache.get()?.let { stats ->
                statisticsFile.writeText(json.encodeToString(stats))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save statistics", e)
        }
    }

    private fun loadHistoryIndex(): HistoryIndex {
        return try {
            if (indexFile.exists()) {
                val indexJson = indexFile.readText()
                json.decodeFromString<HistoryIndex>(indexJson)
            } else {
                HistoryIndex(emptyMap())
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load history index", e)
            HistoryIndex(emptyMap())
        }
    }

    private suspend fun loadHistoryIndex() = withContext(Dispatchers.IO) {
        val index = loadHistoryIndex()
        Log.d(TAG, "Loaded history index with ${index.entries.size} entries")
    }

    private suspend fun cleanupOldEntries() = withContext(Dispatchers.IO) {
        try {
            val maxAge = 90L * 24 * 60 * 60 * 1000L // 90天
            val cutoffTime = System.currentTimeMillis() - maxAge
            
            val entriesToRemove = historyCache.values
                .filter { it.timestamp.time < cutoffTime }
                .map { it.id }
            
            entriesToRemove.forEach { id ->
                deleteCompileHistory(id)
            }
            
            if (entriesToRemove.isNotEmpty()) {
                Log.i(TAG, "Cleaned up ${entriesToRemove.size} old history entries")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old entries", e)
        }
    }

    private suspend fun cleanupHistory() {
        try {
            if (!shouldCleanupHistory()) return
            
            val usage = getHistoryUsage()
            val entriesToRemove = (usage.entryCount * 0.2).toInt() // 移除20%
            
            val oldestEntries = historyCache.values
                .sortedBy { it.timestamp }
                .take(entriesToRemove)
            
            oldestEntries.forEach { entry ->
                deleteCompileHistory(entry.id)
            }
            
            Log.i(TAG, "Cleaned up ${oldestEntries.size} history entries")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup history", e)
        }
    }

    private fun shouldCleanupHistory(): Boolean {
        val usage = runBlocking { getHistoryUsage() }
        return usage.entryCount >= MAX_HISTORY_ENTRIES * CLEANUP_THRESHOLD
    }

    private fun calculateMostActiveDay(history: List<CompileHistory>): String {
        val dayGroups = history.groupBy { 
            val cal = java.util.Calendar.getInstance().apply { time = it.timestamp }
            cal.get(java.util.Calendar.DAY_OF_WEEK)
        }
        
        return dayGroups.maxByOrNull { it.value.size }?.key?.let { day ->
            java.util.Calendar.DAY_OF_WEEK_NAMES[day] ?: "Unknown"
        } ?: "Unknown"
    }

    private fun calculatePeakUsageHour(history: List<CompileHistory>): Int {
        val hourGroups = history.groupBy { 
            val cal = java.util.Calendar.getInstance().apply { time = it.timestamp }
            cal.get(java.util.Calendar.HOUR_OF_DAY)
        }
        
        return hourGroups.maxByOrNull { it.value.size }?.key ?: 0
    }

    private fun calculatePopularProjects(history: List<CompileHistory>): List<ProjectStats> {
        return history
            .groupBy { it.task.projectPath }
            .map { (projectPath, projectHistory) ->
                ProjectStats(
                    projectPath = projectPath,
                    taskCount = projectHistory.size.toLong(),
                    averageTime = projectHistory.map { it.result.executionTime }.average().toLong(),
                    successRate = projectHistory.count { it.result.success }.toDouble() / projectHistory.size,
                    lastUsed = projectHistory.maxOf { it.timestamp }
                )
            }
            .sortedByDescending { it.taskCount }
            .take(10)
    }

    private fun calculateMemoryStats(history: List<CompileHistory>): MemoryStats {
        val peakMemory = history.maxOfOrNull { it.result.peakMemoryUsage } ?: 0L
        val avgMemory = history.map { it.result.peakMemoryUsage }.average()
        
        return MemoryStats(
            peakMemoryUsage = peakMemory,
            averageMemoryUsage = avgMemory,
            memoryLeaks = emptyList() // 简化处理
        )
    }

    private fun getPerformanceTrends(): List<PerformanceTrend> {
        // 简化实现，返回空列表
        return emptyList()
    }

    private suspend fun exportToJson(): String = withContext(Dispatchers.IO) {
        val exportData = ExportData(
            version = "1.0",
            exportDate = System.currentTimeMillis(),
            totalRecords = historyCache.size,
            records = historyCache.values.toList(),
            statistics = getCompileStatistics()
        )
        json.encodeToString(exportData)
    }

    private suspend fun exportToCsv(): String = withContext(Dispatchers.IO) {
        val headers = "ID,Project Path,Language,Success,Execution Time,Memory Usage,Date\n"
        val rows = historyCache.values.joinToString("\n") { history ->
            "${history.id},${history.task.projectPath},${history.task.targetLanguage.displayName}," +
            "${history.result.success},${history.result.executionTime}," +
            "${history.result.peakMemoryUsage},${history.timestamp.time}"
        }
        headers + rows
    }

    private suspend fun exportToText(): String = withContext(Dispatchers.IO) {
        val stats = getCompileStatistics()
        buildString {
            appendLine("CodeMate Mobile Compile History Report")
            appendLine("=====================================")
            appendLine("Generated: ${java.util.Date()}")
            appendLine()
            appendLine("Statistics:")
            appendLine("- Total Tasks: ${stats.totalTasks}")
            appendLine("- Successful Tasks: ${stats.successfulTasks}")
            appendLine("- Failed Tasks: ${stats.failedTasks}")
            appendLine("- Average Execution Time: ${stats.averageExecutionTime}ms")
            appendLine()
            appendLine("Language Usage:")
            stats.languagesUsed.forEach { (lang, count) ->
                appendLine("- ${lang.displayName}: $count")
            }
            appendLine()
            appendLine("Recent History:")
            historyCache.values
                .sortedByDescending { it.timestamp }
                .take(20)
                .forEach { history ->
                    appendLine("- ${history.timestamp}: ${history.task.targetLanguage.displayName} - " +
                            "${history.task.projectPath} - " +
                            "${if (history.result.success) "SUCCESS" else "FAILED"}")
                }
        }
    }
}

// 数据类定义
data class HistoryIndex(
    val entries: Map<String, HistoryIndexEntry>
)

data class HistoryIndexEntry(
    val timestamp: Long,
    val language: Language,
    val projectPath: String
)

data class ProjectStatistics(
    val projectPath: String,
    val totalTasks: Long,
    val successfulTasks: Long,
    val failedTasks: Long,
    val successRate: Double,
    val averageExecutionTime: Long,
    val totalExecutionTime: Long,
    val lastUsed: Date,
    val languageUsage: Map<Language, Long>,
    val errorTypes: Map<String, Long>,
    val averageMemoryUsage: Double,
    val averageLinesProcessed: Double
) {
    companion object {
        fun empty(projectPath: String) = ProjectStatistics(
            projectPath = projectPath,
            totalTasks = 0,
            successfulTasks = 0,
            failedTasks = 0,
            successRate = 0.0,
            averageExecutionTime = 0,
            totalExecutionTime = 0,
            lastUsed = Date(),
            languageUsage = emptyMap(),
            errorTypes = emptyMap(),
            averageMemoryUsage = 0.0,
            averageLinesProcessed = 0.0
        )
    }
}

data class LanguageStatistics(
    val language: Language,
    val totalTasks: Long,
    val successfulTasks: Long,
    val successRate: Double,
    val averageExecutionTime: Long,
    val mostUsedProject: String,
    val mostActiveDay: String,
    val peakUsageHour: Int
)

data class ErrorAnalysis(
    val totalErrors: Long,
    val uniqueErrors: Int,
    val errorCounts: Map<String, Long>,
    val errorByLanguage: Map<Language, Long>,
    val errorBySeverity: Map<ErrorSeverity, Long>,
    val topErrors: List<TopError>,
    val mostProblematicFiles: List<MostProblematicFile>,
    val errorRate: Double,
    val mostCommonSeverity: ErrorSeverity
) {
    companion object {
        fun empty() = ErrorAnalysis(
            totalErrors = 0,
            uniqueErrors = 0,
            errorCounts = emptyMap(),
            errorByLanguage = emptyMap(),
            errorBySeverity = emptyMap(),
            topErrors = emptyList(),
            mostProblematicFiles = emptyList(),
            errorRate = 0.0,
            mostCommonSeverity = ErrorSeverity.INFO
        )
    }
}

data class TopError(
    val errorMessage: String,
    val count: Long
)

data class MostProblematicFile(
    val filePath: String,
    val errorCount: Long
)

data class ExportResult(
    val success: Boolean,
    val filePath: String? = null,
    val fileSize: Long = 0,
    val recordCount: Int = 0,
    val message: String
)

enum class ExportFormat(val extension: String) {
    JSON("json"),
    CSV("csv"),
    TXT("txt")
}

data class ExportData(
    val version: String,
    val exportDate: Long,
    val totalRecords: Int,
    val records: List<CompileHistory>,
    val statistics: CompileStatistics
)

data class HistoryUsage(
    val entryCount: Int,
    val maxEntries: Int,
    val totalSize: Long,
    val fileCount: Int,
    val usagePercentage: Int
)

private val Calendar.DAY_OF_WEEK_NAMES = mapOf(
    1 to "Sunday",
    2 to "Monday",
    3 to "Tuesday",
    4 to "Wednesday",
    5 to "Thursday",
    6 to "Friday",
    7 to "Saturday"
)