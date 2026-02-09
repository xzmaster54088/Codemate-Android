package com.codemate.features.ai.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.codemate.features.ai.domain.entity.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 度量数据库
 * 存储AI服务的性能指标和监控数据
 */
@Singleton
class MetricsDatabase @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("metrics_db", Context.MODE_PRIVATE)
    
    companion object {
        private const val REQUEST_METRICS_KEY = "request_metrics"
        private const val NETWORK_METRICS_KEY = "network_metrics"
        private const val ERROR_METRICS_KEY = "error_metrics"
        private const val PERFORMANCE_METRICS_KEY = "performance_metrics"
        private const val MAX_METRICS_ENTRIES = 10000
        private const val METRICS_RETENTION_DAYS = 30
    }
    
    /**
     * 保存请求度量
     */
    suspend fun saveRequestMetrics(
        provider: AIProvider,
        success: Boolean,
        responseTime: Long,
        error: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val metrics = getRequestMetrics().toMutableList()
            metrics.add(
                RequestMetric(
                    provider = provider,
                    success = success,
                    responseTime = responseTime,
                    timestamp = System.currentTimeMillis(),
                    error = error
                )
            )
            
            // 保持数据量在限制内
            val trimmedMetrics = trimMetricsList(metrics, MAX_METRICS_ENTRIES)
            
            val json = gson.toJson(trimmedMetrics)
            prefs.edit().putString(REQUEST_METRICS_KEY, json).apply()
        } catch (e: Exception) {
            println("保存请求度量失败: ${e.message}")
        }
    }
    
    /**
     * 保存网络度量
     */
    suspend fun saveNetworkMetrics(
        endpoint: String,
        method: String,
        responseCode: Int,
        responseTime: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val metrics = getNetworkMetrics().toMutableList()
            metrics.add(
                NetworkMetric(
                    endpoint = endpoint,
                    method = method,
                    responseCode = responseCode,
                    responseTime = responseTime,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            val trimmedMetrics = trimMetricsList(metrics, MAX_METRICS_ENTRIES)
            val json = gson.toJson(trimmedMetrics)
            prefs.edit().putString(NETWORK_METRICS_KEY, json).apply()
        } catch (e: Exception) {
            println("保存网络度量失败: ${e.message}")
        }
    }
    
    /**
     * 保存网络错误
     */
    suspend fun saveNetworkError(
        endpoint: String,
        method: String,
        error: String,
        responseTime: Long
    ) = withContext(Dispatchers.IO) {
        try {
            val errors = getErrorMetrics().toMutableList()
            errors.add(
                ErrorMetric(
                    endpoint = endpoint,
                    method = method,
                    error = error,
                    responseTime = responseTime,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            val trimmedErrors = trimMetricsList(errors, MAX_METRICS_ENTRIES)
            val json = gson.toJson(trimmedErrors)
            prefs.edit().putString(ERROR_METRICS_KEY, json).apply()
        } catch (e: Exception) {
            println("保存错误度量失败: ${e.message}")
        }
    }
    
    /**
     * 保存性能度量
     */
    suspend fun savePerformanceMetric(
        provider: AIProvider,
        metricType: String,
        value: Double,
        unit: String
    ) = withContext(Dispatchers.IO) {
        try {
            val metrics = getPerformanceMetrics().toMutableList()
            metrics.add(
                PerformanceMetric(
                    provider = provider,
                    metricType = metricType,
                    value = value,
                    unit = unit,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            val trimmedMetrics = trimMetricsList(metrics, MAX_METRICS_ENTRIES)
            val json = gson.toJson(trimmedMetrics)
            prefs.edit().putString(PERFORMANCE_METRICS_KEY, json).apply()
        } catch (e: Exception) {
            println("保存性能度量失败: ${e.message}")
        }
    }
    
    /**
     * 获取性能统计
     */
    suspend fun getPerformanceStats(
        provider: AIProvider,
        startTime: Long,
        endTime: Long
    ): PerformanceStats = withContext(Dispatchers.IO) {
        try {
            val metrics = getRequestMetrics()
            val providerMetrics = metrics.filter { 
                it.provider == provider && 
                it.timestamp in startTime..endTime 
            }
            
            if (providerMetrics.isEmpty()) {
                return@withContext PerformanceStats(
                    totalRequests = 0,
                    successfulRequests = 0,
                    failedRequests = 0,
                    successRate = 0.0f,
                    averageResponseTime = 0L,
                    minResponseTime = 0L,
                    maxResponseTime = 0L,
                    p95ResponseTime = 0L,
                    p99ResponseTime = 0L
                )
            }
            
            val successfulMetrics = providerMetrics.filter { it.success }
            val responseTimes = providerMetrics.map { it.responseTime }.sorted()
            
            PerformanceStats(
                totalRequests = providerMetrics.size,
                successfulRequests = successfulMetrics.size,
                failedRequests = providerMetrics.size - successfulMetrics.size,
                successRate = successfulMetrics.size.toFloat() / providerMetrics.size,
                averageResponseTime = responseTimes.average().toLong(),
                minResponseTime = responseTimes.first(),
                maxResponseTime = responseTimes.last(),
                p95ResponseTime = calculatePercentile(responseTimes, 95),
                p99ResponseTime = calculatePercentile(responseTimes, 99)
            )
        } catch (e: Exception) {
            PerformanceStats()
        }
    }
    
    /**
     * 获取错误统计
     */
    suspend fun getErrorStats(
        provider: AIProvider,
        startTime: Long,
        endTime: Long
    ): ErrorStats = withContext(Dispatchers.IO) {
        try {
            val metrics = getRequestMetrics()
            val errorMetrics = metrics.filter { 
                !it.success && 
                it.provider == provider && 
                it.timestamp in startTime..endTime 
            }
            
            val errorTypes = errorMetrics.groupBy { it.error ?: "Unknown" }
                .mapValues { it.value.size }
            
            ErrorStats(
                totalErrors = errorMetrics.size,
                errorTypes = errorTypes,
                lastErrors = errorMetrics.take(10).map { it.error ?: "Unknown error" },
                errorRate = if (metrics.isNotEmpty()) {
                    errorMetrics.size.toFloat() / metrics.size
                } else {
                    0.0f
                }
            )
        } catch (e: Exception) {
            ErrorStats()
        }
    }
    
    /**
     * 清理过期度量
     */
    suspend fun cleanupExpiredMetrics() = withContext(Dispatchers.IO) {
        try {
            val cutoffTime = System.currentTimeMillis() - (METRICS_RETENTION_DAYS * 24 * 60 * 60 * 1000L)
            
            // 清理请求度量
            val requestMetrics = getRequestMetrics().filter { it.timestamp > cutoffTime }
            val requestJson = gson.toJson(requestMetrics)
            prefs.edit().putString(REQUEST_METRICS_KEY, requestJson).apply()
            
            // 清理网络度量
            val networkMetrics = getNetworkMetrics().filter { it.timestamp > cutoffTime }
            val networkJson = gson.toJson(networkMetrics)
            prefs.edit().putString(NETWORK_METRICS_KEY, networkJson).apply()
            
            // 清理错误度量
            val errorMetrics = getErrorMetrics().filter { it.timestamp > cutoffTime }
            val errorJson = gson.toJson(errorMetrics)
            prefs.edit().putString(ERROR_METRICS_KEY, errorJson).apply()
            
            // 清理性能度量
            val performanceMetrics = getPerformanceMetrics().filter { it.timestamp > cutoffTime }
            val performanceJson = gson.toJson(performanceMetrics)
            prefs.edit().putString(PERFORMANCE_METRICS_KEY, performanceJson).apply()
        } catch (e: Exception) {
            println("清理过期度量失败: ${e.message}")
        }
    }
    
    /**
     * 清空所有度量数据
     */
    suspend fun clearAllMetrics() = withContext(Dispatchers.IO) {
        try {
            prefs.edit()
                .remove(REQUEST_METRICS_KEY)
                .remove(NETWORK_METRICS_KEY)
                .remove(ERROR_METRICS_KEY)
                .remove(PERFORMANCE_METRICS_KEY)
                .apply()
        } catch (e: Exception) {
            println("清空度量数据失败: ${e.message}")
        }
    }
    
    /**
     * 获取请求度量
     */
    private fun getRequestMetrics(): List<RequestMetric> {
        return try {
            val json = prefs.getString(REQUEST_METRICS_KEY, "[]")
            val type = object : TypeToken<List<RequestMetric>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取网络度量
     */
    private fun getNetworkMetrics(): List<NetworkMetric> {
        return try {
            val json = prefs.getString(NETWORK_METRICS_KEY, "[]")
            val type = object : TypeToken<List<NetworkMetric>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取错误度量
     */
    private fun getErrorMetrics(): List<ErrorMetric> {
        return try {
            val json = prefs.getString(ERROR_METRICS_KEY, "[]")
            val type = object : TypeToken<List<ErrorMetric>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取性能度量
     */
    private fun getPerformanceMetrics(): List<PerformanceMetric> {
        return try {
            val json = prefs.getString(PERFORMANCE_METRICS_KEY, "[]")
            val type = object : TypeToken<List<PerformanceMetric>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 清理度量列表（保持数据量在限制内）
     */
    private fun <T> trimMetricsList(metrics: List<T>, maxSize: Int): List<T> {
        return if (metrics.size > maxSize) {
            metrics.takeLast(maxSize)
        } else {
            metrics
        }
    }
    
    /**
     * 计算百分位数
     */
    private fun calculatePercentile(sortedList: List<Long>, percentile: Int): Long {
        if (sortedList.isEmpty()) return 0L
        
        val index = (percentile / 100.0 * (sortedList.size - 1)).toInt()
        return sortedList[index]
    }
    
    /**
     * 清除提供商度量
     */
    suspend fun clearProviderMetrics(provider: AIProvider) = withContext(Dispatchers.IO) {
        try {
            // 清理请求度量
            val requestMetrics = getRequestMetrics().filter { it.provider != provider }
            val requestJson = gson.toJson(requestMetrics)
            prefs.edit().putString(REQUEST_METRICS_KEY, requestJson).apply()
            
            // 清理性能度量
            val performanceMetrics = getPerformanceMetrics().filter { it.provider != provider }
            val performanceJson = gson.toJson(performanceMetrics)
            prefs.edit().putString(PERFORMANCE_METRICS_KEY, performanceJson).apply()
        } catch (e: Exception) {
            println("清理提供商度量失败: ${e.message}")
        }
    }
}

/**
 * 请求度量
 */
data class RequestMetric(
    val provider: AIProvider,
    val success: Boolean,
    val responseTime: Long,
    val timestamp: Long,
    val error: String? = null
)

/**
 * 网络度量
 */
data class NetworkMetric(
    val endpoint: String,
    val method: String,
    val responseCode: Int,
    val responseTime: Long,
    val timestamp: Long
)

/**
 * 错误度量
 */
data class ErrorMetric(
    val endpoint: String,
    val method: String,
    val error: String,
    val responseTime: Long,
    val timestamp: Long
)

/**
 * 性能度量
 */
data class PerformanceMetric(
    val provider: AIProvider,
    val metricType: String,
    val value: Double,
    val unit: String,
    val timestamp: Long
)