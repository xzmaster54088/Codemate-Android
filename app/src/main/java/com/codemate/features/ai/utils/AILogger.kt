package com.codemate.features.ai.utils

import android.util.Log
import com.codemate.features.ai.utils.AIConstants.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI性能指标
 */
data class AIMetrics(
    val timestamp: Long = System.currentTimeMillis(),
    val provider: String,
    val model: String,
    val requestType: String,
    val responseTime: Long,
    val tokensUsed: Int,
    val success: Boolean,
    val errorCode: String? = null,
    val memoryUsage: Long = 0L,
    val cacheHit: Boolean = false
)

/**
 * AI日志条目
 */
data class AILogEntry(
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val throwable: Throwable? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 日志级别
 */
enum class LogLevel {
    VERBOSE, DEBUG, INFO, WARNING, ERROR
}

/**
 * 性能监控器
 * 收集和分析AI服务的性能指标
 */
@Singleton
class AIMetricsCollector @Inject constructor() {
    
    private val metrics = MutableStateFlow<List<AIMetrics>>(emptyList())
    val metricsFlow = metrics.asStateFlow()
    
    private val performanceStats = ConcurrentHashMap<String, MutableList<AIMetrics>>()
    
    /**
     * 记录指标
     */
    fun recordMetrics(metrics: AIMetrics) {
        val currentMetrics = metricsFlow.value.toMutableList()
        currentMetrics += metrics
        metricsFlow.value = currentMetrics.takeLast(1000) // 保留最近1000条记录
        
        // 更新性能统计
        val key = "${metrics.provider}_${metrics.model}"
        performanceStats.getOrPut(key) { mutableListOf() }.add(metrics)
    }
    
    /**
     * 获取性能统计
     */
    fun getPerformanceStats(provider: String, model: String): PerformanceStats {
        val key = "${provider}_${model}"
        val modelMetrics = performanceStats[key] ?: return PerformanceStats()
        
        val totalRequests = modelMetrics.size
        val successfulRequests = modelMetrics.count { it.success }
        val averageResponseTime = modelMetrics.map { it.responseTime }.average()
        val averageTokens = modelMetrics.map { it.tokensUsed }.average()
        val cacheHitRate = modelMetrics.count { it.cacheHit }.toFloat() / totalRequests
        
        return PerformanceStats(
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            successRate = successfulRequests.toFloat() / totalRequests,
            averageResponseTime = averageResponseTime.toLong(),
            averageTokens = averageTokens.toInt(),
            cacheHitRate = cacheHitRate
        )
    }
    
    /**
     * 获取总体统计
     */
    fun getOverallStats(): OverallStats {
        val allMetrics = metricsFlow.value
        if (allMetrics.isEmpty()) return OverallStats()
        
        val totalRequests = allMetrics.size
        val successfulRequests = allMetrics.count { it.success }
        val averageResponseTime = allMetrics.map { it.responseTime }.average()
        val totalTokens = allMetrics.sumOf { it.tokensUsed }
        val cacheHitRate = allMetrics.count { it.cacheHit }.toFloat() / totalRequests
        
        // 按提供商统计
        val providerStats = allMetrics.groupBy { it.provider }.mapValues { (_, metrics) ->
            val successful = metrics.count { it.success }
            metrics.size to successful
        }
        
        return OverallStats(
            totalRequests = totalRequests,
            successfulRequests = successfulRequests,
            successRate = successfulRequests.toFloat() / totalRequests,
            averageResponseTime = averageResponseTime.toLong(),
            totalTokens = totalTokens,
            cacheHitRate = cacheHitRate,
            providerStats = providerStats
        )
    }
    
    /**
     * 清除历史数据
     */
    fun clearHistory() {
        metricsFlow.value = emptyList()
        performanceStats.clear()
    }
}

/**
 * 性能统计
 */
data class PerformanceStats(
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val successRate: Float = 0f,
    val averageResponseTime: Long = 0L,
    val averageTokens: Int = 0,
    val cacheHitRate: Float = 0f
)

/**
 * 总体统计
 */
data class OverallStats(
    val totalRequests: Int = 0,
    val successfulRequests: Int = 0,
    val successRate: Float = 0f,
    val averageResponseTime: Long = 0L,
    val totalTokens: Int = 0,
    val cacheHitRate: Float = 0f,
    val providerStats: Map<String, Pair<Int, Int>> = emptyMap() // (total, successful)
)

/**
 * AI日志记录器
 * 提供结构化日志记录和事件流
 */
@Singleton
class AILogger @Inject constructor(
    private val metricsCollector: AIMetricsCollector
) {
    
    private val _logFlow = MutableSharedFlow<AILogEntry>()
    val logFlow = _logFlow.asSharedFlow()
    
    private val logBuffer = mutableListOf<AILogEntry>()
    private val maxBufferSize = 1000
    
    /**
     * 记录日志
     */
    suspend fun log(
        level: LogLevel,
        tag: String,
        message: String,
        throwable: Throwable? = null,
        metadata: Map<String, Any> = emptyMap()
    ) {
        val entry = AILogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            throwable = throwable,
            metadata = metadata
        )
        
        // 添加到缓冲区
        synchronized(logBuffer) {
            logBuffer.add(entry)
            if (logBuffer.size > maxBufferSize) {
                logBuffer.removeFirst()
            }
        }
        
        // 发送到事件流
        _logFlow.emit(entry)
        
        // 输出到Android Log
        when (level) {
            LogLevel.VERBOSE -> Log.v(tag, message, throwable)
            LogLevel.DEBUG -> Log.d(tag, message, throwable)
            LogLevel.INFO -> Log.i(tag, message, throwable)
            LogLevel.WARNING -> Log.w(tag, message, throwable)
            LogLevel.ERROR -> Log.e(tag, message, throwable)
        }
    }
    
    /**
     * 记录信息日志
     */
    suspend fun info(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        log(LogLevel.INFO, tag, message, metadata = metadata)
    }
    
    /**
     * 记录调试日志
     */
    suspend fun debug(tag: String, message: String, metadata: Map<String, Any> = emptyMap()) {
        log(LogLevel.DEBUG, tag, message, metadata = metadata)
    }
    
    /**
     * 记录警告日志
     */
    suspend fun warning(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.WARNING, tag, message, throwable)
    }
    
    /**
     * 记录错误日志
     */
    suspend fun error(tag: String, message: String, throwable: Throwable? = null) {
        log(LogLevel.ERROR, tag, message, throwable)
    }
    
    /**
     * 记录API调用
     */
    suspend fun logAPICall(
        provider: String,
        endpoint: String,
        method: String,
        responseTime: Long,
        statusCode: Int,
        tokensUsed: Int = 0
    ) {
        val success = statusCode in 200..299
        val metadata = mapOf(
            "provider" to provider,
            "endpoint" to endpoint,
            "method" to method,
            "statusCode" to statusCode,
            "tokensUsed" to tokensUsed
        )
        
        log(
            level = if (success) LogLevel.INFO else LogLevel.ERROR,
            tag = TAG_NETWORK,
            message = "$method $endpoint - ${statusCode} (${responseTime}ms, ${tokensUsed}t)",
            metadata = metadata
        )
        
        // 记录指标
        metricsCollector.recordMetrics(
            AIMetrics(
                provider = provider,
                model = "unknown", // 需要从响应中获取
                requestType = method,
                responseTime = responseTime,
                tokensUsed = tokensUsed,
                success = success,
                errorCode = if (success) null else statusCode.toString()
            )
        )
    }
    
    /**
     * 记录内存使用
     */
    suspend fun logMemoryUsage(
        operation: String,
        memoryUsed: Long,
        memoryAvailable: Long,
        cacheSize: Long = 0
    ) {
        val metadata = mapOf(
            "memoryUsed" to memoryUsed,
            "memoryAvailable" to memoryAvailable,
            "cacheSize" to cacheSize,
            "usagePercent" to (memoryUsed.toFloat() / (memoryUsed + memoryAvailable) * 100)
        )
        
        log(
            level = LogLevel.DEBUG,
            tag = TAG_MEMORY,
            message = "$operation - 内存使用: ${formatBytes(memoryUsed)}/${formatBytes(memoryUsed + memoryAvailable)}",
            metadata = metadata
        )
    }
    
    /**
     * 获取最近的日志
     */
    fun getRecentLogs(count: Int = 100): List<AILogEntry> {
        synchronized(logBuffer) {
            return logBuffer.takeLast(count)
        }
    }
    
    /**
     * 清除日志缓冲区
     */
    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
        }
    }
    
    /**
     * 格式化字节数
     */
    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}