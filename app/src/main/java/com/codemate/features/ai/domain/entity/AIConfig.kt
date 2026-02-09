package com.codemate.features.ai.domain.entity

/**
 * AI配置实体
 * @param id 配置唯一标识
 * @param name 配置名称
 * @param provider AI提供商
 * @param model 模型配置
 * @param apiKey API密钥
 * @param baseUrl 基础URL（自定义API时使用）
 * @param parameters 请求参数
 * @param isEnabled 是否启用
 */
data class AIConfig(
    val id: String,
    val name: String,
    val provider: AIProvider,
    val model: AIModelConfig,
    val apiKey: String = "",
    val baseUrl: String = "",
    val parameters: Map<String, Any> = emptyMap(),
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false
)

/**
 * AI模型配置
 * @param type 模型类型
 * @param displayName 显示名称
 * @param maxTokens 最大令牌数
 * @param supportsStreaming 是否支持流式响应
 * @param contextLength 上下文长度
 * @param capabilities 模型能力
 */
data class AIModelConfig(
    val type: AIModelType,
    val displayName: String,
    val maxTokens: Int = 4096,
    val supportsStreaming: Boolean = true,
    val contextLength: Int = 8192,
    val capabilities: Set<AICapability> = emptySet(),
    val parameters: Map<String, Any> = emptyMap()
)

/**
 * AI能力枚举
 */
enum class AICapability {
    TEXT_GENERATION,
    CODE_GENERATION,
    CODE_REVIEW,
    CHAT,
    EMBEDDING,
    IMAGE_ANALYSIS,
    MULTIMODAL,
    FUNCTION_CALLING,
    REASONING
}