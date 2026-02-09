package com.codemate.features.compiler.domain.entities

import java.util.Date

/**
 * 编译历史记录
 */
data class CompileHistory(
    val id: String,
    val task: CompileTask,
    val result: CompileResult,
    val timestamp: Date = Date(),
    val deviceInfo: DeviceInfo,
    val environmentHash: String, // 环境哈希值，用于检测环境变化
    val fileHashes: Map<String, String> // 文件哈希值，用于检测文件变化
)

/**
 * 设备信息
 */
data class DeviceInfo(
    val androidVersion: String,
    val apiLevel: Int,
    val deviceModel: String,
    val cpuArchitecture: String,
    val availableProcessors: Int,
    val totalMemory: Long,
    val availableMemory: Long
)

/**
 * 编译缓存项
 */
data class CacheEntry(
    val cacheKey: String,
    val outputHash: String,
    val outputFiles: List<String>,
    val result: CompileResult,
    val createdAt: Date,
    val lastAccessed: Date,
    val accessCount: Int = 1,
    val size: Long // 缓存大小（字节）
) {
    /**
     * 更新访问信息
     */
    fun updateAccess(): CacheEntry = copy(
        lastAccessed = Date(),
        accessCount = accessCount + 1
    )
}

/**
 * 编译统计信息
 */
data class CompileStatistics(
    val totalTasks: Long = 0,
    val successfulTasks: Long = 0,
    val failedTasks: Long = 0,
    val averageExecutionTime: Double = 0.0,
    val totalExecutionTime: Long = 0,
    val cacheHitCount: Long = 0,
    val cacheMissCount: Long = 0,
    val languagesUsed: Map<Language, Long> = emptyMap(),
    val errorsByLanguage: Map<Language, Map<String, Long>> = emptyMap(),
    val performanceTrends: List<PerformanceTrend> = emptyList(),
    val popularProjects: List<ProjectStats> = emptyList(),
    val memoryUsage: MemoryStats = MemoryStats()
)

/**
 * 性能趋势
 */
data class PerformanceTrend(
    val date: Date,
    val averageTime: Long,
    val taskCount: Long,
    val successRate: Double
)

/**
 * 项目统计
 */
data class ProjectStats(
    val projectPath: String,
    val taskCount: Long,
    val averageTime: Long,
    val successRate: Double,
    val lastUsed: Date
)

/**
 * 内存使用统计
 */
data class MemoryStats(
    val peakMemoryUsage: Long = 0,
    val averageMemoryUsage: Double = 0.0,
    val memoryLeaks: List<MemoryLeak> = emptyList()
)

/**
 * 内存泄漏信息
 */
data class MemoryLeak(
    val projectPath: String,
    val detectedAt: Date,
    val leakedMemory: Long,
    val suspectedCause: String
)

/**
 * 实时输出事件
 */
sealed class OutputEvent {
    data class StandardOutput(val content: String) : OutputEvent()
    data class StandardError(val content: String) : OutputEvent()
    data class Progress(val progress: Int, val message: String) : OutputEvent()
    data class Info(val message: String) : OutputEvent()
    data class Warning(val message: String) : OutputEvent()
    data class Error(val message: String) : OutputEvent()
    object Completed : OutputEvent()
    data class Cancelled(val reason: String) : OutputEvent()
}

/**
 * Termux环境信息
 */
data class TermuxEnvironment(
    val isTermuxInstalled: Boolean,
    val isTermuxApiAvailable: Boolean,
    val supportedCommands: List<String>,
    val homeDirectory: String,
    val termuxDirectory: String,
    val storageDirectory: String,
    val sharedStorageDirectory: String,
    val writableDirectory: String,
    val environmentVariables: Map<String, String>
)