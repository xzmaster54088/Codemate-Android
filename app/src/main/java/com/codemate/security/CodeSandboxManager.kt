package com.codemate.security

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.security.AccessController
import java.security.PrivilegedAction
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.writeText
import kotlin.io.path.pathString

/**
 * 代码沙盒管理器
 * 创建安全的代码执行环境，隔离恶意代码和资源访问
 * 提供多级安全控制和资源限制
 */
@Singleton
class CodeSandboxManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "CodeSandboxManager"
        private const val MAX_CONCURRENT_SANDBOXES = 5
        private const val MAX_EXECUTION_TIME_MS = 30000L // 30秒
        private const val MAX_MEMORY_USAGE_MB = 64L // 64MB
        private const val MAX_FILE_SIZE_MB = 10L // 10MB
        private const val SANDBOX_TIMEOUT_MS = 60000L // 1分钟
        private const val DEFAULT_TIMEOUT = 5000L // 5秒
    }

    private val sandboxCounter = AtomicInteger(0)
    private val activeSandboxes = ConcurrentHashMap<String, SandboxInstance>()
    private val executionSemaphore = Semaphore(MAX_CONCURRENT_SANDBOXES)
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // 禁止的系统调用和操作
    private val forbiddenOperations = setOf(
        "Runtime.exec",
        "ProcessBuilder",
        "System.loadLibrary",
        "System.load",
        "ClassLoader.defineClass",
        "Reflection.getCallerClass",
        "java.net.Socket",
        "java.net.ServerSocket",
        "java.net.DatagramSocket",
        "java.net.MulticastSocket",
        "java.nio.channels.SocketChannel",
        "java.nio.channels.ServerSocketChannel",
        "java.nio.channels.DatagramChannel",
        "java.net.http.HttpClient"
    )

    // 允许的文件操作路径
    private val allowedFilePaths = setOf(
        "/data/data/${context.packageName}/sandbox/",
        "/cache/sandbox/"
    )

    /**
     * 创建新的沙盒实例
     */
    suspend fun createSandbox(config: SandboxConfig): SandboxResult = withContext(Dispatchers.IO) {
        try {
            if (!executionSemaphore.tryAcquire(DEFAULT_TIMEOUT, TimeUnit.MILLISECONDS)) {
                return@withContext SandboxResult(
                    success = false,
                    sandboxId = null,
                    error = "沙盒池已满，请稍后重试"
                )
            }

            val sandboxId = generateSandboxId()
            val sandboxDir = createSandboxDirectory(sandboxId)
            
            if (sandboxDir == null) {
                executionSemaphore.release()
                return@withContext SandboxResult(
                    success = false,
                    sandboxId = null,
                    error = "创建沙盒目录失败"
                )
            }

            val sandboxInstance = SandboxInstance(
                id = sandboxId,
                directory = sandboxDir,
                config = config,
                createdAt = System.currentTimeMillis(),
                status = SandboxStatus.CREATED
            )

            activeSandboxes[sandboxId] = sandboxInstance
            SecurityLog.i("创建沙盒实例: $sandboxId")

            SandboxResult(
                success = true,
                sandboxId = sandboxId,
                error = null
            )
        } catch (e: Exception) {
            SecurityLog.e("创建沙盒失败", e)
            SandboxResult(
                success = false,
                sandboxId = null,
                error = e.message
            )
        }
    }

    /**
     * 在沙盒中执行代码
     */
    suspend fun executeCode(
        sandboxId: String,
        code: String,
        language: ProgrammingLanguage,
        timeout: Long = MAX_EXECUTION_TIME_MS
    ): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            val sandbox = activeSandboxes[sandboxId]
                ?: return@withContext ExecutionResult(
                    success = false,
                    output = null,
                    error = "沙盒实例不存在: $sandboxId",
                    executionTime = 0L
                )

            // 检查沙盒状态
            if (sandbox.status != SandboxStatus.READY) {
                return@withContext ExecutionResult(
                    success = false,
                    output = null,
                    error = "沙盒状态不正确: ${sandbox.status}",
                    executionTime = 0L
                )
            }

            SecurityLog.i("开始在沙盒中执行代码: $sandboxId")

            val startTime = System.currentTimeMillis()
            
            // 安全检查
            val securityCheck = performSecurityCheck(code, language)
            if (!securityCheck.isSafe) {
                return@withContext ExecutionResult(
                    success = false,
                    output = null,
                    error = "代码安全检查失败: ${securityCheck.reason}",
                    executionTime = 0L
                )
            }

            // 创建代码文件
            val codeFile = createCodeFile(sandbox.directory, code, language)
            if (codeFile == null) {
                return@withContext ExecutionResult(
                    success = false,
                    output = null,
                    error = "创建代码文件失败",
                    executionTime = 0L
                )
            }

            // 执行代码
            val executionJob = coroutineScope.async {
                executeCodeSafely(sandbox, codeFile, language, timeout)
            }

            try {
                val result = executionJob.await()
                val executionTime = System.currentTimeMillis() - startTime
                
                SecurityLog.i("代码执行完成: $sandboxId, 用时: ${executionTime}ms")
                
                ExecutionResult(
                    success = result.success,
                    output = result.output,
                    error = result.error,
                    executionTime = executionTime
                )
            } catch (e: TimeoutCancellationException) {
                SecurityLog.w("代码执行超时: $sandboxId")
                cleanupSandbox(sandboxId)
                ExecutionResult(
                    success = false,
                    output = null,
                    error = "代码执行超时 (${timeout}ms)",
                    executionTime = timeout
                )
            }
        } catch (e: Exception) {
            SecurityLog.e("执行代码失败: $sandboxId", e)
            ExecutionResult(
                success = false,
                output = null,
                error = e.message,
                executionTime = 0L
            )
        }
    }

    /**
     * 获取沙盒状态
     */
    suspend fun getSandboxStatus(sandboxId: String): SandboxStatusInfo? = withContext(Dispatchers.IO) {
        try {
            val sandbox = activeSandboxes[sandboxId] ?: return@withContext null
            
            SandboxStatusInfo(
                id = sandbox.id,
                status = sandbox.status,
                createdAt = sandbox.createdAt,
                lastUsedAt = sandbox.lastUsedAt,
                directory = sandbox.directory,
                config = sandbox.config,
                memoryUsage = getSandboxMemoryUsage(sandbox),
                fileCount = getSandboxFileCount(sandbox)
            )
        } catch (e: Exception) {
            SecurityLog.e("获取沙盒状态失败: $sandboxId", e)
            null
        }
    }

    /**
     * 销毁沙盒实例
     */
    suspend fun destroySandbox(sandboxId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            cleanupSandbox(sandboxId)
            true
        } catch (e: Exception) {
            SecurityLog.e("销毁沙盒失败: $sandboxId", e)
            false
        }
    }

    /**
     * 清理所有沙盒
     */
    suspend fun cleanupAllSandboxes(): Boolean = withContext(Dispatchers.IO) {
        try {
            val sandboxIds = activeSandboxes.keys.toList()
            sandboxIds.forEach { sandboxId ->
                cleanupSandbox(sandboxId)
            }
            true
        } catch (e: Exception) {
            SecurityLog.e("清理所有沙盒失败", e)
            false
        }
    }

    /**
     * 获取活跃沙盒列表
     */
    suspend fun getActiveSandboxes(): List<SandboxStatusInfo> = withContext(Dispatchers.IO) {
        activeSandboxes.values.mapNotNull { sandbox ->
            try {
                getSandboxStatus(sandbox.id)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 创建沙盒目录
     */
    private fun createSandboxDirectory(sandboxId: String): File? {
        return try {
            val sandboxDir = File(context.filesDir, "sandbox/$sandboxId")
            if (!sandboxDir.exists()) {
                sandboxDir.mkdirs()
            }
            
            // 创建安全策略文件
            createSecurityPolicyFile(sandboxDir)
            
            sandboxDir
        } catch (e: Exception) {
            SecurityLog.e("创建沙盒目录失败", e)
            null
        }
    }

    /**
     * 创建安全策略文件
     */
    private fun createSecurityPolicyFile(sandboxDir: File) {
        val policyFile = File(sandboxDir, "security.policy")
        val policy = """
            // CodeMate沙盒安全策略
            grant {
                permission java.io.FilePermission "${sandboxDir.absolutePath}/-", "read,write,delete";
                permission java.lang.RuntimePermission "accessDeclaredMembers";
                permission java.lang.RuntimePermission "modifyThread";
                permission java.lang.RuntimePermission "setIO";
                
                // 禁止的网络权限
                deny java.net.SocketPermission "*", "connect,accept,listen";
                deny java.net.DatagramPermission "*", "*";
            };
        """.trimIndent()
        
        policyFile.writeText(policy)
    }

    /**
     * 执行代码安全检查
     */
    private fun performSecurityCheck(code: String, language: ProgrammingLanguage): SecurityCheckResult {
        val suspiciousPatterns = mutableListOf<String>()
        
        // 检查禁止的操作
        forbiddenOperations.forEach { operation ->
            if (code.contains(operation)) {
                suspiciousPatterns.add("禁止的操作: $operation")
            }
        }

        // 语言特定的安全检查
        when (language) {
            ProgrammingLanguage.KOTLIN -> {
                // Kotlin特定检查
                val kotlinPatterns = listOf(
                    "Runtime\\.getRuntime\\(\\)\\.exec",
                    "ProcessBuilder",
                    "System\\.loadLibrary",
                    "java\\.lang\\.ClassLoader",
                    "java\\.security\\.SecurityManager"
                )
                
                kotlinPatterns.forEach { pattern ->
                    if (Regex(pattern).containsMatchIn(code)) {
                        suspiciousPatterns.add("可疑模式: $pattern")
                    }
                }
            }
            ProgrammingLanguage.JAVASCRIPT -> {
                // JavaScript特定检查
                val jsPatterns = listOf(
                    "eval\\(",
                    "Function\\(",
                    "require\\(",
                    "import\\s+",
                    "XMLHttpRequest",
                    "fetch\\(",
                    "WebSocket"
                )
                
                jsPatterns.forEach { pattern ->
                    if (Regex(pattern).containsMatchIn(code)) {
                        suspiciousPatterns.add("可疑模式: $pattern")
                    }
                }
            }
            ProgrammingLanguage.PYTHON -> {
                // Python特定检查（通过解释器）
                val pythonPatterns = listOf(
                    "__import__",
                    "exec\\(",
                    "eval\\(",
                    "open\\(",
                    "file\\(",
                    "input\\("
                )
                
                pythonPatterns.forEach { pattern ->
                    if (code.contains(pattern)) {
                        suspiciousPatterns.add("可疑模式: $pattern")
                    }
                }
            }
            else -> {
                // 其他语言的基本检查
                if (code.length > 10000) {
                    suspiciousPatterns.add("代码过长")
                }
            }
        }

        return if (suspiciousPatterns.isEmpty()) {
            SecurityCheckResult(isSafe = true, reason = null)
        } else {
            SecurityCheckResult(
                isSafe = false,
                reason = suspiciousPatterns.joinToString("; ")
            )
        }
    }

    /**
     * 创建代码文件
     */
    private fun createCodeFile(sandboxDir: File, code: String, language: ProgrammingLanguage): File? {
        return try {
            val extension = when (language) {
                ProgrammingLanguage.KOTLIN -> "kt"
                ProgrammingLanguage.JAVASCRIPT -> "js"
                ProgrammingLanguage.PYTHON -> "py"
                ProgrammingLanguage.JAVA -> "java"
                ProgrammingLanguage.C -> "c"
                ProgrammingLanguage.CPP -> "cpp"
                else -> "txt"
            }
            
            val codeFile = File(sandboxDir, "main.$extension")
            codeFile.writeText(code, StandardCharsets.UTF_8)
            
            // 检查文件大小
            if (codeFile.length() > MAX_FILE_SIZE_MB * 1024 * 1024) {
                codeFile.delete()
                return null
            }
            
            codeFile
        } catch (e: Exception) {
            SecurityLog.e("创建代码文件失败", e)
            null
        }
    }

    /**
     * 安全执行代码
     */
    private suspend fun executeCodeSafely(
        sandbox: SandboxInstance,
        codeFile: File,
        language: ProgrammingLanguage,
        timeout: Long
    ): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            sandbox.status = SandboxStatus.EXECUTING
            
            val result = when (language) {
                ProgrammingLanguage.KOTLIN -> executeKotlinCode(sandbox, codeFile, timeout)
                ProgrammingLanguage.JAVASCRIPT -> executeJavaScriptCode(sandbox, codeFile, timeout)
                ProgrammingLanguage.PYTHON -> executePythonCode(sandbox, codeFile, timeout)
                ProgrammingLanguage.JAVA -> executeJavaCode(sandbox, codeFile, timeout)
                else -> ExecutionResult(
                    success = false,
                    output = null,
                    error = "不支持的编程语言: $language",
                    executionTime = 0L
                )
            }
            
            sandbox.status = SandboxStatus.COMPLETED
            sandbox.lastUsedAt = System.currentTimeMillis()
            
            result
        } catch (e: Exception) {
            sandbox.status = SandboxStatus.ERROR
            ExecutionResult(
                success = false,
                output = null,
                error = e.message,
                executionTime = 0L
            )
        }
    }

    /**
     * 执行Kotlin代码
     */
    private suspend fun executeKotlinCode(
        sandbox: SandboxInstance,
        codeFile: File,
        timeout: Long
    ): ExecutionResult {
        return try {
            val processBuilder = ProcessBuilder(
                "kotlinc", 
                codeFile.absolutePath, 
                "-include-runtime", 
                "-d", "${codeFile.absolutePath}.jar"
            )
            
            processBuilder.directory(sandbox.directory)
            processBuilder.environment()["PATH"] = sandbox.directory.absolutePath
            
            val process = processBuilder.start()
            val output = captureProcessOutput(process, timeout)
            
            if (process.exitValue() == 0) {
                // 执行编译后的jar
                val runtimeProcess = ProcessBuilder(
                    "java", 
                    "-jar", 
                    "${codeFile.absolutePath}.jar"
                ).start()
                
                val runtimeOutput = captureProcessOutput(runtimeProcess, timeout)
                runtimeOutput
            } else {
                output
            }
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                output = null,
                error = e.message,
                executionTime = 0L
            )
        }
    }

    /**
     * 执行JavaScript代码
     */
    private suspend fun executeJavaScriptCode(
        sandbox: SandboxInstance,
        codeFile: File,
        timeout: Long
    ): ExecutionResult {
        return try {
            // 使用内置的JavaScript引擎执行
            val scriptEngine = javax.script.ScriptEngineManager()
                .getEngineByName("JavaScript")
            
            val result = scriptEngine.eval(codeFile.readText())
            
            ExecutionResult(
                success = true,
                output = result?.toString() ?: "",
                error = null,
                executionTime = 0L
            )
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                output = null,
                error = e.message,
                executionTime = 0L
            )
        }
    }

    /**
     * 执行Python代码
     */
    private suspend fun executePythonCode(
        sandbox: SandboxInstance,
        codeFile: File,
        timeout: Long
    ): ExecutionResult {
        return try {
            val processBuilder = ProcessBuilder(
                "python3",
                codeFile.absolutePath
            )
            
            processBuilder.directory(sandbox.directory)
            val process = processBuilder.start()
            captureProcessOutput(process, timeout)
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                output = null,
                error = e.message,
                executionTime = 0L
            )
        }
    }

    /**
     * 执行Java代码
     */
    private suspend fun executeJavaCode(
        sandbox: SandboxInstance,
        codeFile: File,
        timeout: Long
    ): ExecutionResult {
        return try {
            val processBuilder = ProcessBuilder(
                "javac",
                codeFile.absolutePath
            )
            
            processBuilder.directory(sandbox.directory)
            val process = processBuilder.start()
            val compileOutput = captureProcessOutput(process, timeout)
            
            if (process.exitValue() == 0) {
                val className = codeFile.nameWithoutExtension
                val runtimeProcess = ProcessBuilder(
                    "java",
                    "-cp", sandbox.directory.absolutePath,
                    className
                ).start()
                
                captureProcessOutput(runtimeProcess, timeout)
            } else {
                compileOutput
            }
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                output = null,
                error = e.message,
                executionTime = 0L
            )
        }
    }

    /**
     * 捕获进程输出
     */
    private suspend fun captureProcessOutput(
        process: Process,
        timeout: Long
    ): ExecutionResult = withContext(Dispatchers.IO) {
        try {
            val output = StringBuilder()
            val error = StringBuilder()
            
            // 读取标准输出
            val outputReader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            
            var line: String?
            while (outputReader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            
            while (errorReader.readLine().also { line = it } != null) {
                error.appendLine(line)
            }
            
            val finished = process.waitFor(timeout, TimeUnit.MILLISECONDS)
            
            if (!finished) {
                process.destroyForcibly()
                return@withContext ExecutionResult(
                    success = false,
                    output = null,
                    error = "进程执行超时",
                    executionTime = timeout
                )
            }
            
            val exitCode = process.exitValue()
            val success = exitCode == 0
            
            ExecutionResult(
                success = success,
                output = output.toString().ifEmpty { error.toString() },
                error = if (success) null else error.toString(),
                executionTime = 0L
            )
        } catch (e: Exception) {
            ExecutionResult(
                success = false,
                output = null,
                error = e.message,
                executionTime = 0L
            )
        }
    }

    /**
     * 生成沙盒ID
     */
    private fun generateSandboxId(): String {
        return "sandbox_${sandboxCounter.incrementAndGet()}_${System.currentTimeMillis()}"
    }

    /**
     * 清理沙盒
     */
    private fun cleanupSandbox(sandboxId: String) {
        try {
            val sandbox = activeSandboxes.remove(sandboxId)
            if (sandbox != null) {
                // 清理文件
                if (sandbox.directory.exists()) {
                    sandbox.directory.deleteRecursively()
                }
                executionSemaphore.release()
                SecurityLog.i("清理沙盒: $sandboxId")
            }
        } catch (e: Exception) {
            SecurityLog.e("清理沙盒失败: $sandboxId", e)
        }
    }

    /**
     * 获取沙盒内存使用情况
     */
    private fun getSandboxMemoryUsage(sandbox: SandboxInstance): Long {
        return try {
            sandbox.directory.walkTopDown()
                .filter { it.isFile }
                .sumOf { it.length() }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 获取沙盒文件数量
     */
    private fun getSandboxFileCount(sandbox: SandboxInstance): Int {
        return try {
            sandbox.directory.walkTopDown()
                .filter { it.isFile }
                .count()
        } catch (e: Exception) {
            0
        }
    }
}

/**
 * 沙盒配置
 */
data class SandboxConfig(
    val maxMemory: Long = MAX_MEMORY_USAGE_MB,
    val maxExecutionTime: Long = MAX_EXECUTION_TIME_MS,
    val maxFileSize: Long = MAX_FILE_SIZE_MB,
    val allowedLanguages: Set<ProgrammingLanguage> = setOf(
        ProgrammingLanguage.KOTLIN,
        ProgrammingLanguage.JAVASCRIPT,
        ProgrammingLanguage.PYTHON
    ),
    val enableNetworkAccess: Boolean = false,
    val enableFileSystemAccess: Boolean = true
)

/**
 * 编程语言枚举
 */
enum class ProgrammingLanguage {
    KOTLIN,
    JAVASCRIPT,
    PYTHON,
    JAVA,
    C,
    CPP,
    TYPESCRIPT
}

/**
 * 沙盒实例状态
 */
enum class SandboxStatus {
    CREATED,
    READY,
    EXECUTING,
    COMPLETED,
    ERROR,
    DESTROYED
}

/**
 * 沙盒实例
 */
data class SandboxInstance(
    val id: String,
    val directory: File,
    val config: SandboxConfig,
    val createdAt: Long,
    var status: SandboxStatus,
    var lastUsedAt: Long = createdAt
)

/**
 * 沙盒结果
 */
data class SandboxResult(
    val success: Boolean,
    val sandboxId: String?,
    val error: String?
)

/**
 * 执行结果
 */
data class ExecutionResult(
    val success: Boolean,
    val output: String?,
    val error: String?,
    val executionTime: Long
)

/**
 * 沙盒状态信息
 */
data class SandboxStatusInfo(
    val id: String,
    val status: SandboxStatus,
    val createdAt: Long,
    val lastUsedAt: Long,
    val directory: File,
    val config: SandboxConfig,
    val memoryUsage: Long,
    val fileCount: Int
)

/**
 * 安全检查结果
 */
data class SecurityCheckResult(
    val isSafe: Boolean,
    val reason: String?
)

/**
 * 常量扩展
 */
private const val MAX_CONCURRENT_SANDBOXES = 5
private const val MAX_EXECUTION_TIME_MS = 30000L
private const val MAX_MEMORY_USAGE_MB = 64L
private const val MAX_FILE_SIZE_MB = 10L