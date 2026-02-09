package com.codemate.features.ai.domain.repository

import com.codemate.features.ai.domain.entity.*

/**
 * AI安全过滤仓储接口
 */
interface AISafetyRepository {
    
    // ===== 内容安全检查 =====
    
    /**
     * 检查内容安全性
     */
    suspend fun checkContentSafety(content: String): ContentSafetyResult
    
    /**
     * 批量检查内容安全性
     */
    suspend fun checkBatchContentSafety(contents: List<String>): List<ContentSafetyResult>
    
    /**
     * 获取安全过滤规则
     */
    suspend fun getSafetyRules(): Map<String, Any>
    
    /**
     * 更新安全过滤规则
     */
    suspend fun updateSafetyRules(rules: Map<String, Any>): Boolean
    
    /**
     * 获取允许的内容类型
     */
    suspend fun getAllowedContentTypes(): Set<String>
    
    /**
     * 设置允许的内容类型
     */
    suspend fun setAllowedContentTypes(types: Set<String>): Boolean
    
    // ===== 敏感信息检测 =====
    
    /**
     * 检测敏感信息
     */
    suspend fun detectSensitiveInfo(content: String): SensitiveInfoDetectionResult
    
    /**
     * 批量检测敏感信息
     */
    suspend fun batchDetectSensitiveInfo(contents: List<String>): List<SensitiveInfoDetectionResult>
    
    /**
     * 获取敏感信息模式
     */
    suspend fun getSensitiveInfoPatterns(): Map<String, Any>
    
    /**
     * 更新敏感信息模式
     */
    suspend fun updateSensitiveInfoPatterns(patterns: Map<String, Any>): Boolean
    
    /**
     * 掩码敏感信息
     */
    suspend fun maskSensitiveInfo(content: String): String
    
    // ===== 自定义过滤器 =====
    
    /**
     * 添加自定义过滤器
     */
    suspend fun addCustomFilter(filter: CustomFilter): Boolean
    
    /**
     * 获取所有自定义过滤器
     */
    suspend fun getAllCustomFilters(): List<CustomFilter>
    
    /**
     * 删除自定义过滤器
     */
    suspend fun deleteCustomFilter(id: String): Boolean
    
    /**
     * 更新自定义过滤器
     */
    suspend fun updateCustomFilter(filter: CustomFilter): Boolean
    
    /**
     * 测试自定义过滤器
     */
    suspend fun testCustomFilter(filterId: String, content: String): Boolean
    
    // ===== 审计日志 =====
    
    /**
     * 记录安全事件
     */
    suspend fun logSafetyEvent(event: SafetyAuditEvent): Boolean
    
    /**
     * 获取安全审计日志
     */
    suspend fun getSafetyAuditLogs(
        startTime: Long,
        endTime: Long,
        eventTypes: List<String> = emptyList()
    ): List<SafetyAuditEvent>
    
    /**
     * 获取安全统计信息
     */
    suspend fun getSafetyStatistics(startTime: Long, endTime: Long): Map<String, Any>
}

/**
 * 自定义过滤器
 * @param id 过滤器ID
 * @param name 过滤器名称
 * @param description 描述
 * @param pattern 匹配模式
 * @param action 执行动作
 * @param isEnabled 是否启用
 */
data class CustomFilter(
    val id: String,
    val name: String,
    val description: String,
    val pattern: String,
    val action: SafetyAction,
    val isEnabled: Boolean = true,
    val priority: Int = 0
)

/**
 * 安全审计事件
 * @param id 事件ID
 * @param timestamp 时间戳
 * @param eventType 事件类型
 * @param content 内容
 * @param result 检查结果
 * @param actionTaken 采取的行动
 * @param metadata 额外元数据
 */
data class SafetyAuditEvent(
    val id: String,
    val timestamp: Long,
    val eventType: String,
    val content: String,
    val result: ContentSafetyResult,
    val actionTaken: SafetyAction,
    val metadata: Map<String, Any> = emptyMap()
)