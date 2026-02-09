package com.codemate.security

import android.content.ContentResolver
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.*
import java.lang.ref.WeakReference
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * 资源管理器
 * 自动管理内存、文件句柄、数据库连接等资源的分配和释放
 * 提供资源池、引用计数、生命周期管理等功能
 */
@Singleton
class ResourceManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "ResourceManager"
        private const val MAX_MEMORY_CACHE_SIZE = 50 * 1024 * 1024L // 50MB
        private const val MAX_FILE_DESCRIPTORS = 100
        private const val MAX_BITMAP_CACHE_SIZE = 20
        private const val RESOURCE_CLEANUP_INTERVAL = 60000L // 1分钟
        private const val MAX_POOL_SIZE = 10
        private const val MONITORING_INTERVAL = 30000L // 30秒
    }

    private val monitoringScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val cleanupScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isMonitoring = AtomicBoolean(false)
    
    // 内存缓存
    private val memoryCache = LRUCache<String, Any>(MAX_MEMORY_CACHE_SIZE)
    
    // 文件描述符池
    private val fileDescriptorPool = ResourcePool<FileDescriptor> { FileDescriptor() }
    
    // Bitmap缓存
    private val bitmapCache = LRUCache<String, Bitmap>(MAX_BITMAP_CACHE_SIZE)
    
    // 资源跟踪
    private val trackedResources = ConcurrentHashMap<String, TrackedResource>()
    private val resourceLock = ReentrantLock()
    
    // 资源监控
    private val resourceMetrics = ResourceMetrics()
    private val resourceEvents = Channel<ResourceEvent>(Channel.UNLIMITED)
    
    // 数据库连接池
    private val dbConnectionPool = ResourcePool<android.database.sqlite.SQLiteDatabase> { 
        // 这里应该返回实际的数据库连接
        throw NotImplementedError("数据库连接池需要具体实现")
    }

    /**
     * 启动资源管理
     */
    fun startResourceManagement(): Boolean {
        return try {
            if (isMonitoring.getAndSet(true)) {
                Log.w(TAG, "资源管理已经在运行中")
                return true
            }

            // 启动监控
            monitoringScope.launch {
                resourceMonitoringLoop()
            }

            // 启动清理任务
            cleanupScope.launch {
                resourceCleanupLoop()
            }

            SecurityLog.i("资源管理已启动")
            true
        } catch (e: Exception) {
            SecurityLog.e("启动资源管理失败", e)
            isMonitoring.set(false)
            false
        }
    }

    /**
     * 停止资源管理
     */
    fun stopResourceManagement() {
        if (isMonitoring.getAndSet(false)) {
            try {
                monitoringScope.cancel()
                cleanupScope.cancel()
                
                // 清理所有资源
                cleanupAllResources()
                
                SecurityLog.i("资源管理已停止")
            } catch (e: Exception) {
                SecurityLog.e("停止资源管理失败", e)
            }
        }
    }

    /**
     * 存储对象到内存缓存
     */
    fun <T> storeInCache(key: String, value: T): Boolean {
        return try {
            val size = estimateSize(value)
            memoryCache.put(key, value, size)
            
            trackResource(
                key = "cache:$key",
                type = ResourceType.MEMORY_CACHE,
                size = size,
                timestamp = System.currentTimeMillis()
            )
            
            SecurityLog.d("对象已存储到缓存: $key, 大小: $size bytes")
            true
        } catch (e: Exception) {
            SecurityLog.e("存储缓存对象失败: $key", e)
            false
        }
    }

    /**
     * 从内存缓存获取对象
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getFromCache(key: String): T? {
        return try {
            memoryCache.get(key) as? T
        } catch (e: Exception) {
            SecurityLog.e("从缓存获取对象失败: $key", e)
            null
        }
    }

    /**
     * 移除缓存对象
     */
    fun removeFromCache(key: String): Boolean {
        return try {
            memoryCache.remove(key)
            untrackResource("cache:$key")
            SecurityLog.d("缓存对象已移除: $key")
            true
        } catch (e: Exception) {
            SecurityLog.e("移除缓存对象失败: $key", e)
            false
        }
    }

    /**
     * 存储Bitmap
     */
    suspend fun storeBitmap(key: String, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            if (bitmapCache.size() >= MAX_BITMAP_CACHE_SIZE) {
                // 清理最老的Bitmap
                val oldestKey = bitmapCache.keys().nextElement()
                val oldBitmap = bitmapCache.remove(oldestKey)
                oldBitmap?.recycle()
            }
            
            bitmapCache.put(key, bitmap)
            val size = bitmap.byteCount
            
            trackResource(
                key = "bitmap:$key",
                type = ResourceType.BITMAP,
                size = size.toLong(),
                timestamp = System.currentTimeMillis()
            )
            
            SecurityLog.d("Bitmap已存储: $key, 大小: ${size} bytes")
            true
        } catch (e: Exception) {
            SecurityLog.e("存储Bitmap失败: $key", e)
            false
        }
    }

    /**
     * 获取Bitmap
     */
    suspend fun getBitmap(key: String): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            bitmapCache.get(key)
        } catch (e: Exception) {
            SecurityLog.e("获取Bitmap失败: $key", e)
            null
        }
    }

    /**
     * 从URI加载和缓存Bitmap
     */
    suspend fun loadBitmapFromUri(
        uri: Uri,
        key: String,
        maxWidth: Int = 1080,
        maxHeight: Int = 1920
    ): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            // 先检查缓存
            getBitmap(key)?.let { cachedBitmap ->
                SecurityLog.d("从缓存获取Bitmap: $key")
                return@withContext cachedBitmap
            }

            val bitmap = decodeSampledBitmapFromUri(uri, maxWidth, maxHeight)
            bitmap?.let { loadedBitmap ->
                storeBitmap(key, loadedBitmap)
                SecurityLog.d("从URI加载Bitmap: $key")
            }
            
            bitmap
        } catch (e: Exception) {
            SecurityLog.e("从URI加载Bitmap失败: $uri", e)
            null
        }
    }

    /**
     * 获取文件描述符
     */
    suspend fun getFileDescriptor(
        uri: Uri,
        mode: String = "r"
    ): FileDescriptor? = withContext(Dispatchers.IO) {
        return@withContext try {
            val contentResolver = context.contentResolver
            val fileDescriptor = resourceLock.withLock {
                fileDescriptorPool.acquire()
            }
            
            try {
                val parcelFileDescriptor = contentResolver.openFileDescriptor(uri, mode)
                if (parcelFileDescriptor != null) {
                    trackResource(
                        key = "fd:${fileDescriptor.hashCode()}",
                        type = ResourceType.FILE_DESCRIPTOR,
                        size = estimateFileSize(contentResolver, uri),
                        timestamp = System.currentTimeMillis()
                    )
                    
                    SecurityLog.d("文件描述符已获取: $uri")
                    return@withContext parcelFileDescriptor.fileDescriptor
                }
            } catch (e: Exception) {
                fileDescriptorPool.release(fileDescriptor)
                SecurityLog.e("获取文件描述符失败: $uri", e)
            }
            
            null
        } catch (e: Exception) {
            SecurityLog.e("获取文件描述符失败: $uri", e)
            null
        }
    }

    /**
     * 释放文件描述符
     */
    suspend fun releaseFileDescriptor(fileDescriptor: FileDescriptor): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            resourceLock.withLock {
                fileDescriptorPool.release(fileDescriptor)
            }
            untrackResource("fd:${fileDescriptor.hashCode()}")
            SecurityLog.d("文件描述符已释放")
            true
        } catch (e: Exception) {
            SecurityLog.e("释放文件描述符失败", e)
            false
        }
    }

    /**
     * 创建安全的InputStream
     */
    suspend fun createSecureInputStream(uri: Uri): InputStream? = withContext(Dispatchers.IO) {
        return@withContext try {
            val fileDescriptor = getFileDescriptor(uri, "r")
            if (fileDescriptor != null) {
                FileInputStream(fileDescriptor).apply {
                    // 设置自动关闭标记
                    addShutdownHook {
                        try {
                            close()
                        } catch (e: Exception) {
                            SecurityLog.w("自动关闭InputStream失败", e)
                        }
                    }
                }
            } else {
                context.contentResolver.openInputStream(uri)
            }
        } catch (e: Exception) {
            SecurityLog.e("创建安全InputStream失败: $uri", e)
            null
        }
    }

    /**
     * 创建安全的OutputStream
     */
    suspend fun createSecureOutputStream(uri: Uri): OutputStream? = withContext(Dispatchers.IO) {
        return@withContext try {
            context.contentResolver.openOutputStream(uri)
        } catch (e: Exception) {
            SecurityLog.e("创建安全OutputStream失败: $uri", e)
            null
        }
    }

    /**
     * 清理特定资源
     */
    suspend fun cleanupResource(key: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            when {
                key.startsWith("cache:") -> {
                    val cacheKey = key.removePrefix("cache:")
                    removeFromCache(cacheKey)
                }
                key.startsWith("bitmap:") -> {
                    val bitmapKey = key.removePrefix("bitmap:")
                    val bitmap = bitmapCache.remove(bitmapKey)
                    bitmap?.recycle()
                    untrackResource(key)
                }
                key.startsWith("fd:") -> {
                    // 文件描述符已在其他地方处理
                    untrackResource(key)
                }
                else -> {
                    SecurityLog.w("未知的资源类型: $key")
                }
            }
            true
        } catch (e: Exception) {
            SecurityLog.e("清理资源失败: $key", e)
            false
        }
    }

    /**
     * 获取资源统计信息
     */
    suspend fun getResourceStatistics(): ResourceStatistics = withContext(Dispatchers.IO) {
        return@withContext try {
            ResourceStatistics(
                memoryCacheSize = memoryCache.size(),
                memoryCacheItemCount = memoryCache.size(),
                bitmapCacheSize = bitmapCache.size(),
                bitmapCacheItemCount = bitmapCache.size(),
                trackedResourceCount = trackedResources.size,
                totalTrackedSize = trackedResources.values.sumOf { it.size },
                fileDescriptorPoolSize = fileDescriptorPool.getPoolSize(),
                activeResources = trackedResources.values.filter { it.isActive }.size,
                cleanupCount = resourceMetrics.cleanupCount.get(),
                memoryFreed = resourceMetrics.memoryFreed.get()
            )
        } catch (e: Exception) {
            SecurityLog.e("获取资源统计信息失败", e)
            ResourceStatistics()
        }
    }

    /**
     * 获取资源事件流
     */
    fun getResourceEventFlow(): Flow<ResourceEvent> = flow {
        resourceEvents.receiveAsFlow().collect { event ->
            emit(event)
        }
    }

    /**
     * 强制垃圾回收
     */
    suspend fun forceGarbageCollection(): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val startMemory = getAvailableMemory()
            System.gc()
            System.runFinalization()
            val endMemory = getAvailableMemory()
            val freedMemory = endMemory - startMemory
            
            resourceMetrics.memoryFreed.addAndGet(freedMemory)
            
            SecurityLog.i("强制垃圾回收完成，释放内存: ${freedMemory} bytes")
            true
        } catch (e: Exception) {
            SecurityLog.e("强制垃圾回收失败", e)
            false
        }
    }

    /**
     * 优化资源
     */
    suspend fun optimizeResources(): OptimizationResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val startTime = System.currentTimeMillis()
            var totalOptimized = 0
            
            // 清理未使用的缓存
            val cacheKeys = memoryCache.keys().toList()
            cacheKeys.forEach { key ->
                val resource = trackedResources["cache:$key"]
                if (resource != null && !resource.isActive) {
                    removeFromCache(key)
                    totalOptimized++
                }
            }
            
            // 清理过期Bitmap
            val bitmapKeys = bitmapCache.keys().toList()
            bitmapKeys.forEach { key ->
                val bitmap = bitmapCache.get(key)
                if (bitmap != null && bitmap.isRecycled) {
                    bitmapCache.remove(key)
                    totalOptimized++
                }
            }
            
            // 强制垃圾回收
            forceGarbageCollection()
            
            val endTime = System.currentTimeMillis()
            val optimizationTime = endTime - startTime
            
            val statistics = getResourceStatistics()
            
            OptimizationResult(
                success = true,
                optimizedItems = totalOptimized,
                optimizationTime = optimizationTime,
                statistics = statistics,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            SecurityLog.e("资源优化失败", e)
            OptimizationResult(
                success = false,
                optimizedItems = 0,
                optimizationTime = 0,
                statistics = ResourceStatistics(),
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * 监控循环
     */
    private suspend fun resourceMonitoringLoop() {
        while (isMonitoring.get()) {
            try {
                val statistics = getResourceStatistics()
                
                // 发送监控事件
                resourceEvents.send(ResourceEvent(
                    type = ResourceEventType.MONITORING,
                    message = "资源监控: ${statistics.trackedResourceCount} 个资源",
                    timestamp = System.currentTimeMillis(),
                    data = statistics
                ))
                
                // 检查资源使用率
                if (statistics.memoryCacheSize > MAX_MEMORY_CACHE_SIZE * 0.9f) {
                    resourceEvents.send(ResourceEvent(
                        type = ResourceEventType.WARNING,
                        message = "内存缓存使用率过高: ${statistics.memoryCacheSize} bytes",
                        timestamp = System.currentTimeMillis(),
                        data = statistics
                    ))
                }
                
                delay(MONITORING_INTERVAL)
            } catch (e: Exception) {
                SecurityLog.e("资源监控循环异常", e)
                delay(MONITORING_INTERVAL)
            }
        }
    }

    /**
     * 清理循环
     */
    private suspend fun resourceCleanupLoop() {
        while (isMonitoring.get()) {
            try {
                val result = optimizeResources()
                if (result.success) {
                    resourceMetrics.cleanupCount.incrementAndGet()
                    SecurityLog.d("资源清理完成: 优化了 ${result.optimizedItems} 项")
                }
                
                delay(RESOURCE_CLEANUP_INTERVAL)
            } catch (e: Exception) {
                SecurityLog.e("资源清理循环异常", e)
                delay(RESOURCE_CLEANUP_INTERVAL)
            }
        }
    }

    /**
     * 清理所有资源
     */
    private suspend fun cleanupAllResources() {
        try {
            memoryCache.evictAll()
            bitmapCache.values.forEach { it.recycle() }
            bitmapCache.evictAll()
            fileDescriptorPool.close()
            trackedResources.clear()
            resourceEvents.close()
            
            SecurityLog.i("所有资源已清理")
        } catch (e: Exception) {
            SecurityLog.e("清理所有资源失败", e)
        }
    }

    /**
     * 跟踪资源
     */
    private fun trackResource(
        key: String,
        type: ResourceType,
        size: Long,
        timestamp: Long
    ) {
        resourceLock.withLock {
            trackedResources[key] = TrackedResource(
                key = key,
                type = type,
                size = size,
                createdAt = timestamp,
                isActive = true
            )
        }
    }

    /**
     * 取消资源跟踪
     */
    private fun untrackResource(key: String) {
        resourceLock.withLock {
            trackedResources.remove(key)
        }
    }

    /**
     * 估算对象大小
     */
    private fun estimateSize(obj: Any): Long {
        return when (obj) {
            is String -> obj.length * 2 // 假设每个字符2字节
            is ByteArray -> obj.size.toLong()
            is IntArray -> obj.size * 4
            is LongArray -> obj.size * 8
            is FloatArray -> obj.size * 4
            is DoubleArray -> obj.size * 8
            is Array<*> -> obj.size * 8 // 估算
            else -> 64L // 默认大小
        }
    }

    /**
     * 估算文件大小
     */
    private fun estimateFileSize(contentResolver: ContentResolver, uri: Uri): Long {
        return try {
            contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        cursor.getLong(sizeIndex)
                    } else {
                        -1L
                    }
                } ?: -1L
        } catch (e: Exception) {
            -1L
        }
    }

    /**
     * 从URI解码采样Bitmap
     */
    private fun decodeSampledBitmapFromUri(
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream, null, options)
            }
            
            options.outWidth.let { outWidth ->
                options.outHeight.let { outHeight ->
                    val inSampleSize = calculateInSampleSize(outWidth, outHeight, reqWidth, reqHeight)
                    
                    val sampleOptions = BitmapFactory.Options().apply {
                        inSampleSize = inSampleSize
                    }
                    
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream, null, sampleOptions)
                    }
                }
            }
        } catch (e: Exception) {
            SecurityLog.e("解码Bitmap失败: $uri", e)
            null
        }
    }

    /**
     * 计算采样大小
     */
    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2
            
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }

    /**
     * 获取可用内存
     */
    private fun getAvailableMemory(): Long {
        return try {
            Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        } catch (e: Exception) {
            0L
        }
    }
}

/**
 * 资源池
 */
class ResourcePool<T : Any>(private val factory: () -> T, private val maxSize: Int = MAX_POOL_SIZE) {
    private val pool = mutableListOf<T>()
    private val poolLock = ReentrantLock()
    
    fun acquire(): T {
        return poolLock.withLock {
            if (pool.isNotEmpty()) {
                pool.removeAt(0)
            } else {
                factory()
            }
        }
    }
    
    fun release(item: T) {
        poolLock.withLock {
            if (pool.size < maxSize) {
                pool.add(item)
            }
        }
    }
    
    fun getPoolSize(): Int = pool.size
    
    fun close() {
        poolLock.withLock {
            pool.clear()
        }
    }
}

/**
 * LRU缓存
 */
class LRUCache<K, V>(private val maxSize: Long) : LinkedHashMap<K, V>(16, 0.75f, true) {
    private var currentSize = 0L
    
    override fun put(key: K?, value: V?): V? {
        if (key == null || value == null) return null
        
        val result = super.put(key, value)
        val size = estimateSize(value)
        currentSize += size
        
        // 如果超过最大大小，移除最老的条目
        while (currentSize > maxSize) {
            val oldest = entries.firstOrNull() ?: break
            currentSize -= estimateSize(oldest.value)
            remove(oldest.key)
        }
        
        return result
    }
    
    override fun remove(key: K?): V? {
        val result = super.remove(key)
        if (result != null) {
            currentSize -= estimateSize(result)
        }
        return result
    }
    
    fun size(): Long = currentSize
    
    private fun estimateSize(value: V): Long {
        return when (value) {
            is String -> value.length * 2L
            is ByteArray -> value.size.toLong()
            is Bitmap -> value.byteCount.toLong()
            else -> 64L
        }
    }
}

// 数据类定义
data class TrackedResource(
    val key: String,
    val type: ResourceType,
    val size: Long,
    val createdAt: Long,
    var isActive: Boolean
)

data class ResourceStatistics(
    val memoryCacheSize: Long = 0,
    val memoryCacheItemCount: Int = 0,
    val bitmapCacheSize: Int = 0,
    val bitmapCacheItemCount: Int = 0,
    val trackedResourceCount: Int = 0,
    val totalTrackedSize: Long = 0,
    val fileDescriptorPoolSize: Int = 0,
    val activeResources: Int = 0,
    val cleanupCount: Int = 0,
    val memoryFreed: Long = 0
)

data class ResourceEvent(
    val type: ResourceEventType,
    val message: String,
    val timestamp: Long,
    val data: Any? = null
)

data class OptimizationResult(
    val success: Boolean,
    val optimizedItems: Int,
    val optimizationTime: Long,
    val statistics: ResourceStatistics,
    val timestamp: Long
)

data class ResourceMetrics(
    val cleanupCount: AtomicLong = AtomicLong(0),
    val memoryFreed: AtomicLong = AtomicLong(0)
)

enum class ResourceType {
    MEMORY_CACHE,
    BITMAP,
    FILE_DESCRIPTOR,
    DATABASE_CONNECTION,
    NETWORK_CONNECTION
}

enum class ResourceEventType {
    MONITORING,
    WARNING,
    ERROR,
    CLEANUP
}

private const val MAX_MEMORY_CACHE_SIZE = 50 * 1024 * 1024L
private const val MAX_FILE_DESCRIPTORS = 100
private const val MAX_BITMAP_CACHE_SIZE = 20
private const val RESOURCE_CLEANUP_INTERVAL = 60000L
private const val MAX_POOL_SIZE = 10
private const val MONITORING_INTERVAL = 30000L