package com.codemate.features.ai.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.codemate.features.ai.domain.entity.Conversation
import com.codemate.features.ai.domain.entity.AIMessage
import com.codemate.features.ai.domain.entity.MessageRole
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本地数据库管理器
 * 负责AI相关数据的本地存储（使用SharedPreferences作为简单存储）
 */
@Singleton
class LocalDatabase @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    private val conversationsPrefs: SharedPreferences = context.getSharedPreferences("conversations_db", Context.MODE_PRIVATE)
    private val messagesPrefs: SharedPreferences = context.getSharedPreferences("messages_db", Context.MODE_PRIVATE)
    
    companion object {
        private const val CONVERSATIONS_KEY = "conversations"
        private const val MESSAGES_KEY = "messages"
    }
    
    /**
     * 保存对话
     */
    suspend fun saveConversation(conversation: Conversation) = withContext(Dispatchers.IO) {
        try {
            val conversations = getAllConversations().toMutableSet()
            conversations.add(conversation)
            
            val json = gson.toJson(conversations)
            conversationsPrefs.edit().putString(CONVERSATIONS_KEY, json).apply()
        } catch (e: Exception) {
            throw RuntimeException("保存对话失败", e)
        }
    }
    
    /**
     * 获取对话
     */
    suspend fun getConversation(id: String): Conversation? = withContext(Dispatchers.IO) {
        try {
            val conversations = getAllConversations()
            conversations.find { it.id == id }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取所有对话
     */
    suspend fun getAllConversations(): Set<Conversation> = withContext(Dispatchers.IO) {
        try {
            val json = conversationsPrefs.getString(CONVERSATIONS_KEY, null) ?: return@withContext emptySet()
            val type = object : TypeToken<Set<Conversation>>() {}.type
            val conversations: Set<Conversation> = gson.fromJson(json, type)
            conversations
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    /**
     * 删除对话
     */
    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        try {
            // 删除对话
            val conversations = getAllConversations().toMutableSet()
            conversations.removeAll { it.id == id }
            
            val json = gson.toJson(conversations)
            conversationsPrefs.edit().putString(CONVERSATIONS_KEY, json).apply()
            
            // 删除对话的消息
            deleteConversationMessages(id)
        } catch (e: Exception) {
            throw RuntimeException("删除对话失败", e)
        }
    }
    
    /**
     * 搜索对话
     */
    suspend fun searchConversations(query: String): List<Conversation> = withContext(Dispatchers.IO) {
        try {
            val conversations = getAllConversations()
            val lowercaseQuery = query.lowercase()
            
            conversations.filter { conversation ->
                conversation.title.lowercase().contains(lowercaseQuery) ||
                conversation.messages.any { message ->
                    message.content.lowercase().contains(lowercaseQuery)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取对话的消息
     */
    suspend fun getConversationMessages(conversationId: String): List<AIMessage> = withContext(Dispatchers.IO) {
        try {
            val messagesMap = getMessagesMap()
            messagesMap[conversationId]?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 添加消息到对话
     */
    suspend fun addMessageToConversation(conversationId: String, message: AIMessage) = withContext(Dispatchers.IO) {
        try {
            val messagesMap = getMessagesMap().toMutableMap()
            val messages = messagesMap[conversationId]?.toMutableList() ?: mutableListOf()
            messages.add(message)
            messagesMap[conversationId] = messages
            
            saveMessagesMap(messagesMap)
        } catch (e: Exception) {
            throw RuntimeException("添加消息失败", e)
        }
    }
    
    /**
     * 更新对话中的消息
     */
    suspend fun updateMessageInConversation(conversationId: String, messageId: String, newContent: String) = withContext(Dispatchers.IO) {
        try {
            val messagesMap = getMessagesMap().toMutableMap()
            val messages = messagesMap[conversationId]?.toMutableList() ?: return@withContext
            
            val messageIndex = messages.indexOfFirst { it.id == messageId }
            if (messageIndex >= 0) {
                messages[messageIndex] = messages[messageIndex].copy(content = newContent)
                messagesMap[conversationId] = messages
                saveMessagesMap(messagesMap)
            }
        } catch (e: Exception) {
            throw RuntimeException("更新消息失败", e)
        }
    }
    
    /**
     * 从对话中删除消息
     */
    suspend fun deleteMessageFromConversation(conversationId: String, messageId: String) = withContext(Dispatchers.IO) {
        try {
            val messagesMap = getMessagesMap().toMutableMap()
            val messages = messagesMap[conversationId]?.toMutableList() ?: return@withContext
            
            messages.removeAll { it.id == messageId }
            messagesMap[conversationId] = messages
            saveMessagesMap(messagesMap)
        } catch (e: Exception) {
            throw RuntimeException("删除消息失败", e)
        }
    }
    
    /**
     * 删除过期对话
     */
    suspend fun deleteExpiredConversations(cutoffTime: Long) = withContext(Dispatchers.IO) {
        try {
            val conversations = getAllConversations()
            val validConversations = conversations.filter { it.updatedAt > cutoffTime }
            
            val conversationsSet = validConversations.toSet()
            val json = gson.toJson(conversationsSet)
            conversationsPrefs.edit().putString(CONVERSATIONS_KEY, json).apply()
            
            // 清理过期对话的消息
            cleanupExpiredMessages(cutoffTime)
        } catch (e: Exception) {
            throw RuntimeException("清理过期对话失败", e)
        }
    }
    
    /**
     * 删除对话的所有消息
     */
    private suspend fun deleteConversationMessages(conversationId: String) {
        val messagesMap = getMessagesMap().toMutableMap()
        messagesMap.remove(conversationId)
        saveMessagesMap(messagesMap)
    }
    
    /**
     * 清理过期消息
     */
    private suspend fun cleanupExpiredMessages(cutoffTime: Long) {
        val messagesMap = getMessagesMap()
        val validMessages = messagesMap.mapValues { (_, messages) ->
            messages.filter { it.timestamp > cutoffTime }
        }.filterValues { it.isNotEmpty() }
        
        saveMessagesMap(validMessages)
    }
    
    /**
     * 获取消息映射
     */
    private fun getMessagesMap(): Map<String, List<AIMessage>> {
        return try {
            val json = messagesPrefs.getString(MESSAGES_KEY, "{}")
            val type = object : TypeToken<Map<String, List<AIMessage>>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * 保存消息映射
     */
    private fun saveMessagesMap(messagesMap: Map<String, List<AIMessage>>) {
        val json = gson.toJson(messagesMap)
        messagesPrefs.edit().putString(MESSAGES_KEY, json).apply()
    }
    
    /**
     * 导出数据库
     */
    suspend fun exportDatabase(): String = withContext(Dispatchers.IO) {
        try {
            val exportData = mapOf(
                "conversations" to getAllConversations(),
                "messages" to getMessagesMap(),
                "exportTime" to System.currentTimeMillis()
            )
            gson.toJson(exportData)
        } catch (e: Exception) {
            throw RuntimeException("导出数据库失败", e)
        }
    }
    
    /**
     * 导入数据库
     */
    suspend fun importDatabase(jsonData: String) = withContext(Dispatchers.IO) {
        try {
            val importData = gson.fromJson(jsonData, Map::class.java)
            
            // 导入对话
            val conversationsJson = gson.toJson(importData["conversations"])
            conversationsPrefs.edit().putString(CONVERSATIONS_KEY, conversationsJson).apply()
            
            // 导入消息
            val messagesJson = gson.toJson(importData["messages"])
            messagesPrefs.edit().putString(MESSAGES_KEY, messagesJson).apply()
        } catch (e: Exception) {
            throw RuntimeException("导入数据库失败", e)
        }
    }
    
    /**
     * 清空数据库
     */
    suspend fun clearDatabase() = withContext(Dispatchers.IO) {
        try {
            conversationsPrefs.edit().clear().apply()
            messagesPrefs.edit().clear().apply()
        } catch (e: Exception) {
            throw RuntimeException("清空数据库失败", e)
        }
    }
    
    /**
     * 获取数据库统计信息
     */
    suspend fun getDatabaseStatistics(): DatabaseStatistics = withContext(Dispatchers.IO) {
        try {
            val conversations = getAllConversations()
            val messagesMap = getMessagesMap()
            val totalMessages = messagesMap.values.flatten().size
            
            DatabaseStatistics(
                totalConversations = conversations.size,
                totalMessages = totalMessages,
                oldestConversation = conversations.minOfOrNull { it.createdAt },
                newestConversation = conversations.maxOfOrNull { it.createdAt },
                averageMessagesPerConversation = if (conversations.isNotEmpty()) {
                    totalMessages.toFloat() / conversations.size
                } else {
                    0f
                }
            )
        } catch (e: Exception) {
            DatabaseStatistics()
        }
    }
}

/**
 * 数据库统计信息
 */
data class DatabaseStatistics(
    val totalConversations: Int = 0,
    val totalMessages: Int = 0,
    val oldestConversation: Long? = null,
    val newestConversation: Long? = null,
    val averageMessagesPerConversation: Float = 0f
)