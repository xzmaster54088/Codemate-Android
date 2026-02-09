package com.codemate.features.editor.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.system.measureTimeMillis

/**
 * 编辑器性能优化管理器
 * 负责管理语法高亮、代码补全等功能的性能优化
 */
class EditorPerformanceManager {
    
    // 缓存相关
    private val syntaxCache = ConcurrentHashMap<String, CachedSyntaxHighlight>()
    private val completionCache = ConcurrentHashMap<String, List<CompletionItem>>()
    private val analysisCache = ConcurrentHashMap<String, CodeAnalysis>()
    
    // 线程池
    private val highPriorityExecutor = Dispatchers.Default.limitedParallelism(2)
    private val lowPriorityExecutor = Dispatchers.IO.limitedParallelism(4)
    
    // 虚拟滚动相关
    private var visibleLineStart = 0
    private var visibleLineEnd = 0
    private var lineHeight = 20f
    private var scrollPosition = 0f
    
    // 缓存过期时间（毫秒）
    private val cacheExpiryTime = 30_000L // 30秒
    
    // 锁机制保证线程安全
    private val cacheLock = ReentrantReadWriteLock()
    
    /**
     * 异步语法高亮（带缓存）
     */
    suspend fun highlightSyntaxAsync(
        code: String,
        language: EditorLanguage,
        theme: EditorTheme,
        visibleRange: IntRange? = null
    ): List<SyntaxHighlight> = withContext(highPriorityExecutor) {
        
        val cacheKey = "${language.name}_${theme.name}_${visibleRange?.let { "${it.first}_${it.second}" } ?: "full"}_${code.hashCode()}"
        
        // 先检查缓存
        cacheLock.read {
            val cached = syntaxCache[cacheKey]
            if (cached != null && !cached.isExpired()) {
                return@read cached.highlights
            }
        }
        
        // 如果指定了可见范围，只高亮可见部分
        val codeToHighlight = if (visibleRange != null) {
            getVisibleCode(code, visibleRange)
        } else {
            code
        }
        
        val highlights = measureTimeMillis("syntax_highlight") {
            SyntaxHighlighterEngine().highlightSyntax(codeToHighlight, language, theme)
        }
        
        // 调整高亮位置（如果只高亮了部分代码）
        val adjustedHighlights = if (visibleRange != null) {
            val offset = visibleRange.first
            highlights.map { it.copy(start = it.start + offset, end = it.end + offset) }
        } else {
            highlights
        }
        
        // 缓存结果
        cacheLock.write {
            syntaxCache[cacheKey] = CachedSyntaxHighlight(
                highlights = adjustedHighlights,
                timestamp = System.currentTimeMillis()
            )
            
            // 清理过期缓存
            cleanExpiredCache()
        }
        
        adjustedHighlights
    }
    
    /**
     * 异步代码补全（带缓存）
     */
    suspend fun getCompletionsAsync(
        code: String,
        position: Int,
        language: EditorLanguage,
        maxResults: Int = 20
    ): List<CompletionItem> = withContext(highPriorityExecutor) {
        
        val cacheKey = "${language.name}_${position}_${getCurrentWord(code, position)}_${maxResults}"
        
        // 检查缓存
        cacheLock.read {
            val cached = completionCache[cacheKey]
            if (cached != null) {
                return@read cached
            }
        }
        
        val completions = measureTimeMillis("code_completion") {
            CodeCompletionEngine().getCompletions(code, position, language, maxResults)
        }
        
        // 缓存结果
        cacheLock.write {
            completionCache[cacheKey] = completions
            cleanExpiredCache()
        }
        
        completions
    }
    
    /**
     * 异步代码分析（带缓存）
     */
    suspend fun analyzeCodeAsync(
        code: String,
        language: EditorLanguage
    ): CodeAnalysis = withContext(lowPriorityExecutor) {
        
        val cacheKey = "${language.name}_${code.hashCode()}"
        
        // 检查缓存
        cacheLock.read {
            val cached = analysisCache[cacheKey]
            if (cached != null) {
                return@read cached
            }
        }
        
        val analysis = measureTimeMillis("code_analysis") {
            performCodeAnalysis(code, language)
        }
        
        // 缓存结果
        cacheLock.write {
            analysisCache[cacheKey] = analysis
            cleanExpiredCache()
        }
        
        analysis
    }
    
    /**
     * 虚拟滚动优化
     */
    fun updateVisibleRange(
        code: String,
        scrollY: Float,
        viewportHeight: Float,
        lineHeight: Float
    ) {
        this.scrollPosition = scrollY
        this.lineHeight = lineHeight
        
        val totalLines = code.count { it == '\n' } + 1
        val visibleLines = (viewportHeight / lineHeight).toInt()
        val buffer = 5 // 缓冲区行数
        
        visibleLineStart = max(0, ((scrollY / lineHeight) - buffer).toInt())
        visibleLineEnd = min(totalLines, visibleLineStart + visibleLines + buffer * 2)
        
        // 触发可见范围的语法高亮更新
        updateVisibleSyntax()
    }
    
    /**
     * 获取可见范围的代码
     */
    private fun getVisibleCode(code: String, visibleRange: IntRange): String {
        val lines = code.split('\n')
        val startLine = max(0, visibleRange.first)
        val endLine = min(lines.size, visibleRange.second + 1)
        
        return lines.subList(startLine, endLine).joinToString("\n")
    }
    
    /**
     * 更新可见范围的语法高亮
     */
    private fun updateVisibleSyntax() {
        // 这个方法会在后台异步更新可见范围的语法高亮
        // 实现可以触发重绘或预加载相邻区域的高亮
    }
    
    /**
     * 预加载相邻区域
     */
    suspend fun preloadAdjacentAreas(
        code: String,
        currentVisibleRange: IntRange,
        language: EditorLanguage,
        theme: EditorTheme
    ) {
        // 预加载上方区域
        launch(highPriorityExecutor) {
            if (currentVisibleRange.first > 0) {
                val preloadRange = IntRange(
                    max(0, currentVisibleRange.first - 50),
                    currentVisibleRange.first - 1
                )
                highlightSyntaxAsync(code, language, theme, preloadRange)
            }
        }
        
        // 预加载下方区域
        launch(highPriorityExecutor) {
            val totalLines = code.count { it == '\n' } + 1
            if (currentVisibleRange.second < totalLines - 1) {
                val preloadRange = IntRange(
                    currentVisibleRange.second + 1,
                    min(totalLines - 1, currentVisibleRange.second + 50)
                )
                highlightSyntaxAsync(code, language, theme, preloadRange)
            }
        }
    }
    
    /**
     * 清理过期缓存
     */
    private fun cleanExpiredCache() {
        val currentTime = System.currentTimeMillis()
        val expiredKeys = mutableListOf<String>()
        
        // 清理过期的语法高亮缓存
        cacheLock.write {
            syntaxCache.entries.removeAll { entry ->
                val isExpired = currentTime - entry.value.timestamp > cacheExpiryTime
                if (isExpired) expiredKeys.add(entry.key)
                isExpired
            }
        }
        
        // 限制缓存大小
        limitCacheSize(syntaxCache, 100)
        limitCacheSize(completionCache, 50)
        limitCacheSize(analysisCache, 20)
    }
    
    /**
     * 限制缓存大小
     */
    private fun <K, V> limitCacheSize(cache: ConcurrentHashMap<K, V>, maxSize: Int) {
        if (cache.size > maxSize) {
            val keysToRemove = cache.keys.drop(cache.size - maxSize)
            cache.keys.removeAll(keysToRemove)
        }
    }
    
    /**
     * 获取性能统计
     */
    fun getPerformanceStats(): PerformanceStats {
        return PerformanceStats(
            syntaxCacheSize = syntaxCache.size,
            completionCacheSize = completionCache.size,
            analysisCacheSize = analysisCache.size,
            visibleLineRange = IntRange(visibleLineStart, visibleLineEnd),
            totalOperations = operationCount.get(),
            averageOperationTime = averageOperationTime
        )
    }
    
    /**
     * 重置统计信息
     */
    fun resetStats() {
        operationCount.set(0)
        averageOperationTime = 0.0
    }
    
    /**
     * 清理所有缓存
     */
    fun clearCache() {
        cacheLock.write {
            syntaxCache.clear()
            completionCache.clear()
            analysisCache.clear()
        }
    }
    
    companion object {
        private val operationCount = java.util.concurrent.atomic.AtomicInteger(0)
        private var averageOperationTime = 0.0
    }
}

/**
 * 测量执行时间
 */
private suspend inline fun <T> measureTimeMillis(
    operationName: String,
    crossinline block: suspend () -> T
): T {
    val startTime = System.currentTimeMillis()
    val result = block()
    val endTime = System.currentTimeMillis()
    val duration = endTime - startTime
    
    // 更新统计信息
    val currentCount = EditorPerformanceManager.operationCount.incrementAndGet()
    val currentAverage = EditorPerformanceManager.averageOperationTime
    EditorPerformanceManager.averageOperationTime = 
        (currentAverage * (currentCount - 1) + duration) / currentCount
    
    // 在开发模式下打印性能信息
    if (BuildConfig.DEBUG) {
        println("Operation '$operationName' took ${duration}ms")
    }
    
    return result
}

/**
 * 缓存的语法高亮数据
 */
private data class CachedSyntaxHighlight(
    val highlights: List<SyntaxHighlight>,
    val timestamp: Long
) {
    fun isExpired(): Boolean {
        return System.currentTimeMillis() - timestamp > 30_000L // 30秒过期
    }
}

/**
 * 性能统计信息
 */
data class PerformanceStats(
    val syntaxCacheSize: Int,
    val completionCacheSize: Int,
    val analysisCacheSize: Int,
    val visibleLineRange: IntRange,
    val totalOperations: Int,
    val averageOperationTime: Double
)

/**
 * 内存使用监控器
 */
class MemoryMonitor {
    private var memoryUsage = MutableStateFlow(0L)
    val memoryUsageFlow = memoryUsage.asStateFlow()
    
    private var peakMemoryUsage = 0L
    private val memoryThreshold = 50 * 1024 * 1024 // 50MB
    
    /**
     * 更新内存使用情况
     */
    fun updateMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        
        memoryUsage.value = usedMemory
        peakMemoryUsage = maxOf(peakMemoryUsage, usedMemory)
        
        // 如果内存使用超过阈值，触发清理
        if (usedMemory > memoryThreshold) {
            triggerMemoryCleanup()
        }
    }
    
    /**
     * 触发内存清理
     */
    private fun triggerMemoryCleanup() {
        System.gc() // 建议垃圾回收
        
        // 触发外部清理回调
        onMemoryCleanup?.invoke()
    }
    
    /**
     * 内存清理回调
     */
    var onMemoryCleanup: (() -> Unit)? = null
    
    /**
     * 获取内存使用报告
     */
    fun getMemoryReport(): MemoryReport {
        val runtime = Runtime.getRuntime()
        return MemoryReport(
            usedMemory = memoryUsage.value,
            totalMemory = runtime.totalMemory(),
            maxMemory = runtime.maxMemory(),
            freeMemory = runtime.freeMemory(),
            peakMemoryUsage = peakMemoryUsage,
            memoryPressure = memoryUsage.value > memoryThreshold
        )
    }
}

/**
 * 内存报告
 */
data class MemoryReport(
    val usedMemory: Long,
    val totalMemory: Long,
    val maxMemory: Long,
    val freeMemory: Long,
    val peakMemoryUsage: Long,
    val memoryPressure: Boolean
)

/**
 * 虚拟滚动优化器
 */
class VirtualScrollOptimizer {
    
    private var viewportHeight = 0f
    private var lineHeight = 20f
    private var totalLines = 0
    private var cacheBuffer = 10 // 缓冲区行数
    
    /**
     * 计算可见行范围
     */
    fun calculateVisibleRange(
        scrollY: Float,
        viewportHeight: Float,
        lineHeight: Float,
        totalLines: Int
    ): IntRange {
        this.viewportHeight = viewportHeight
        this.lineHeight = lineHeight
        this.totalLines = totalLines
        
        val firstVisibleLine = (scrollY / lineHeight).toInt()
        val lastVisibleLine = ((scrollY + viewportHeight) / lineHeight).toInt()
        
        return IntRange(
            max(0, firstVisibleLine - cacheBuffer),
            min(totalLines - 1, lastVisibleLine + cacheBuffer)
        )
    }
    
    /**
     * 计算需要渲染的行
     */
    fun getRenderLines(
        scrollY: Float,
        viewportHeight: Float,
        lineHeight: Float,
        totalLines: Int,
        visibleRange: IntRange
    ): List<Int> {
        val range = calculateVisibleRange(scrollY, viewportHeight, lineHeight, totalLines)
        return (range.first..range.second).toList()
    }
    
    /**
     * 预计算行高度
     */
    fun precalculateLineHeights(lines: List<String>): Map<Int, Float> {
        return lines.withIndex().associate { (index, line) ->
            index to calculateLineHeight(line)
        }
    }
    
    /**
     * 计算单行高度
     */
    private fun calculateLineHeight(line: String): Float {
        // 简单的行高计算：基础高度 + 换行符数量 * 行间距
        val baseHeight = lineHeight
        val lineBreaks = line.count { it == '\n' }
        return baseHeight + lineBreaks * lineHeight * 0.5f
    }
}

/**
 * 电池优化建议
 */
object BatteryOptimizer {
    
    /**
     * 根据设备状态调整性能设置
     */
    fun getOptimizedSettings(deviceState: DeviceState): OptimizedSettings {
        return when (deviceState) {
            DeviceState.HIGH_PERFORMANCE -> OptimizedSettings(
                enableSyntaxHighlight = true,
                enableCodeCompletion = true,
                enableAutoAnalysis = true,
                maxCacheSize = 200,
                animationQuality = AnimationQuality.HIGH,
                enablePreloading = true
            )
            DeviceState.BALANCED -> OptimizedSettings(
                enableSyntaxHighlight = true,
                enableCodeCompletion = true,
                enableAutoAnalysis = false,
                maxCacheSize = 100,
                animationQuality = AnimationQuality.MEDIUM,
                enablePreloading = false
            )
            DeviceState.BATTERY_SAVER -> OptimizedSettings(
                enableSyntaxHighlight = false,
                enableCodeCompletion = false,
                enableAutoAnalysis = false,
                maxCacheSize = 20,
                animationQuality = AnimationQuality.LOW,
                enablePreloading = false
            )
        }
    }
}

/**
 * 设备状态
 */
enum class DeviceState {
    HIGH_PERFORMANCE,
    BALANCED,
    BATTERY_SAVER
}

/**
 * 优化设置
 */
data class OptimizedSettings(
    val enableSyntaxHighlight: Boolean,
    val enableCodeCompletion: Boolean,
    val enableAutoAnalysis: Boolean,
    val maxCacheSize: Int,
    val animationQuality: AnimationQuality,
    val enablePreloading: Boolean
)

/**
 * 动画质量
 */
enum class AnimationQuality {
    HIGH, MEDIUM, LOW, DISABLED
}

/**
 * 辅助方法
 */
private fun getCurrentWord(code: String, position: Int): String {
    if (code.isEmpty() || position <= 0) return ""
    
    val start = max(0, findWordStart(code, position))
    val end = min(code.length, findWordEnd(code, position))
    
    return code.substring(start, end)
}

private fun findWordStart(code: String, position: Int): Int {
    var start = position - 1
    while (start >= 0 && (code[start].isLetterOrDigit() || code[start] == '_')) {
        start--
    }
    return start + 1
}

private fun findWordEnd(code: String, position: Int): Int {
    var end = position
    while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) {
        end++
    }
    return end
}