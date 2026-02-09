package com.codemate.features.ai.data.repository

import com.codemate.features.ai.domain.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 服务监控器
 * 监控AI服务的健康状态和性能指标
 */
@Singleton
class ServiceMonitor @Inject constructor(
    private val metricsDatabase: MetricsDatabase
) {
    private val _serviceStatuses = ConcurrentHashMap<AIProvider, MutableStateFlow<ServiceHealthStatus>>()
    private val _monitorInfo = ConcurrentHashMap<String, ServiceMonitorInfo>()
    
    init {
        // 初始化所有服务状态
        AIProvider.values().forEach { provider ->
            _serviceStatuses[provider] = MutableStateFlow(
                ServiceHealthStatus(
                    isHealthy = false,
                    status = HealthStatus.UNKNOWN
                )
            )
        }
    }
    
    /**
     * 更新服务健康状态
     */
    fun updateHealthStatus(provider: AIProvider, status: ServiceHealthStatus) {
        _serviceStatuses[provider]?.value = status
        
        // 更新监控信息
        val currentInfo = _monitorInfo[provider.name] ?: ServiceMonitorInfo(
            serviceId = provider.name,
            provider = provider
        )
        
        val updatedInfo = currentInfo.copy(
            lastPingTime = status.lastCheckTime,
            averageResponseTime = calculateAverageResponseTime(provider),
            successRate = calculateSuccessRate(provider)
        )
        
        _monitorInfo[provider.name] = updatedInfo
    }
    
    /**
     * 记录请求
     */
    fun recordRequest(
        provider: AIProvider,
        success: Boolean,
        responseTime: Long,
        error: String? = null
    ) {
        val currentInfo = _monitorInfo[provider.name] ?: ServiceMonitorInfo(
            serviceId = provider.name,
            provider = provider
        )
        
        val updatedInfo = currentInfo.copy(
            totalRequests = currentInfo.totalRequests + 1,
            failedRequests = if (success) currentInfo.failedRequests else currentInfo.failedRequests + 1,
            lastErrors = updateLastErrors(currentInfo.lastErrors, error)
        )
        
        _monitorInfo[provider.name] = updatedInfo
        
        // 保存到数据库
        metricsDatabase.saveRequestMetrics(
            provider = provider,
            success = success,
            responseTime = responseTime,
            error = error
        )
    }
    
    /**
     * 记录网络请求
     */
    fun recordRequest(
        endpoint: String,
        method: String,
        responseCode: Int,
        responseTime: Long
    ) {
        // 更新网络监控指标
        metricsDatabase.saveNetworkMetrics(
            endpoint = endpoint,
            method = method,
            responseCode = responseCode,
            responseTime = responseTime
        )
    }
    
    /**
     * 记录网络错误
     */
    fun recordError(
        endpoint: String,
        method: String,
        error: String?,
        responseTime: Long
    ) {
        metricsDatabase.saveNetworkError(
            endpoint = endpoint,
            method = method,
            error = error ?: "未知错误",
            responseTime = responseTime
        )
    }
    
    /**
     * 获取服务监控信息
     */
    fun getServiceMonitorInfo(provider: AIProvider): ServiceMonitorInfo? {
        return _monitorInfo[provider.name]
    }
    
    /**
     * 获取所有服务监控信息
     */
    fun getAllServiceMonitorInfo(): Map<AIProvider, ServiceMonitorInfo> {
        return _monitorInfo.entries.associateNotNull { (key, value) ->
            try {
                AIProvider.valueOf(key) to value
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
    
    /**
     * 获取性能统计
     */
    suspend fun getPerformanceStats(
        provider: AIProvider,
        startTime: Long,
        endTime: Long
    ): PerformanceStats {
        return metricsDatabase.getPerformanceStats(provider, startTime, endTime)
    }
    
    /**
     * 获取错误统计
     */
    suspend fun getErrorStats(
        provider: AIProvider,
        startTime: Long,
        endTime: Long
    ): ErrorStats {
        return metricsDatabase.getErrorStats(provider, startTime, endTime)
    }
    
    /**
     * 获取成功率
     */
    private fun calculateSuccessRate(provider: AIProvider): Float {
        val info = _monitorInfo[provider.name] ?: return 0.0f
        
        return if (info.totalRequests > 0) {
            (info.totalRequests - info.failedRequests).toFloat() / info.totalRequests
        } else {
            0.0f
        }
    }
    
    /**
     * 计算平均响应时间
     */
    private fun calculateAverageResponseTime(provider: AIProvider): Long {
        // 这里应该从数据库中计算
        // 为了演示，返回默认值
        return 1000L
    }
    
    /**
     * 更新最后错误列表
     */
    private fun updateLastErrors(lastErrors: List<String>, newError: String?): List<String> {
        val updatedErrors = if (newError != null) {
            listOf(newError) + lastErrors
        } else {
            lastErrors
        }
        
        // 只保留最近的10个错误
        return updatedErrors.take(10)
    }
    
    /**
     * 重置监控数据
     */
    suspend fun resetMonitoringData(provider: AIProvider) {
        _monitorInfo.remove(provider.name)
        metricsDatabase.clearProviderMetrics(provider)
    }
    
    /**
     * 生成监控报告
     */
    suspend fun generateMonitoringReport(
        startTime: Long,
        endTime: Long
    ): MonitoringReport {
        val allProviders = AIProvider.values()
        val providerStats = mutableMapOf<AIProvider, PerformanceStats>()
        
        allProviders.forEach { provider ->
            providerStats[provider] = getPerformanceStats(provider, startTime, endTime)
        }
        
        return MonitoringReport(
            generatedAt = System.currentTimeMillis(),
            timeRange = TimeRange(startTime, endTime),
            providerStats = providerStats,
            overallHealth = calculateOverallHealth(providerStats.values.toList())
        )
    }
    
    /**
     * 计算整体健康状态
     */
    private fun calculateOverallHealth(stats: List<PerformanceStats>): HealthStatus {
        val healthyProviders = stats.count { it.successRate > 0.95f }
        val totalProviders = stats.size
        
        return when {
            healthyProviders == totalProviders -> HealthStatus.HEALTHY
            healthyProviders > totalProviders * 0.5 -> HealthStatus.DEGRADED
            else -> HealthStatus.UNHEALTHY
        }
    }
}

/**
 * 性能统计
 */
data class PerformanceStats(
    val totalRequests: Int,
    val successfulRequests: Int,
    val failedRequests: Int,
    val successRate: Float,
    val averageResponseTime: Long,
    val minResponseTime: Long,
    val maxResponseTime: Long,
    val p95ResponseTime: Long,
    val p99ResponseTime: Long
)

/**
 * 错误统计
 */
data class ErrorStats(
    val totalErrors: Int,
    val errorTypes: Map<String, Int>,
    val lastErrors: List<String>,
    val errorRate: Float
)

/**
 * 监控报告
 */
data class MonitoringReport(
    val generatedAt: Long,
    val timeRange: TimeRange,
    val providerStats: Map<AIProvider, PerformanceStats>,
    val overallHealth: HealthStatus
)

/**
 * 时间范围
 */
data class TimeRange(
    val startTime: Long,
    val endTime: Long
)