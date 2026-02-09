package com.codemate.features.github.domain.model

import java.util.Date

/**
 * 协作会话实体
 * 支持实时代码分享和协同编辑
 */
data class CollaborationSession(
    val id: String,
    val name: String,
    val description: String?,
    val owner: User,
    val participants: List<User>,
    val repository: String,
    val branch: String,
    val files: List<String>,
    val createdAt: Date,
    val expiresAt: Date,
    val status: SessionStatus,
    val settings: CollaborationSettings,
    val activities: List<SessionActivity>
)

/**
 * 协作会话状态枚举
 */
enum class SessionStatus(val value: String) {
    ACTIVE("active"),
    PAUSED("paused"),
    ENDED("ended"),
    EXPIRED("expired")
}

/**
 * 协作设置
 */
data class CollaborationSettings(
    val maxParticipants: Int = 10,
    val allowGuests: Boolean = false,
    val requireApproval: Boolean = false,
    val autoSave: Boolean = true,
    val realTimeSync: Boolean = true,
    val showCursors: Boolean = true
)

/**
 * 会话活动记录
 */
data class SessionActivity(
    val id: String,
    val type: ActivityType,
    val user: User,
    val timestamp: Date,
    val data: Map<String, Any>,
    val file: String? = null,
    val position: CodePosition? = null
)

/**
 * 活动类型枚举
 */
enum class ActivityType {
    JOIN,
    LEAVE,
    CURSOR_MOVE,
    TEXT_CHANGE,
    FILE_OPEN,
    FILE_CLOSE,
    SELECTION_CHANGE,
    SAVE,
    COMMENT_ADD
}

/**
 * 代码位置信息
 */
data class CodePosition(
    val line: Int,
    val column: Int,
    val file: String
)

/**
 * 分享链接信息
 */
data class ShareLink(
    val id: String,
    val url: String,
    val token: String,
    val expiresAt: Date,
    val permissions: SharePermissions,
    val accessCount: Int = 0,
    val maxAccess: Int? = null
)

/**
 * 分享权限枚举
 */
enum class SharePermissions(val value: String) {
    READ_ONLY("read-only"),
    COMMENT_ONLY("comment-only"),
    COLLABORATE("collaborate"),
    ADMIN("admin")
}

/**
 * 代码审查结果
 */
data class CodeReviewResult(
    val id: String,
    val prNumber: Int,
    val reviewer: User,
    val status: ReviewStatus,
    val overallComment: String?,
    val fileReviews: List<FileReview>,
    val suggestions: List<ReviewSuggestion>,
    val createdAt: Date,
    val submittedAt: Date?
)

/**
 * 审查状态枚举
 */
enum class ReviewStatus {
    PENDING,
    APPROVED,
    CHANGES_REQUESTED,
    COMMENTED
}

/**
 * 文件审查结果
 */
data class FileReview(
    val filename: String,
    val status: ReviewStatus,
    val comments: List<ReviewComment>,
    val suggestions: List<CodeSuggestion>,
    val additions: Int,
    val deletions: Int,
    val changes: Int
)

/**
 * 审查评论
 */
data class ReviewComment(
    val id: Long,
    val path: String,
    val position: Int?,
    val line: Int?,
    val body: String,
    val user: User,
    val createdAt: Date,
    val updatedAt: Date,
    val inReplyTo: Long?
)

/**
 * 代码建议
 */
data class CodeSuggestion(
    val id: String,
    val type: SuggestionType,
    val severity: SuggestionSeverity,
    val title: String,
    val description: String,
    val file: String,
    val line: Int,
    val column: Int?,
    val code: String?,
    val replacement: String?,
    val explanation: String?
)

/**
 * 建议类型枚举
 */
enum class SuggestionType {
    BUG,
    PERFORMANCE,
    SECURITY,
    STYLE,
    BEST_PRACTICE,
    DOCUMENTATION,
    REFACTOR,
    TEST
}

/**
 * 建议严重程度枚举
 */
enum class SuggestionSeverity {
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}

/**
 * 审查建议
 */
data class ReviewSuggestion(
    val title: String,
    val description: String,
    val type: SuggestionType,
    val impact: ImpactLevel,
    val effort: EffortLevel,
    val implementation: String?
)

/**
 * 影响级别枚举
 */
enum class ImpactLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * 实施难度枚举
 */
enum class EffortLevel {
    TRIVIAL,
    SMALL,
    MEDIUM,
    LARGE,
    EPIC
}

/**
 * 代码质量评估
 */
data class CodeQualityAssessment(
    val repository: String,
    val branch: String,
    val score: QualityScore,
    val metrics: CodeMetrics,
    val issues: List<CodeIssue>,
    val suggestions: List<QualitySuggestion>,
    val trends: QualityTrends
)

/**
 * 质量评分
 */
data class QualityScore(
    val overall: Float, // 0-100
    val maintainability: Float,
    val reliability: Float,
    val security: Float,
    val testCoverage: Float,
    val complexity: Float
)

/**
 * 代码度量指标
 */
data class CodeMetrics(
    val linesOfCode: Int,
    val cyclomaticComplexity: Float,
    val technicalDebt: Int, // in hours
    val duplication: Float, // percentage
    val documentation: Float, // percentage
    val testCoverage: Float, // percentage
    val codeSmells: Int,
    val bugs: Int,
    val vulnerabilities: Int
)

/**
 * 代码问题
 */
data class CodeIssue(
    val id: String,
    val type: IssueType,
    val severity: SuggestionSeverity,
    val title: String,
    val description: String,
    val file: String,
    val line: Int,
    val column: Int?,
    val ruleId: String?,
    val url: String?
)

/**
 * 问题类型枚举
 */
enum class IssueType {
    BUG,
    VULNERABILITY,
    CODE_SMELL,
    DUPLICATION,
    COVERAGE,
    COMPLEXITY,
    DOCUMENTATION
}

/**
 * 质量改进建议
 */
data class QualitySuggestion(
    val title: String,
    val description: String,
    val category: SuggestionType,
    val priority: Priority,
    val impact: ImpactLevel,
    val effort: EffortLevel,
    val benefits: List<String>,
    val implementation: String?
)

/**
 * 优先级枚举
 */
enum class Priority {
    LOW,
    MEDIUM,
    HIGH,
    URGENT
}

/**
 * 质量趋势
 */
data class QualityTrends(
    val period: TrendPeriod,
    val changes: Map<String, Float>, // metric -> change percentage
    val overallTrend: TrendDirection,
    val criticalIssues: Int,
    val recommendations: List<String>
)

/**
 * 趋势周期枚举
 */
enum class TrendPeriod {
    DAY,
    WEEK,
    MONTH,
    QUARTER,
    YEAR
}

/**
 * 趋势方向枚举
 */
enum class TrendDirection {
    IMPROVING,
    STABLE,
    DECLINING,
    VOLATILE
}