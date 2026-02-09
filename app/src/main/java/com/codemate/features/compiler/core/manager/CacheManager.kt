package com.codemate.features.compiler.core.manager

import android.content.Context
import android.util.Log
import com.codemate.features.compiler.domain.entities.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * 编译缓存管理器
 * 负责管理编译缓存，避免重复编译相同文件
 * 提高编译效率，支持智能缓存失效和清理策略
 */
class CacheManager(private val context: Context) {
    companion object {
        private const val TAG = "CacheManager"
        private const val CACHE_DIR = "compile_cache"
        private const val METADATA_FILE = "cache_metadata.json"
        private const val MAX_CACHE_SIZE = 100 * 1024 * 1024L // 100MB
        private const val MAX_CACHE_ENTRIES = 1000
        private const val CLEANUP_THRESHOLD = 0.9 // 90%时清理
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cacheDir: File = File(context.cacheDir, CACHE_DIR)
    private val metadataFile: File = File(cacheDir, METADATA_FILE)
    
    // 内存缓存
    private val memoryCache = ConcurrentHashMap<String, CacheEntry>()
    
    // 缓存统计
    private val cacheStats = CacheStatistics()
    
    // JSON序列化器
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        isLenient = true
    }

    init {
        // 确保缓存目录存在
        cacheDir.mkdirs()
        scope.launch {
            loadCacheMetadata()
            cleanupExpiredEntries()
        }
    }

    /**
     * 获取缓存键
     */
    suspend fun generateCacheKey(
        sourceFiles: List<String>,
        compilerConfig: CompilerConfig,
        environmentVariables: Map<String, String>
    ): String = withContext(Dispatchers.IO) {
        try {
            val keyData = mutableListOf<String>()
            
            // 添加源文件哈希
            sourceFiles.forEach { file ->
                val fileHash = calculateFileHash(file)
                keyData.add("$file:$fileHash")
            }
            
            // 添加编译器配置
            keyData.add("compiler:${compilerConfig.compilerCommand}")
            keyData.add("args:${compilerConfig.compilerArgs.joinToString("|")}")
            keyData.add("optimization:${compilerConfig.optimizationLevel}")
            keyData.add("debug:${compilerConfig.debugSymbols}")
            keyData.add("warnings:${compilerConfig.warningsEnabled}")
            
            // 添加环境变量哈希
            val envHash = environmentVariables.entries
                .sortedBy { it.key }
                .joinToString("|") { "${it.key}=${it.value}" }
            keyData.add("env:$envHash")
            
            // 生成最终哈希
            val keyString = keyData.joinToString("\n")
            calculateHash(keyString)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate cache key", e)
            // 如果失败，生成一个基于时间的简单键
            "fallback_${System.currentTimeMillis()}"
        }
    }

    /**
     * 检查缓存是否存在且有效
     */
    suspend fun isCacheValid(cacheKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 先检查内存缓存
            memoryCache[cacheKey]?.let { entry ->
                if (isCacheEntryValid(entry)) {
                    cacheStats.hitCount++
                    updateAccessTime(cacheKey)
                    return@withContext true
                }
            }
            
            // 检查磁盘缓存
            val cacheFile = getCacheFile(cacheKey)
            if (cacheFile.exists() && isCacheEntryValid(cacheFile)) {
                // 加载到内存缓存
                val entry = loadCacheEntry(cacheFile)
                if (entry != null) {
                    memoryCache[cacheKey] = entry.updateAccess()
                    cacheStats.hitCount++
                    return@withContext true
                }
            }
            
            cacheStats.missCount++
            false
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check cache validity for key: $cacheKey", e)
            false
        }
    }

    /**
     * 获取缓存结果
     */
    suspend fun getCachedResult(cacheKey: String): CompileResult? = withContext(Dispatchers.IO) {
        try {
            // 先检查内存缓存
            memoryCache[cacheKey]?.let { entry ->
                if (isCacheEntryValid(entry)) {
                    cacheStats.hitCount++
                    return@withContext entry.result
                }
            }
            
            // 检查磁盘缓存
            val cacheFile = getCacheFile(cacheKey)
            if (cacheFile.exists()) {
                val entry = loadCacheEntry(cacheFile)
                if (entry != null && isCacheEntryValid(entry)) {
                    memoryCache[cacheKey] = entry.updateAccess()
                    cacheStats.hitCount++
                    return@withContext entry.result
                }
            }
            
            null
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get cached result for key: $cacheKey", e)
            null
        }
    }

    /**
     * 保存缓存结果
     */
    suspend fun saveCache(
        cacheKey: String,
        result: CompileResult,
        outputFiles: List<String>,
        sourceFileHashes: Map<String, String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val cacheEntry = CacheEntry(
                cacheKey = cacheKey,
                outputHash = calculateHash(result.toString()),
                outputFiles = outputFiles,
                result = result,
                createdAt = java.util.Date(),
                lastAccessed = java.util.Date(),
                size = estimateCacheSize(result, outputFiles)
            )
            
            // 保存到内存缓存
            memoryCache[cacheKey] = cacheEntry
            
            // 保存到磁盘
            val cacheFile = getCacheFile(cacheKey)
            saveCacheEntry(cacheEntry, cacheFile)
            
            // 更新统计信息
            cacheStats.totalSize += cacheEntry.size
            cacheStats.totalEntries = memoryCache.size
            
            // 检查是否需要清理缓存
            if (shouldCleanupCache()) {
                scope.launch {
                    cleanupCache()
                }
            }
            
            Log.d(TAG, "Cached result for key: $cacheKey (${outputFiles.size} files)")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache for key: $cacheKey", e)
            false
        }
    }

    /**
     * 清除指定缓存
     */
    suspend fun clearCache(cacheKey: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 从内存缓存移除
            val removed = memoryCache.remove(cacheKey)
            
            // 从磁盘删除
            val cacheFile = getCacheFile(cacheKey)
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
            
            if (removed != null) {
                cacheStats.totalSize -= removed.size
            }
            
            Log.d(TAG, "Cleared cache for key: $cacheKey")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear cache for key: $cacheKey", e)
            false
        }
    }

    /**
     * 清除所有缓存
     */
    suspend fun clearAllCache(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 清除内存缓存
            memoryCache.clear()
            
            // 清除磁盘缓存
            cacheDir.listFiles()?.forEach { file ->
                if (file.name != METADATA_FILE) {
                    file.deleteRecursively()
                }
            }
            
            // 重置统计信息
            cacheStats.reset()
            
            Log.i(TAG, "Cleared all cache")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear all cache", e)
            false
        }
    }

    /**
     * 获取缓存统计信息
     */
    suspend fun getCacheStatistics(): CacheStatistics = withContext(Dispatchers.IO) {
        cacheStats.apply {
            totalEntries = memoryCache.size
            hitRate = if (hitCount + missCount > 0) {
                hitCount.toDouble() / (hitCount + missCount).toDouble()
            } else {
                0.0
            }
        }
    }

    /**
     * 获取缓存使用情况
     */
    suspend fun getCacheUsage(): CacheUsage = withContext(Dispatchers.IO) {
        val currentSize = cacheDir.walkTopDown()
            .filter { it.isFile && it.name != METADATA_FILE }
            .sumOf { it.length() }
        
        val totalEntries = memoryCache.size
        
        CacheUsage(
            usedSize = currentSize,
            maxSize = MAX_CACHE_SIZE,
            entryCount = totalEntries,
            maxEntries = MAX_CACHE_ENTRIES,
            usagePercentage = (currentSize * 100.0 / MAX_CACHE_SIZE).toInt()
        )
    }

    /**
     * 获取所有缓存键
     */
    suspend fun getAllCacheKeys(): Set<String> = withContext(Dispatchers.IO) {
        memoryCache.keys.toSet()
    }

    /**
     * 获取缓存的输出文件列表
     */
    suspend fun getCachedOutputFiles(cacheKey: String): List<String> = withContext(Dispatchers.IO) {
        memoryCache[cacheKey]?.outputFiles ?: emptyList()
    }

    /**
     * 预热缓存
     */
    suspend fun warmupCache() = withContext(Dispatchers.IO) {
        try {
            val cacheFiles = cacheDir.listFiles()?.filter { 
                it.isFile && it.name != METADATA_FILE 
            } ?: emptyList()
            
            Log.i(TAG, "Warming up cache with ${cacheFiles.size} entries")
            
            cacheFiles.forEach { cacheFile ->
                try {
                    val entry = loadCacheEntry(cacheFile)
                    if (entry != null && isCacheEntryValid(entry)) {
                        memoryCache[entry.cacheKey] = entry
                    } else {
                        // 删除无效缓存
                        cacheFile.delete()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to load cache entry: ${cacheFile.name}", e)
                    cacheFile.delete()
                }
            }
            
            Log.i(TAG, "Cache warmup completed, loaded ${memoryCache.size} entries")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to warmup cache", e)
        }
    }

    /**
     * 清理管理器资源
     */
    fun cleanup() {
        scope.cancel()
        saveCacheMetadata()
    }

    // 私有方法
    private suspend fun calculateFileHash(filePath: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) {
                return@withContext "missing"
            }
            
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            
            md.digest().joinToString("") { "%02x".format(it) }
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to calculate hash for file: $filePath", e)
            "error"
        }
    }

    private fun calculateHash(input: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(input.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun getCacheFile(cacheKey: String): File {
        return File(cacheDir, "$cacheKey.cache")
    }

    private fun isCacheEntryValid(entry: CacheEntry): Boolean {
        // 检查是否过期（30天）
        val maxAge = 30L * 24 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - entry.createdAt.time) < maxAge
    }

    private fun isCacheEntryValid(cacheFile: File): Boolean {
        return cacheFile.exists() && cacheFile.length() > 0
    }

    private fun loadCacheEntry(cacheFile: File): CacheEntry? {
        return try {
            val jsonString = cacheFile.readText()
            json.decodeFromString<CacheEntry>(jsonString)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cache entry from: ${cacheFile.name}", e)
            null
        }
    }

    private fun saveCacheEntry(entry: CacheEntry, cacheFile: File) {
        try {
            cacheFile.parentFile?.mkdirs()
            val jsonString = json.encodeToString(entry)
            cacheFile.writeText(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache entry to: ${cacheFile.name}", e)
        }
    }

    private suspend fun updateAccessTime(cacheKey: String) {
        memoryCache[cacheKey]?.let { entry ->
            memoryCache[cacheKey] = entry.updateAccess()
        }
    }

    private fun estimateCacheSize(result: CompileResult, outputFiles: List<String>): Long {
        var size = 1000L // 基础元数据大小
        
        // 添加结果数据大小
        size += result.output.length.toLong()
        size += result.errorOutput.length.toLong()
        size += result.performanceMetrics.toString().length.toLong()
        
        // 添加输出文件大小估算
        size += outputFiles.size * 100L // 假设每个文件路径100字节
        
        return size
    }

    private suspend fun shouldCleanupCache(): Boolean = withContext(Dispatchers.IO) {
        val usage = getCacheUsage()
        usage.entryCount >= MAX_CACHE_ENTRIES * CLEANUP_THRESHOLD ||
                usage.usedSize >= MAX_CACHE_SIZE * CLEANUP_THRESHOLD
    }

    private suspend fun cleanupCache() {
        try {
            Log.i(TAG, "Starting cache cleanup")
            
            val currentSize = cacheDir.walkTopDown()
                .filter { it.isFile && it.name != METADATA_FILE }
                .sumOf { it.length() }
            
            if (currentSize < MAX_CACHE_SIZE * CLEANUP_THRESHOLD && 
                memoryCache.size < MAX_CACHE_ENTRIES * CLEANUP_THRESHOLD) {
                Log.d(TAG, "Cache cleanup not needed")
                return
            }
            
            // 按访问时间排序，移除最少使用的条目
            val sortedEntries = memoryCache.values
                .sortedBy { it.lastAccessed }
            
            var removedSize = 0L
            var removedCount = 0
            
            sortedEntries.take(MAX_CACHE_ENTRIES / 4).forEach { entry -> // 移除25%
                clearCache(entry.cacheKey)
                removedSize += entry.size
                removedCount++
            }
            
            // 如果还有超大的问题，清除最老的条目
            if (removedSize < currentSize * 0.3) {
                sortedEntries.take(MAX_CACHE_ENTRIES / 2).forEach { entry -> // 再移除25%
                    clearCache(entry.cacheKey)
                    removedSize += entry.size
                    removedCount++
                }
            }
            
            cacheStats.cleanupCount++
            
            Log.i(TAG, "Cache cleanup completed: removed $removedCount entries, freed ${removedSize / 1024}KB")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup cache", e)
        }
    }

    private suspend fun cleanupExpiredEntries() {
        try {
            val expiredKeys = memoryCache.values
                .filter { !isCacheEntryValid(it) }
                .map { it.cacheKey }
            
            expiredKeys.forEach { key ->
                clearCache(key)
            }
            
            if (expiredKeys.isNotEmpty()) {
                Log.i(TAG, "Cleaned up ${expiredKeys.size} expired cache entries")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup expired entries", e)
        }
    }

    private suspend fun loadCacheMetadata() {
        try {
            if (metadataFile.exists()) {
                val metadata = metadataFile.readText()
                // 这里可以加载更详细的缓存元数据
                Log.d(TAG, "Loaded cache metadata")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load cache metadata", e)
        }
    }

    private fun saveCacheMetadata() {
        try {
            val metadata = CacheMetadata(
                totalEntries = memoryCache.size,
                totalSize = cacheStats.totalSize,
                lastCleanup = System.currentTimeMillis(),
                hitRate = cacheStats.hitRate
            )
            
            val metadataJson = json.encodeToString(metadata)
            metadataFile.writeText(metadataJson)
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save cache metadata", e)
        }
    }
}

/**
 * 缓存统计信息
 */
data class CacheStatistics(
    var hitCount: Long = 0,
    var missCount: Long = 0,
    var totalSize: Long = 0,
    var totalEntries: Int = 0,
    var cleanupCount: Long = 0
) {
    val hitRate: Double
        get() = if (hitCount + missCount > 0) {
            hitCount.toDouble() / (hitCount + missCount).toDouble()
        } else 0.0
}

/**
 * 缓存使用情况
 */
data class CacheUsage(
    val usedSize: Long,
    val maxSize: Long,
    val entryCount: Int,
    val maxEntries: Int,
    val usagePercentage: Int
)

/**
 * 缓存元数据
 */
data class CacheMetadata(
    val totalEntries: Int,
    val totalSize: Long,
    val lastCleanup: Long,
    val hitRate: Double
)