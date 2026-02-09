package com.codemate.features.compiler.core.bridge

import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * 实时输出流处理器
 * 负责捕获和管理编译过程的stdout、stderr输出
 * 支持实时流式输出、进度跟踪和输出解析
 */
class OutputStreamHandler(
    private val process: Process,
    private val outputListener: OutputEventListener? = null
) {
    companion object {
        private const val TAG = "OutputStreamHandler"
        private const val BUFFER_SIZE = 8192 // 8KB缓冲区
        private const val MAX_OUTPUT_SIZE = 10 * 1024 * 1024 // 10MB最大输出
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val outputLock = ReentrantLock()
    private val startTime = System.currentTimeMillis()
    private val bytesRead = AtomicLong(0)
    
    private var standardOutput = StringBuilder()
    private var errorOutput = StringBuilder()
    private var isStreaming = false
    private var currentProgress = 0
    private var lastProgressUpdate = System.currentTimeMillis()

    /**
     * 启动实时流式输出
     */
    suspend fun startStreaming() {
        if (isStreaming) return
        
        isStreaming = true
        scope.launch {
            try {
                // 分别处理stdout和stderr
                val stdoutJob = launch { handleStandardOutput() }
                val stderrJob = launch { handleStandardError() }
                
                // 等待所有输出处理完成
                stdoutJob.join()
                stderrJob.join()
                
            } catch (e: Exception) {
                Log.e(TAG, "Error during stream processing", e)
                outputListener?.onEvent(OutputEvent.Error("Output stream error: ${e.message}"))
            } finally {
                isStreaming = false
            }
        }
    }

    /**
     * 停止流式输出
     */
    suspend fun stopStreaming() {
        isStreaming = false
        scope.cancel()
    }

    /**
     * 获取标准输出
     */
    fun getOutput(): String = outputLock.withLock {
        standardOutput.toString()
    }

    /**
     * 获取错误输出
     */
    fun getErrorOutput(): String = outputLock.withLock {
        errorOutput.toString()
    }

    /**
     * 获取执行时间（毫秒）
     */
    fun getExecutionTime(): Long = System.currentTimeMillis() - startTime

    /**
     * 获取当前进度（0-100）
     */
    fun getProgress(): Int = currentProgress

    /**
     * 清除输出缓冲区
     */
    fun clearOutput() = outputLock.withLock {
        standardOutput.clear()
        errorOutput.clear()
        bytesRead.set(0)
        currentProgress = 0
    }

    /**
     * 解析输出内容，提取有用信息
     */
    suspend fun parseOutputForInfo(): CompileOutputInfo {
        return withContext(Dispatchers.Default) {
            val output = getOutput()
            val error = getErrorOutput()
            val allOutput = "$output\n$error"
            
            val lines = allOutput.lines()
            val info = CompileOutputInfo()
            
            // 解析进度信息
            lines.forEach { line ->
                parseProgressLine(line)?.let { progress ->
                    info.progressUpdates.add(progress)
                    return@forEach
                }
                
                // 解析文件信息
                parseFileLine(line)?.let { fileInfo ->
                    info.processedFiles.add(fileInfo)
                    return@forEach
                }
                
                // 解析警告信息
                parseWarningLine(line)?.let { warning ->
                    info.warnings.add(warning)
                    return@forEach
                }
                
                // 解析错误信息
                parseErrorLine(line)?.let { error ->
                    info.errors.add(error)
                    return@forEach
                }
            }
            
            info
        }
    }

    // 私有方法
    private suspend fun handleStandardOutput() {
        try {
            val inputStream = process.inputStream.bufferedReader()
            val buffer = CharArray(BUFFER_SIZE)
            
            var charsRead: Int
            while (isStreaming && inputStream.read(buffer).also { charsRead = it } != -1) {
                if (charsRead > 0) {
                    val text = String(buffer, 0, charsRead)
                    appendOutput(standardOutput, text)
                    bytesRead.addAndGet(charsRead.toLong())
                    
                    // 检查输出大小限制
                    if (bytesRead.get() > MAX_OUTPUT_SIZE) {
                        Log.w(TAG, "Output size limit exceeded, truncating")
                        truncateOutput()
                        break
                    }
                    
                    // 解析和发送输出事件
                    parseAndEmitOutput(text, false)
                    
                    // 更新进度
                    updateProgress()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling standard output", e)
            outputListener?.onEvent(OutputEvent.Error("Standard output error: ${e.message}"))
        }
    }

    private suspend fun handleStandardError() {
        try {
            val errorStream = process.errorStream.bufferedReader()
            val buffer = CharArray(BUFFER_SIZE)
            
            var charsRead: Int
            while (isStreaming && errorStream.read(buffer).also { charsRead = it } != -1) {
                if (charsRead > 0) {
                    val text = String(buffer, 0, charsRead)
                    appendOutput(errorOutput, text)
                    bytesRead.addAndGet(charsRead.toLong())
                    
                    // 解析和发送错误事件
                    parseAndEmitOutput(text, true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling standard error", e)
            outputListener?.onEvent(OutputEvent.Error("Standard error error: ${e.message}"))
        }
    }

    private fun appendOutput(builder: StringBuilder, text: String) {
        outputLock.withLock {
            // 限制单次添加的文本长度
            val trimmedText = if (text.length > MAX_OUTPUT_SIZE / 4) {
                text.substring(0, (MAX_OUTPUT_SIZE / 4).toInt())
            } else {
                text
            }
            builder.append(trimmedText)
        }
    }

    private fun truncateOutput() {
        outputLock.withLock {
            if (standardOutput.length > MAX_OUTPUT_SIZE / 2) {
                val excess = standardOutput.length - (MAX_OUTPUT_SIZE / 2).toInt()
                standardOutput.delete(0, excess)
            }
            if (errorOutput.length > MAX_OUTPUT_SIZE / 2) {
                val excess = errorOutput.length - (MAX_OUTPUT_SIZE / 2).toInt()
                errorOutput.delete(0, excess)
            }
        }
    }

    private suspend fun parseAndEmitOutput(text: String, isError: Boolean) {
        withContext(Dispatchers.Main) {
            val lines = text.lines()
            
            lines.forEach { line ->
                when {
                    line.isBlank() -> return@forEach
                    
                    isError -> {
                        outputListener?.onEvent(OutputEvent.StandardError(line))
                    }
                    
                    line.contains("error", ignoreCase = true) -> {
                        outputListener?.onEvent(OutputEvent.Error(line))
                    }
                    
                    line.contains("warning", ignoreCase = true) -> {
                        outputListener?.onEvent(OutputEvent.Warning(line))
                    }
                    
                    line.contains("info", ignoreCase = true) -> {
                        outputListener?.onEvent(OutputEvent.Info(line))
                    }
                    
                    else -> {
                        outputListener?.onEvent(OutputEvent.StandardOutput(line))
                    }
                }
            }
        }
    }

    private fun updateProgress() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastProgressUpdate > 1000) { // 每秒最多更新一次
            val output = getOutput()
            val lines = output.lines()
            
            // 简单启发式进度计算
            val totalLines = lines.size
            val processedLines = lines.count { it.contains("[") || it.contains("Processing") }
            val progress = if (totalLines > 0) {
                minOf(100, (processedLines * 100 / totalLines))
            } else {
                currentProgress
            }
            
            if (progress != currentProgress) {
                currentProgress = progress
                lastProgressUpdate = currentTime
                outputListener?.onEvent(
                    OutputEvent.Progress(
                        progress = progress,
                        message = "Processing... $progress%"
                    )
                )
            }
        }
    }

    private fun parseProgressLine(line: String): ProgressInfo? {
        // 匹配各种进度模式
        val patterns = listOf(
            Regex("\\[(\\d+)%\\]"),
            Regex("Progress: (\\d+)%"),
            Regex("(\\d+)/(\\d+)"),
            Regex("Building (\\d+)/(\\d+)")
        )
        
        patterns.forEach { pattern ->
            pattern.find(line)?.let { match ->
                return ProgressInfo(line, match.groups[1]?.value?.toIntOrNull() ?: 0)
            }
        }
        
        return null
    }

    private fun parseFileLine(line: String): ProcessedFile? {
        val patterns = listOf(
            Regex("Compiling (.+)"),
            Regex("Processing (.+)"),
            Regex("Building (.+)")
        )
        
        patterns.forEach { pattern ->
            pattern.find(line)?.let { match ->
                return ProcessedFile(match.groups[1]?.value ?: "", line)
            }
        }
        
        return null
    }

    private fun parseWarningLine(line: String): WarningInfo? {
        if (line.contains("warning", ignoreCase = true)) {
            return WarningInfo(line)
        }
        return null
    }

    private fun parseErrorLine(line: String): ErrorInfo? {
        if (line.contains("error", ignoreCase = true) || 
            line.contains("Error", ignoreCase = true)) {
            return ErrorInfo(line)
        }
        return null
    }
}

/**
 * 编译输出信息
 */
data class CompileOutputInfo(
    val progressUpdates: MutableList<ProgressInfo> = mutableListOf(),
    val processedFiles: MutableList<ProcessedFile> = mutableListOf(),
    val warnings: MutableList<WarningInfo> = mutableListOf(),
    val errors: MutableList<ErrorInfo> = mutableListOf()
)

/**
 * 进度信息
 */
data class ProgressInfo(
    val line: String,
    val percentage: Int
)

/**
 * 处理的文件信息
 */
data class ProcessedFile(
    val filename: String,
    val line: String
)

/**
 * 警告信息
 */
data class WarningInfo(
    val line: String
)

/**
 * 错误信息
 */
data class ErrorInfo(
    val line: String
)