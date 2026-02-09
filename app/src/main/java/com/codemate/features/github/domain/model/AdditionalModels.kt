package com.codemate.features.github.domain.model

import java.util.Date

/**
 * Git状态信息
 */
data class GitStatus(
    val branch: String,
    val aheadBy: Int,
    val behindBy: Int,
    val stagedFiles: List<String>,
    val modifiedFiles: List<String>,
    val untrackedFiles: List<String>,
    val conflictedFiles: List<String>,
    val clean: Boolean
)

/**
 * 合并结果
 */
data class MergeResult(
    val success: Boolean,
    val commitSha: String?,
    val conflicts: List<String>?,
    val message: String?
)

/**
 * Git差异信息
 */
data class GitDiff(
    val additions: Int,
    val deletions: Int,
    val files: List<GitDiffFile>
)

/**
 * Git差异文件
 */
data class GitDiffFile(
    val filename: String,
    val oldMode: String?,
    val newMode: String?,
    val deletedFileMode: String?,
    val newFileMode: String?,
    val rename: Boolean,
    val copy: Boolean,
    val binary: Boolean,
    val shaBefore: String?,
    val shaAfter: String?,
    val patches: List<GitDiffPatch>
)

/**
 * Git差异补丁
 */
data class GitDiffPatch(
    val oldPath: String,
    val newPath: String,
    val oldMode: String,
    val newMode: String,
    val added: Boolean,
    val deleted: Boolean,
    val typeChanged: Boolean,
    val Hunks: List<Hunk>
)

/**
 * 差异块
 */
data class Hunk(
    val oldStart: Int,
    val oldLines: Int,
    val newStart: Int,
    val newLines: Int,
    val heading: String?,
    val content: String
)

/**
 * 重置模式枚举
 */
enum class ResetMode {
    SOFT,
    MIXED,
    HARD,
    MERGE,
    KEEP
}

/**
 * 仓库类型枚举
 */
enum class RepositoryType {
    ALL,
    OWNER,
    MEMBER,
    PUBLIC,
    PRIVATE,
    FORKS,
    SOURCES
}

/**
 * 仓库排序枚举
 */
enum class RepositorySort {
    CREATED,
    UPDATED,
    PUSHED,
    FULL_NAME
}

/**
 * Issue排序枚举
 */
enum class IssueSort {
    CREATED,
    UPDATED,
    COMMENTS
}

/**
 * PR排序枚举
 */
enum class PRSort {
    CREATED,
    UPDATED,
    POPULARITY
}

/**
 * 排序方向枚举
 */
enum class SortDirection {
    ASC,
    DESC
}

/**
 * 合并方法枚举
 */
enum class MergeMethod {
    MERGE,
    SQUASH,
    REBASE
}

/**
 * 审查事件枚举
 */
enum class ReviewEvent {
    APPROVE,
    REQUEST_CHANGES,
    COMMENT
}

/**
 * 分页响应
 */
data class PaginatedResponse<T>(
    val data: List<T>,
    val pagination: PaginationInfo
)

/**
 * 搜索响应
 */
data class SearchResponse<T>(
    val data: List<T>,
    val totalCount: Int,
    val incompleteResults: Boolean,
    val pagination: PaginationInfo
)

/**
 * Webhook实体
 */
data class Webhook(
    val id: Long,
    val url: String,
    val testUrl: String,
    val pingUrl: String,
    val config: WebhookConfig,
    val events: List<WebhookEvent>,
    val active: Boolean,
    val createdAt: Date,
    val updatedAt: Date
)

/**
 * 训练数据
 */
data class TrainingData(
    val message: String,
    val label: CommitType,
    val metadata: Map<String, Any>? = null
)

/**
 * 模型度量
 */
data class ModelMetrics(
    val accuracy: Float,
    val precision: Float,
    val recall: Float,
    val f1Score: Float,
    val confusionMatrix: Map<Pair<CommitType, CommitType>, Int>
)

/**
 * 模型信息
 */
data class ModelInfo(
    val name: String,
    val version: String,
    val createdAt: Date,
    val lastTrained: Date,
    val accuracy: Float,
    val totalSamples: Int,
    val trainingTime: Long
)

/**
 * 模型配置
 */
data class ModelConfig(
    val algorithm: String,
    val hyperparameters: Map<String, Any>,
    val features: List<String>,
    val preprocessing: PreprocessingConfig
)

/**
 * 预处理配置
 */
data class PreprocessingConfig(
    val normalizeText: Boolean,
    val removeStopWords: Boolean,
    val stemming: Boolean,
    val maxFeatures: Int?
)

/**
 * 会话更新
 */
data class SessionUpdate(
    val name: String? = null,
    val description: String? = null,
    val status: SessionStatus? = null,
    val settings: CollaborationSettings? = null
)

/**
 * 分享链接访问
 */
data class ShareLinkAccess(
    val link: ShareLink,
    val repository: String,
    val branch: String,
    val files: List<String>,
    val permissions: SharePermissions
)

/**
 * 安全扫描结果
 */
data class SecurityScanResult(
    val vulnerabilities: List<Vulnerability>,
    val scanTime: Date,
    val totalIssues: Int,
    val criticalIssues: Int,
    val highIssues: Int,
    val mediumIssues: Int,
    val lowIssues: Int
)

/**
 * 漏洞信息
 */
data class Vulnerability(
    val id: String,
    val severity: SuggestionSeverity,
    val type: String,
    val title: String,
    val description: String,
    val file: String,
    val line: Int,
    val recommendation: String,
    val cve: String? = null,
    val cwe: String? = null
)