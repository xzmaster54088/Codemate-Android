package com.codemate.features.compiler.domain.entities

/**
 * 编译错误实体
 */
data class CompileError(
    val line: Int,
    val column: Int,
    val message: String,
    val severity: ErrorSeverity,
    val code: String? = null,
    val file: String = "",
    val suggestions: List<ErrorSuggestion> = emptyList()
)

/**
 * 错误严重程度枚举
 */
enum class ErrorSeverity {
    INFO, WARNING, ERROR, FATAL
}

/**
 * 错误建议修复方案
 */
data class ErrorSuggestion(
    val title: String,
    val description: String,
    val fixCode: String? = null,
    val confidence: Double // 0.0-1.0, 表示建议的置信度
)

/**
 * 编译结果分析
 */
data class CompileResult(
    val taskId: String,
    val success: Boolean,
    val outputFiles: List<String> = emptyList(),
    val warnings: List<CompileError> = emptyList(),
    val errors: List<CompileError> = emptyList(),
    val executionTime: Long,
    val memoryUsage: Long = 0,
    val peakMemoryUsage: Long = 0,
    val performanceMetrics: PerformanceMetrics = PerformanceMetrics(),
    val dependencyGraph: DependencyGraph = DependencyGraph()
)

/**
 * 性能指标
 */
data class PerformanceMetrics(
    val compilationTime: Long = 0,
    val fileCount: Int = 0,
    val linesProcessed: Int = 0,
    val modulesCount: Int = 0,
    val compilationSpeed: Double = 0.0, // lines per second
    val cacheHitRate: Double = 0.0 // 0.0-1.0
)

/**
 * 依赖关系图
 */
data class DependencyGraph(
    val nodes: Set<String> = emptySet(),
    val edges: Set<Pair<String, String>> = emptySet()
)

/**
 * 工具链信息
 */
data class ToolchainInfo(
    val name: String,
    val version: String,
    val path: String,
    val isInstalled: Boolean,
    val supportedLanguages: List<Language>,
    val capabilities: Set<ToolchainCapability>,
    val installationDate: Long? = null,
    val lastUsed: Long? = null
)

/**
 * 工具链能力枚举
 */
enum class ToolchainCapability {
    COMPILATION,
    LINKING,
    DEBUGGING,
    OPTIMIZATION,
    PROFILING,
    STATIC_ANALYSIS,
    UNIT_TESTING,
    CROSS_COMPILATION
}