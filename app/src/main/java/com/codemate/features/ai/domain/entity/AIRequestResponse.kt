package com.codemate.features.ai.domain.entity

import kotlinx.coroutines.flow.Flow

/**
 * AI请求基础类
 * @param id 请求唯一标识
 * @param type 请求类型
 * @param modelType 模型类型
 * @param context 上下文
 * @param parameters 请求参数
 * @param metadata 额外元数据
 */
abstract class AIRequest(
    val id: String,
    val type: AIRequestType,
    val modelType: AIModelType,
    val context: ConversationContext,
    val parameters: Map<String, Any> = emptyMap(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 聊天请求
 */
data class ChatRequest(
    override val id: String = "",
    override val modelType: AIModelType,
    override val context: ConversationContext,
    val userMessage: String,
    override val parameters: Map<String, Any> = emptyMap(),
    override val metadata: Map<String, Any> = emptyMap()
) : AIRequest(id, AIRequestType.CHAT, modelType, context, parameters, metadata)

/**
 * 代码生成请求
 */
data class CodeGenerationRequest(
    override val id: String = "",
    override val modelType: AIModelType,
    override val context: ConversationContext,
    val codePrompt: String,
    val language: String = "kotlin",
    val includeExplanation: Boolean = true,
    override val parameters: Map<String, Any> = emptyMap(),
    override val metadata: Map<String, Any> = emptyMap()
) : AIRequest(id, AIRequestType.CODE_GENERATION, modelType, context, parameters, metadata)

/**
 * AI响应基础类
 * @param id 响应唯一标识
 * @param requestId 关联请求ID
 * @param status 响应状态
 * @param tokensUsed 使用令牌数
 * @param timestamp 时间戳
 * @param metadata 额外元数据
 */
abstract class AIResponse(
    val id: String,
    val requestId: String,
    val status: AIResponseStatus,
    val tokensUsed: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 文本响应
 * @param content 文本内容
 * @param isComplete 是否完整
 */
data class TextResponse(
    override val id: String,
    override val requestId: String,
    override val status: AIResponseStatus,
    val content: String,
    val isComplete: Boolean = false,
    override val tokensUsed: Int = 0,
    override val metadata: Map<String, Any> = emptyMap()
) : AIResponse(id, requestId, status, tokensUsed, metadata)

/**
 * 流式响应
 * @param contentDelta 内容增量
 */
data class StreamingResponse(
    override val id: String,
    override val requestId: String,
    override val status: AIResponseStatus,
    val contentDelta: String,
    val isComplete: Boolean = false,
    override val tokensUsed: Int = 0,
    override val metadata: Map<String, Any> = emptyMap()
) : AIResponse(id, requestId, status, tokensUsed, metadata)

/**
 * 错误响应
 * @param error 错误信息
 */
data class ErrorResponse(
    override val id: String,
    override val requestId: String,
    val error: AIError,
    override val tokensUsed: Int = 0,
    override val metadata: Map<String, Any> = emptyMap()
) : AIResponse(id, requestId, AIResponseStatus.ERROR, tokensUsed)

/**
 * AI错误类
 */
data class AIError(
    val code: String,
    val message: String,
    val details: String? = null,
    val isRetryable: Boolean = false
)