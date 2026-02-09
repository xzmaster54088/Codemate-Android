package com.codemate.features.ai.data.repository

import com.codemate.features.ai.domain.entity.Conversation
import com.codemate.features.ai.domain.entity.AIMessage
import com.codemate.features.ai.domain.entity.MessageRole
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 对话管理器
 * 负责对话的本地存储和管理
 */
@Singleton
class ConversationManager @Inject constructor(
    private val localDatabase: LocalDatabase,
    private val cacheManager: CacheManager
) {
    private val mutex = Mutex()
    
    /**
     * 保存对话
     */
    suspend fun saveConversation(conversation: Conversation) = mutex.withLock {
        localDatabase.saveConversation(conversation)
        cacheManager.cacheConversation(conversation)
    }
    
    /**
     * 获取对话
     */
    suspend fun getConversation(id: String): Conversation? = mutex.withLock {
        // 先从缓存获取
        cacheManager.getCachedConversation(id)?.let { return it }
        
        // 从数据库获取
        localDatabase.getConversation(id)?.let { conversation ->
            // 缓存对话
            cacheManager.cacheConversation(conversation)
            return conversation
        }
        
        return null
    }
    
    /**
     * 获取所有对话
     */
    suspend fun getAllConversations(): List<Conversation> = mutex.withLock {
        // 先从缓存获取
        val cachedConversations = cacheManager.getAllCachedConversations()
        if (cachedConversations.isNotEmpty()) {
            return cachedConversations
        }
        
        // 从数据库获取
        val conversations = localDatabase.getAllConversations()
        conversations.forEach { conversation ->
            cacheManager.cacheConversation(conversation)
        }
        
        return conversations
    }
    
    /**
     * 删除对话
     */
    suspend fun deleteConversation(id: String) = mutex.withLock {
        localDatabase.deleteConversation(id)
        cacheManager.removeCachedConversation(id)
    }
    
    /**
     * 搜索对话
     */
    suspend fun searchConversations(query: String): List<Conversation> = mutex.withLock {
        localDatabase.searchConversations(query)
    }
    
    /**
     * 获取对话消息
     */
    suspend fun getConversationMessages(conversationId: String): List<AIMessage> = mutex.withLock {
        localDatabase.getConversationMessages(conversationId)
    }
    
    /**
     * 保存消息到对话
     */
    suspend fun addMessageToConversation(conversationId: String, message: AIMessage) = mutex.withLock {
        localDatabase.addMessageToConversation(conversationId, message)
        
        // 更新缓存
        getConversation(conversationId)?.let { conversation ->
            val updatedMessages = conversation.messages.toMutableList()
            updatedMessages.add(message)
            cacheManager.cacheConversation(conversation.copy(messages = updatedMessages))
        }
    }
    
    /**
     * 更新对话消息
     */
    suspend fun updateMessageInConversation(conversationId: String, messageId: String, newContent: String) = mutex.withLock {
        localDatabase.updateMessageInConversation(conversationId, messageId, newContent)
        
        // 更新缓存
        getConversation(conversationId)?.let { conversation ->
            val updatedMessages = conversation.messages.map { message ->
                if (message.id == messageId) {
                    message.copy(content = newContent)
                } else {
                    message
                }
            }
            cacheManager.cacheConversation(conversation.copy(messages = updatedMessages))
        }
    }
    
    /**
     * 删除对话消息
     */
    suspend fun deleteMessageFromConversation(conversationId: String, messageId: String) = mutex.withLock {
        localDatabase.deleteMessageFromConversation(conversationId, messageId)
        
        // 更新缓存
        getConversation(conversationId)?.let { conversation ->
            val updatedMessages = conversation.messages.filter { it.id != messageId }
            cacheManager.cacheConversation(conversation.copy(messages = updatedMessages))
        }
    }
    
    /**
     * 清理过期的对话
     */
    suspend fun cleanupExpiredConversations(maxAgeInDays: Long = 30) = mutex.withLock {
        val cutoffTime = System.currentTimeMillis() - (maxAgeInDays * 24 * 60 * 60 * 1000)
        localDatabase.deleteExpiredConversations(cutoffTime)
        cacheManager.clearExpiredConversations(cutoffTime)
    }
    
    /**
     * 导出对话数据
     */
    suspend fun exportConversations(): String = mutex.withLock {
        val conversations = getAllConversations()
        gson.toJson(conversations)
    }
    
    /**
     * 导入对话数据
     */
    suspend fun importConversations(jsonData: String): Boolean = mutex.withLock {
        return try {
            val conversations: List<Conversation> = gson.fromJson(jsonData)
            conversations.forEach { conversation ->
                saveConversation(conversation)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}