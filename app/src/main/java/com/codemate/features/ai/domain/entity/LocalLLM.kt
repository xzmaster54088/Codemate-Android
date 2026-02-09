package com.codemate.features.ai.domain.entity

/**
 * 本地LLM模型信息
 * @param id 模型ID
 * @param name 模型名称
 * @param description 模型描述
 * @param modelPath 模型文件路径
 * @param quantization 量化类型
 * @param contextLength 上下文长度
 * @param parameters 模型参数
 * @param metadata 额外元数据
 */
data class LocalLLMModel(
    val id: String,
    val name: String,
    val description: String,
    val modelPath: String,
    val quantization: QuantizationType,
    val contextLength: Int,
    val parameters: Map<String, Any> = emptyMap(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 量化类型
 */
enum class QuantizationType {
    NONE,
    INT8,
    INT4,
    FP16
}

/**
 * 本地模型推理请求
 */
data class LocalLLMRequest(
    override val id: String = "",
    override val modelType: AIModelType,
    override val context: ConversationContext,
    val modelId: String,
    val inputText: String,
    val maxTokens: Int = 512,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    override val parameters: Map<String, Any> = emptyMap(),
    override val metadata: Map<String, Any> = emptyMap()
) : AIRequest(id, AIRequestType.COMPLETION, modelType, context, parameters, metadata)

/**
 * 本地模型推理响应
 */
data class LocalLLMResponse(
    override val id: String,
    override val requestId: String,
    override val status: AIResponseStatus,
    val outputText: String,
    val modelInfo: LocalLLMModel,
    val inferenceTime: Long = 0L,
    override val tokensUsed: Int = 0,
    override val metadata: Map<String, Any> = emptyMap()
) : AIResponse(id, requestId, status, tokensUsed, metadata)