package com.codemate.features.ai.data.repository

import com.codemate.features.ai.domain.entity.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI GPT服务实现
 * 处理OpenAI GPT模型的API调用
 */
@Singleton
class OpenAIService @Inject constructor(
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
                endpoint = "https://api.openai.com/v1/models",
                method = "GET",
                headers = mapOf(
                    "Authorization" to "Bearer ${configManager.getApiKey(AIProvider.OPENAI)}"
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
        val config = configManager.getConfig(AIProvider.OPENAI)
        val apiKey = config?.apiKey ?: throw IllegalStateException("OpenAI API密钥未配置")
        
        val openAIRequest = mapOf(
            "model" to getModelName(request.modelType),
            "messages" to buildMessages(request),
            "max_tokens" to request.context.maxTokens,
            "temperature" to request.context.temperature,
            "top_p" to request.context.topP,
            "stream" to false
        )
        
        return retryManager.executeWithRetry {
            val response = networkClient.makeRequest(
                endpoint = "https://api.openai.com/v1/chat/completions",
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "Content-Type" to "application/json"
                ),
                body = openAIRequest
            )
            
            parseChatResponse(response)
        }
    }
    
    /**
     * 发送流式聊天消息
     */
    fun sendStreamingChatMessage(request: ChatRequest): Flow<StreamingResponse> {
        return flow {
            val config = configManager.getConfig(AIProvider.OPENAI)
            val apiKey = config?.apiKey ?: throw IllegalStateException("OpenAI API密钥未配置")
            
            val openAIRequest = mapOf(
                "model" to getModelName(request.modelType),
                "messages" to buildMessages(request),
                "max_tokens" to request.context.maxTokens,
                "temperature" to request.context.temperature,
                "top_p" to request.context.topP,
                "stream" to true
            )
            
            try {
                networkClient.makeStreamingRequest(
                    endpoint = "https://api.openai.com/v1/chat/completions",
                    method = "POST",
                    headers = mapOf(
                        "Authorization" to "Bearer $apiKey",
                        "Content-Type" to "application/json"
                    ),
                    body = openAIRequest
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
        val config = configManager.getConfig(AIProvider.OPENAI)
        val apiKey = config?.apiKey ?: throw IllegalStateException("OpenAI API密钥未配置")
        
        val prompt = buildCodePrompt(request)
        val openAIRequest = mapOf(
            "model" to getModelName(request.modelType),
            "messages" to listOf(
                mapOf(
                    "role" to "user",
                    "content" to prompt
                )
            ),
            "max_tokens" to request.parameters["max_tokens"] as? Int ?: 2048,
            "temperature" to request.parameters["temperature"] as? Float ?: 0.1f,
            "top_p" to request.parameters["top_p"] as? Float ?: 1.0f,
            "stream" to false
        )
        
        return retryManager.executeWithRetry {
            val response = networkClient.makeRequest(
                endpoint = "https://api.openai.com/v1/chat/completions",
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer $apiKey",
                    "Content-Type" to "application/json"
                ),
                body = openAIRequest
            )
            
            parseChatResponse(response)
        }
    }
    
    /**
     * 生成流式代码
     */
    fun generateStreamingCode(request: CodeGenerationRequest): Flow<StreamingResponse> {
        return flow {
            val config = configManager.getConfig(AIProvider.OPENAI)
            val apiKey = config?.apiKey ?: throw IllegalStateException("OpenAI API密钥未配置")
            
            val prompt = buildCodePrompt(request)
            val openAIRequest = mapOf(
                "model" to getModelName(request.modelType),
                "messages" to listOf(
                    mapOf(
                        "role" to "user",
                        "content" to prompt
                    )
                ),
                "max_tokens" to request.parameters["max_tokens"] as? Int ?: 2048,
                "temperature" to request.parameters["temperature"] as? Float ?: 0.1f,
                "top_p" to request.parameters["top_p"] as? Float ?: 1.0f,
                "stream" to true
            )
            
            try {
                networkClient.makeStreamingRequest(
                    endpoint = "https://api.openai.com/v1/chat/completions",
                    method = "POST",
                    headers = mapOf(
                        "Authorization" to "Bearer $apiKey",
                        "Content-Type" to "application/json"
                    ),
                    body = openAIRequest
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
            AIModelType.OPENAI_GPT_4 -> "gpt-4"
            AIModelType.OPENAI_GPT_35_TURBO -> "gpt-3.5-turbo"
            else -> throw IllegalArgumentException("不支持的模型类型: $modelType")
        }
    }
    
    private fun buildMessages(request: ChatRequest): List<Map<String, String>> {
        val messages = mutableListOf<Map<String, String>>()
        
        // 添加系统提示
        if (request.context.systemPrompt.isNotEmpty()) {
            messages += mapOf(
                "role" to "system",
                "content" to request.context.systemPrompt
            )
        }
        
        // 添加对话历史
        request.context.conversationHistory.forEach { message ->
            messages += mapOf(
                "role" to when (message.role) {
                    MessageRole.USER -> "user"
                    MessageRole.ASSISTANT -> "assistant"
                    MessageRole.SYSTEM -> "system"
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
            promptBuilder.append("请为以下${request.language}代码生成详细说明：\n\n")
        } else {
            promptBuilder.append("请生成${request.language}代码：\n\n")
        }
        
        promptBuilder.append("需求：${request.codePrompt}\n\n")
        
        if (request.includeExplanation) {
            promptBuilder.append("请提供：\n")
            promptBuilder.append("1. 代码实现\n")
            promptBuilder.append("2. 详细说明\n")
            promptBuilder.append("3. 使用示例\n")
            promptBuilder.append("4. 注意事项\n")
        }
        
        return promptBuilder.toString()
    }
    
    private fun parseChatResponse(response: NetworkResponse): TextResponse {
        // 解析OpenAI响应
        // 这里需要根据实际的API响应格式进行解析
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
        // 解析流式响应块
        if (!chunk.contains("\"choices\"")) return null
        
        val contentDelta = extractContentDeltaFromChunk(chunk)
        val isComplete = chunk.contains("[DONE]")
        
        return StreamingResponse(
            id = java.util.UUID.randomUUID().toString(),
            requestId = "request_id", // 需要从请求中获取
            status = if (isComplete) AIResponseStatus.COMPLETED else AIResponseStatus.STREAMING,
            contentDelta = contentDelta,
            isComplete = isComplete,
            tokensUsed = 0 // 流式响应中通常不包含令牌计数
        )
    }
    
    private fun extractContentFromResponse(data: String): String {
        // 从API响应中提取内容
        // 这里需要根据实际的JSON响应格式进行解析
        return try {
            val json = org.json.JSONObject(data)
            val choices = json.getJSONArray("choices")
            if (choices.length() > 0) {
                val choice = choices.getJSONObject(0)
                choice.getJSONObject("message").getString("content")
            } else {
                "无法解析响应内容"
            }
        } catch (e: Exception) {
            "响应解析错误: ${e.message}"
        }
    }
    
    private fun extractTokensFromResponse(data: String): Int {
        // 从API响应中提取令牌使用量
        return try {
            val json = org.json.JSONObject(data)
            val usage = json.getJSONObject("usage")
            usage.getInt("total_tokens")
        } catch (e: Exception) {
            0
        }
    }
    
    private fun extractContentDeltaFromChunk(chunk: String): String {
        // 从流式响应块中提取内容增量
        return try {
            val lines = chunk.split("\n")
            for (line in lines) {
                if (line.startsWith("data: ") && line != "data: [DONE]") {
                    val data = line.substring(6)
                    val json = org.json.JSONObject(data)
                    val choices = json.getJSONArray("choices")
                    if (choices.length() > 0) {
                        val choice = choices.getJSONObject(0)
                        val delta = choice.optJSONObject("delta")
                        if (delta != null && delta.has("content")) {
                            return delta.getString("content")
                        }
                    }
                }
            }
            ""
        } catch (e: Exception) {
            ""
        }
    }
}