package com.codemate.features.github.domain.usecase

import com.codemate.features.github.domain.model.*
import com.codemate.features.github.domain.repository.ClassificationRepository
import com.codemate.features.github.domain.repository.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 智能提交分类用例
 * 使用机器学习算法对commit消息进行自动分类
 */
class CommitClassificationUseCase(
    private val classificationRepository: ClassificationRepository,
    private val gitRepository: GitRepository
) {
    
    /**
     * 分类单个提交消息
     */
    suspend fun classifyCommitMessage(message: String): Result<CommitClassification> {
        return try {
            val result = classificationRepository.classifyCommit(message)
            if (result.isSuccess) {
                Result.success(result.getOrNull()!!)
            } else {
                // 降级到基于规则的分析
                val ruleBasedClassification = classifyWithRules(message)
                Result.success(ruleBasedClassification)
            }
        } catch (e: Exception) {
            // 降级到基于规则的分析
            val ruleBasedClassification = classifyWithRules(message)
            Result.success(ruleBasedClassification)
        }
    }
    
    /**
     * 批量分类提交消息
     */
    suspend fun batchClassifyCommits(messages: List<String>): Result<List<CommitClassification>> {
        return try {
            val result = classificationRepository.classifyCommits(messages)
            if (result.isSuccess) {
                Result.success(result.getOrNull()!!)
            } else {
                // 降级到基于规则的分析
                val classifications = messages.map { classifyWithRules(it) }
                Result.success(classifications)
            }
        } catch (e: Exception) {
            // 降级到基于规则的分析
            val classifications = messages.map { classifyWithRules(it) }
            Result.success(classifications)
        }
    }
    
    /**
     * 分析仓库中的提交历史
     */
    suspend fun analyzeRepositoryCommits(
        repositoryPath: String,
        branch: String? = null,
        since: Date? = null,
        until: Date? = null
    ): Result<CommitAnalysisResult> {
        return try {
            // 获取提交历史
            val commitsResult = gitRepository.getCommitHistory(repositoryPath, branch, since, until)
            if (commitsResult.isFailure) {
                return Result.failure(commitsResult.exceptionOrNull()!!)
            }
            
            val commits = commitsResult.getOrNull()!!
            val messages = commits.map { it.message }
            
            // 批量分类
            val classificationsResult = batchClassifyCommits(messages)
            if (classificationsResult.isFailure) {
                return Result.failure(classificationsResult.exceptionOrNull()!!)
            }
            
            val classifications = classificationsResult.getOrNull()!!
            
            // 统计信息
            val statistics = generateStatistics(commits, classifications)
            
            Result.success(
                CommitAnalysisResult(
                    repositoryPath = repositoryPath,
                    branch = branch,
                    totalCommits = commits.size,
                    classifications = classifications,
                    statistics = statistics,
                    timeRange = TimeRange(since, until)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 识别重大变更
     */
    suspend fun identifyBreakingChanges(
        repositoryPath: String,
        fromVersion: Version,
        toVersion: Version
    ): Result<BreakingChangesAnalysis> {
        return try {
            // 获取版本间的提交
            val commitsResult = gitRepository.getCommitHistory(
                repositoryPath = repositoryPath,
                since = Date(), // 这里需要根据实际版本信息调整
                until = Date()
            )
            
            if (commitsResult.isFailure) {
                return Result.failure(commitsResult.exceptionOrNull()!!)
            }
            
            val commits = commitsResult.getOrNull()!!
            val breakingCommits = commits.filter { commit ->
                commit.message.contains("BREAKING CHANGE", ignoreCase = true) ||
                commit.message.contains("breaking change", ignoreCase = true) ||
                commit.message.contains("!")
            }
            
            // 分析这些提交的详细信息
            val breakingChanges = breakingCommits.map { commit ->
                val classification = classifyCommitMessage(commit.message).getOrNull()
                BreakingChange(
                    commit = commit,
                    classification = classification,
                    impact = assessImpact(commit),
                    affectedFiles = commit.files?.map { it.filename } ?: emptyList()
                )
            }
            
            Result.success(
                BreakingChangesAnalysis(
                    fromVersion = fromVersion,
                    toVersion = toVersion,
                    breakingChanges = breakingChanges,
                    totalBreakingChanges = breakingChanges.size,
                    severity = calculateSeverity(breakingChanges)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 生成语义化版本号
     */
    suspend fun generateSemanticVersion(
        repositoryPath: String,
        currentVersion: Version? = null
    ): Result<VersionSuggestion> {
        return try {
            // 获取最新的提交历史
            val commitsResult = gitRepository.getCommitHistory(
                repositoryPath = repositoryPath,
                maxCount = 50
            )
            
            if (commitsResult.isFailure) {
                return Result.failure(commitsResult.exceptionOrNull()!!)
            }
            
            val commits = commitsResult.getOrNull()!!
            val messages = commits.map { it.message }
            
            // 分类提交
            val classificationsResult = batchClassifyCommits(messages)
            if (classificationsResult.isFailure) {
                return Result.failure(classificationsResult.exceptionOrNull()!!)
            }
            
            val classifications = classificationsResult.getOrNull()!!
            
            // 分析变更类型
            val hasBreakingChanges = classifications.any { it.breaking }
            val hasFeatures = classifications.any { it.type == CommitType.FEATURE }
            val hasFixes = classifications.any { it.type == CommitType.FIX }
            val hasRefactoring = classifications.any { it.type == CommitType.REFACTOR }
            
            // 确定版本类型
            val nextVersion = when {
                hasBreakingChanges -> currentVersion?.next(ReleaseType.MAJOR) 
                    ?: Version(1, 0, 0, null, null, null)
                hasFeatures -> currentVersion?.next(ReleaseType.MINOR)
                    ?: Version(0, 1, 0, null, null, null)
                hasFixes -> currentVersion?.next(ReleaseType.PATCH)
                    ?: Version(0, 0, 1, null, null, null)
                hasRefactoring -> currentVersion?.next(ReleaseType.PATCH)
                    ?: Version(0, 0, 1, null, null, null)
                else -> currentVersion?.next(ReleaseType.PATCH)
                    ?: Version(0, 0, 1, null, null, null)
            }
            
            // 生成建议
            val suggestions = mutableListOf<String>()
            if (hasBreakingChanges) {
                suggestions.add("Contains breaking changes - increment major version")
            }
            if (hasFeatures) {
                suggestions.add("Contains new features - increment minor version")
            }
            if (hasFixes) {
                suggestions.add("Contains bug fixes - increment patch version")
            }
            
            Result.success(
                VersionSuggestion(
                    suggestedVersion = nextVersion,
                    reason = suggestions.joinToString("; "),
                    currentVersion = currentVersion,
                    changes = ChangeSummary(
                        breakingChanges = classifications.count { it.breaking },
                        features = classifications.count { it.type == CommitType.FEATURE },
                        fixes = classifications.count { it.type == CommitType.FIX },
                        refactoring = classifications.count { it.type == CommitType.REFACTOR },
                        documentation = classifications.count { it.type == CommitType.DOCS }
                    )
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 基于规则的分析（降级方案）
     */
    private fun classifyWithRules(message: String): CommitClassification {
        val lowerMessage = message.lowercase()
        
        // 预定义规则
        val rules = mapOf(
            CommitType.FEATURE to listOf(
                "feat", "feature", "add", "implement", "new", "introduce", "create"
            ),
            CommitType.FIX to listOf(
                "fix", "bug", "hotfix", "patch", "resolve", "repair", "correct"
            ),
            CommitType.DOCS to listOf(
                "docs", "documentation", "doc", "readme", "comment", "guide"
            ),
            CommitType.STYLE to listOf(
                "style", "format", "beautify", "clean", "lint", "css", "style"
            ),
            CommitType.REFACTOR to listOf(
                "refactor", "restructure", "reorganize", "improve", "optimize"
            ),
            CommitType.PERF to listOf(
                "perf", "performance", "optimize", "speed", "faster", "efficiency"
            ),
            CommitType.TEST to listOf(
                "test", "tests", "testing", "spec", "coverage", "verify"
            ),
            CommitType.CHORE to listOf(
                "chore", "maintenance", "update", "upgrade", "dependency", "build"
            ),
            CommitType.BUILD to listOf(
                "build", "compile", "bundle", "deploy", "package", "release"
            ),
            CommitType.CI to listOf(
                "ci", "continuous", "integration", "pipeline", "workflow", "actions"
            )
        )
        
        // 检查是否包含重大变更标记
        val hasBreakingChange = lowerMessage.contains("breaking") || lowerMessage.contains("!")
        
        // 查找匹配的规则
        for ((type, keywords) in rules) {
            if (keywords.any { lowerMessage.startsWith(it) || lowerMessage.contains(it) }) {
                return CommitClassification(
                    type = type,
                    scope = extractScope(message),
                    description = cleanDescription(message),
                    breaking = hasBreakingChange,
                    confidence = 0.8f, // 基于规则的置信度
                    features = mapOf("rule_based" to 1.0f)
                )
            }
        }
        
        // 默认分类为CHORE
        return CommitClassification(
            type = CommitType.CHORE,
            scope = extractScope(message),
            description = cleanDescription(message),
            breaking = false,
            confidence = 0.3f,
            features = mapOf("default" to 1.0f)
        )
    }
    
    /**
     * 提取范围信息
     */
    private fun extractScope(message: String): String? {
        val match = Regex("\\(([^)]+)\\)").find(message)
        return match?.groupValues?.get(1)
    }
    
    /**
     * 清理描述信息
     */
    private fun cleanDescription(message: String): String {
        return message
            .replace(Regex("^[^:]+:\\s*"), "") // 移除类型前缀
            .replace(Regex("\\([^)]+\\)"), "") // 移除范围信息
            .trim()
    }
    
    /**
     * 生成统计信息
     */
    private fun generateStatistics(
        commits: List<GitCommit>,
        classifications: List<CommitClassification>
    ): CommitStatistics {
        val typeCounts = classifications.groupingBy { it.type }.eachCount()
        val breakingChanges = classifications.count { it.breaking }
        val totalLines = commits.sumOf { it.stats?.total ?: 0 }
        val avgConfidence = classifications.map { it.confidence }.average().toFloat()
        
        return CommitStatistics(
            totalCommits = commits.size,
            typeDistribution = typeCounts,
            breakingChanges = breakingChanges,
            totalLinesChanged = totalLines,
            averageConfidence = avgConfidence
        )
    }
    
    /**
     * 评估变更影响
     */
    private fun assessImpact(commit: GitCommit): ImpactLevel {
        val fileCount = commit.files?.size ?: 0
        val lineCount = commit.stats?.total ?: 0
        
        return when {
            commit.parents.size > 1 -> ImpactLevel.HIGH // 合并提交
            lineCount > 100 -> ImpactLevel.HIGH
            fileCount > 10 -> ImpactLevel.MEDIUM
            lineCount > 50 -> ImpactLevel.MEDIUM
            else -> ImpactLevel.LOW
        }
    }
    
    /**
     * 计算严重程度
     */
    private fun calculateSeverity(breakingChanges: List<BreakingChange>): SuggestionSeverity {
        return when {
            breakingChanges.size > 5 -> SuggestionSeverity.CRITICAL
            breakingChanges.size > 2 -> SuggestionSeverity.HIGH
            breakingChanges.isNotEmpty() -> SuggestionSeverity.MEDIUM
            else -> SuggestionSeverity.INFO
        }
    }
}

/**
 * 提交分析结果
 */
data class CommitAnalysisResult(
    val repositoryPath: String,
    val branch: String?,
    val totalCommits: Int,
    val classifications: List<CommitClassification>,
    val statistics: CommitStatistics,
    val timeRange: TimeRange
)

/**
 * 提交统计信息
 */
data class CommitStatistics(
    val totalCommits: Int,
    val typeDistribution: Map<CommitType, Int>,
    val breakingChanges: Int,
    val totalLinesChanged: Int,
    val averageConfidence: Float
)

/**
 * 时间范围
 */
data class TimeRange(
    val start: Date?,
    val end: Date?
)

/**
 * 重大变更分析
 */
data class BreakingChangesAnalysis(
    val fromVersion: Version,
    val toVersion: Version,
    val breakingChanges: List<BreakingChange>,
    val totalBreakingChanges: Int,
    val severity: SuggestionSeverity
)

/**
 * 重大变更
 */
data class BreakingChange(
    val commit: GitCommit,
    val classification: CommitClassification?,
    val impact: ImpactLevel,
    val affectedFiles: List<String>
)

/**
 * 版本建议
 */
data class VersionSuggestion(
    val suggestedVersion: Version,
    val reason: String,
    val currentVersion: Version?,
    val changes: ChangeSummary
)

/**
 * 变更摘要
 */
data class ChangeSummary(
    val breakingChanges: Int,
    val features: Int,
    val fixes: Int,
    val refactoring: Int,
    val documentation: Int
)