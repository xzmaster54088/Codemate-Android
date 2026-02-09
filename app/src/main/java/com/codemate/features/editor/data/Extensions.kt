package com.codemate.features.editor.data

import androidx.compose.ui.graphics.Color

/**
 * BuildConfig 模拟类，用于开发环境
 */
object BuildConfig {
    const val DEBUG = true
    const val BUILD_TYPE = "debug"
    const val VERSION_CODE = 1
    const val VERSION_NAME = "1.0.0"
}

/**
 * 扩展函数
 */
private fun String.contains(substring: String, ignoreCase: Boolean = false): Boolean {
    return if (ignoreCase) {
        this.lowercase().contains(substring.lowercase())
    } else {
        this.contains(substring)
    }
}

private fun String.startsWith(prefix: String, ignoreCase: Boolean = false): Boolean {
    return if (ignoreCase) {
        this.lowercase().startsWith(prefix.lowercase())
    } else {
        this.startsWith(prefix)
    }
}

private fun Char.isLetterOrDigit(): Boolean {
    return this.isLetter() || this.isDigit()
}

/**
 * 缺失的扩展函数
 */
private fun IntRange.clamp(value: Int): Int {
    return value.coerceIn(this.first, this.last)
}

/**
 * 缺失的集合扩展函数
 */
private fun <K, V> Map<K, V>.entries(): Set<Map.Entry<K, V>> = this.entries

private fun <T> Set<T>.drop(n: Int): List<T> = this.toList().drop(n)

/**
 * 扩展String.trimIndent函数
 */
private fun String.trimIndent(): String {
    // 简单的缩进移除实现
    val lines = this.split("\n")
    if (lines.isEmpty()) return this
    
    // 找到最小缩进（忽略空行）
    val minIndent = lines
        .filter { it.isNotBlank() }
        .minOfOrNull { it.length - it.trimStart().length } ?: 0
    
    // 移除最小缩进
    return lines.joinToString("\n") { line ->
        if (line.isBlank()) line
        else line.substring(minIndent.coerceAtMost(line.length))
    }
}

/**
 * 扩展distanceTo函数
 */
private fun Offset.distanceTo(other: Offset): Float {
    val dx = this.x - other.x
    val dy = this.y - other.y
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/**
 * 扩展joinToString函数
 */
private fun <T> Collection<T>.joinToString(separator: String, transform: ((T) -> String)? = null): String {
    val sb = StringBuilder()
    this.forEachIndexed { index, item ->
        if (index > 0) sb.append(separator)
        sb.append(transform?.invoke(item) ?: item.toString())
    }
    return sb.toString()
}

/**
 * 扩展substring函数支持负数索引
 */
private fun String.safeSubstring(startIndex: Int, endIndex: Int): String {
    val adjustedStart = if (startIndex < 0) 0 else startIndex
    val adjustedEnd = if (endIndex > length) length else endIndex
    return if (adjustedStart < adjustedEnd) {
        this.substring(adjustedStart, adjustedEnd)
    } else {
        ""
    }
}