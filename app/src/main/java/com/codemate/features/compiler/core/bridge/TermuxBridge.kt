package com.codemate.features.compiler.core.bridge

import android.content.Context
import android.util.Log
import com.codemate.features.compiler.domain.entities.*
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Termux桥接器
 * 提供与Termux环境交互的核心功能
 * 包括命令执行、文件访问、环境变量管理等功能
 */
class TermuxBridge(private val context: Context) {
    companion object {
        private const val TAG = "TermuxBridge"
        private const val TERMUX_API_PACKAGE = "com.termux.api"
        private const val STORAGE_PERMISSION_REQUEST = 1000
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val isConnected = AtomicBoolean(false)
    private val runningProcesses = ConcurrentHashMap<String, Process>()
    private val outputHandlers = ConcurrentHashMap<String, OutputStreamHandler>()

    /**
     * 检查Termux环境是否可用
     */
    suspend fun checkTermuxAvailability(): TermuxEnvironment {
        return withContext(Dispatchers.IO) {
            try {
                val isTermuxInstalled = checkTermuxInstalled()
                val isTermuxApiAvailable = isTermuxInstalled && checkTermuxApiAvailable()
                val supportedCommands = if (isTermuxApiAvailable) {
                    getSupportedCommands()
                } else emptyList()
                
                val environmentVariables = if (isTermuxApiAvailable) {
                    getEnvironmentVariables()
                } else emptyMap()

                isConnected.set(isTermuxApiAvailable)

                TermuxEnvironment(
                    isTermuxInstalled = isTermuxInstalled,
                    isTermuxApiAvailable = isTermuxApiAvailable,
                    supportedCommands = supportedCommands,
                    homeDirectory = environmentVariables["HOME"] ?: "/data/data/com.termux/files/home",
                    termuxDirectory = environmentVariables["TERMUX_VERSION"]?.let {
                        "/data/data/com.termux/files/home/.termux"
                    } ?: "",
                    storageDirectory = environmentVariables["STORAGE"] ?: "/data/data/com.termux/files/home/storage",
                    sharedStorageDirectory = environmentVariables["EXTERNAL_STORAGE"] ?: "",
                    writableDirectory = environmentVariables["HOME"] ?: "/data/data/com.termux/files/home",
                    environmentVariables = environmentVariables
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to check Termux availability", e)
                TermuxEnvironment(
                    isTermuxInstalled = false,
                    isTermuxApiAvailable = false,
                    supportedCommands = emptyList(),
                    homeDirectory = "",
                    termuxDirectory = "",
                    storageDirectory = "",
                    sharedStorageDirectory = "",
                    writableDirectory = "",
                    environmentVariables = emptyMap()
                )
            }
        }
    }

    /**
     * 执行编译命令
     */
    suspend fun executeCommand(
        command: String,
        workingDirectory: String = "",
        environment: Map<String, String> = emptyMap(),
        timeout: Long = 300000L // 5分钟超时
    ): ProcessResult = withContext(Dispatchers.IO) {
        try {
            val processId = generateProcessId()
            Log.d(TAG, "Executing command: $command in directory: $workingDirectory")

            // 构建命令
            val processBuilder = ProcessBuilder(*command.split(" ").toTypedArray())
            
            // 设置工作目录
            if (workingDirectory.isNotEmpty()) {
                processBuilder.directory(File(workingDirectory))
            }

            // 设置环境变量
            val env = processBuilder.environment()
            environment.forEach { (key, value) ->
                env[key] = value
            }

            // 启动进程
            val process = processBuilder.start()
            runningProcesses[processId] = process

            // 创建输出处理器
            val outputHandler = OutputStreamHandler(process)
            outputHandlers[processId] = outputHandler

            // 等待进程完成
            val exitCode = withTimeoutOrNull(timeout) {
                process.waitFor()
            } ?: {
                process.destroyForcibly()
                -1
            }()

            val output = outputHandler.getOutput()
            val errorOutput = outputHandler.getErrorOutput()

            // 清理资源
            runningProcesses.remove(processId)
            outputHandlers.remove(processId)

            ProcessResult(
                processId = processId,
                exitCode = exitCode,
                output = output,
                errorOutput = errorOutput,
                success = exitCode == 0,
                executionTime = outputHandler.getExecutionTime()
            )

        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Command execution timed out: $command", e)
            ProcessResult.empty().copy(success = false, errorOutput = "Command timed out")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute command: $command", e)
            ProcessResult.empty().copy(success = false, errorOutput = e.message ?: "Unknown error")
        }
    }

    /**
     * 执行编译任务（带实时输出）
     */
    suspend fun executeCompileTask(
        task: CompileTask,
        outputListener: OutputEventListener? = null
    ): ProcessResult = withContext(Dispatchers.IO) {
        try {
            val command = buildCompileCommand(task)
            outputListener?.onEvent(OutputEvent.Info("Starting compilation: ${task.targetLanguage.displayName}"))
            
            val processId = generateProcessId()
            Log.d(TAG, "Executing compile task: ${task.id}")

            // 构建命令
            val processBuilder = ProcessBuilder(*command.split(" ").toTypedArray())
            
            // 设置工作目录
            if (task.workingDirectory.isNotEmpty()) {
                processBuilder.directory(File(task.workingDirectory))
            } else {
                processBuilder.directory(File(task.projectPath))
            }

            // 设置环境变量
            val env = processBuilder.environment()
            task.environmentVariables.forEach { (key, value) ->
                env[key] = value
            }

            // 启动进程
            val process = processBuilder.start()
            runningProcesses[processId] = process

            // 创建实时输出处理器
            val outputHandler = OutputStreamHandler(process, outputListener)
            outputHandlers[processId] = outputHandler

            // 异步处理输出
            scope.launch {
                outputHandler.startStreaming()
            }

            // 等待进程完成
            val exitCode = process.waitFor()

            // 等待输出处理完成
            outputHandler.stopStreaming()

            val result = ProcessResult(
                processId = processId,
                exitCode = exitCode,
                output = outputHandler.getOutput(),
                errorOutput = outputHandler.getErrorOutput(),
                success = exitCode == 0,
                executionTime = outputHandler.getExecutionTime()
            )

            // 清理资源
            runningProcesses.remove(processId)
            outputHandlers.remove(processId)

            if (result.success) {
                outputListener?.onEvent(OutputEvent.Completed)
            } else {
                outputListener?.onEvent(OutputEvent.Error("Compilation failed with exit code: $exitCode"))
            }

            result

        } catch (e: Exception) {
            Log.e(TAG, "Failed to execute compile task: ${task.id}", e)
            outputListener?.onEvent(OutputEvent.Error("Compilation error: ${e.message}"))
            ProcessResult.empty().copy(success = false, errorOutput = e.message ?: "Unknown error")
        }
    }

    /**
     * 取消正在运行的进程
     */
    suspend fun cancelProcess(processId: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val process = runningProcesses[processId] ?: return@withContext false
                val outputHandler = outputHandlers[processId]
                
                outputHandler?.stopStreaming()
                process.destroyForcibly()
                
                runningProcesses.remove(processId)
                outputHandlers.remove(processId)
                
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel process: $processId", e)
                false
            }
        }
    }

    /**
     * 检查文件是否存在
     */
    suspend fun fileExists(filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            file.exists() && file.canRead()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check file existence: $filePath", e)
            false
        }
    }

    /**
     * 读取文件内容
     */
    suspend fun readFile(filePath: String): String? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return@withContext null
            
            file.readText()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read file: $filePath", e)
            null
        }
    }

    /**
     * 写入文件内容
     */
    suspend fun writeFile(filePath: String, content: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write file: $filePath", e)
            false
        }
    }

    /**
     * 获取目录列表
     */
    suspend fun listDirectory(directoryPath: String): List<FileInfo> = withContext(Dispatchers.IO) {
        try {
            val directory = File(directoryPath)
            if (!directory.exists() || !directory.canRead()) return@withContext emptyList()
            
            directory.listFiles()?.map { file ->
                FileInfo(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list directory: $directoryPath", e)
            emptyList()
        }
    }

    /**
     * 创建目录
     */
    suspend fun createDirectory(directoryPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val directory = File(directoryPath)
            directory.mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create directory: $directoryPath", e)
            false
        }
    }

    /**
     * 删除文件或目录
     */
    suspend fun deleteFileOrDirectory(path: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            if (file.isDirectory) {
                file.deleteRecursively()
            } else {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete: $path", e)
            false
        }
    }

    /**
     * 获取环境变量
     */
    suspend fun getEnvironmentVariable(key: String): String? = withContext(Dispatchers.IO) {
        try {
            System.getenv(key)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get environment variable: $key", e)
            null
        }
    }

    /**
     * 关闭桥接器
     */
    fun shutdown() {
        scope.cancel()
        runningProcesses.values.forEach { process ->
            process.destroyForcibly()
        }
        runningProcesses.clear()
        outputHandlers.clear()
    }

    // 私有方法
    private suspend fun checkTermuxInstalled(): Boolean = withContext(Dispatchers.IO) {
        try {
            val packageManager = context.packageManager
            packageManager.getPackageInfo(TERMUX_API_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun checkTermuxApiAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val command = "termux-info"
            val result = executeCommand(command)
            result.success
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun getSupportedCommands(): List<String> = withContext(Dispatchers.IO) {
        try {
            val result = executeCommand("termux-info --help")
            if (result.success) {
                result.output.lines().filter { it.isNotBlank() }
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun getEnvironmentVariables(): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            val result = executeCommand("env")
            if (result.success) {
                result.output.lines()
                    .mapNotNull { line ->
                        val parts = line.split("=", limit = 2)
                        if (parts.size == 2) parts[0] to parts[1] else null
                    }
                    .toMap()
            } else {
                emptyMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun buildCompileCommand(task: CompileTask): String {
        val config = task.compilerConfig
        val command = config.compilerCommand
        val args = config.compilerArgs.toMutableList()
        
        // 添加优化选项
        when (config.optimizationLevel) {
            OptimizationLevel.NONE -> args.add("-O0")
            OptimizationLevel.LESS -> args.add("-O1")
            OptimizationLevel.MORE -> args.add("-O2")
            OptimizationLevel.AGGRESSIVE -> args.add("-O3")
        }
        
        // 添加调试符号
        if (config.debugSymbols) {
            args.add("-g")
        }
        
        // 添加警告选项
        if (!config.warningsEnabled) {
            args.add("-w")
        } else if (config.warningsAsErrors) {
            args.add("-Werror")
        }
        
        // 添加包含路径
        config.includePaths.forEach { path ->
            args.add("-I$path")
        }
        
        // 添加库路径
        config.libraryPaths.forEach { path ->
            args.add("-L$path")
        }
        
        // 添加宏定义
        config.defines.forEach { (key, value) ->
            args.add("-D$key=${value}")
        }
        
        // 添加输出路径
        config.outputPath?.let { outputPath ->
            args.add("-o")
            args.add(outputPath)
        }
        
        // 添加源文件
        args.addAll(task.sourceFiles)
        
        return "$command ${args.joinToString(" ")}"
    }

    private fun generateProcessId(): String = "proc_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
}

/**
 * 进程执行结果
 */
data class ProcessResult(
    val processId: String,
    val exitCode: Int,
    val output: String,
    val errorOutput: String,
    val success: Boolean,
    val executionTime: Long
) {
    companion object {
        fun empty() = ProcessResult(
            processId = "",
            exitCode = -1,
            output = "",
            errorOutput = "",
            success = false,
            executionTime = 0
        )
    }
}

/**
 * 文件信息
 */
data class FileInfo(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long
)

/**
 * 输出事件监听器
 */
interface OutputEventListener {
    fun onEvent(event: OutputEvent)
}