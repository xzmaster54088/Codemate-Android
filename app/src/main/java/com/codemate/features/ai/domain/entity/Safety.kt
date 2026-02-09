package com.codemate.features.ai.domain.entity

/**
 * 内容安全检查结果
 * @param isSafe 是否安全
 * @param riskLevel 风险等级
 * @param violations 违规类型列表
 * @param confidence 置信度
 * @param suggestedAction 建议操作
 */
data class ContentSafetyResult(
    val isSafe: Boolean,
    val riskLevel: RiskLevel,
    val violations: List<ViolationType>,
    val confidence: Float,
    val suggestedAction: SafetyAction,
    val details: Map<String, Any> = emptyMap()
)

/**
 * 风险等级枚举
 */
enum class RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * 违规类型枚举
 */
enum class ViolationType {
    HATE_SPEECH,
    HARASSMENT,
    VIOLENCE,
    SEXUAL_CONTENT,
    SELF_HARM,
    ILLEGAL_ACTIVITIES,
    PRIVACY_VIOLATION,
    SPAM,
    MISINFORMATION,
    COPYRIGHT_INFRINGEMENT,
    PERSONAL_INFORMATION
}

/**
 * 安全操作枚举
 */
enum class SafetyAction {
    ALLOW,
    WARN,
    BLOCK,
    MODERATE,
    REVIEW
}

/**
 * 敏感信息检测结果
 * @param containsSensitive 是否包含敏感信息
 * @param detectedTypes 检测到的敏感信息类型
 * @param maskedContent 掩码后的内容
 * @param detectionConfidence 检测置信度
 */
data class SensitiveInfoDetectionResult(
    val containsSensitive: Boolean,
    val detectedTypes: List<SensitiveInfoType>,
    val maskedContent: String,
    val detectionConfidence: Float
)

/**
 * 敏感信息类型枚举
 */
enum class SensitiveInfoType {
    EMAIL,
    PHONE_NUMBER,
    CREDIT_CARD,
    SSN,
    IP_ADDRESS,
    MAC_ADDRESS,
    API_KEY,
    PASSWORD,
    LOCATION,
    PERSONAL_ID
}