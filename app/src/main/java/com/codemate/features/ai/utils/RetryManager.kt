package com.codemate.features.ai.utils

import android.util.Log
import com.codemate.features.ai.utils.AIConstants.TAG_AI_SERVICE
import kotlinx.coroutines.delay
import java.util.concurrent.CancellationException
import kotlin.random.Random

/**
 * 重试策略枚举
 */
enum class RetryStrategy {
    FIXED_DELAY,
    EXPONENTIAL_BACKOFF,
    LINEAR_BACKOFF,
    IMMEDIATE
}

/**
 * 错误重试管理器
 * 提供智能重试机制，包括退避策略和错误分类
 */
class RetryManager(
    private val maxAttempts: Int = AIConstants.MAX_RETRY_ATTEMPTS,
    private val baseDelayMs: Long = AIConstants.RETRY_DELAY_MS,
    private val backoffMultiplier: Float = AIConstants.RETRY_BACKOFF_MULTIPLIER
) {
    
    private val nonRetryableErrors = setOf(
        AIConstants.ERROR_AUTHENTICATION,
        AIConstants.ERROR_INVALID_REQUEST,
        AIConstants.ERROR_QUOTA_EXCEEDED,
        AIConstants.ERROR_CONTENT_FILTERED
    )
    
    /**
     * 执行带重试的操作
     */
    suspend fun <T> executeWithRetry(
        operation: suspend () -> T,
        strategy: RetryStrategy = RetryStrategy.EXPONENTIAL_BACKOFF,
        retryCondition: (Exception) -> Boolean = { shouldRetry(it) }
    ): T {
        var lastException: Exception? = null
        
        for (attempt in 1..maxAttempts) {
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                
                // 检查是否应该重试
                if (!retryCondition(e) || attempt == maxAttempts) {
                    throw e
                }
                
                // 计算延迟时间
                val delayMs = calculateDelay(attempt, strategy)
                Log.w(TAG_AI_SERVICE, "重试第 $attempt 次，延迟 ${delayMs}ms，错误: ${e.message}")
                
                delay(delayMs)
            }
        }
        
        throw lastException ?: Exception("重试失败")
    }
    
    /**
     * 判断错误是否可重试
     */
    private fun shouldRetry(exception: Exception): Boolean {
        val message = exception.message?.lowercase() ?: return false
        
        // 网络错误通常可重试
        if (message.contains("timeout") || 
            message.contains("network") || 
            message.contains("connection")) {
            return true
        }
        
        // 服务器错误通常可重试
        if (message.contains("5") || 
            message.contains("server error") ||
            message.contains("internal")) {
            return true
        }
        
        // 速率限制通常可重试
        if (message.contains("rate limit") || 
            message.contains("too many requests")) {
            return true
        }
        
        return false
    }
    
    /**
     * 计算重试延迟
     */
    private fun calculateDelay(attempt: Int, strategy: RetryStrategy): Long {
        return when (strategy) {
            RetryStrategy.FIXED_DELAY -> baseDelayMs
            RetryStrategy.LINEAR_BACKOFF -> baseDelayMs * attempt
            RetryStrategy.EXPONENTIAL_BACKOFF -> 
                (baseDelayMs * Math.pow(backoffMultiplier.toDouble(), (attempt - 1).toDouble())).toLong()
            RetryStrategy.IMMEDIATE -> 0L
        }
    }
    
    /**
     * 执行异步重试（带抖动）
     */
    suspend fun <T> executeWithJitterRetry(
        operation: suspend () -> T,
        maxJitterMs: Long = 1000L
    ): T {
        var lastException: Exception? = null
        
        for (attempt in 1..maxAttempts) {
            try {
                return operation()
            } catch (e: Exception) {
                lastException = e
                
                if (!shouldRetry(e) || attempt == maxAttempts) {
                    throw e
                }
                
                val baseDelay = calculateDelay(attempt, RetryStrategy.EXPONENTIAL_BACKOFF)
                val jitter = Random.nextLong(0, maxJitterMs)
                val totalDelay = baseDelay + jitter
                
                Log.w(TAG_AI_SERVICE, "重试第 $attempt 次，延迟 ${totalDelay}ms (基础: ${baseDelay}, 抖动: ${jitter})")
                
                delay(totalDelay)
            }
        }
        
        throw lastException ?: Exception("重试失败")
    }
}

/**
 * 指数退避重试器
 */
class ExponentialBackoffRetry(
    private val initialDelayMs: Long = 1000L,
    private val maxDelayMs: Long = 30000L,
    private val multiplier: Float = 2.0f,
    private val jitter: Boolean = true
) {
    
    suspend fun <T> execute(
        operation: suspend () -> T,
        maxAttempts: Int = 3
    ): T {
        var delayMs = initialDelayMs
        
        repeat(maxAttempts - 1) { attempt ->
            try {
                return operation()
            } catch (e: Exception) {
                Log.w(TAG_AI_SERVICE, "重试 $attempt/$maxAttempts，延迟 ${delayMs}ms")
                kotlinx.coroutines.delay(delayMs)
                
                // 计算下次延迟
                delayMs = (delayMs * multiplier).toLong().coerceAtMost(maxDelayMs)
                
                // 添加抖动
                if (jitter) {
                    delayMs += Random.nextLong(0, delayMs / 4)
                }
            }
        }
        
        // 最后一次尝试
        return operation()
    }
}