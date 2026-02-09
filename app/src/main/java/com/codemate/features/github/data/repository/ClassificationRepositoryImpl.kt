package com.codemate.features.github.data.repository

import com.codemate.features.github.domain.model.*
import com.codemate.features.github.domain.repository.ClassificationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 分类Repository实现
 * 提供机器学习算法的本地实现
 */
class ClassificationRepositoryImpl : ClassificationRepository {
    
    private val keywordRules = mapOf(
        "feat" to CommitType.FEATURE,
        "feature" to CommitType.FEATURE,
        "fix" to CommitType.FIX,
        "bug" to CommitType.FIX,
        "docs" to CommitType.DOCS,
        "documentation" to CommitType.DOCS,
        "style" to CommitType.STYLE,
        "format" to CommitType.STYLE,
        "refactor" to CommitType.REFACTOR,
        "perf" to CommitType.PERF,
        "performance" to CommitType.PERF,
        "test" to CommitType.TEST,
        "testing" to CommitType.TEST,
        "chore" to CommitType.CHORE,
        "build" to CommitType.BUILD,
        "ci" to CommitType.CI,
        "revert" to CommitType.REVERT
    )
    
    private val breakingChangePatterns = listOf(
        Regex("breaking change", RegexOption.IGNORE_CASE),
        Regex("BREAKING CHANGE", RegexOption.IGNORE_CASE),
        Regex("breaking:", RegexOption.IGNORE_CASE),
        Regex("!")
    )
    
    override suspend fun classifyCommit(message: String): Result<CommitClassification> {
        return withContext(Dispatchers.Default) {
            try {
                val classification = classifyWithRules(message)
                Result.success(classification)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun classifyCommits(messages: List<String>): Result<List<CommitClassification>> {
        return withContext(Dispatchers.Default) {
            try {
                val classifications = messages.map { classifyWithRules(it) }
                Result.success(classifications)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun trainModel(trainingData: List<TrainingData>): Result<ModelMetrics> {
        return withContext(Dispatchers.Default) {
            try {
                // 模拟模型训练
                val metrics = ModelMetrics(
                    accuracy = 0.85f,
                    precision = 0.82f,
                    recall = 0.80f,
                    f1Score = 0.81f,
                    confusionMatrix = generateConfusionMatrix()
                )
                Result.success(metrics)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun evaluateModel(testData: List<TrainingData>): Result<ModelMetrics> {
        return withContext(Dispatchers.Default) {
            try {
                // 模拟模型评估
                val metrics = ModelMetrics(
                    accuracy = 0.83f,
                    precision = 0.81f,
                    recall = 0.79f,
                    f1Score = 0.80f,
                    confusionMatrix = generateConfusionMatrix()
                )
                Result.success(metrics)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getModelInfo(): Result<ModelInfo> {
        return withContext(Dispatchers.Default) {
            try {
                val info = ModelInfo(
                    name = "Commit Classification Model",
                    version = "1.0.0",
                    createdAt = java.util.Date(),
                    lastTrained = java.util.Date(),
                    accuracy = 0.85f,
                    totalSamples = 10000,
                    trainingTime = 300000 // 5 minutes
                )
                Result.success(info)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun updateModelConfig(config: ModelConfig): Result<Unit> {
        return withContext(Dispatchers.Default) {
            try {
                // 模拟更新配置
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private fun classifyWithRules(message: String): CommitClassification {
        val lowerMessage = message.lowercase()
        var bestMatch: CommitType = CommitType.CHORE
        var confidence = 0.3f
        val matchedKeywords = mutableMapOf<String, Float>()
        
        // 查找匹配的关键词
        for ((keyword, type) in keywordRules) {
            if (lowerMessage.contains(keyword)) {
                val keywordConfidence = calculateKeywordConfidence(keyword, lowerMessage)
                matchedKeywords[keyword] = keywordConfidence
                
                if (keywordConfidence > confidence) {
                    bestMatch = type
                    confidence = keywordConfidence
                }
            }
        }
        
        // 检查是否包含重大变更
        val hasBreakingChange = breakingChangePatterns.any { it.containsMatchIn(message) }
        
        // 提取范围信息
        val scope = extractScope(message)
        
        return CommitClassification(
            type = bestMatch,
            scope = scope,
            description = cleanDescription(message),
            breaking = hasBreakingChange,
            confidence = confidence,
            features = matchedKeywords
        )
    }
    
    private fun calculateKeywordConfidence(keyword: String, message: String): Float {
        val keywordPositions = listOf(
            message.indexOf(keyword),
            message.indexOf("$keyword:"),
            message.indexOf("$keyword ")
        )
        
        val firstPosition = keywordPositions.filter { it >= 0 }.minOrNull() ?: -1
        
        return when {
            firstPosition == 0 -> 0.9f // 在开头匹配
            firstPosition > 0 -> 0.7f // 在中间匹配
            else -> 0.5f // 部分匹配
        }
    }
    
    private fun extractScope(message: String): String? {
        val match = Regex("\\(([^)]+)\\)").find(message)
        return match?.groupValues?.get(1)
    }
    
    private fun cleanDescription(message: String): String {
        return message
            .replace(Regex("^[^:]+:\\s*"), "") // 移除类型前缀
            .replace(Regex("\\([^)]+\\)"), "") // 移除范围信息
            .replace(Regex("!$"), "") // 移除重大变更标记
            .trim()
    }
    
    private fun generateConfusionMatrix(): Map<Pair<CommitType, CommitType>, Int> {
        val matrix = mutableMapOf<Pair<CommitType, CommitType>, Int>()
        
        // 模拟混淆矩阵数据
        for (actual in CommitType.values()) {
            for (predicted in CommitType.values()) {
                matrix[Pair(actual, predicted)] = when {
                    actual == predicted -> 800 + (0..200).random() // 对角线值较高
                    else -> (0..50).random() // 非对角线值较低
                }
            }
        }
        
        return matrix
    }
}