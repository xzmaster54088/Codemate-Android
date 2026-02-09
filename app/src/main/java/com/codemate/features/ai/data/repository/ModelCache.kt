package com.codemate.features.ai.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 模型缓存管理器
 * 负责ONNX模型的缓存和管理
 */
@Singleton
class ModelCache @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("model_cache", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    
    companion object {
        private const val CACHE_KEY = "cached_models"
        private const val CACHE_EXPIRY_TIME = 7 * 24 * 60 * 60 * 1000L // 7天
    }
    
    /**
     * 缓存模型
     */
    suspend fun cacheModel(modelId: String, session: Any) = mutex.withLock {
        try {
            val cacheData = getCacheData().toMutableMap()
            cacheData[modelId] = ModelCacheEntry(
                sessionData = serializeSession(session),
                timestamp = System.currentTimeMillis(),
                accessCount = getAccessCount(modelId) + 1,
                lastAccess = System.currentTimeMillis()
            )
            
            saveCacheData(cacheData)
        } catch (e: Exception) {
            println("缓存模型失败: ${e.message}")
        }
    }
    
    /**
     * 获取缓存的模型
     */
    suspend fun getModel(modelId: String): Any? = mutex.withLock {
        try {
            val cacheData = getCacheData()
            val entry = cacheData[modelId] ?: return@withLock null
            
            // 检查是否过期
            if (isExpired(entry.timestamp)) {
                removeModel(modelId)
                return@withLock null
            }
            
            // 更新访问统计
            updateAccessStats(modelId)
            
            // 反序列化会话
            return@withLock deserializeSession(entry.sessionData)
        } catch (e: Exception) {
            println("获取缓存模型失败: ${e.message}")
            removeModel(modelId) // 移除损坏的缓存
            null
        }
    }
    
    /**
     * 移除缓存的模型
     */
    suspend fun removeModel(modelId: String) = mutex.withLock {
        try {
            val cacheData = getCacheData().toMutableMap()
            cacheData.remove(modelId)
            saveCacheData(cacheData)
        } catch (e: Exception) {
            println("移除缓存模型失败: ${e.message}")
        }
    }
    
    /**
     * 清除所有缓存
     */
    suspend fun clearCache() = mutex.withLock {
        try {
            preferences.edit().remove(CACHE_KEY).apply()
        } catch (e: Exception) {
            println("清除缓存失败: ${e.message}")
        }
    }
    
    /**
     * 获取缓存统计
     */
    suspend fun getCacheStatistics(): ModelCacheStatistics = mutex.withLock {
        val cacheData = getCacheData()
        val now = System.currentTimeMillis()
        
        val totalModels = cacheData.size
        val expiredModels = cacheData.values.count { isExpired(it.timestamp) }
        val activeModels = totalModels - expiredModels
        val totalAccessCount = cacheData.values.sumOf { it.accessCount }
        
        val sizeEstimate = cacheData.values.sumOf { it.sessionData.length }
        
        ModelCacheStatistics(
            totalModels = totalModels,
            activeModels = activeModels,
            expiredModels = expiredModels,
            totalAccessCount = totalAccessCount,
            averageAccessCount = if (totalModels > 0) totalAccessCount.toFloat() / totalModels else 0f,
            cacheSize = sizeEstimate,
            oldestEntry = cacheData.values.minOfOrNull { it.timestamp },
            newestEntry = cacheData.values.maxOfOrNull { it.timestamp }
        )
    }
    
    /**
     * 获取LRU模型ID（最近最少使用）
     */
    suspend fun getLRUModelId(): String? = mutex.withLock {
        val cacheData = getCacheData()
        if (cacheData.isEmpty()) return@withLock null
        
        cacheData.minByOrNull { it.value.lastAccess }?.key
    }
    
    /**
     * 清理过期模型
     */
    suspend fun cleanupExpiredModels() = mutex.withLock {
        val cacheData = getCacheData()
        val validEntries = cacheData.filter { !isExpired(it.value.timestamp) }
        saveCacheData(validEntries)
    }
    
    /**
     * 清理最少使用的模型
     */
    suspend fun cleanupLeastUsedModels(maxModels: Int = 3) = mutex.withLock {
        val cacheData = getCacheData()
        if (cacheData.size <= maxModels) return@withLock
        
        // 按访问次数排序，移除最少使用的
        val sortedEntries = cacheData.entries.sortedBy { it.value.accessCount }
        val modelsToRemove = sortedEntries.take(cacheData.size - maxModels)
        
        val updatedCache = cacheData.toMutableMap()
        modelsToRemove.forEach { (modelId, _) ->
            updatedCache.remove(modelId)
        }
        
        saveCacheData(updatedCache)
    }
    
    /**
     * 预加载模型
     */
    suspend fun preloadModels(modelIds: List<String>) = mutex.withLock {
        modelIds.forEach { modelId ->
            try {
                // 这里应该从网络或本地文件加载模型
                // 为了演示，我们只是标记为已预加载
                val cacheData = getCacheData().toMutableMap()
                if (!cacheData.containsKey(modelId)) {
                    cacheData[modelId] = ModelCacheEntry(
                        sessionData = "preloaded_session",
                        timestamp = System.currentTimeMillis(),
                        accessCount = 0,
                        lastAccess = System.currentTimeMillis(),
                        isPreloaded = true
                    )
                    saveCacheData(cacheData)
                }
            } catch (e: Exception) {
                println("预加载模型失败 $modelId: ${e.message}")
            }
        }
    }
    
    /**
     * 获取访问次数
     */
    private suspend fun getAccessCount(modelId: String): Int {
        val cacheData = getCacheData()
        return cacheData[modelId]?.accessCount ?: 0
    }
    
    /**
     * 更新访问统计
     */
    private suspend fun updateAccessStats(modelId: String) {
        val cacheData = getCacheData().toMutableMap()
        val entry = cacheData[modelId] ?: return
        
        cacheData[modelId] = entry.copy(
            accessCount = entry.accessCount + 1,
            lastAccess = System.currentTimeMillis()
        )
        
        saveCacheData(cacheData)
    }
    
    /**
     * 检查缓存项是否过期
     */
    private fun isExpired(timestamp: Long): Boolean {
        return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_TIME
    }
    
    /**
     * 获取缓存数据
     */
    private fun getCacheData(): Map<String, ModelCacheEntry> {
        return try {
            val json = preferences.getString(CACHE_KEY, "{}")
            val type = object : TypeToken<Map<String, ModelCacheEntry>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * 保存缓存数据
     */
    private fun saveCacheData(cacheData: Map<String, ModelCacheEntry>) {
        val json = gson.toJson(cacheData)
        preferences.edit().putString(CACHE_KEY, json).apply()
    }
    
    /**
     * 序列化会话（简化实现）
     */
    private fun serializeSession(session: Any): String {
        // 实际实现中应该序列化ONNX会话对象
        // 这里返回简化的字符串表示
        return "session_${session.hashCode()}"
    }
    
    /**
     * 反序列化会话（简化实现）
     */
    private fun deserializeSession(sessionData: String): Any {
        // 实际实现中应该反序列化ONNX会话对象
        // 这里返回模拟的会话对象
        return object {
            override fun hashCode(): Int = sessionData.hashCode()
            override fun equals(other: Any?): Boolean = other?.hashCode() == this.hashCode()
        }
    }
}

/**
 * 模型缓存条目
 */
data class ModelCacheEntry(
    val sessionData: String,
    val timestamp: Long,
    val accessCount: Int,
    val lastAccess: Long,
    val isPreloaded: Boolean = false
)

/**
 * 模型缓存统计
 */
data class ModelCacheStatistics(
    val totalModels: Int,
    val activeModels: Int,
    val expiredModels: Int,
    val totalAccessCount: Long,
    val averageAccessCount: Float,
    val cacheSize: Int,
    val oldestEntry: Long?,
    val newestEntry: Long?
)