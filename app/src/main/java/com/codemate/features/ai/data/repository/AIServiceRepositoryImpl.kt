package com.codemate.features.ai.data.repository

import com.codemate.features.ai.domain.entity.*
import com.codemate.features.ai.domain.repository.AIServiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI服务仓储实现
 * 处理所有AI服务的实际业务逻辑
 */
@Singleton
class AIServiceRepositoryImpl @Inject constructor(
    private val openAIService: OpenAIService,
    private val claudeService: ClaudeService,
    private val localLLMService: LocalLLMService,
    private val conversationManager: ConversationManager,
    private val serviceMonitor: ServiceMonitor
) : AIServiceRepository {
    
    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    override fun monitorConversations(): Flow<List<Conversation>> = _conversations.asStateFlow()
    
    private val _serviceHealthStates = mutableMapOf<AIProvider, MutableStateFlow<ServiceHealthStatus>>()
    
    init {
        // 初始化服务健康状态流
        AIProvider.values().forEach { provider ->
            _serviceHealthStates[provider] = MutableStateFlow(
                ServiceHealthStatus(
                    isHealthy = false,
                    status = HealthStatus.UNKNOWN
                )
            )
        }
    }
    
    // ===== 服务健康状态管理 =====
    
    override fun monitorServiceHealth(provider: AIProvider): Flow<ServiceHealthStatus> {
        return _serviceHealthStates[provider]?.asStateFlow() 
            ?: throw IllegalArgumentException("不支持的AI提供商: $provider")
    }
    
    override suspend fun getServiceHealth(provider: AIProvider): ServiceHealthStatus {
        return _serviceHealthStates[provider]?.value 
            ?: ServiceHealthStatus(false, HealthStatus.UNKNOWN)
    }
    
    override suspend fun performHealthCheck(provider: AIProvider): ServiceHealthStatus {
        val startTime = System.currentTimeMillis()
        return try {
            val status = when (provider) {
                AIProvider.OPENAI -> openAIService.performHealthCheck()
                AIProvider.ANTHROPIC -> claudeService.performHealthCheck()
                AIProvider.LOCAL -> localLLMService.performHealthCheck()
                AIProvider.CUSTOM -> ServiceHealthStatus(true, HealthStatus.HEALTHY) // 自定义API需要额外配置
            }
            
            val responseTime = System.currentTimeMillis() - startTime
            val updatedStatus = status.copy(
                lastCheckTime = System.currentTimeMillis(),
                responseTime = responseTime
            )
            
            _serviceHealthStates[provider]?.value = updatedStatus
            serviceMonitor.updateHealthStatus(provider, updatedStatus)
            
            updatedStatus
        } catch (e: Exception) {
            val errorStatus = ServiceHealthStatus(
                isHealthy = false,
                status = HealthStatus.UNHEALTHY,
                errorDetails = e.message
            )
            
            _serviceHealthStates[provider]?.value = errorStatus
            serviceMonitor.updateHealthStatus(provider, errorStatus)
            
            errorStatus
        }
    }
    
    // ===== 对话管理 =====
    
    override suspend fun createConversation(title: String): Conversation {
        val conversation = Conversation(
            title = title,
            messages = mutableListOf(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        
        conversationManager.saveConversation(conversation)
        loadAllConversations()
        return conversation
    }
    
    override suspend fun getConversation(id: String): Conversation? {
        return conversationManager.getConversation(id)
    }
    
    override suspend fun getAllConversations(): List<Conversation> {
        return loadAllConversations()
    }
    
    override suspend fun updateConversation(conversation: Conversation): Conversation {
        val updatedConversation = conversation.copy(
            updatedAt = System.currentTimeMillis()
        )
        
        conversationManager.saveConversation(updatedConversation)
        loadAllConversations()
        return updatedConversation
    }
    
    override suspend fun deleteConversation(id: String): Boolean {
        val success = conversationManager.deleteConversation(id)
        if (success) {
            loadAllConversations()
        }
        return success
    }
    
    private suspend fun loadAllConversations(): List<Conversation> {
        val conversations = conversationManager.getAllConversations()
        _conversations.value = conversations
        return conversations
    }
    
    // ===== AI请求执行 =====
    
    override suspend fun sendChatMessage(request: ChatRequest): TextResponse {
        val startTime = System.currentTimeMillis()
        
        try {
            // 安全检查
            val safetyResult = performSafetyCheck(request.userMessage)
            if (!safetyResult.isSafe) {
                return TextResponse(
                    id = UUID.randomUUID().toString(),
                    requestId = request.id,
                    status = AIResponseStatus.ERROR,
                    content = "内容不符合安全要求: ${safetyResult.violations.joinToString()}"
                )
            }
            
            // 执行AI请求
            val response = when (request.modelType) {
                AIModelType.OPENAI_GPT_4, AIModelType.OPENAI_GPT_35_TURBO -> {
                    openAIService.sendChatMessage(request)
                }
                AIModelType.ANTHROPIC_CLAUDE_3_OPUS, 
                AIModelType.ANTHROPIC_CLAUDE_3_SONNET, 
                AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> {
                    claudeService.sendChatMessage(request)
                }
                AIModelType.LOCAL_LLM -> {
                    val localRequest = request.toLocalLLMRequest()
                    localLLMService.executeLocalLLM(localRequest).toTextResponse()
                }
                AIModelType.CUSTOM_API -> {
                    // 自定义API实现
                    TODO("实现自定义API")
                }
            }
            
            // 更新对话历史
            if (request.id.isNotEmpty()) {
                val conversation = getConversation(request.id)
                conversation?.let { conv ->
                    val updatedMessages = conv.messages.toMutableList()
                    updatedMessages.add(AIMessage(
                        role = MessageRole.USER,
                        content = request.userMessage
                    ))
                    updatedMessages.add(AIMessage(
                        role = MessageRole.ASSISTANT,
                        content = response.content
                    ))
                    
                    updateConversation(conv.copy(messages = updatedMessages))
                }
            }
            
            // 更新服务监控
            serviceMonitor.recordRequest(
                provider = when (request.modelType) {
                    AIModelType.OPENAI_GPT_4, AIModelType.OPENAI_GPT_35_TURBO -> AIProvider.OPENAI
                    AIModelType.ANTHROPIC_CLAUDE_3_OPUS, 
                    AIModelType.ANTHROPIC_CLAUDE_3_SONNET, 
                    AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> AIProvider.ANTHROPIC
                    AIModelType.LOCAL_LLM -> AIProvider.LOCAL
                    AIModelType.CUSTOM_API -> AIProvider.CUSTOM
                },
                success = response.status == AIResponseStatus.COMPLETED,
                responseTime = System.currentTimeMillis() - startTime
            )
            
            return response
            
        } catch (e: Exception) {
            // 更新服务监控
            serviceMonitor.recordRequest(
                provider = when (request.modelType) {
                    AIModelType.OPENAI_GPT_4, AIModelType.OPENAI_GPT_35_TURBO -> AIProvider.OPENAI
                    AIModelType.ANTHROPIC_CLAUDE_3_OPUS, 
                    AIModelType.ANTHROPIC_CLAUDE_3_SONNET, 
                    AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> AIProvider.ANTHROPIC
                    AIModelType.LOCAL_LLM -> AIProvider.LOCAL
                    AIModelType.CUSTOM_API -> AIProvider.CUSTOM
                },
                success = false,
                responseTime = System.currentTimeMillis() - startTime,
                error = e.message
            )
            
            throw e
        }
    }
    
    override fun sendStreamingChatMessage(request: ChatRequest): Flow<StreamingResponse> {
        return flow {
            val startTime = System.currentTimeMillis()
            
            try {
                // 安全检查
                val safetyResult = performSafetyCheck(request.userMessage)
                if (!safetyResult.isSafe) {
                    emit(StreamingResponse(
                        id = UUID.randomUUID().toString(),
                        requestId = request.id,
                        status = AIResponseStatus.ERROR,
                        contentDelta = "内容不符合安全要求: ${safetyResult.violations.joinToString()}",
                        isComplete = true
                    ))
                    return@flow
                }
                
                // 执行流式AI请求
                val responseFlow = when (request.modelType) {
                    AIModelType.OPENAI_GPT_4, AIModelType.OPENAI_GPT_35_TURBO -> {
                        openAIService.sendStreamingChatMessage(request)
                    }
                    AIModelType.ANTHROPIC_CLAUDE_3_OPUS, 
                    AIModelType.ANTHROPIC_CLAUDE_3_SONNET, 
                    AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> {
                        claudeService.sendStreamingChatMessage(request)
                    }
                    AIModelType.LOCAL_LLM -> {
                        val localRequest = request.toLocalLLMRequest()
                        localLLMService.executeStreamingLocalLLM(localRequest)
                    }
                    AIModelType.CUSTOM_API -> {
                        // 自定义API实现
                        TODO("实现自定义API流式响应")
                    }
                }
                
                var fullContent = ""
                var totalTokens = 0
                
                responseFlow.collect { response ->
                    fullContent += response.contentDelta
                    totalTokens += response.tokensUsed
                    
                    emit(response)
                    
                    // 如果响应完成，更新对话历史
                    if (response.isComplete && request.id.isNotEmpty()) {
                        val conversation = getConversation(request.id)
                        conversation?.let { conv ->
                            val updatedMessages = conv.messages.toMutableList()
                            updatedMessages.add(AIMessage(
                                role = MessageRole.USER,
                                content = request.userMessage
                            ))
                            updatedMessages.add(AIMessage(
                                role = MessageRole.ASSISTANT,
                                content = fullContent
                            ))
                            
                            updateConversation(conv.copy(messages = updatedMessages))
                        }
                    }
                }
                
            } catch (e: Exception) {
                emit(StreamingResponse(
                    id = UUID.randomUUID().toString(),
                    requestId = request.id,
                    status = AIResponseStatus.ERROR,
                    contentDelta = "错误: ${e.message}",
                    isComplete = true
                ))
            }
        }
    }
    
    override suspend fun generateCode(request: CodeGenerationRequest): TextResponse {
        val startTime = System.currentTimeMillis()
        
        try {
            // 安全检查
            val safetyResult = performSafetyCheck(request.codePrompt)
            if (!safetyResult.isSafe) {
                return TextResponse(
                    id = UUID.randomUUID().toString(),
                    requestId = request.id,
                    status = AIResponseStatus.ERROR,
                    content = "内容不符合安全要求: ${safetyResult.violations.joinToString()}"
                )
            }
            
            val response = when (request.modelType) {
                AIModelType.OPENAI_GPT_4, AIModelType.OPENAI_GPT_35_TURBO -> {
                    openAIService.generateCode(request)
                }
                AIModelType.ANTHROPIC_CLAUDE_3_OPUS, 
                AIModelType.ANTHROPIC_CLAUDE_3_SONNET, 
                AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> {
                    claudeService.generateCode(request)
                }
                AIModelType.LOCAL_LLM -> {
                    val localRequest = request.toLocalLLMRequest()
                    localLLMService.executeLocalLLM(localRequest).toTextResponse()
                }
                AIModelType.CUSTOM_API -> {
                    TODO("实现自定义API代码生成")
                }
            }
            
            // 更新服务监控
            serviceMonitor.recordRequest(
                provider = when (request.modelType) {
                    AIModelType.OPENAI_GPT_4, AIModelType.OPENAI_GPT_35_TURBO -> AIProvider.OPENAI
                    AIModelType.ANTHROPIC_CLAUDE_3_OPUS, 
                    AIModelType.ANTHROPIC_CLAUDE_3_SONNET, 
                    AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> AIProvider.ANTHROPIC
                    AIModelType.LOCAL_LLM -> AIProvider.LOCAL
                    AIModelType.CUSTOM_API -> AIProvider.CUSTOM
                },
                success = response.status == AIResponseStatus.COMPLETED,
                responseTime = System.currentTimeMillis() - startTime
            )
            
            return response
            
        } catch (e: Exception) {
            // 更新服务监控
            serviceMonitor.recordRequest(
                provider = when (request.modelType) {
                    AIModelType.OPENAI_GPT_4, AIModelType.OPENAI_GPT_35_TURBO -> AIProvider.OPENAI
                    AIModelType.ANTHROPIC_CLAUDE_3_OPUS, 
                    AIModelType.ANTHROPIC_CLAUDE_3_SONNET, 
                    AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> AIProvider.ANTHROPIC
                    AIModelType.LOCAL_LLM -> AIProvider.LOCAL
                    AIModelType.CUSTOM_API -> AIProvider.CUSTOM
                },
                success = false,
                responseTime = System.currentTimeMillis() - startTime,
                error = e.message
            )
            
            throw e
        }
    }
    
    override fun generateStreamingCode(request: CodeGenerationRequest): Flow<StreamingResponse> {
        return flow {
            val startTime = System.currentTimeMillis()
            
            try {
                // 安全检查
                val safetyResult = performSafetyCheck(request.codePrompt)
                if (!safetyResult.isSafe) {
                    emit(StreamingResponse(
                        id = UUID.randomUUID().toString(),
                        requestId = request.id,
                        status = AIResponseStatus.ERROR,
                        contentDelta = "内容不符合安全要求: ${safetyResult.violations.joinToString()}",
                        isComplete = true
                    ))
                    return@flow
                }
                
                val responseFlow = when (request.modelType) {
                    AIModelType.OPENAI_GPT_4, AIModelType.OPENAI_GPT_35_TURBO -> {
                        openAIService.generateStreamingCode(request)
                    }
                    AIModelType.ANTHROPIC_CLAUDE_3_OPUS, 
                    AIModelType.ANTHROPIC_CLAUDE_3_SONNET, 
                    AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> {
                        claudeService.generateStreamingCode(request)
                    }
                    AIModelType.LOCAL_LLM -> {
                        val localRequest = request.toLocalLLMRequest()
                        localLLMService.executeStreamingLocalLLM(localRequest)
                    }
                    AIModelType.CUSTOM_API -> {
                        TODO("实现自定义API流式代码生成")
                    }
                }
                
                responseFlow.collect { response ->
                    emit(response)
                }
                
            } catch (e: Exception) {
                emit(StreamingResponse(
                    id = UUID.randomUUID().toString(),
                    requestId = request.id,
                    status = AIResponseStatus.ERROR,
                    contentDelta = "错误: ${e.message}",
                    isComplete = true
                ))
            }
        }
    }
    
    override suspend fun executeLocalLLM(request: LocalLLMRequest): LocalLLMResponse {
        return localLLMService.executeLocalLLM(request)
    }
    
    override fun executeStreamingLocalLLM(request: LocalLLMRequest): Flow<StreamingResponse> {
        return localLLMService.executeStreamingLocalLLM(request)
    }
    
    // ===== 私有方法 =====
    
    private fun performSafetyCheck(content: String): ContentSafetyResult {
        // 简化的安全检查实现
        return ContentSafetyResult(
            isSafe = true,
            riskLevel = RiskLevel.LOW,
            violations = emptyList(),
            confidence = 0.9f,
            suggestedAction = SafetyAction.ALLOW
        )
    }
    
    private fun ChatRequest.toLocalLLMRequest(): LocalLLMRequest {
        return LocalLLMRequest(
            id = this.id,
            modelType = AIModelType.LOCAL_LLM,
            context = this.context,
            modelId = "default_model", // 需要从配置获取
            inputText = this.userMessage,
            maxTokens = this.parameters["max_tokens"] as? Int ?: 512,
            temperature = this.parameters["temperature"] as? Float ?: 0.7f,
            topP = this.parameters["top_p"] as? Float ?: 0.9f
        )
    }
    
    private fun CodeGenerationRequest.toLocalLLMRequest(): LocalLLMRequest {
        return LocalLLMRequest(
            id = this.id,
            modelType = AIModelType.LOCAL_LLM,
            context = this.context,
            modelId = "default_model", // 需要从配置获取
            inputText = this.codePrompt,
            maxTokens = this.parameters["max_tokens"] as? Int ?: 1024,
            temperature = this.parameters["temperature"] as? Float ?: 0.7f,
            topP = this.parameters["top_p"] as? Float ?: 0.9f
        )
    }
    
    private fun LocalLLMResponse.toTextResponse(): TextResponse {
        return TextResponse(
            id = this.id,
            requestId = this.requestId,
            status = this.status,
            content = this.outputText,
            isComplete = this.status == AIResponseStatus.COMPLETED,
            tokensUsed = this.tokensUsed
        )
    }
}