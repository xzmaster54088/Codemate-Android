package com.codemate.features.ai.domain.usecase

import com.codemate.features.ai.domain.entity.*
import com.codemate.features.ai.domain.repository.AIServiceRepository
import kotlinx.coroutines.flow.Flow

/**
 * AI聊天用例
 * 处理聊天对话的业务逻辑
 */
class ChatUseCase(
    private val aiServiceRepository: AIServiceRepository
) {
    
    /**
     * 发送聊天消息
     */
    suspend fun sendMessage(
        conversationId: String,
        message: String,
        context: ConversationContext,
        modelType: AIModelType
    ): Result<TextResponse> {
        return try {
            val request = ChatRequest(
                id = conversationId,
                modelType = modelType,
                context = context,
                userMessage = message
            )
            
            val response = aiServiceRepository.sendChatMessage(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 发送流式聊天消息
     */
    fun sendStreamingMessage(
        conversationId: String,
        message: String,
        context: ConversationContext,
        modelType: AIModelType
    ): Flow<StreamingResponse> {
        val request = ChatRequest(
            id = conversationId,
            modelType = modelType,
            context = context,
            userMessage = message
        )
        
        return aiServiceRepository.sendStreamingChatMessage(request)
    }
    
    /**
     * 创建新对话
     */
    suspend fun createNewConversation(title: String = "新对话"): Result<Conversation> {
        return try {
            val conversation = aiServiceRepository.createConversation(title)
            Result.success(conversation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取对话
     */
    suspend fun getConversation(id: String): Result<Conversation> {
        return try {
            val conversation = aiServiceRepository.getConversation(id)
            if (conversation != null) {
                Result.success(conversation)
            } else {
                Result.failure(Exception("对话不存在"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取所有对话
     */
    suspend fun getAllConversations(): Result<List<Conversation>> {
        return try {
            val conversations = aiServiceRepository.getAllConversations()
            Result.success(conversations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 删除对话
     */
    suspend fun deleteConversation(id: String): Result<Boolean> {
        return try {
            val success = aiServiceRepository.deleteConversation(id)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 更新对话
     */
    suspend fun updateConversation(conversation: Conversation): Result<Conversation> {
        return try {
            val updatedConversation = aiServiceRepository.updateConversation(conversation)
            Result.success(updatedConversation)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}