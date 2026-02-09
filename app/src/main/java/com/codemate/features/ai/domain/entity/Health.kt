package com.codemate.features.ai.domain.entity

import java.util.*

/**
 * 服务健康状态
 * @param isHealthy 是否健康
 * @param status 健康状态详情
 * @param lastCheckTime 最后检查时间
 * @param responseTime 响应时间
 * @param errorDetails 错误详情
 */
data class ServiceHealthStatus(
    val isHealthy: Boolean,
    val status: HealthStatus,
    val lastCheckTime: Long = System.currentTimeMillis(),
    val responseTime: Long = 0L,
    val errorDetails: String? = null,
    val metrics: Map<String, Any> = emptyMap()
)

/**
 * 健康状态枚举
 */
enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN,
    MAINTENANCE
}

/**
 * 服务监控信息
 * @param serviceId 服务ID
 * @param provider 服务提供商
 * @param lastPingTime 最后ping时间
 * @param averageResponseTime 平均响应时间
 * @param successRate 成功率
 * @param totalRequests 总请求数
 * @param failedRequests 失败请求数
 */
data class ServiceMonitorInfo(
    val serviceId: String,
    val provider: AIProvider,
    val lastPingTime: Long = 0L,
    val averageResponseTime: Long = 0L,
    val successRate: Float = 0.0f,
    val totalRequests: Int = 0,
    val failedRequests: Int = 0,
    val lastErrors: List<String> = emptyList()
)

/**
 * 错误重试策略
 * @param maxRetries 最大重试次数
 * @param baseDelay 基础延迟时间（毫秒）
 * @param maxDelay 最大延迟时间（毫秒）
 * @param backoffMultiplier 退避倍数
 * @param jitter 是否添加随机抖动
 */
data class RetryStrategy(
    val maxRetries: Int = 3,
    val baseDelay: Long = 1000L,
    val maxDelay: Long = 30000L,
    val backoffMultiplier: Float = 2.0f,
    val jitter: Boolean = true
)

/**
 * 重试记录
 * @param attemptNumber 尝试次数
 * @param timestamp 时间戳
 * @param error 错误信息
 * @param delay 延迟时间
 */
data class RetryRecord(
    val attemptNumber: Int,
    val timestamp: Long,
    val error: String,
    val delay: Long
)