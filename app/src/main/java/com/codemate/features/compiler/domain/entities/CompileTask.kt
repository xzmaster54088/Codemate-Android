package com.codemate.features.compiler.domain.entities

import java.util.Date

/**
 * 编译任务实体
 * 表示一个编译任务的完整信息
 */
data class CompileTask(
    val id: String,
    val projectPath: String,
    val sourceFiles: List<String>,
    val targetLanguage: Language,
    val compilerConfig: CompilerConfig,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val createdAt: Date = Date(),
    var status: TaskStatus = TaskStatus.PENDING,
    var startTime: Date? = null,
    var endTime: Date? = null,
    var output: String = "",
    var errorOutput: String = "",
    var exitCode: Int = 0,
    val dependencies: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val workingDirectory: String = ""
) {
    /**
     * 获取任务执行时间（毫秒）
     */
    fun getExecutionTime(): Long {
        return when {
            startTime != null && endTime != null -> {
                endTime!!.time - startTime!!.time
            }
            startTime != null -> {
                Date().time - startTime!!.time
            }
            else -> 0L
        }
    }

    /**
     * 检查任务是否已完成
     */
    fun isCompleted(): Boolean = status in setOf(TaskStatus.SUCCESS, TaskStatus.FAILED, TaskStatus.CANCELLED)

    /**
     * 检查任务是否正在执行
     */
    fun isRunning(): Boolean = status == TaskStatus.RUNNING

    /**
     * 检查任务是否成功
     */
    fun isSuccessful(): Boolean = status == TaskStatus.SUCCESS

    /**
     * 检查任务是否失败
     */
    fun isFailed(): Boolean = status == TaskStatus.FAILED
}

/**
 * 支持的编程语言枚举
 */
enum class Language(val displayName: String, val extensions: List<String>, val defaultCompiler: String) {
    JAVA("Java", listOf("java", "kt"), "javac"),
    JAVASCRIPT("JavaScript", listOf("js", "ts", "jsx", "tsx"), "node"),
    PYTHON("Python", listOf("py", "py3"), "python3"),
    CPP("C++", listOf("cpp", "cc", "cxx", "h", "hpp"), "g++"),
    C("C", listOf("c", "h"), "gcc"),
    RUST("Rust", listOf("rs"), "rustc"),
    GO("Go", listOf("go"), "go")
}

/**
 * 任务优先级枚举
 */
enum class TaskPriority {
    LOW, NORMAL, HIGH, CRITICAL
}

/**
 * 任务状态枚举
 */
enum class TaskStatus {
    PENDING,    // 等待执行
    RUNNING,    // 正在执行
    SUCCESS,    // 执行成功
    FAILED,     // 执行失败
    CANCELLED   // 已取消
}

/**
 * 编译器配置
 */
data class CompilerConfig(
    val compilerCommand: String,
    val compilerArgs: List<String>,
    val outputPath: String? = null,
    val includePaths: List<String> = emptyList(),
    val libraryPaths: List<String> = emptyList(),
    val defines: Map<String, String> = emptyMap(),
    val optimizationLevel: OptimizationLevel = OptimizationLevel.NONE,
    val debugSymbols: Boolean = false,
    val warningsEnabled: Boolean = true,
    val warningsAsErrors: Boolean = false
)

/**
 * 优化级别枚举
 */
enum class OptimizationLevel {
    NONE, LESS, MORE, AGGRESSIVE
}