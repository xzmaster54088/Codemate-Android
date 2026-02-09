package com.codemate.features.github.domain.model

import java.util.Date

/**
 * Git提交实体模型
 * 支持智能分类和变更跟踪
 */
data class GitCommit(
    val sha: String,
    val message: String,
    val author: CommitAuthor,
    val committer: CommitAuthor,
    val parents: List<String>,
    val tree: String,
    val url: String,
    val stats: CommitStats?,
    val files: List<CommitFile>?,
    val timestamp: Date,
    val branch: String,
    val repository: String
)

/**
 * 提交作者信息
 */
data class CommitAuthor(
    val name: String,
    val email: String,
    val date: Date
)

/**
 * 提交统计信息
 */
data class CommitStats(
    val additions: Int,
    val deletions: Int,
    val total: Int
)

/**
 * 提交文件变更信息
 */
data class CommitFile(
    val filename: String,
    val status: FileStatus,
    val additions: Int,
    val deletions: Int,
    val changes: Int,
    val patch: String?
)

/**
 * 文件状态枚举
 */
enum class FileStatus {
    ADDED,
    MODIFIED,
    REMOVED,
    RENAMED,
    COPIED,
    UNCHANGED
}

/**
 * 智能提交分类
 */
data class CommitClassification(
    val type: CommitType,
    val scope: String?,
    val description: String,
    val breaking: Boolean,
    val confidence: Float,
    val features: Map<String, Float>
)

/**
 * 提交类型枚举
 */
enum class CommitType(val label: String, val emoji: String, val color: String) {
    FEATURE("Feature", "✨", "#10B981"),
    FIX("Fix", "🐛", "#EF4444"),
    DOCS("Documentation", "📚", "#3B82F6"),
    STYLE("Style", "🎨", "#F59E0B"),
    REFACTOR("Refactor", "♻️", "#6B7280"),
    PERF("Performance", "⚡", "#F97316"),
    TEST("Test", "✅", "#22C55E"),
    CHORE("Chore", "🔧", "#64748B"),
    BUILD("Build", "🏗️", "#DC2626"),
    CI("CI", "👷", "#7C3AED"),
    REVERT("Revert", "⏪", "#EF4444"),
    MERGE("Merge", "🔀", "#8B5CF6")
}

/**
 * 提交消息解析器
 */
data class CommitMessage(
    val type: CommitType,
    val scope: String?,
    val subject: String,
    val body: String?,
    val footer: String?,
    val breaking: Boolean,
    val rawMessage: String
)

/**
 * 提交过滤器
 */
data class CommitFilter(
    val author: String? = null,
    val since: Date? = null,
    val until: Date? = null,
    val path: String? = null,
    val maxCount: Int = 50,
    val skip: Int = 0
)