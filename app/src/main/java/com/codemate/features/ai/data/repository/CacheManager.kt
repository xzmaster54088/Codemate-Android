package com.codemate.features.ai.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.codemate.features.ai.domain.entity.Conversation
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 缓存管理器
 * 负责AI相关数据的缓存管理
 */
@Singleton
class CacheManager @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("ai_cache", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    
    companion object {
        private const val CONVERSATIONS_CACHE_KEY = "cached_conversations"
        private const val RESPONSES_CACHE_KEY = "cached_responses"
        private const val MODELS_CACHE_KEY = "cached_models"
        private const val CACHE_EXPIRY_TIME = 24 * 60 * 60 * 1000L // 24小时
    }
    
    // ===== 对话缓存 =====
    
    /**
     * 缓存对话
     */
    suspend fun cacheConversation(conversation: Conversation) = mutex.withLock {
        val cacheData = getConversationCacheData().toMutableMap()
        cacheData[conversation.id] = CacheEntry(
            data = conversation,
            timestamp = System.currentTimeMillis()
        )
        
        saveConversationCacheData(cacheData)
    }
    
    /**
     * 获取缓存的对话
     */
    suspend fun getCachedConversation(id: String): Conversation? = mutex.withLock {
        val cacheData = getConversationCacheData()
        val entry = cacheData[id] ?: return@withLock null
        
        // 检查是否过期
        if (isExpired(entry.timestamp)) {
            removeConversationFromCache(id)
            return@withLock null
        }
        
        @Suppress("UNCHECKED_CAST")
        return@withLock entry.data as Conversation
    }
    
    /**
     * 获取所有缓存的对话
     */
    suspend fun getAllCachedConversations(): List<Conversation> = mutex.withLock {
        val cacheData = getConversationCacheData()
        val validEntries = cacheData.values.filter { !isExpired(it.timestamp) }
        
        validEntries.map { it.data as Conversation }
    }
    
    /**
     * 从缓存中移除对话
     */
    suspend fun removeConversationFromCache(id: String) = mutex.withLock {
        val cacheData = getConversationCacheData().toMutableMap()
        cacheData.remove(id)
        saveConversationCacheData(cacheData)
    }
    
    /**
     * 清除过期的对话缓存
     */
    suspend fun clearExpiredConversations(cutoffTime: Long) = mutex.withLock {
        val cacheData = getConversationCacheData()
        val validEntries = cacheData.filter { !isExpired(it.value.timestamp) }
        saveConversationCacheData(validEntries)
    }
    
    private fun getConversationCacheData(): Map<String, CacheEntry> {
        return try {
            val json = preferences.getString(CONVERSATIONS_CACHE_KEY, "{}")
            val type = object : TypeToken<Map<String, CacheEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun saveConversationCacheData(cacheData: Map<String, CacheEntry>) {
        val json = gson.toJson(cacheData)
        preferences.edit().putString(CONVERSATIONS_CACHE_KEY, json).apply()
    }
    
    // ===== 响应缓存 =====
    
    /**
     * 缓存响应
     */
    suspend fun cacheResponse(endpoint: String, response: String, timestamp: Long) = mutex.withLock {
        val cacheData = getResponseCacheData().toMutableMap()
        cacheData[endpoint] = CacheEntry(
            data = response,
            timestamp = timestamp
        )
        
        saveResponseCacheData(cacheData)
    }
    
    /**
     * 获取缓存的响应
     */
    suspend fun getCachedResponse(endpoint: String): NetworkResponse? = mutex.withLock {
        val cacheData = getResponseCacheData()
        val entry = cacheData[endpoint] ?: return@withLock null
        
        // 检查是否过期
        if (isExpired(entry.timestamp)) {
            removeResponseFromCache(endpoint)
            return@withLock null
        }
        
        @Suppress("UNCHECKED_CAST")
        val responseData = entry.data as String
        
        return@withLock NetworkResponse(
            isSuccess = true,
            responseCode = 200,
            responseMessage = "Cached",
            data = responseData,
            responseTime = 0L
        )
    }
    
    /**
     * 从缓存中移除响应
     */
    suspend fun removeResponseFromCache(endpoint: String) = mutex.withLock {
        val cacheData = getResponseCacheData().toMutableMap()
        cacheData.remove(endpoint)
        saveResponseCacheData(cacheData)
    }
    
    private fun getResponseCacheData(): Map<String, CacheEntry> {
        return try {
            val json = preferences.getString(RESPONSES_CACHE_KEY, "{}")
            val type = object : TypeToken<Map<String, CacheEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun saveResponseCacheData(cacheData: Map<String, CacheEntry>) {
        val json = gson.toJson(cacheData)
        preferences.edit().putString(RESPONSES_CACHE_KEY, json).apply()
    }
    
    // ===== 模型缓存 =====
    
    /**
     * 缓存模型
     */
    suspend fun cacheModel(modelId: String, model: Any) = mutex.withLock {
        val cacheData = getModelCacheData().toMutableMap()
        cacheData[modelId] = CacheEntry(
            data = model,
            timestamp = System.currentTimeMillis()
        )
        
        saveModelCacheData(cacheData)
    }
    
    /**
     * 获取缓存的模型
     */
    suspend fun getModel(modelId: String): Any? = mutex.withLock {
        val cacheData = getModelCacheData()
        val entry = cacheData[modelId] ?: return@withLock null
        
        // 检查是否过期
        if (isExpired(entry.timestamp)) {
            removeModelFromCache(modelId)
            return@withLock null
        }
        
        return@withLock entry.data
    }
    
    /**
     * 从缓存中移除模型
     */
    suspend fun removeModelFromCache(modelId: String) = mutex.withLock {
        val cacheData = getModelCacheData().toMutableMap()
        cacheData.remove(modelId)
        saveModelCacheData(cacheData)
    }
    
    /**
     * 清除模型缓存
     */
    suspend fun clearModelCache() = mutex.withLock {
        preferences.edit().remove(MODELS_CACHE_KEY).apply()
    }
    
    private fun getModelCacheData(): Map<String, CacheEntry> {
        return try {
            val json = preferences.getString(MODELS_CACHE_KEY, "{}")
            val type = object : TypeToken<Map<String, CacheEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun saveModelCacheData(cacheData: Map<String, CacheEntry>) {
        val json = gson.toJson(cacheData)
        preferences.edit().putString(MODELS_CACHE_KEY, json).apply()
    }
    
    // ===== 通用方法 =====
    
    /**
     * 清除所有缓存
     */
    suspend fun clearCache() = mutex.withLock {
        preferences.edit()
            .remove(CONVERSATIONS_CACHE_KEY)
            .remove(RESPONSES_CACHE_KEY)
            .remove(MODELS_CACHE_KEY)
            .apply()
    }
    
    /**
     * 检查缓存项是否过期
     */
    private fun isExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME
    }
    
    /**
     * 获取缓存统计信息
     */
    suspend fun getCacheStatistics(): CacheStatistics = mutex.withLock {
        val conversationCache = getConversationCacheData()
        val responseCache = getResponseCacheData()
        val modelCache = getModelCacheData()
        
        val validConversations = conversationCache.values.count { !isExpired(it.timestamp) }
        val validResponses = responseCache.values.count { !isExpired(it.timestamp) }
        val validModels = modelCache.values.count { !isExpired(it.timestamp) }
        
        CacheStatistics(
            totalConversations = conversationCache.size,
            validConversations = validConversations,
            expiredConversations = conversationCache.size - validConversations,
            totalResponses = responseCache.size,
            validResponses = validResponses,
            expiredResponses = responseCache.size - validResponses,
            totalModels = modelCache.size,
            validModels = validModels,
            expiredModels = modelCache.size - validModels
        )
    }
}

/**
 * 缓存条目
 */
data class CacheEntry(
    val data: Any,
    val timestamp: Long
)

/**
 * 缓存统计信息
 */
data class CacheStatistics(
    val totalConversations: Int,
    val validConversations: Int,
    val expiredConversations: Int,
    val totalResponses: Int,
    val validResponses: Int,
    val expiredResponses: Int,
    val totalModels: Int,
    val validModels: Int,
    val expiredModels: Int
)