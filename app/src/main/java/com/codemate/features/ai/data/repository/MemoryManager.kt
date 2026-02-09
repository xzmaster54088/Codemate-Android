package com.codemate.features.ai.data.repository

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.lang.Runtime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 内存管理器
 * 负责监控和管理应用程序内存使用
 */
@Singleton
class MemoryManager @Inject constructor(
    private val context: Context
) {
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val runtime = Runtime.getRuntime()
    
    /**
     * 获取当前内存使用情况
     */
    fun getMemoryInfo(): MemoryInfo {
        val runtimeInfo = runtime.info
        val debugInfo = debugInfo
        val memInfo = activityManager.memoryInfo
        
        return MemoryInfo(
            totalMemory = runtimeInfo.totalMemory,
            freeMemory = runtimeInfo.freeMemory,
            usedMemory = runtimeInfo.totalMemory - runtimeInfo.freeMemory,
            maxMemory = runtimeInfo.maxMemory,
            availableProcessors = runtimeInfo.availableProcessors,
            nativeHeapSize = debugInfo.nativeHeapSize,
            nativeHeapAllocatedSize = debugInfo.nativeHeapAllocatedSize,
            nativeHeapFreeSize = debugInfo.nativeHeapFreeSize,
            memoryClass = memInfo.totalMem,
            availableMemory = memInfo.availMem,
            isLowMemory = memInfo.lowMemory,
            memoryLimit = memInfo.totalMem
        )
    }
    
    /**
     * 检查是否有足够的内存
     */
    suspend fun checkMemoryAvailability(requiredMemory: Long): Boolean = withContext(Dispatchers.IO) {
        val memoryInfo = getMemoryInfo()
        
        // 计算可用内存（总内存减去一些保留内存）
        val reservedMemory = (memoryInfo.totalMemory * 0.1).toLong() // 保留10%
        val availableMemory = memoryInfo.totalMemory - reservedMemory
        
        availableMemory >= requiredMemory
    }
    
    /**
     * 获取内存使用率
     */
    suspend fun getMemoryUsageRatio(): Float = withContext(Dispatchers.IO) {
        val memoryInfo = getMemoryInfo()
        (memoryInfo.totalMemory - memoryInfo.freeMemory).toFloat() / memoryInfo.totalMemory
    }
    
    /**
     * 检查是否处于低内存状态
     */
    fun isLowMemory(): Boolean {
        val memInfo = activityManager.memoryInfo
        return memInfo.lowMemory
    }
    
    /**
     * 触发垃圾回收
     */
    suspend fun triggerGC(): Long = withContext(Dispatchers.IO) {
        val beforeMemory = getMemoryInfo().usedMemory
        val startTime = System.currentTimeMillis()
        
        System.gc()
        
        // 等待一小段时间让GC完成
        kotlinx.coroutines.delay(100)
        
        val afterMemory = getMemoryInfo().usedMemory
        val endTime = System.currentTimeMillis()
        
        beforeMemory - afterMemory // 返回释放的内存
    }
    
    /**
     * 获取内存优化建议
     */
    suspend fun getOptimizationRecommendations(): List<MemoryOptimizationRecommendation> = withContext(Dispatchers.IO) {
        val memoryInfo = getMemoryInfo()
        val usageRatio = getMemoryUsageRatio()
        
        val recommendations = mutableListOf<MemoryOptimizationRecommendation>()
        
        when {
            usageRatio > 0.9 -> {
                recommendations.add(
                    MemoryOptimizationRecommendation(
                        type = MemoryOptimizationType.URGENT_CLEANUP,
                        description = "内存使用率超过90%，建议立即清理内存",
                        priority = 1,
                        estimatedGain = "50-200MB"
                    )
                )
            }
            usageRatio > 0.7 -> {
                recommendations.add(
                    MemoryOptimizationRecommendation(
                        type = MemoryOptimizationType.MODERATE_CLEANUP,
                        description = "内存使用率超过70%，建议进行内存清理",
                        priority = 2,
                        estimatedGain = "20-50MB"
                    )
                )
            }
            usageRatio > 0.5 -> {
                recommendations.add(
                    MemoryOptimizationRecommendation(
                        type = MemoryOptimizationType.PREVENTIVE_CLEANUP,
                        description = "内存使用率较高，建议预防性清理",
                        priority = 3,
                        estimatedGain = "10-20MB"
                    )
                )
            }
        }
        
        // 检查大型对象
        if (memoryInfo.nativeHeapAllocatedSize > 100 * 1024 * 1024) { // 100MB
            recommendations.add(
                MemoryOptimizationRecommendation(
                    type = MemoryOptimizationType.LARGE_OBJECT_CLEANUP,
                    description = "检测到大型内存分配，建议清理不必要的大型对象",
                    priority = 2,
                    estimatedGain = "100-500MB"
                )
            )
        }
        
        // 检查模型内存使用
        recommendations.addAll(checkModelMemoryUsage())
        
        recommendations.sortedBy { it.priority }
    }
    
    /**
     * 清理内存
     */
    suspend fun optimizeMemory(): MemoryOptimizationResult = withContext(Dispatchers.IO) {
        val beforeMemory = getMemoryInfo()
        val startTime = System.currentTimeMillis()
        
        // 执行内存优化操作
        val freedMemory = triggerGC()
        
        // 清理缓存
        val cacheFreed = cleanupCaches()
        
        // 清理未使用的模型
        val modelFreed = cleanupUnusedModels()
        
        val afterMemory = getMemoryInfo()
        val endTime = System.currentTimeMillis()
        
        MemoryOptimizationResult(
            freedMemory = freedMemory + cacheFreed + modelFreed,
            totalFreed = afterMemory.freeMemory - beforeMemory.freeMemory,
            timeTaken = endTime - startTime,
            memoryBefore = beforeMemory,
            memoryAfter = afterMemory
        )
    }
    
    /**
     * 监控内存使用并生成报告
     */
    suspend fun generateMemoryReport(): MemoryReport = withContext(Dispatchers.IO) {
        val currentTime = System.currentTimeMillis()
        val memoryInfo = getMemoryInfo()
        val recommendations = getOptimizationRecommendations()
        
        MemoryReport(
            generatedAt = currentTime,
            memoryInfo = memoryInfo,
            usageRatio = getMemoryUsageRatio(),
            isLowMemory = isLowMemory(),
            recommendations = recommendations,
            status = when {
                isLowMemory() -> MemoryStatus.CRITICAL
                getMemoryUsageRatio() > 0.8 -> MemoryStatus.WARNING
                getMemoryUsageRatio() > 0.6 -> MemoryStatus.CAUTION
                else -> MemoryStatus.NORMAL
            }
        )
    }
    
    /**
     * 检查模型内存使用
     */
    private suspend fun checkModelMemoryUsage(): List<MemoryOptimizationRecommendation> {
        val recommendations = mutableListOf<MemoryOptimizationRecommendation>()
        
        // 这里应该检查当前加载的模型数量和内存使用
        // 为了演示，返回模拟数据
        
        return recommendations
    }
    
    /**
     * 清理缓存
     */
    private suspend fun cleanupCaches(): Long {
        // 这里应该调用缓存管理器的清理方法
        return 0L
    }
    
    /**
     * 清理未使用的模型
     */
    private suspend fun cleanupUnusedModels(): Long {
        // 这里应该卸载不活跃的模型
        return 0L
    }
    
    /**
     * 获取运行时信息
     */
    private val runtimeInfo: RuntimeInfo
        get() = RuntimeInfo(
            totalMemory = runtime.totalMemory(),
            freeMemory = runtime.freeMemory(),
            maxMemory = runtime.maxMemory(),
            availableProcessors = runtime.availableProcessors()
        )
    
    /**
     * 获取调试信息
     */
    private val debugInfo: DebugInfo
        get() = DebugInfo(
            nativeHeapSize = Debug.getNativeHeapSize(),
            nativeHeapAllocatedSize = Debug.getNativeHeapAllocatedSize(),
            nativeHeapFreeSize = Debug.getNativeHeapFreeSize()
        )
}

/**
 * 内存信息
 */
data class MemoryInfo(
    val totalMemory: Long,
    val freeMemory: Long,
    val usedMemory: Long,
    val maxMemory: Long,
    val availableProcessors: Int,
    val nativeHeapSize: Long,
    val nativeHeapAllocatedSize: Long,
    val nativeHeapFreeSize: Long,
    val memoryClass: Long,
    val availableMemory: Long,
    val isLowMemory: Boolean,
    val memoryLimit: Long
)

/**
 * 运行时信息
 */
data class RuntimeInfo(
    val totalMemory: Long,
    val freeMemory: Long,
    val maxMemory: Long,
    val availableProcessors: Int
)

/**
 * 调试信息
 */
data class DebugInfo(
    val nativeHeapSize: Long,
    val nativeHeapAllocatedSize: Long,
    val nativeHeapFreeSize: Long
)

/**
 * 内存优化建议
 */
data class MemoryOptimizationRecommendation(
    val type: MemoryOptimizationType,
    val description: String,
    val priority: Int,
    val estimatedGain: String
)

/**
 * 内存优化类型
 */
enum class MemoryOptimizationType {
    URGENT_CLEANUP,
    MODERATE_CLEANUP,
    PREVENTIVE_CLEANUP,
    LARGE_OBJECT_CLEANUP,
    MODEL_CLEANUP
}

/**
 * 内存优化结果
 */
data class MemoryOptimizationResult(
    val freedMemory: Long,
    val totalFreed: Long,
    val timeTaken: Long,
    val memoryBefore: MemoryInfo,
    val memoryAfter: MemoryInfo
)

/**
 * 内存报告
 */
data class MemoryReport(
    val generatedAt: Long,
    val memoryInfo: MemoryInfo,
    val usageRatio: Float,
    val isLowMemory: Boolean,
    val recommendations: List<MemoryOptimizationRecommendation>,
    val status: MemoryStatus
)

/**
 * 内存状态
 */
enum class MemoryStatus {
    NORMAL,
    CAUTION,
    WARNING,
    CRITICAL
}