package com.codemate.features.ai.domain.repository

import com.codemate.features.ai.domain.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * AI服务仓储接口
 * 定义所有AI服务的基本操作
 */
interface AIServiceRepository {
    
    // ===== 服务状态管理 =====
    
    /**
     * 获取服务健康状态
     */
    suspend fun getServiceHealth(provider: AIProvider): ServiceHealthStatus
    
    /**
     * 监听服务健康状态变化
     */
    fun monitorServiceHealth(provider: AIProvider): Flow<ServiceHealthStatus>
    
    /**
     * 执行服务健康检查
     */
    suspend fun performHealthCheck(provider: AIProvider): ServiceHealthStatus
    
    // ===== 对话管理 =====
    
    /**
     * 创建新对话
     */
    suspend fun createConversation(title: String = "新对话"): Conversation
    
    /**
     * 获取对话
     */
    suspend fun getConversation(id: String): Conversation?
    
    /**
     * 获取所有对话
     */
    suspend fun getAllConversations(): List<Conversation>
    
    /**
     * 更新对话
     */
    suspend fun updateConversation(conversation: Conversation): Conversation
    
    /**
     * 删除对话
     */
    suspend fun deleteConversation(id: String): Boolean
    
    /**
     * 监听对话变化
     */
    fun monitorConversations(): Flow<List<Conversation>>
    
    // ===== AI请求执行 =====
    
    /**
     * 发送聊天请求
     */
    suspend fun sendChatMessage(request: ChatRequest): TextResponse
    
    /**
     * 发送流式聊天请求
     */
    fun sendStreamingChatMessage(request: ChatRequest): Flow<StreamingResponse>
    
    /**
     * 生成代码
     */
    suspend fun generateCode(request: CodeGenerationRequest): TextResponse
    
    /**
     * 生成流式代码
     */
    fun generateStreamingCode(request: CodeGenerationRequest): Flow<StreamingResponse>
    
    /**
     * 执行本地LLM推理
     */
    suspend fun executeLocalLLM(request: LocalLLMRequest): LocalLLMResponse
    
    /**
     * 执行流式本地LLM推理
     */
    fun executeStreamingLocalLLM(request: LocalLLMRequest): Flow<StreamingResponse>
}