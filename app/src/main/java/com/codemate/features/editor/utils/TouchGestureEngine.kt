package com.codemate.features.editor.utils

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 触摸手势处理引擎
 * 负责处理移动端的各种触摸手势
 */
class TouchGestureEngine {
    
    private var lastTapTime = 0L
    private var lastTapPosition = Offset.Zero
    private var tapCount = 0
    
    private var longPressDetector: LongPressDetector? = null
    private var doubleTapDetector: DoubleTapDetector? = null
    private var dragDetector: DragDetector? = null
    private var pinchDetector: PinchDetector? = null
    
    /**
     * 处理触摸事件
     */
    suspend fun handleTouchEvent(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit,
        textPositionProvider: (Offset) -> Int,
        lineBoundsProvider: (IntRange) -> List<Offset>
    ) {
        when (event.type) {
            GestureType.TAP -> handleTap(event, onGestureDetected, textPositionProvider)
            GestureType.DOUBLE_TAP -> handleDoubleTap(event, onGestureDetected, textPositionProvider, lineBoundsProvider)
            GestureType.TRIPLE_TAP -> handleTripleTap(event, onGestureDetected, textPositionProvider, lineBoundsProvider)
            GestureType.LONG_PRESS -> handleLongPress(event, onGestureDetected, textPositionProvider)
            GestureType.DRAG_START -> handleDragStart(event, onGestureDetected, textPositionProvider)
            GestureType.DRAG_MOVE -> handleDragMove(event, onGestureDetected, textPositionProvider)
            GestureType.DRAG_END -> handleDragEnd(event, onGestureDetected)
            GestureType.PINCH_START -> handlePinchStart(event, onGestureDetected)
            GestureType.PINCH_MOVE -> handlePinchMove(event, onGestureDetected)
            GestureType.PINCH_END -> handlePinchEnd(event, onGestureDetected)
        }
    }
    
    /**
     * 处理单击
     */
    private suspend fun handleTap(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit,
        textPositionProvider: (Offset) -> Int
    ) {
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastTapTime
        val positionDiff = event.position.distanceTo(lastTapPosition)
        
        // 检查是否为双击或三击
        when {
            timeDiff < DOUBLE_TAP_TIME_LIMIT && positionDiff < DOUBLE_TAP_DISTANCE_LIMIT -> {
                tapCount++
                if (tapCount == 2) {
                    onGestureDetected(GestureType.DOUBLE_TAP, event.position)
                    tapCount = 0
                    lastTapTime = 0
                    lastTapPosition = Offset.Zero
                }
            }
            timeDiff >= DOUBLE_TAP_TIME_LIMIT || positionDiff >= DOUBLE_TAP_DISTANCE_LIMIT -> {
                tapCount = 1
                lastTapTime = currentTime
                lastTapPosition = event.position
            }
        }
        
        // 检查三击
        if (tapCount == 3) {
            onGestureDetected(GestureType.TRIPLE_TAP, event.position)
            tapCount = 0
            lastTapTime = 0
            lastTapPosition = Offset.Zero
        }
    }
    
    /**
     * 处理双击
     */
    private fun handleDoubleTap(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit,
        textPositionProvider: (Offset) -> Int,
        lineBoundsProvider: (IntRange) -> List<Offset>
    ) {
        // 选中当前单词
        val position = textPositionProvider(event.position)
        val wordRange = findWordRange(position)
        
        if (wordRange != null) {
            onGestureDetected(GestureType.DOUBLE_TAP, event.position)
        }
    }
    
    /**
     * 处理三击
     */
    private fun handleTripleTap(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit,
        textPositionProvider: (Offset) -> Int,
        lineBoundsProvider: (IntRange) -> List<Offset>
    ) {
        // 选中当前行
        val position = textPositionProvider(event.position)
        val lineRange = findLineRange(position)
        
        if (lineRange != null) {
            onGestureDetected(GestureType.TRIPLE_TAP, event.position)
        }
    }
    
    /**
     * 处理长按
     */
    private fun handleLongPress(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit,
        textPositionProvider: (Offset) -> Int
    ) {
        // 启动长按检测器
        longPressDetector = LongPressDetector { detected ->
            if (detected) {
                onGestureDetected(GestureType.LONG_PRESS, event.position)
            }
        }
        longPressDetector?.start(event.position)
    }
    
    /**
     * 处理拖拽开始
     */
    private fun handleDragStart(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit,
        textPositionProvider: (Offset) -> Int
    ) {
        dragDetector = DragDetector { start, end ->
            if (start != end) {
                val startPos = textPositionProvider(start)
                val endPos = textPositionProvider(end)
                // 选中文本范围
                onGestureDetected(GestureType.DRAG_START, start)
            }
        }
        dragDetector?.onStart(event.position)
    }
    
    /**
     * 处理拖拽移动
     */
    private fun handleDragMove(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit,
        textPositionProvider: (Offset) -> Int
    ) {
        dragDetector?.onMove(event.position)
    }
    
    /**
     * 处理拖拽结束
     */
    private fun handleDragEnd(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit
    ) {
        dragDetector?.onEnd()
        if (dragDetector?.hasSelection() == true) {
            onGestureDetected(GestureType.DRAG_END, event.position)
        }
    }
    
    /**
     * 处理捏合开始
     */
    private fun handlePinchStart(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit
    ) {
        if (event.pointers.size >= 2) {
            pinchDetector = PinchDetector { scale, center ->
                // 处理缩放开始
                onGestureDetected(GestureType.PINCH_START, center)
            }
            pinchDetector?.onStart(event.pointers)
        }
    }
    
    /**
     * 处理捏合移动
     */
    private fun handlePinchMove(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit
    ) {
        pinchDetector?.onMove(event.pointers)
    }
    
    /**
     * 处理捏合结束
     */
    private fun handlePinchEnd(
        event: TouchEvent,
        onGestureDetected: (GestureType, Offset) -> Unit
    ) {
        pinchDetector?.onEnd()
    }
    
    /**
     * 取消所有活动的手势
     */
    fun cancelAllGestures() {
        longPressDetector?.cancel()
        doubleTapDetector?.cancel()
        dragDetector?.cancel()
        pinchDetector?.cancel()
    }
    
    companion object {
        private const val DOUBLE_TAP_TIME_LIMIT = 300L // 毫秒
        private const val DOUBLE_TAP_DISTANCE_LIMIT = 50f // 像素
        private const val LONG_PRESS_TIME_LIMIT = 500L // 毫秒
        private const val LONG_PRESS_MOVEMENT_LIMIT = 10f // 像素
    }
}

/**
 * 长按检测器
 */
private class LongPressDetector(
    private val onLongPress: (Boolean) -> Unit
) {
    private var isActive = false
    private var startPosition = Offset.Zero
    private var startTime = 0L
    private var hasMoved = false
    
    fun start(position: Offset) {
        isActive = true
        startPosition = position
        startTime = System.currentTimeMillis()
        hasMoved = false
        
        // 在后台启动长按检测
        CoroutineScope(Dispatchers.Main).launch {
            while (isActive && !hasMoved) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= TouchGestureEngine.LONG_PRESS_TIME_LIMIT) {
                    onLongPress(true)
                    isActive = false
                    break
                }
                delay(50) // 每50ms检查一次
            }
        }
    }
    
    fun onMove(position: Offset) {
        if (isActive && position.distanceTo(startPosition) > TouchGestureEngine.LONG_PRESS_MOVEMENT_LIMIT) {
            hasMoved = true
            isActive = false
        }
    }
    
    fun cancel() {
        isActive = false
        hasMoved = true
    }
}

/**
 * 拖拽检测器
 */
private class DragDetector(
    private val onDrag: (start: Offset, end: Offset) -> Unit
) {
    private var startPosition = Offset.Zero
    private var currentPosition = Offset.Zero
    private var isDragging = false
    private var selectionRange: IntRange? = null
    
    fun onStart(position: Offset) {
        startPosition = position
        currentPosition = position
        isDragging = true
    }
    
    fun onMove(position: Offset) {
        currentPosition = position
        if (isDragging) {
            onDrag(startPosition, currentPosition)
        }
    }
    
    fun onEnd() {
        isDragging = false
    }
    
    fun hasSelection(): Boolean {
        return selectionRange != null
    }
    
    fun cancel() {
        isDragging = false
    }
}

/**
 * 捏合检测器
 */
private class PinchDetector(
    private val onPinch: (scale: Float, center: Offset) -> Unit
) {
    private var lastDistance = 0f
    private var lastCenter = Offset.Zero
    
    fun onStart(pointers: List<Pointer>) {
        if (pointers.size >= 2) {
            val distance = calculateDistance(pointers[0].position, pointers[1].position)
            lastDistance = distance
            lastCenter = calculateCenter(pointers[0].position, pointers[1].position)
        }
    }
    
    fun onMove(pointers: List<Pointer>) {
        if (pointers.size >= 2) {
            val currentDistance = calculateDistance(pointers[0].position, pointers[1].position)
            val currentCenter = calculateCenter(pointers[0].position, pointers[1].position)
            
            val scale = if (lastDistance > 0) currentDistance / lastDistance else 1f
            onPinch(scale, currentCenter)
            
            lastDistance = currentDistance
            lastCenter = currentCenter
        }
    }
    
    fun onEnd() {
        // 清理状态
    }
    
    private fun calculateDistance(point1: Offset, point2: Offset): Float {
        return point1.distanceTo(point2)
    }
    
    private fun calculateCenter(point1: Offset, point2: Offset): Offset {
        return Offset(
            (point1.x + point2.x) / 2f,
            (point1.y + point2.y) / 2f
        )
    }
}

/**
 * 查找单词范围
 */
private fun findWordRange(position: Int, text: String): IntRange? {
    if (text.isEmpty() || position < 0 || position >= text.length) return null
    
    val wordCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_"
    
    // 找到单词的开始
    var start = position
    while (start > 0 && wordCharacters.contains(text[start - 1])) {
        start--
    }
    
    // 找到单词的结束
    var end = position
    while (end < text.length - 1 && wordCharacters.contains(text[end + 1])) {
        end++
    }
    
    return if (start <= end) IntRange(start, end + 1) else null
}

/**
 * 查找行范围
 */
private fun findLineRange(position: Int, text: String): IntRange? {
    if (text.isEmpty() || position < 0 || position >= text.length) return null
    
    // 找到行的开始
    var start = position
    while (start > 0 && text[start - 1] != '\n') {
        start--
    }
    
    // 找到行的结束
    var end = position
    while (end < text.length - 1 && text[end] != '\n') {
        end++
    }
    
    return if (start <= end) IntRange(start, end + 1) else null
}

/**
 * 查找段落范围
 */
private fun findParagraphRange(position: Int, text: String): IntRange? {
    if (text.isEmpty() || position < 0 || position >= text.length) return null
    
    // 找到段落的开始（空行或文本开始）
    var start = position
    while (start > 0 && !(text[start - 1] == '\n' && (start == 1 || text[start - 2] == '\n'))) {
        start--
    }
    
    // 找到段落的结束（空行或文本结束）
    var end = position
    while (end < text.length - 1 && !(text[end] == '\n' && (end == text.length - 2 || text[end + 1] == '\n'))) {
        end++
    }
    
    return if (start <= end) IntRange(start, end + 1) else null
}

/**
 * 文本选择助手
 */
class TextSelectionHelper(private val text: String) {
    
    /**
     * 扩展选择到单词
     */
    fun expandToWord(position: Int): IntRange {
        return findWordRange(position, text) ?: IntRange(position, position)
    }
    
    /**
     * 扩展选择到行
     */
    fun expandToLine(position: Int): IntRange {
        return findLineRange(position, text) ?: IntRange(position, position)
    }
    
    /**
     * 扩展选择到段落
     */
    fun expandToParagraph(position: Int): IntRange {
        return findParagraphRange(position, text) ?: IntRange(position, position)
    }
    
    /**
     * 查找匹配的大括号
     */
    fun findMatchingBrace(position: Int, brace: Char): Int? {
        if (text.isEmpty() || position < 0 || position >= text.length) return null
        
        val openBraces = setOf('(', '[', '{')
        val closeBraces = setOf(')', ']', '}')
        val matchingBrace = when (brace) {
            '(' -> ')'
            '[' -> ']'
            '{' -> '}'
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            else -> return null
        }
        
        var depth = 0
        var direction = if (openBraces.contains(brace)) 1 else -1
        
        var current = position + direction
        while (current in text.indices) {
            when (text[current]) {
                in openBraces -> if (direction == 1) depth++ else depth--
                in closeBraces -> if (direction == 1) depth-- else depth++
            }
            
            if (depth == 0 && text[current] == matchingBrace) {
                return current
            }
            
            current += direction
        }
        
        return null
    }
    
    /**
     * 获取光标位置的上下文
     */
    fun getContext(position: Int, contextSize: Int = 20): ContextInfo {
        val start = max(0, position - contextSize)
        val end = min(text.length, position + contextSize)
        
        val before = text.substring(start, position)
        val after = text.substring(position, end)
        val lineStart = findLineRange(position, text)?.first ?: 0
        val lineEnd = findLineRange(position, text)?.last ?: text.length
        val lineNumber = text.substring(0, lineStart).count { it == '\n' } + 1
        val column = position - lineStart + 1
        
        return ContextInfo(
            before = before,
            after = after,
            lineNumber = lineNumber,
            column = column,
            context = text.substring(max(0, start), min(text.length, end))
        )
    }
}

/**
 * 上下文信息
 */
data class ContextInfo(
    val before: String,
    val after: String,
    val lineNumber: Int,
    val column: Int,
    val context: String
)