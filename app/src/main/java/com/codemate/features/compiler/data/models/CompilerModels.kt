package com.codemate.features.compiler.data.models

import com.codemate.features.compiler.domain.entities.*
import kotlinx.serialization.Serializable

/**
 * 数据传输对象 (DTO)
 * 用于API调用和数据序列化
 */

/**
 * 编译任务DTO
 */
@Serializable
data class CompileTaskDto(
    val id: String,
    val projectPath: String,
    val sourceFiles: List<String>,
    val targetLanguage: Language,
    val compilerConfig: CompilerConfigDto,
    val priority: TaskPriority,
    val status: TaskStatus,
    val createdAt: Long,
    val startTime: Long? = null,
    val endTime: Long? = null,
    val output: String = "",
    val errorOutput: String = "",
    val exitCode: Int = 0,
    val dependencies: List<String> = emptyList(),
    val environmentVariables: Map<String, String> = emptyMap(),
    val workingDirectory: String = ""
)

/**
 * 编译器配置DTO
 */
@Serializable
data class CompilerConfigDto(
    val compilerCommand: String,
    val compilerArgs: List<String>,
    val outputPath: String? = null,
    val includePaths: List<String> = emptyList(),
    val libraryPaths: List<String> = emptyList(),
    val defines: Map<String, String> = emptyMap(),
    val optimizationLevel: OptimizationLevel,
    val debugSymbols: Boolean = false,
    val warningsEnabled: Boolean = true,
    val warningsAsErrors: Boolean = false
)

/**
 * 编译结果DTO
 */
@Serializable
data class CompileResultDto(
    val taskId: String,
    val success: Boolean,
    val outputFiles: List<String> = emptyList(),
    val warnings: List<CompileErrorDto> = emptyList(),
    val errors: List<CompileErrorDto> = emptyList(),
    val executionTime: Long,
    val memoryUsage: Long = 0,
    val peakMemoryUsage: Long = 0,
    val performanceMetrics: PerformanceMetricsDto = PerformanceMetricsDto(),
    val dependencyGraph: DependencyGraphDto = DependencyGraphDto()
)

/**
 * 编译错误DTO
 */
@Serializable
data class CompileErrorDto(
    val line: Int,
    val column: Int,
    val message: String,
    val severity: ErrorSeverity,
    val code: String? = null,
    val file: String = "",
    val suggestions: List<ErrorSuggestionDto> = emptyList()
)

/**
 * 错误建议DTO
 */
@Serializable
data class ErrorSuggestionDto(
    val title: String,
    val description: String,
    val fixCode: String? = null,
    val confidence: Double
)

/**
 * 性能指标DTO
 */
@Serializable
data class PerformanceMetricsDto(
    val compilationTime: Long = 0,
    val fileCount: Int = 0,
    val linesProcessed: Int = 0,
    val modulesCount: Int = 0,
    val compilationSpeed: Double = 0.0,
    val cacheHitRate: Double = 0.0
)

/**
 * 依赖图DTO
 */
@Serializable
data class DependencyGraphDto(
    val nodes: Set<String> = emptySet(),
    val edges: Set<Pair<String, String>> = emptySet()
)

/**
 * 编译历史DTO
 */
@Serializable
data class CompileHistoryDto(
    val id: String,
    val task: CompileTaskDto,
    val result: CompileResultDto,
    val timestamp: Long,
    val deviceInfo: DeviceInfoDto,
    val environmentHash: String,
    val fileHashes: Map<String, String>
)

/**
 * 设备信息DTO
 */
@Serializable
data class DeviceInfoDto(
    val androidVersion: String,
    val apiLevel: Int,
    val deviceModel: String,
    val cpuArchitecture: String,
    val availableProcessors: Int,
    val totalMemory: Long,
    val availableMemory: Long
)

/**
 * 工具链信息DTO
 */
@Serializable
data class ToolchainInfoDto(
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
 * 缓存统计DTO
 */
@Serializable
data class CacheStatisticsDto(
    val hitCount: Long = 0,
    val missCount: Long = 0,
    val totalSize: Long = 0,
    val totalEntries: Int = 0,
    val cleanupCount: Long = 0,
    val hitRate: Double = 0.0
)

/**
 * 编译统计DTO
 */
@Serializable
data class CompileStatisticsDto(
    val totalTasks: Long = 0,
    val successfulTasks: Long = 0,
    val failedTasks: Long = 0,
    val averageExecutionTime: Double = 0.0,
    val totalExecutionTime: Long = 0,
    val cacheHitCount: Long = 0,
    val cacheMissCount: Long = 0,
    val languagesUsed: Map<Language, Long> = emptyMap(),
    val errorsByLanguage: Map<Language, Map<String, Long>> = emptyMap(),
    val performanceTrends: List<PerformanceTrendDto> = emptyList(),
    val popularProjects: List<ProjectStatsDto> = emptyList(),
    val memoryUsage: MemoryStatsDto = MemoryStatsDto()
)

/**
 * 性能趋势DTO
 */
@Serializable
data class PerformanceTrendDto(
    val date: Long,
    val averageTime: Long,
    val taskCount: Long,
    val successRate: Double
)

/**
 * 项目统计DTO
 */
@Serializable
data class ProjectStatsDto(
    val projectPath: String,
    val taskCount: Long,
    val averageTime: Long,
    val successRate: Double,
    val lastUsed: Long
)

/**
 * 内存统计DTO
 */
@Serializable
data class MemoryStatsDto(
    val peakMemoryUsage: Long = 0,
    val averageMemoryUsage: Double = 0.0,
    val memoryLeaks: List<MemoryLeakDto> = emptyList()
)

/**
 * 内存泄漏DTO
 */
@Serializable
data class MemoryLeakDto(
    val projectPath: String,
    val detectedAt: Long,
    val leakedMemory: Long,
    val suspectedCause: String
)

/**
 * 实时输出事件DTO
 */
@Serializable
sealed class OutputEventDto {
    data class StandardOutputDto(val content: String) : OutputEventDto()
    data class StandardErrorDto(val content: String) : OutputEventDto()
    data class ProgressDto(val progress: Int, val message: String) : OutputEventDto()
    data class InfoDto(val message: String) : OutputEventDto()
    data class WarningDto(val message: String) : OutputEventDto()
    data class ErrorDto(val message: String) : OutputEventDto()
    object CompletedDto : OutputEventDto()
    data class CancelledDto(val reason: String) : OutputEventDto()
}

/**
 * API响应基类
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val error: String? = null,
    val message: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 编译任务请求DTO
 */
@Serializable
data class CompileTaskRequest(
    val projectPath: String,
    val sourceFiles: List<String>,
    val targetLanguage: Language,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val compilerConfig: CompilerConfigDto? = null
)

/**
 * 批量编译请求DTO
 */
@Serializable
data class BatchCompileRequest(
    val projects: List<CompileTaskRequest>,
    val maxConcurrent: Int = 3
)

/**
 * 批量编译响应DTO
 */
@Serializable
data class BatchCompileResponse(
    val totalProjects: Int,
    val successCount: Int,
    val failureCount: Int,
    val results: Map<String, CompileResultDto>,
    val totalExecutionTime: Long
)

/**
 * 工具链安装请求DTO
 */
@Serializable
data class ToolchainInstallRequest(
    val language: Language
)

/**
 * 工具链安装响应DTO
 */
@Serializable
data class ToolchainInstallResponse(
    val success: Boolean,
    val message: String,
    val progress: Int = 0,
    val toolchain: ToolchainInfoDto? = null
)

/**
 * 统计查询请求DTO
 */
@Serializable
data class StatisticsQueryRequest(
    val type: StatisticsType,
    val projectPath: String? = null,
    val language: Language? = null,
    val days: Int = 30,
    val limit: Int = 50
)

/**
 * 统计查询响应DTO
 */
@Serializable
data class StatisticsQueryResponse(
    val type: StatisticsType,
    val data: String, // JSON序列化后的统计数据
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 数据导出请求DTO
 */
@Serializable
data class DataExportRequest(
    val format: ExportFormat,
    val includeHistory: Boolean = true,
    val includeCache: Boolean = false,
    val includeStatistics: Boolean = true
)

/**
 * 数据导出响应DTO
 */
@Serializable
data class DataExportResponse(
    val success: Boolean,
    val filePath: String? = null,
    val fileSize: Long = 0,
    val recordCount: Int = 0,
    val message: String
)

/**
 * 任务事件DTO
 */
@Serializable
sealed class TaskEventDto {
    data class TaskAddedDto(val task: CompileTaskDto) : TaskEventDto()
    data class TaskStartedDto(val taskId: String) : TaskEventDto()
    data class TaskProgressDto(val taskId: String, val progress: Int, val message: String) : TaskEventDto()
    data class TaskSucceededDto(val taskId: String, val result: CompileResultDto) : TaskEventDto()
    data class TaskFailedDto(val taskId: String, val error: String) : TaskEventDto()
    data class TaskCompletedDto(val task: CompileTaskDto) : TaskEventDto()
    data class TaskCancelledDto(val taskId: String) : TaskEventDto()
    data class TaskErrorDto(val taskId: String, val error: String) : TaskEventDto()
    data class TaskWarningDto(val taskId: String, val warning: String) : TaskEventDto()
    object ManagerPausedDto : TaskEventDto()
    object ManagerResumedDto : TaskEventDto()
}

/**
 * 统计类型枚举
 */
enum class StatisticsType {
    COMPILATION,
    PROJECT,
    LANGUAGE,
    ERROR,
    PERFORMANCE
}

/**
 * 转换函数
 * 将领域实体转换为DTO
 */
fun CompileTask.toDto(): CompileTaskDto = CompileTaskDto(
    id = id,
    projectPath = projectPath,
    sourceFiles = sourceFiles,
    targetLanguage = targetLanguage,
    compilerConfig = compilerConfig.toDto(),
    priority = priority,
    status = status,
    createdAt = createdAt.time,
    startTime = startTime?.time,
    endTime = endTime?.time,
    output = output,
    errorOutput = errorOutput,
    exitCode = exitCode,
    dependencies = dependencies,
    environmentVariables = environmentVariables,
    workingDirectory = workingDirectory
)

/**
 * 将DTO转换为领域实体
 */
fun CompileTaskDto.toDomain(): CompileTask = CompileTask(
    id = id,
    projectPath = projectPath,
    sourceFiles = sourceFiles,
    targetLanguage = targetLanguage,
    compilerConfig = compilerConfig.toDomain(),
    priority = priority,
    status = status,
    createdAt = java.util.Date(createdAt),
    startTime = startTime?.let { java.util.Date(it) },
    endTime = endTime?.let { java.util.Date(it) },
    output = output,
    errorOutput = errorOutput,
    exitCode = exitCode,
    dependencies = dependencies,
    environmentVariables = environmentVariables,
    workingDirectory = workingDirectory
)

/**
 * 编译器配置转换函数
 */
fun CompilerConfig.toDto(): CompilerConfigDto = CompilerConfigDto(
    compilerCommand = compilerCommand,
    compilerArgs = compilerArgs,
    outputPath = outputPath,
    includePaths = includePaths,
    libraryPaths = libraryPaths,
    defines = defines,
    optimizationLevel = optimizationLevel,
    debugSymbols = debugSymbols,
    warningsEnabled = warningsEnabled,
    warningsAsErrors = warningsAsErrors
)

/**
 * DTO转换为编译器配置
 */
fun CompilerConfigDto.toDomain(): CompilerConfig = CompilerConfig(
    compilerCommand = compilerCommand,
    compilerArgs = compilerArgs,
    outputPath = outputPath,
    includePaths = includePaths,
    libraryPaths = libraryPaths,
    defines = defines,
    optimizationLevel = optimizationLevel,
    debugSymbols = debugSymbols,
    warningsEnabled = warningsEnabled,
    warningsAsErrors = warningsAsErrors
)

/**
 * 编译结果转换函数
 */
fun CompileResult.toDto(): CompileResultDto = CompileResultDto(
    taskId = taskId,
    success = success,
    outputFiles = outputFiles,
    warnings = warnings.map { it.toDto() },
    errors = errors.map { it.toDto() },
    executionTime = executionTime,
    memoryUsage = memoryUsage,
    peakMemoryUsage = peakMemoryUsage,
    performanceMetrics = performanceMetrics.toDto(),
    dependencyGraph = dependencyGraph.toDto()
)

/**
 * 编译错误转换函数
 */
fun CompileError.toDto(): CompileErrorDto = CompileErrorDto(
    line = line,
    column = column,
    message = message,
    severity = severity,
    code = code,
    file = file,
    suggestions = suggestions.map { it.toDto() }
)

/**
 * 错误建议转换函数
 */
fun ErrorSuggestion.toDto(): ErrorSuggestionDto = ErrorSuggestionDto(
    title = title,
    description = description,
    fixCode = fixCode,
    confidence = confidence
)

/**
 * 性能指标转换函数
 */
fun PerformanceMetrics.toDto(): PerformanceMetricsDto = PerformanceMetricsDto(
    compilationTime = compilationTime,
    fileCount = fileCount,
    linesProcessed = linesProcessed,
    modulesCount = modulesCount,
    compilationSpeed = compilationSpeed,
    cacheHitRate = cacheHitRate
)

/**
 * 依赖图转换函数
 */
fun DependencyGraph.toDto(): DependencyGraphDto = DependencyGraphDto(
    nodes = nodes,
    edges = edges
)

/**
 * 设备信息转换函数
 */
fun DeviceInfo.toDto(): DeviceInfoDto = DeviceInfoDto(
    androidVersion = androidVersion,
    apiLevel = apiLevel,
    deviceModel = deviceModel,
    cpuArchitecture = cpuArchitecture,
    availableProcessors = availableProcessors,
    totalMemory = totalMemory,
    availableMemory = availableMemory
)

/**
 * 工具链信息转换函数
 */
fun ToolchainInfo.toDto(): ToolchainInfoDto = ToolchainInfoDto(
    name = name,
    version = version,
    path = path,
    isInstalled = isInstalled,
    supportedLanguages = supportedLanguages,
    capabilities = capabilities,
    installationDate = installationDate,
    lastUsed = lastUsed
)

/**
 * 编译历史转换函数
 */
fun CompileHistory.toDto(): CompileHistoryDto = CompileHistoryDto(
    id = id,
    task = task.toDto(),
    result = result.toDto(),
    timestamp = timestamp.time,
    deviceInfo = deviceInfo.toDto(),
    environmentHash = environmentHash,
    fileHashes = fileHashes
)

/**
 * 性能趋势转换函数
 */
fun PerformanceTrend.toDto(): PerformanceTrendDto = PerformanceTrendDto(
    date = date.time,
    averageTime = averageTime,
    taskCount = taskCount,
    successRate = successRate
)

/**
 * 项目统计转换函数
 */
fun ProjectStats.toDto(): ProjectStatsDto = ProjectStatsDto(
    projectPath = projectPath,
    taskCount = taskCount,
    averageTime = averageTime,
    successRate = successRate,
    lastUsed = lastUsed.time
)

/**
 * 内存统计转换函数
 */
fun MemoryStats.toDto(): MemoryStatsDto = MemoryStatsDto(
    peakMemoryUsage = peakMemoryUsage,
    averageMemoryUsage = averageMemoryUsage,
    memoryLeaks = memoryLeaks.map { it.toDto() }
)

/**
 * 内存泄漏转换函数
 */
fun MemoryLeak.toDto(): MemoryLeakDto = MemoryLeakDto(
    projectPath = projectPath,
    detectedAt = detectedAt.time,
    leakedMemory = leakedMemory,
    suspectedCause = suspectedCause
)