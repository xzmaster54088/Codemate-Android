package com.codemate.features.ai.domain.usecase

import com.codemate.features.ai.domain.entity.*
import com.codemate.features.ai.domain.repository.AISafetyRepository

/**
 * AI安全过滤用例
 * 处理内容安全检查和敏感信息检测的业务逻辑
 */
class AISafetyUseCase(
    private val aiSafetyRepository: AISafetyRepository
) {
    
    /**
     * 检查内容安全性
     */
    suspend fun checkContentSafety(content: String): Result<ContentSafetyResult> {
        return try {
            val result = aiSafetyRepository.checkContentSafety(content)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 批量检查内容安全性
     */
    suspend fun checkBatchContentSafety(contents: List<String>): Result<List<ContentSafetyResult>> {
        return try {
            val results = aiSafetyRepository.checkBatchContentSafety(contents)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 检测敏感信息
     */
    suspend fun detectSensitiveInfo(content: String): Result<SensitiveInfoDetectionResult> {
        return try {
            val result = aiSafetyRepository.detectSensitiveInfo(content)
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 批量检测敏感信息
     */
    suspend fun batchDetectSensitiveInfo(contents: List<String>): Result<List<SensitiveInfoDetectionResult>> {
        return try {
            val results = aiSafetyRepository.batchDetectSensitiveInfo(contents)
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 掩码敏感信息
     */
    suspend fun maskSensitiveInfo(content: String): Result<String> {
        return try {
            val maskedContent = aiSafetyRepository.maskSensitiveInfo(content)
            Result.success(maskedContent)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 安全预处理内容
     * 检查内容安全性并根据结果决定是否掩码或阻止
     */
    suspend fun preprocessContent(content: String): PreprocessResult {
        return try {
            // 检查内容安全性
            val safetyResult = aiSafetyRepository.checkContentSafety(content)
            
            when (safetyResult.suggestedAction) {
                SafetyAction.BLOCK -> {
                    PreprocessResult.BLOCKED(
                        originalContent = content,
                        reason = "内容被安全过滤器阻止",
                        riskLevel = safetyResult.riskLevel,
                        violations = safetyResult.violations
                    )
                }
                SafetyAction.MODERATE -> {
                    // 掩码敏感信息
                    val maskedContent = aiSafetyRepository.maskSensitiveInfo(content)
                    PreprocessResult.MODERATED(
                        originalContent = content,
                        maskedContent = maskedContent,
                        reason = "内容包含敏感信息，已进行掩码处理"
                    )
                }
                SafetyAction.WARN -> {
                    PreprocessResult.WARNED(
                        content = content,
                        warning = "内容可能存在风险",
                        riskLevel = safetyResult.riskLevel
                    )
                }
                else -> {
                    PreprocessResult.ALLOWED(content = content)
                }
            }
        } catch (e: Exception) {
            PreprocessResult.ERROR(
                originalContent = content,
                error = e.message ?: "安全检查失败"
            )
        }
    }
    
    /**
     * 审计安全事件
     */
    suspend fun logSafetyEvent(
        eventType: String,
        content: String,
        result: ContentSafetyResult,
        actionTaken: SafetyAction
    ): Result<Boolean> {
        return try {
            val event = SafetyAuditEvent(
                id = java.util.UUID.randomUUID().toString(),
                timestamp = System.currentTimeMillis(),
                eventType = eventType,
                content = content,
                result = result,
                actionTaken = actionTaken
            )
            
            val success = aiSafetyRepository.logSafetyEvent(event)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取安全统计信息
     */
    suspend fun getSafetyStatistics(startTime: Long, endTime: Long): Result<Map<String, Any>> {
        return try {
            val statistics = aiSafetyRepository.getSafetyStatistics(startTime, endTime)
            Result.success(statistics)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 添加自定义过滤器
     */
    suspend fun addCustomFilter(filter: CustomFilter): Result<Boolean> {
        return try {
            val success = aiSafetyRepository.addCustomFilter(filter)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取所有自定义过滤器
     */
    suspend fun getAllCustomFilters(): Result<List<CustomFilter>> {
        return try {
            val filters = aiSafetyRepository.getAllCustomFilters()
            Result.success(filters)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 删除自定义过滤器
     */
    suspend fun deleteCustomFilter(id: String): Result<Boolean> {
        return try {
            val success = aiSafetyRepository.deleteCustomFilter(id)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 内容预处理结果
 */
sealed class PreprocessResult {
    data class ALLOWED(val content: String) : PreprocessResult()
    data class BLOCKED(
        val originalContent: String,
        val reason: String,
        val riskLevel: RiskLevel,
        val violations: List<ViolationType>
    ) : PreprocessResult()
    data class MODERATED(
        val originalContent: String,
        val maskedContent: String,
        val reason: String
    ) : PreprocessResult()
    data class WARNED(
        val content: String,
        val warning: String,
        val riskLevel: RiskLevel
    ) : PreprocessResult()
    data class ERROR(
        val originalContent: String,
        val error: String
    ) : PreprocessResult()
}