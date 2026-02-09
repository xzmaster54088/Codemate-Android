package com.codemate.features.ai.data.repository

import com.codemate.features.ai.domain.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Anthropic Claude服务实现
 * 处理Anthropic Claude模型的API调用
 */
@Singleton
class ClaudeService @Inject constructor(
    private val networkClient: NetworkClient,
    private val configManager: AIConfigManager,
    private val retryManager: RetryManager
) {
    
    /**
     * 执行健康检查
     */
    suspend fun performHealthCheck(): ServiceHealthStatus {
        return try {
            val response = networkClient.makeRequest(
                endpoint = "https://api.anthropic.com/v1/messages",
                method = "POST",
                headers = mapOf(
                    "x-api-key" to configManager.getApiKey(AIProvider.ANTHROPIC),
                    "Content-Type" to "application/json",
                    "anthropic-version" to "2023-06-01"
                ),
                body = mapOf(
                    "model" to "claude-3-haiku-20240307",
                    "max_tokens" to 1,
                    "messages" to listOf(mapOf("role" to "user", "content" to "hi"))
                )
            )
            
            ServiceHealthStatus(
                isHealthy = response.isSuccess,
                status = if (response.isSuccess) HealthStatus.HEALTHY else HealthStatus.UNHEALTHY,
                responseTime = response.responseTime
            )
        } catch (e: Exception) {
            ServiceHealthStatus(
                isHealthy = false,
                status = HealthStatus.UNHEALTHY,
                errorDetails = e.message
            )
        }
    }
    
    /**
     * 发送聊天消息
     */
    suspend fun sendChatMessage(request: ChatRequest): TextResponse {
        val config = configManager.getConfig(AIProvider.ANTHROPIC)
        val apiKey = config?.apiKey ?: throw IllegalStateException("Claude API密钥未配置")
        
        val claudeRequest = mapOf(
            "model" to getModelName(request.modelType),
            "max_tokens" to request.context.maxTokens,
            "temperature" to request.context.temperature,
            "top_p" to request.context.topP,
            "stream" to false,
            "messages" to buildMessages(request)
        )
        
        return retryManager.executeWithRetry {
            val response = networkClient.makeRequest(
                endpoint = "https://api.anthropic.com/v1/messages",
                method = "POST",
                headers = mapOf(
                    "x-api-key" to apiKey,
                    "Content-Type" to "application/json",
                    "anthropic-version" to "2023-06-01"
                ),
                body = claudeRequest
            )
            
            parseChatResponse(response)
        }
    }
    
    /**
     * 发送流式聊天消息
     */
    fun sendStreamingChatMessage(request: ChatRequest): Flow<StreamingResponse> {
        return flow {
            val config = configManager.getConfig(AIProvider.ANTHROPIC)
            val apiKey = config?.apiKey ?: throw IllegalStateException("Claude API密钥未配置")
            
            val claudeRequest = mapOf(
                "model" to getModelName(request.modelType),
                "max_tokens" to request.context.maxTokens,
                "temperature" to request.context.temperature,
                "top_p" to request.context.topP,
                "stream" to true,
                "messages" to buildMessages(request)
            )
            
            try {
                networkClient.makeStreamingRequest(
                    endpoint = "https://api.anthropic.com/v1/messages",
                    method = "POST",
                    headers = mapOf(
                        "x-api-key" to apiKey,
                        "Content-Type" to "application/json",
                        "anthropic-version" to "2023-06-01"
                    ),
                    body = claudeRequest
                ) { chunk ->
                    val streamingResponse = parseStreamingChunk(chunk)
                    if (streamingResponse != null) {
                        emit(streamingResponse)
                    }
                }
            } catch (e: Exception) {
                emit(StreamingResponse(
                    id = request.id,
                    requestId = request.id,
                    status = AIResponseStatus.ERROR,
                    contentDelta = "错误: ${e.message}",
                    isComplete = true
                ))
            }
        }
    }
    
    /**
     * 生成代码
     */
    suspend fun generateCode(request: CodeGenerationRequest): TextResponse {
        val config = configManager.getConfig(AIProvider.ANTHROPIC)
        val apiKey = config?.apiKey ?: throw IllegalStateException("Claude API密钥未配置")
        
        val prompt = buildCodePrompt(request)
        val claudeRequest = mapOf(
            "model" to getModelName(request.modelType),
            "max_tokens" to request.parameters["max_tokens"] as? Int ?: 2048,
            "temperature" to request.parameters["temperature"] as? Float ?: 0.1f,
            "top_p" to request.parameters["top_p"] as? Float ?: 1.0f,
            "stream" to false,
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to prompt
                )
            )
        )
        
        return retryManager.executeWithRetry {
            val response = networkClient.makeRequest(
                endpoint = "https://api.anthropic.com/v1/messages",
                method = "POST",
                headers = mapOf(
                    "x-api-key" to apiKey,
                    "Content-Type" to "application/json",
                    "anthropic-version" to "2023-06-01"
                ),
                body = claudeRequest
            )
            
            parseChatResponse(response)
        }
    }
    
    /**
     * 生成流式代码
     */
    fun generateStreamingCode(request: CodeGenerationRequest): Flow<StreamingResponse> {
        return flow {
            val config = configManager.getConfig(AIProvider.ANTHROPIC)
            val apiKey = config?.apiKey ?: throw IllegalStateException("Claude API密钥未配置")
            
            val prompt = buildCodePrompt(request)
            val claudeRequest = mapOf(
                "model" to getModelName(request.modelType),
                "max_tokens" to request.parameters["max_tokens"] as? Int ?: 2048,
                "temperature" to request.parameters["temperature"] as? Float ?: 0.1f,
                "top_p" to request.parameters["top_p"] as? Float ?: 1.0f,
                "stream" to true,
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to prompt
                    )
                )
            )
            
            try {
                networkClient.makeStreamingRequest(
                    endpoint = "https://api.anthropic.com/v1/messages",
                    method = "POST",
                    headers = mapOf(
                        "x-api-key" to apiKey,
                        "Content-Type" to "application/json",
                        "anthropic-version" to "2023-06-01"
                    ),
                    body = claudeRequest
                ) { chunk ->
                    val streamingResponse = parseStreamingChunk(chunk)
                    if (streamingResponse != null) {
                        emit(streamingResponse)
                    }
                }
            } catch (e: Exception) {
                emit(StreamingResponse(
                    id = request.id,
                    requestId = request.id,
                    status = AIResponseStatus.ERROR,
                    contentDelta = "错误: ${e.message}",
                    isComplete = true
                ))
            }
        }
    }
    
    // ===== 私有方法 =====
    
    private fun getModelName(modelType: AIModelType): String {
        return when (modelType) {
            AIModelType.ANTHROPIC_CLAUDE_3_OPUS -> "claude-3-opus-20240229"
            AIModelType.ANTHROPIC_CLAUDE_3_SONNET -> "claude-3-sonnet-20240229"
            AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> "claude-3-haiku-20240307"
            else -> throw IllegalArgumentException("不支持的模型类型: $modelType")
        }
    }
    
    private fun buildMessages(request: ChatRequest): List<Map<String, String>> {
        val messages = mutableListOf<Map<String, String>>()
        
        // Claude使用system字段而不是system消息
        // 系统提示将在请求级别处理
        
        // 添加对话历史
        request.context.conversationHistory.forEach { message ->
            messages += mapOf(
                "role" to when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "user" // Claude将system消息转换为user消息
                },
                "content" to message.content
            )
        }
        
        // 添加当前用户消息
        messages += mapOf(
            "role" to "user",
            "content" to request.userMessage
        )
        
        return messages
    }
    
    private fun buildCodePrompt(request: CodeGenerationRequest): String {
        val promptBuilder = StringBuilder()
        
        if (request.includeExplanation) {
            promptBuilder.append("You are an expert ${request.language} programmer. Please provide detailed explanations alongside your code.\n\n")
        }
        
        promptBuilder.append("Request: ${request.codePrompt}\n\n")
        
        if (request.includeExplanation) {
            promptBuilder.append("Please provide:\n")
            promptBuilder.append("1. The ${request.language} code implementation\n")
            promptBuilder.append("2. Detailed explanation\n")
            promptBuilder.append("3. Usage example\n")
            promptBuilder.append("4. Important notes\n\n")
        } else {
            promptBuilder.append("Please generate the ${request.language} code only.\n\n")
        }
        
        // 添加系统提示
        if (request.context.systemPrompt.isNotEmpty()) {
            promptBuilder.append("System context: ${request.context.systemPrompt}\n\n")
        }
        
        return promptBuilder.toString()
    }
    
    private fun parseChatResponse(response: NetworkResponse): TextResponse {
        val content = extractContentFromResponse(response.data)
        val tokensUsed = extractTokensFromResponse(response.data)
        
        return TextResponse(
            id = java.util.UUID.randomUUID().toString(),
            requestId = "request_id", // 需要从请求中获取
            status = AIResponseStatus.COMPLETED,
            content = content,
            isComplete = true,
            tokensUsed = tokensUsed
        )
    }
    
    private fun parseStreamingChunk(chunk: String): StreamingResponse? {
        if (!chunk.contains("\"content\"")) return null
        
        val contentDelta = extractContentDeltaFromChunk(chunk)
        val isComplete = chunk.contains("[END]")
        
        return StreamingResponse(
            id = java.util.UUID.randomUUID().toString(),
            requestId = "request_id", // 需要从请求中获取
            status = if (isComplete) AIResponseStatus.COMPLETED else AIResponseStatus.STREAMING,
            contentDelta = contentDelta,
            isComplete = isComplete,
            tokensUsed = 0
        )
    }
    
    private fun extractContentFromResponse(data: String): String {
        return try {
            val json = org.json.JSONObject(data)
            val content = json.getJSONArray("content")
            if (content.length() > 0) {
                val contentBlock = content.getJSONObject(0)
                contentBlock.getString("text")
            } else {
                "无法解析响应内容"
            }
        } catch (e: Exception) {
            "响应解析错误: ${e.message}"
        }
    }
    
    private fun extractTokensFromResponse(data: String): Int {
        return try {
            val json = org.json.JSONObject(data)
            val usage = json.getJSONObject("usage")
            usage.getInt("input_tokens") + usage.getInt("output_tokens")
        } catch (e: Exception) {
            0
        }
    }
    
    private fun extractContentDeltaFromChunk(chunk: String): String {
        return try {
            if (chunk.startsWith("data: ")) {
                val data = chunk.substring(6)
                if (data == "[END]") return ""
                
                val json = org.json.JSONObject(data)
                val content = json.getJSONArray("content")
                if (content.length() > 0) {
                    val contentBlock = content.getJSONObject(0)
                    if (contentBlock.has("text")) {
                        return contentBlock.getString("text")
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }
}