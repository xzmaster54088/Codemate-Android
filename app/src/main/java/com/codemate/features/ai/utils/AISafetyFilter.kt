package com.codemate.features.ai.utils

import android.util.Log
import com.codemate.features.ai.utils.AIConstants.TAG_SAFETY
import com.codemate.features.ai.utils.AIConstants.BANNED_PATTERNS
import com.codemate.features.ai.utils.AIConstants.MAX_INPUT_LENGTH
import com.codemate.features.ai.utils.AIConstants.MAX_OUTPUT_LENGTH
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI安全过滤器
 * 提供内容安全检查、敏感信息过滤和内容审查功能
 */
@Singleton
class AISafetyFilter @Inject constructor() {
    
    private val bannedPatterns = BANNED_PATTERNS.map { Pattern.compile(it, Pattern.CASE_INSENSITIVE) }
    private val securityPatterns = listOf(
        Pattern.compile("\\b\\d{4}[- ]?\\d{4}[- ]?\\d{4}[- ]?\\d{4}\\b", Pattern.CASE_INSENSITIVE), // 信用卡号
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b", Pattern.CASE_INSENSITIVE), // 邮箱
        Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b", Pattern.CASE_INSENSITIVE), // IP地址
        Pattern.compile("\\b\\d{10,15}\\b", Pattern.CASE_INSENSITIVE), // 长数字（可能是敏感信息）
    )
    
    private val harmfulContentPatterns = listOf(
        Pattern.compile("(?i)(malware|virus|exploit|hack|phishing|spam)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(bomb|weapon|drug|illegal|criminal)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(?i)(suicide|self-harm|violence|hate|terror)", Pattern.CASE_INSENSITIVE)
    )
    
    /**
     * 检查输入内容安全性
     */
    fun checkInputSafety(content: String): SafetyResult {
        val issues = mutableListOf<SafetyIssue>()
        
        // 检查长度
        if (content.length > MAX_INPUT_LENGTH) {
            issues += SafetyIssue(
                type = SafetyIssueType.LENGTH_VIOLATION,
                message = "输入内容过长，超过最大长度限制",
                severity = SafetySeverity.MEDIUM
            )
        }
        
        // 检查敏感信息
        val sensitiveInfo = findSensitiveInformation(content)
        if (sensitiveInfo.isNotEmpty()) {
            issues += SafetyIssue(
                type = SafetyIssueType.SENSITIVE_INFORMATION,
                message = "检测到可能的敏感信息: ${sensitiveInfo.joinToString(", ")}",
                severity = SafetySeverity.HIGH
            )
        }
        
        // 检查有害内容
        val harmfulContent = findHarmfulContent(content)
        if (harmfulContent.isNotEmpty()) {
            issues += SafetyIssue(
                type = SafetyIssueType.HARMFUL_CONTENT,
                message = "检测到有害内容: ${harmfulContent.joinToString(", ")}",
                severity = SafetySeverity.CRITICAL
            )
        }
        
        // 检查代码注入
        if (containsCodeInjection(content)) {
            issues += SafetyIssue(
                type = SafetyIssueType.CODE_INJECTION,
                message = "检测到可能的代码注入尝试",
                severity = SafetySeverity.HIGH
            )
        }
        
        return SafetyResult(
            isSafe = issues.isEmpty(),
            issues = issues,
            filteredContent = filterContent(content, issues)
        )
    }
    
    /**
     * 检查输出内容安全性
     */
    fun checkOutputSafety(content: String): SafetyResult {
        val issues = mutableListOf<SafetyIssue>()
        
        // 检查长度
        if (content.length > MAX_OUTPUT_LENGTH) {
            issues += SafetyIssue(
                type = SafetyIssueType.LENGTH_VIOLATION,
                message = "输出内容过长",
                severity = SafetySeverity.MEDIUM
            )
        }
        
        // 检查敏感信息泄露
        val sensitiveInfo = findSensitiveInformation(content)
        if (sensitiveInfo.isNotEmpty()) {
            issues += SafetyIssue(
                type = SafetyIssueType.SENSITIVE_INFORMATION,
                message = "输出中可能包含敏感信息: ${sensitiveInfo.joinToString(", ")}",
                severity = SafetySeverity.HIGH
            )
        }
        
        // 检查有害内容
        val harmfulContent = findHarmfulContent(content)
        if (harmfulContent.isNotEmpty()) {
            issues += SafetyIssue(
                type = SafetyIssueType.HARMFUL_CONTENT,
                message = "输出中包含有害内容: ${harmfulContent.joinToString(", ")}",
                severity = SafetySeverity.CRITICAL
            )
        }
        
        return SafetyResult(
            isSafe = issues.isEmpty(),
            issues = issues,
            filteredContent = filterContent(content, issues)
        )
    }
    
    /**
     * 过滤敏感内容
     */
    fun filterContent(content: String, issues: List<SafetyIssue>): String {
        var filteredContent = content
        
        // 根据问题类型进行过滤
        issues.forEach { issue ->
            when (issue.type) {
                SafetyIssueType.SENSITIVE_INFORMATION -> {
                    // 替换敏感信息
                    securityPatterns.forEach { pattern ->
                        filteredContent = pattern.matcher(filteredContent)
                            .replaceAll("[已过滤]")
                    }
                }
                SafetyIssueType.HARMFUL_CONTENT -> {
                    // 替换有害内容
                    harmfulContentPatterns.forEach { pattern ->
                        filteredContent = pattern.matcher(filteredContent)
                            .replaceAll("[内容已过滤]")
                    }
                }
                else -> {
                    // 其他类型的过滤逻辑
                }
            }
        }
        
        return filteredContent
    }
    
    /**
     * 检测敏感信息
     */
    private fun findSensitiveInformation(content: String): List<String> {
        val found = mutableListOf<String>()
        
        // 检查被禁止的模式
        bannedPatterns.forEach { pattern ->
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                found.add("敏感词汇: ${matcher.group()}")
            }
        }
        
        // 检查安全相关模式
        securityPatterns.forEach { pattern ->
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                found.add("敏感信息: ${matcher.group()}")
            }
        }
        
        return found
    }
    
    /**
     * 检测有害内容
     */
    private fun findHarmfulContent(content: String): List<String> {
        val found = mutableListOf<String>()
        
        harmfulContentPatterns.forEach { pattern ->
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                found.add(matcher.group())
            }
        }
        
        return found
    }
    
    /**
     * 检查代码注入
     */
    private fun containsCodeInjection(content: String): Boolean {
        val injectionPatterns = listOf(
            Pattern.compile("(?i)(eval\\(|exec\\(|system\\(|shell_exec\\()", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(<script|javascript:|vbscript:|onload=|onerror=)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(SELECT.*FROM|INSERT.*INTO|UPDATE.*SET|DELETE.*FROM)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?i)(rm\\s+-rf|chmod|curl\\s+wget)", Pattern.CASE_INSENSITIVE)
        )
        
        return injectionPatterns.any { pattern ->
            pattern.matcher(content).find()
        }
    }
    
    /**
     * 记录安全事件
     */
    fun logSafetyEvent(result: SafetyResult, content: String, context: String) {
        if (result.issues.isNotEmpty()) {
            Log.w(TAG_SAFETY, "安全事件 - 上下文: $context")
            result.issues.forEach { issue ->
                Log.w(TAG_SAFETY, "安全问题: ${issue.type} - ${issue.message} (严重程度: ${issue.severity})")
            }
            
            if (result.issues.any { it.severity == SafetySeverity.CRITICAL }) {
                Log.e(TAG_SAFETY, "检测到严重安全问题，内容已被阻止")
            }
        }
    }
}

/**
 * 安全检查结果
 */
data class SafetyResult(
    val isSafe: Boolean,
    val issues: List<SafetyIssue>,
    val filteredContent: String
)

/**
 * 安全问题
 */
data class SafetyIssue(
    val type: SafetyIssueType,
    val message: String,
    val severity: SafetySeverity
)

/**
 * 安全问题类型
 */
enum class SafetyIssueType {
    SENSITIVE_INFORMATION,
    HARMFUL_CONTENT,
    CODE_INJECTION,
    LENGTH_VIOLATION,
    MALICIOUS_CONTENT
}

/**
 * 安全严重程度
 */
enum class SafetySeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}