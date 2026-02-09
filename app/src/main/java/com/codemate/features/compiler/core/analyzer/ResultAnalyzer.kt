package com.codemate.features.compiler.core.analyzer

import android.util.Log
import com.codemate.features.compiler.domain.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * 编译结果分析器
 * 负责分析编译产物、依赖关系、性能指标等
 * 提供详细的编译报告和优化建议
 */
class ResultAnalyzer {
    companion object {
        private const val TAG = "ResultAnalyzer"
        
        // 性能基准（不同语言的典型值）
        private val PERFORMANCE_BENCHMARKS = mapOf(
            Language.JAVA to PerformanceBenchmark(
                avgCompilationTime = 2000L, // 2秒
                linesPerSecond = 5000.0,
                memoryUsagePerFile = 1024 * 1024L, // 1MB
                recommendedMaxFileSize = 10000 // 1万行
            ),
            Language.JAVASCRIPT to PerformanceBenchmark(
                avgCompilationTime = 500L, // 0.5秒
                linesPerSecond = 20000.0,
                memoryUsagePerFile = 512 * 1024L, // 512KB
                recommendedMaxFileSize = 50000 // 5万行
            ),
            Language.PYTHON to PerformanceBenchmark(
                avgCompilationTime = 1000L, // 1秒
                linesPerSecond = 10000.0,
                memoryUsagePerFile = 256 * 1024L, // 256KB
                recommendedMaxFileSize = 20000 // 2万行
            ),
            Language.CPP to PerformanceBenchmark(
                avgCompilationTime = 5000L, // 5秒
                linesPerSecond = 1000.0,
                memoryUsagePerFile = 5 * 1024 * 1024L, // 5MB
                recommendedMaxFileSize = 5000 // 5千行
            ),
            Language.C to PerformanceBenchmark(
                avgCompilationTime = 3000L, // 3秒
                linesPerSecond = 2000.0,
                memoryUsagePerFile = 2 * 1024 * 1024L, // 2MB
                recommendedMaxFileSize = 8000 // 8千行
            ),
            Language.RUST to PerformanceBenchmark(
                avgCompilationTime = 8000L, // 8秒
                linesPerSecond = 800.0,
                memoryUsagePerFile = 10 * 1024 * 1024L, // 10MB
                recommendedMaxFileSize = 3000 // 3千行
            ),
            Language.GO to PerformanceBenchmark(
                avgCompilationTime = 2000L, // 2秒
                linesPerSecond = 5000.0,
                memoryUsagePerFile = 3 * 1024 * 1024L, // 3MB
                recommendedMaxFileSize = 10000 // 1万行
            )
        )
        
        // 依赖关系模式
        private val DEPENDENCY_PATTERNS = mapOf(
            Language.JAVA to listOf(
                Regex("import\\s+([\\w.]+);"),
                Regex("extends\\s+(\\w+)"),
                Regex("implements\\s+([\\w,\\s]+)")
            ),
            Language.JAVASCRIPT to listOf(
                Regex("require\\(['\"]([^'\"]+)['\"]\\)"),
                Regex("import.*from\\s+['\"]([^'\"]+)['\"]"),
                Regex("import\\s+['\"]([^'\"]+)['\"]")
            ),
            Language.PYTHON to listOf(
                Regex("import\\s+(\\w+)"),
                Regex("from\\s+(\\w+)\\s+import"),
                Regex("\\s+as\\s+\\w+")
            ),
            Language.CPP to listOf(
                Regex("#include\\s+[<\"]([^>\"]+)[\">]"),
                Regex("using\\s+namespace\\s+(\\w+)"),
                Regex("class\\s+\\w+\\s*:\\s*public\\s+(\\w+)")
            ),
            Language.C to listOf(
                Regex("#include\\s+[<\"]([^>\"]+)[\">]")
            ),
            Language.RUST to listOf(
                Regex("use\\s+([^;]+);"),
                Regex("extern\\s+crate\\s+(\\w+)"),
                Regex("pub\\s+mod\\s+(\\w+)")
            ),
            Language.GO to listOf(
                Regex("import\\s+\"([^\"]+)\""),
                Regex("package\\s+(\\w+)")
            )
        )
    }

    private val analysisCache = ConcurrentHashMap<String, AnalysisResult>()

    /**
     * 分析编译结果
     */
    suspend fun analyzeResult(
        result: CompileResult,
        task: CompileTask,
        sourceFiles: List<String>
    ): AnalysisResult = withContext(Dispatchers.IO) {
        try {
            val cacheKey = "${task.id}_${result.executionTime}"
            
            // 检查缓存
            analysisCache[cacheKey]?.let { cached ->
                if (isCacheValid(cached, result)) {
                    return@withContext cached
                }
            }
            
            Log.d(TAG, "Analyzing result for task: ${task.id}")
            
            val analysis = AnalysisResult(
                taskId = task.id,
                timestamp = System.currentTimeMillis(),
                performanceAnalysis = analyzePerformance(result, task),
                dependencyAnalysis = analyzeDependencies(task, sourceFiles),
                codeQuality = analyzeCodeQuality(result, task),
                optimizationSuggestions = generateOptimizationSuggestions(result, task),
                resourceUsage = analyzeResourceUsage(result),
                issues = detectIssues(result, task)
            )
            
            // 缓存结果
            analysisCache[cacheKey] = analysis
            
            Log.d(TAG, "Analysis completed for task: ${task.id}")
            analysis
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze result for task: ${task.id}", e)
            createErrorAnalysis(task.id, e.message ?: "Unknown error")
        }
    }

    /**
     * 生成详细分析报告
     */
    suspend fun generateReport(
        analysis: AnalysisResult,
        task: CompileTask,
        result: CompileResult
    ): AnalysisReport = withContext(Dispatchers.IO) {
        try {
            val report = AnalysisReport(
                taskId = task.id,
                analysis = analysis,
                recommendations = generateRecommendations(analysis, task),
                metrics = calculateDetailedMetrics(result, task),
                trends = analyzeTrends(analysis),
                summary = generateSummary(analysis, task)
            )
            
            report
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate report", e)
            AnalysisReport.empty(task.id)
        }
    }

    /**
     * 性能分析
     */
    private suspend fun analyzePerformance(
        result: CompileResult,
        task: CompileTask
    ): PerformanceAnalysis = withContext(Dispatchers.IO) {
        val benchmark = PERFORMANCE_BENCHMARKS[task.targetLanguage] ?: 
            PerformanceBenchmark(0, 0.0, 0, 0)
        
        val performanceMetrics = result.performanceMetrics
        
        val performanceScore = calculatePerformanceScore(
            performanceMetrics, benchmark, task
        )
        
        val bottlenecks = detectBottlenecks(performanceMetrics, benchmark)
        
        val improvementSuggestions = generatePerformanceImprovements(
            performanceMetrics, benchmark, task
        )
        
        PerformanceAnalysis(
            score = performanceScore,
            bottlenecks = bottlenecks,
            improvementSuggestions = improvementSuggestions,
            compilationSpeed = performanceMetrics.compilationSpeed,
            efficiencyRating = getEfficiencyRating(performanceScore),
            comparisonToBenchmark = compareToBenchmark(performanceMetrics, benchmark)
        )
    }

    /**
     * 依赖关系分析
     */
    private suspend fun analyzeDependencies(
        task: CompileTask,
        sourceFiles: List<String>
    ): DependencyAnalysis = withContext(Dispatchers.IO) {
        val dependencies = mutableMapOf<String, Set<String>>()
        val dependencyGraph = DependencyGraph()
        
        try {
            // 分析每个源文件的依赖关系
            sourceFiles.forEach { filePath ->
                val fileDeps = analyzeFileDependencies(filePath, task.targetLanguage)
                dependencies[filePath] = fileDeps
            }
            
            // 构建依赖图
            val nodes = dependencies.keys.toSet()
            val edges = dependencies.flatMap { (file, deps) ->
                deps.map { dep -> file to dep }
            }.toSet()
            
            val graph = DependencyGraph(nodes, edges)
            
            // 分析循环依赖
            val circularDependencies = detectCircularDependencies(graph)
            
            // 找出关键依赖
            val criticalDependencies = identifyCriticalDependencies(graph)
            
            // 分析依赖复杂度
            val complexity = calculateDependencyComplexity(graph)
            
            DependencyAnalysis(
                dependencyGraph = graph,
                circularDependencies = circularDependencies,
                criticalDependencies = criticalDependencies,
                complexity = complexity,
                dependencyHealth = assessDependencyHealth(graph, circularDependencies)
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to analyze dependencies", e)
            DependencyAnalysis.empty()
        }
    }

    /**
     * 代码质量分析
     */
    private suspend fun analyzeCodeQuality(
        result: CompileResult,
        task: CompileTask
    ): CodeQuality = withContext(Dispatchers.IO) {
        val qualityScore = calculateCodeQualityScore(result, task)
        
        val qualityIssues = detectQualityIssues(result, task)
        
        val qualityTrends = calculateQualityTrends(result, task)
        
        val recommendations = generateQualityRecommendations(qualityIssues, task)
        
        CodeQuality(
            overallScore = qualityScore,
            issues = qualityIssues,
            trends = qualityTrends,
            recommendations = recommendations,
            qualityGrade = getQualityGrade(qualityScore)
        )
    }

    /**
     * 生成优化建议
     */
    private suspend fun generateOptimizationSuggestions(
        result: CompileResult,
        task: CompileTask
    ): List<OptimizationSuggestion> = withContext(Dispatchers.IO) {
        val suggestions = mutableListOf<OptimizationSuggestion>()
        
        try {
            val benchmark = PERFORMANCE_BENCHMARKS[task.targetLanguage] ?: return@withContext emptyList()
            
            val metrics = result.performanceMetrics
            
            // 编译速度优化
            if (metrics.compilationSpeed < benchmark.linesPerSecond * 0.5) {
                suggestions.add(
                    OptimizationSuggestion(
                        type = OptimizationType.PERFORMANCE,
                        priority = OptimizationPriority.HIGH,
                        title = "Improve compilation speed",
                        description = "Compilation is slower than expected. Consider reducing file size or optimizing includes.",
                        impact = ImpactLevel.HIGH,
                        effort = EffortLevel.MEDIUM,
                        details = mapOf(
                            "currentSpeed" to "${metrics.compilationSpeed} lines/sec",
                            "expectedSpeed" to "${benchmark.linesPerSecond} lines/sec"
                        )
                    )
                )
            }
            
            // 内存使用优化
            if (result.peakMemoryUsage > benchmark.memoryUsagePerFile * 2) {
                suggestions.add(
                    OptimizationSuggestion(
                        type = OptimizationType.MEMORY,
                        priority = OptimizationPriority.MEDIUM,
                        title = "Reduce memory usage",
                        description = "Memory usage is higher than expected. Consider optimizing data structures.",
                        impact = ImpactLevel.MEDIUM,
                        effort = EffortLevel.HIGH,
                        details = mapOf(
                            "currentMemory" to "${result.peakMemoryUsage / 1024 / 1024}MB",
                            "expectedMemory" to "${benchmark.memoryUsagePerFile / 1024 / 1024}MB"
                        )
                    )
                )
            }
            
            // 错误率优化
            val errorRate = result.errors.size.toDouble() / max(1, metrics.linesProcessed)
            if (errorRate > 0.1) {
                suggestions.add(
                    OptimizationSuggestion(
                        type = OptimizationType.QUALITY,
                        priority = OptimizationPriority.HIGH,
                        title = "Reduce compilation errors",
                        description = "High error rate detected. Focus on fixing syntax and type errors first.",
                        impact = ImpactLevel.HIGH,
                        effort = EffortLevel.MEDIUM,
                        details = mapOf(
                            "errorRate" to "%.2f%%".format(errorRate * 100),
                            "totalErrors" to result.errors.size.toString()
                        )
                    )
                )
            }
            
            suggestions
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate optimization suggestions", e)
            emptyList()
        }
    }

    /**
     * 资源使用分析
     */
    private suspend fun analyzeResourceUsage(result: CompileResult): ResourceUsage = withContext(Dispatchers.IO) {
        ResourceUsage(
            cpuUsage = estimateCpuUsage(result.executionTime),
            memoryUsage = result.peakMemoryUsage,
            diskUsage = estimateDiskUsage(result),
            networkUsage = 0L, // 编译通常不需要网络
            batteryImpact = estimateBatteryImpact(result),
            performanceRating = getResourcePerformanceRating(result)
        )
    }

    /**
     * 问题检测
     */
    private suspend fun detectIssues(
        result: CompileResult,
        task: CompileTask
    ): List<CodeIssue> = withContext(Dispatchers.IO) {
        val issues = mutableListOf<CodeIssue>()
        
        try {
            // 检测错误相关问题
            result.errors.forEach { error ->
                val issue = when (error.severity) {
                    ErrorSeverity.ERROR -> {
                        when {
                            error.message.contains("undefined", ignoreCase = true) -> {
                                CodeIssue(
                                    type = IssueType.UNDEFINED_SYMBOL,
                                    severity = IssueSeverity.HIGH,
                                    description = "Undefined symbol: ${error.message}",
                                    location = "${error.file}:${error.line}",
                                    suggestions = error.suggestions
                                )
                            }
                            error.message.contains("syntax", ignoreCase = true) -> {
                                CodeIssue(
                                    type = IssueType.SYNTAX_ERROR,
                                    severity = IssueSeverity.HIGH,
                                    description = "Syntax error: ${error.message}",
                                    location = "${error.file}:${error.line}",
                                    suggestions = error.suggestions
                                )
                            }
                            else -> {
                                CodeIssue(
                                    type = IssueType.GENERAL_ERROR,
                                    severity = IssueSeverity.HIGH,
                                    description = "Compilation error: ${error.message}",
                                    location = "${error.file}:${error.line}",
                                    suggestions = error.suggestions
                                )
                            }
                        }
                    }
                    ErrorSeverity.WARNING -> {
                        CodeIssue(
                            type = IssueType.WARNING,
                            severity = IssueSeverity.MEDIUM,
                            description = "Warning: ${error.message}",
                            location = "${error.file}:${error.line}",
                            suggestions = error.suggestions
                        )
                    }
                    else -> null
                }
                
                issue?.let { issues.add(it) }
            }
            
            // 检测性能问题
            val benchmark = PERFORMANCE_BENCHMARKS[task.targetLanguage]
            if (benchmark != null) {
                val metrics = result.performanceMetrics
                if (metrics.compilationSpeed < benchmark.linesPerSecond * 0.3) {
                    issues.add(
                        CodeIssue(
                            type = IssueType.PERFORMANCE,
                            severity = IssueSeverity.MEDIUM,
                            description = "Very slow compilation speed",
                            location = "Project",
                            suggestions = listOf(
                                ErrorSuggestion(
                                    title = "Optimize compilation",
                                    description = "Consider breaking large files or improving includes",
                                    confidence = 0.8
                                )
                            )
                        )
                    )
                }
            }
            
            issues
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to detect issues", e)
            emptyList()
        }
    }

    // 私有方法
    private suspend fun analyzeFileDependencies(filePath: String, language: Language): Set<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists() || !file.canRead()) return@withContext emptySet()
            
            val content = file.readText()
            val patterns = DEPENDENCY_PATTERNS[language] ?: return@withContext emptySet()
            
            val dependencies = mutableSetOf<String>()
            patterns.forEach { pattern ->
                pattern.findAll(content).forEach { match ->
                    match.groupValues.getOrNull(1)?.let { dep ->
                        dependencies.add(dep)
                    }
                }
            }
            
            dependencies
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to analyze dependencies for file: $filePath", e)
            emptySet()
        }
    }

    private fun calculatePerformanceScore(
        metrics: PerformanceMetrics,
        benchmark: PerformanceBenchmark,
        task: CompileTask
    ): Int {
        var score = 100
        
        // 编译速度评分
        val speedRatio = metrics.compilationSpeed / benchmark.linesPerSecond
        score += (speedRatio * 20 - 10).toInt()
        
        // 文件处理效率评分
        val filesPerSecond = metrics.fileCount / (metrics.compilationTime / 1000.0)
        if (filesPerSecond > 10) score += 10
        else if (filesPerSecond < 1) score -= 10
        
        // 模块数量评分（适中最好）
        val modulesRatio = metrics.modulesCount / max(1, metrics.fileCount).toDouble()
        score += when {
            modulesRatio in 0.3..0.7 -> 10
            modulesRatio < 0.1 || modulesRatio > 0.9 -> -10
            else -> 0
        }
        
        return max(0, min(100, score))
    }

    private fun detectBottlenecks(
        metrics: PerformanceMetrics,
        benchmark: PerformanceBenchmark
    ): List<String> {
        val bottlenecks = mutableListOf<String>()
        
        if (metrics.compilationSpeed < benchmark.linesPerSecond * 0.5) {
            bottlenecks.add("Compilation speed")
        }
        
        if (metrics.modulesCount > metrics.fileCount * 2) {
            bottlenecks.add("Too many modules")
        }
        
        if (metrics.cacheHitRate < 0.5) {
            bottlenecks.add("Low cache efficiency")
        }
        
        return bottlenecks
    }

    private fun generatePerformanceImprovements(
        metrics: PerformanceMetrics,
        benchmark: PerformanceBenchmark,
        task: CompileTask
    ): List<String> {
        val improvements = mutableListOf<String>()
        
        if (metrics.compilationSpeed < benchmark.linesPerSecond * 0.7) {
            improvements.add("Enable compiler optimizations")
            improvements.add("Reduce include dependencies")
            improvements.add("Consider parallel compilation")
        }
        
        if (metrics.modulesCount > metrics.fileCount) {
            improvements.add("Reduce module complexity")
            improvements.add("Improve code organization")
        }
        
        return improvements
    }

    private fun getEfficiencyRating(score: Int): EfficiencyRating {
        return when {
            score >= 90 -> EfficiencyRating.EXCELLENT
            score >= 75 -> EfficiencyRating.GOOD
            score >= 60 -> EfficiencyRating.FAIR
            score >= 40 -> EfficiencyRating.POOR
            else -> EfficiencyRating.VERY_POOR
        }
    }

    private fun compareToBenchmark(metrics: PerformanceMetrics, benchmark: PerformanceBenchmark): Map<String, Double> {
        return mapOf(
            "compilationSpeed" to (metrics.compilationSpeed / benchmark.linesPerSecond),
            "memoryEfficiency" to (benchmark.memoryUsagePerFile.toDouble() / max(1, metrics.linesProcessed * 100)),
            "moduleEfficiency" to (benchmark.recommendedMaxFileSize.toDouble() / max(1, metrics.fileCount))
        )
    }

    private fun detectCircularDependencies(graph: DependencyGraph): List<List<String>> {
        // 简化的循环依赖检测
        val visited = mutableSetOf<String>()
        val recStack = mutableSetOf<String>()
        val cycles = mutableListOf<List<String>>()
        
        fun hasCycle(node: String, path: List<String>): Boolean {
            if (recStack.contains(node)) {
                val cycleStart = path.indexOf(node)
                if (cycleStart >= 0) {
                    cycles.add(path.drop(cycleStart) + node)
                }
                return true
            }
            
            if (visited.contains(node)) return false
            
            visited.add(node)
            recStack.add(node)
            
            val neighbors = graph.edges.filter { it.first == node }.map { it.second }
            neighbors.forEach { neighbor ->
                if (hasCycle(neighbor, path + node)) {
                    return true
                }
            }
            
            recStack.remove(node)
            return false
        }
        
        graph.nodes.forEach { node ->
            if (!visited.contains(node)) {
                hasCycle(node, emptyList())
            }
        }
        
        return cycles
    }

    private fun identifyCriticalDependencies(graph: DependencyGraph): List<String> {
        val dependencyCount = mutableMapOf<String, Int>()
        
        graph.edges.forEach { (from, to) ->
            dependencyCount[to] = (dependencyCount[to] ?: 0) + 1
        }
        
        return dependencyCount.entries
            .filter { it.value > graph.nodes.size / 3 } // 超过1/3的项目依赖
            .sortedByDescending { it.value }
            .map { it.key }
    }

    private fun calculateDependencyComplexity(graph: DependencyGraph): DependencyComplexity {
        val density = if (graph.nodes.isNotEmpty()) {
            graph.edges.size.toDouble() / (graph.nodes.size * (graph.nodes.size - 1))
        } else 0.0
        
        return when {
            density < 0.1 -> DependencyComplexity.SIMPLE
            density < 0.3 -> DependencyComplexity.MODERATE
            density < 0.6 -> DependencyComplexity.COMPLEX
            else -> DependencyComplexity.VERY_COMPLEX
        }
    }

    private fun assessDependencyHealth(graph: DependencyGraph, circularDeps: List<List<String>>): DependencyHealth {
        val cycleCount = circularDeps.size
        val nodeCount = graph.nodes.size
        
        return when {
            cycleCount == 0 -> DependencyHealth.EXCELLENT
            cycleCount <= nodeCount * 0.1 -> DependencyHealth.GOOD
            cycleCount <= nodeCount * 0.3 -> DependencyHealth.FAIR
            cycleCount <= nodeCount * 0.5 -> DependencyHealth.POOR
            else -> DependencyHealth.VERY_POOR
        }
    }

    private fun calculateCodeQualityScore(result: CompileResult, task: CompileTask): Int {
        var score = 100
        
        // 错误扣分
        score -= result.errors.size * 5
        
        // 警告扣分
        score -= result.warnings.size * 2
        
        // 成功加分
        if (result.success) score += 10
        
        return max(0, score)
    }

    private fun detectQualityIssues(result: CompileResult, task: CompileTask): List<QualityIssue> {
        val issues = mutableListOf<QualityIssue>()
        
        if (result.errors.isNotEmpty()) {
            issues.add(QualityIssue("Compilation errors detected", IssueSeverity.HIGH))
        }
        
        if (result.warnings.size > 5) {
            issues.add(QualityIssue("Too many warnings", IssueSeverity.MEDIUM))
        }
        
        return issues
    }

    private fun calculateQualityTrends(result: CompileResult, task: CompileTask): QualityTrend {
        return QualityTrend(
            errorTrend = "Stable", // 简化处理
            warningTrend = "Stable",
            overallTrend = if (result.success) "Improving" else "Declining"
        )
    }

    private fun generateQualityRecommendations(issues: List<QualityIssue>, task: CompileTask): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (issues.any { it.severity == IssueSeverity.HIGH }) {
            recommendations.add("Address high-severity issues immediately")
        }
        
        if (issues.size > 10) {
            recommendations.add("Consider code review and refactoring")
        }
        
        return recommendations
    }

    private fun getQualityGrade(score: Int): QualityGrade {
        return when {
            score >= 90 -> QualityGrade.A
            score >= 80 -> QualityGrade.B
            score >= 70 -> QualityGrade.C
            score >= 60 -> QualityGrade.D
            else -> QualityGrade.F
        }
    }

    private fun estimateCpuUsage(executionTime: Long): Double {
        // 简化的CPU使用率估算
        return min(100.0, (executionTime / 1000.0) * 10)
    }

    private fun estimateDiskUsage(result: CompileResult): Long {
        // 估算磁盘使用量（基于输出文件大小）
        return result.outputFiles.sumOf { file ->
            File(file).length()
        }
    }

    private fun estimateBatteryImpact(result: CompileResult): BatteryImpact {
        return when {
            result.executionTime > 30000 -> BatteryImpact.HIGH
            result.executionTime > 10000 -> BatteryImpact.MEDIUM
            else -> BatteryImpact.LOW
        }
    }

    private fun getResourcePerformanceRating(result: CompileResult): PerformanceRating {
        return when {
            result.executionTime < 5000 -> PerformanceRating.EXCELLENT
            result.executionTime < 15000 -> PerformanceRating.GOOD
            result.executionTime < 30000 -> PerformanceRating.FAIR
            result.executionTime < 60000 -> PerformanceRating.POOR
            else -> PerformanceRating.VERY_POOR
        }
    }

    private fun isCacheValid(cached: AnalysisResult, currentResult: CompileResult): Boolean {
        // 检查缓存是否仍然有效
        val cacheAge = System.currentTimeMillis() - cached.timestamp
        return cacheAge < 300000 // 5分钟缓存
    }

    private fun createErrorAnalysis(taskId: String, error: String): AnalysisResult {
        return AnalysisResult(
            taskId = taskId,
            timestamp = System.currentTimeMillis(),
            performanceAnalysis = PerformanceAnalysis.empty(),
            dependencyAnalysis = DependencyAnalysis.empty(),
            codeQuality = CodeQuality.empty(),
            optimizationSuggestions = listOf(
                OptimizationSuggestion(
                    type = OptimizationType.GENERAL,
                    priority = OptimizationPriority.LOW,
                    title = "Analysis failed",
                    description = "Unable to analyze due to error: $error",
                    impact = ImpactLevel.UNKNOWN,
                    effort = EffortLevel.UNKNOWN
                )
            ),
            resourceUsage = ResourceUsage.empty(),
            issues = listOf(
                CodeIssue(
                    type = IssueType.ANALYSIS_ERROR,
                    severity = IssueSeverity.HIGH,
                    description = "Analysis failed: $error",
                    location = "Analysis system",
                    suggestions = emptyList()
                )
            )
        )
    }

    private suspend fun generateRecommendations(
        analysis: AnalysisResult,
        task: CompileTask
    ): List<Recommendation> = withContext(Dispatchers.IO) {
        val recommendations = mutableListOf<Recommendation>()
        
        analysis.optimizationSuggestions.forEach { suggestion ->
            recommendations.add(
                Recommendation(
                    type = RecommendationType.OPTIMIZATION,
                    priority = when (suggestion.priority) {
                        OptimizationPriority.HIGH -> RecommendationPriority.HIGH
                        OptimizationPriority.MEDIUM -> RecommendationPriority.MEDIUM
                        OptimizationPriority.LOW -> RecommendationPriority.LOW
                    },
                    title = suggestion.title,
                    description = suggestion.description,
                    impact = when (suggestion.impact) {
                        ImpactLevel.HIGH -> RecommendationImpact.HIGH
                        ImpactLevel.MEDIUM -> RecommendationImpact.MEDIUM
                        ImpactLevel.LOW -> RecommendationImpact.LOW
                        else -> RecommendationImpact.UNKNOWN
                    },
                    effort = when (suggestion.effort) {
                        EffortLevel.HIGH -> RecommendationEffort.HIGH
                        EffortLevel.MEDIUM -> RecommendationEffort.MEDIUM
                        EffortLevel.LOW -> RecommendationEffort.LOW
                        else -> RecommendationEffort.UNKNOWN
                    }
                )
            )
        }
        
        recommendations
    }

    private suspend fun calculateDetailedMetrics(
        result: CompileResult,
        task: CompileTask
    ): DetailedMetrics = withContext(Dispatchers.IO) {
        DetailedMetrics(
            compilationMetrics = result.performanceMetrics,
            errorMetrics = ErrorMetrics(
                totalErrors = result.errors.size,
                errorTypes = result.errors.groupBy { it.severity },
                filesWithErrors = result.errors.map { it.file }.distinct().size
            ),
            performanceMetrics = PerformanceMetrics(
                executionTime = result.executionTime,
                cpuTime = estimateCpuUsage(result.executionTime),
                memoryTime = result.peakMemoryUsage / 1024 / 1024.0,
                ioTime = estimateDiskUsage(result) / 1024 / 1024.0
            )
        )
    }

    private suspend fun analyzeTrends(analysis: AnalysisResult): AnalysisTrends = withContext(Dispatchers.IO) {
        AnalysisTrends.empty() // 简化实现
    }

    private suspend fun generateSummary(
        analysis: AnalysisResult,
        task: CompileTask
    ): AnalysisSummary = withContext(Dispatchers.IO) {
        AnalysisSummary(
            overallScore = (analysis.performanceAnalysis.score + analysis.codeQuality.overallScore) / 2,
            keyFindings = analysis.issues.map { it.description },
            topRecommendations = analysis.optimizationSuggestions.take(3).map { it.title }
        )
    }
}

// 数据类定义
data class PerformanceBenchmark(
    val avgCompilationTime: Long,
    val linesPerSecond: Double,
    val memoryUsagePerFile: Long,
    val recommendedMaxFileSize: Int
)

data class AnalysisResult(
    val taskId: String,
    val timestamp: Long,
    val performanceAnalysis: PerformanceAnalysis,
    val dependencyAnalysis: DependencyAnalysis,
    val codeQuality: CodeQuality,
    val optimizationSuggestions: List<OptimizationSuggestion>,
    val resourceUsage: ResourceUsage,
    val issues: List<CodeIssue>
)

data class PerformanceAnalysis(
    val score: Int,
    val bottlenecks: List<String>,
    val improvementSuggestions: List<String>,
    val compilationSpeed: Double,
    val efficiencyRating: EfficiencyRating,
    val comparisonToBenchmark: Map<String, Double>
) {
    companion object {
        fun empty() = PerformanceAnalysis(
            score = 0,
            bottlenecks = emptyList(),
            improvementSuggestions = emptyList(),
            compilationSpeed = 0.0,
            efficiencyRating = EfficiencyRating.POOR,
            comparisonToBenchmark = emptyMap()
        )
    }
}

data class DependencyAnalysis(
    val dependencyGraph: DependencyGraph,
    val circularDependencies: List<List<String>>,
    val criticalDependencies: List<String>,
    val complexity: DependencyComplexity,
    val dependencyHealth: DependencyHealth
) {
    companion object {
        fun empty() = DependencyAnalysis(
            dependencyGraph = DependencyGraph(),
            circularDependencies = emptyList(),
            criticalDependencies = emptyList(),
            complexity = DependencyComplexity.SIMPLE,
            dependencyHealth = DependencyHealth.GOOD
        )
    }
}

data class CodeQuality(
    val overallScore: Int,
    val issues: List<QualityIssue>,
    val trends: QualityTrend,
    val recommendations: List<String>,
    val qualityGrade: QualityGrade
) {
    companion object {
        fun empty() = CodeQuality(
            overallScore = 0,
            issues = emptyList(),
            trends = QualityTrend("Stable", "Stable", "Stable"),
            recommendations = emptyList(),
            qualityGrade = QualityGrade.F
        )
    }
}

data class OptimizationSuggestion(
    val type: OptimizationType,
    val priority: OptimizationPriority,
    val title: String,
    val description: String,
    val impact: ImpactLevel,
    val effort: EffortLevel,
    val details: Map<String, String> = emptyMap()
)

data class ResourceUsage(
    val cpuUsage: Double,
    val memoryUsage: Long,
    val diskUsage: Long,
    val networkUsage: Long,
    val batteryImpact: BatteryImpact,
    val performanceRating: PerformanceRating
) {
    companion object {
        fun empty() = ResourceUsage(
            cpuUsage = 0.0,
            memoryUsage = 0L,
            diskUsage = 0L,
            networkUsage = 0L,
            batteryImpact = BatteryImpact.LOW,
            performanceRating = PerformanceRating.POOR
        )
    }
}

data class CodeIssue(
    val type: IssueType,
    val severity: IssueSeverity,
    val description: String,
    val location: String,
    val suggestions: List<ErrorSuggestion>
)

enum class EfficiencyRating {
    EXCELLENT, GOOD, FAIR, POOR, VERY_POOR
}

enum class DependencyComplexity {
    SIMPLE, MODERATE, COMPLEX, VERY_COMPLEX
}

enum class DependencyHealth {
    EXCELLENT, GOOD, FAIR, POOR, VERY_POOR
}

enum class OptimizationType {
    PERFORMANCE, MEMORY, QUALITY, GENERAL
}

enum class OptimizationPriority {
    HIGH, MEDIUM, LOW
}

enum class ImpactLevel {
    HIGH, MEDIUM, LOW, UNKNOWN
}

enum class EffortLevel {
    HIGH, MEDIUM, LOW, UNKNOWN
}

enum class IssueType {
    UNDEFINED_SYMBOL, SYNTAX_ERROR, GENERAL_ERROR, WARNING, PERFORMANCE, ANALYSIS_ERROR
}

enum class IssueSeverity {
    HIGH, MEDIUM, LOW
}

data class QualityIssue(
    val description: String,
    val severity: IssueSeverity
)

data class QualityTrend(
    val errorTrend: String,
    val warningTrend: String,
    val overallTrend: String
)

enum class QualityGrade {
    A, B, C, D, F
}

enum class BatteryImpact {
    HIGH, MEDIUM, LOW
}

enum class PerformanceRating {
    EXCELLENT, GOOD, FAIR, POOR, VERY_POOR
}

data class AnalysisReport(
    val taskId: String,
    val analysis: AnalysisResult,
    val recommendations: List<Recommendation>,
    val metrics: DetailedMetrics,
    val trends: AnalysisTrends,
    val summary: AnalysisSummary
) {
    companion object {
        fun empty(taskId: String) = AnalysisReport(
            taskId = taskId,
            analysis = AnalysisResult(
                taskId = taskId,
                timestamp = System.currentTimeMillis(),
                performanceAnalysis = PerformanceAnalysis.empty(),
                dependencyAnalysis = DependencyAnalysis.empty(),
                codeQuality = CodeQuality.empty(),
                optimizationSuggestions = emptyList(),
                resourceUsage = ResourceUsage.empty(),
                issues = emptyList()
            ),
            recommendations = emptyList(),
            metrics = DetailedMetrics.empty(),
            trends = AnalysisTrends.empty(),
            summary = AnalysisSummary.empty()
        )
    }
}

enum class RecommendationType {
    OPTIMIZATION, QUALITY, DEPENDENCY, GENERAL
}

enum class RecommendationPriority {
    HIGH, MEDIUM, LOW
}

enum class RecommendationImpact {
    HIGH, MEDIUM, LOW, UNKNOWN
}

enum class RecommendationEffort {
    HIGH, MEDIUM, LOW, UNKNOWN
}

data class Recommendation(
    val type: RecommendationType,
    val priority: RecommendationPriority,
    val title: String,
    val description: String,
    val impact: RecommendationImpact,
    val effort: RecommendationEffort
)

data class DetailedMetrics(
    val compilationMetrics: PerformanceMetrics,
    val errorMetrics: ErrorMetrics,
    val performanceMetrics: PerformanceMetrics
) {
    companion object {
        fun empty() = DetailedMetrics(
            compilationMetrics = PerformanceMetrics(),
            errorMetrics = ErrorMetrics(0, emptyMap(), 0),
            performanceMetrics = PerformanceMetrics()
        )
    }
}

data class ErrorMetrics(
    val totalErrors: Int,
    val errorTypes: Map<ErrorSeverity, List<CompileError>>,
    val filesWithErrors: Int
)

data class AnalysisTrends(
    val performanceTrend: String,
    val qualityTrend: String,
    val dependencyTrend: String
) {
    companion object {
        fun empty() = AnalysisTrends("Stable", "Stable", "Stable")
    }
}

data class AnalysisSummary(
    val overallScore: Int,
    val keyFindings: List<String>,
    val topRecommendations: List<String>
) {
    companion object {
        fun empty() = AnalysisSummary(0, emptyList(), emptyList())
    }
}