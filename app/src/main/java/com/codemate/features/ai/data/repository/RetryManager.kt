package com.codemate.features.ai.data.repository

import com.codemate.features.ai.domain.entity.AIError
import com.codemate.features.ai.domain.entity.RetryRecord
import com.codemate.features.ai.domain.entity.RetryStrategy
import kotlinx.coroutines.delay
import kotlin.random.Random
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 重试管理器
 * 负责AI请求的错误重试机制
 */
@Singleton
class RetryManager @Inject constructor() {
    
    /**
     * 执行带重试的操作
     */
    suspend fun <T> executeWithRetry(
        operation: suspend () -> T,
        strategy: RetryStrategy = defaultStrategy,
        maxRetries: Int = strategy.maxRetries
    ): T {
        var lastException: Exception? = null
        var attempt = 0
        
        while (attempt <= maxRetries) {
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                attempt++
                
                // 检查是否应该重试
                if (!shouldRetry(e, attempt, maxRetries)) {
                    break
                }
                
                // 计算延迟时间
                val delayTime = calculateDelay(attempt, strategy)
                
                // 记录重试信息
                recordRetry(attempt, e, delayTime)
                
                // 等待重试
                delay(delayTime)
            }
        }
        
        // 所有重试都失败了，抛出最后一个异常
        throw lastException ?: Exception("重试失败")
    }
    
    /**
     * 检查是否应该重试
     */
    private fun shouldRetry(exception: Exception, attempt: Int, maxRetries: Int): Boolean {
        if (attempt > maxRetries) {
            return false
        }
        
        // 根据异常类型决定是否重试
        return when (exception) {
            is NetworkException -> when (exception.code) {
                "NETWORK_ERROR",
                "TIMEOUT",
                "CONNECTION_RESET" -> true
                "RATE_LIMIT" -> attempt <= maxRetries / 2 // 限流错误重试次数减半
                else -> false
            }
            is ServerException -> when (exception.code) {
                "INTERNAL_SERVER_ERROR",
                "BAD_GATEWAY",
                "SERVICE_UNAVAILABLE",
                "GATEWAY_TIMEOUT" -> true
                else -> false
            }
            is AuthenticationException -> false // 认证错误不应该重试
            is ValidationException -> false // 验证错误不应该重试
            else -> true // 其他异常默认重试
        }
    }
    
    /**
     * 计算延迟时间
     */
    private fun calculateDelay(attempt: Int, strategy: RetryStrategy): Long {
        // 指数退避
        val exponentialDelay = strategy.baseDelay * Math.pow(strategy.backoffMultiplier.toDouble(), (attempt - 1).toDouble()).toLong()
        
        // 限制最大延迟
        val cappedDelay = exponentialDelay.coerceAtMost(strategy.maxDelay)
        
        // 添加随机抖动
        return if (strategy.jitter) {
            val jitter = Random.nextLong(0, cappedDelay / 10)
            cappedDelay + jitter
        } else {
            cappedDelay
        }
    }
    
    /**
     * 记录重试信息
     */
    private fun recordRetry(attempt: Int, exception: Exception, delay: Long) {
        // 这里可以记录到日志或数据库
        println("重试第 $attempt 次，延迟 ${delay}ms，异常: ${exception.message}")
    }
    
    companion object {
        private val defaultStrategy = RetryStrategy(
            maxRetries = 3,
            baseDelay = 1000L,
            maxDelay = 30000L,
            backoffMultiplier = 2.0f,
            jitter = true
        )
    }
}

/**
 * 网络异常
 */
class NetworkException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 服务器异常
 */
class ServerException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 认证异常
 */
class AuthenticationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * 验证异常
 */
class ValidationException(
    val code: String,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)