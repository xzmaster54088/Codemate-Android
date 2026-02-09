package com.codemate.features.github.domain.model

import java.util.Date

/**
 * Git命令执行结果
 */
data class GitCommandResult(
    val success: Boolean,
    val output: String,
    val error: String?,
    val exitCode: Int,
    val command: String,
    val timestamp: Date = Date()
)

/**
 * Git操作类型枚举
 */
enum class GitOperation {
    INIT,
    CLONE,
    ADD,
    COMMIT,
    PUSH,
    PULL,
    FETCH,
    MERGE,
    REBASE,
    BRANCH,
    CHECKOUT,
    STATUS,
    LOG,
    DIFF,
    RESET,
    REVERT,
    TAG,
    STASH,
    CONFLICT_RESOLUTION
}

/**
 * Git配置信息
 */
data class GitConfig(
    val userName: String,
    val userEmail: String,
    val remoteUrl: String,
    val defaultBranch: String = "main",
    val editor: String? = null
)

/**
 * 冲突信息
 */
data class ConflictInfo(
    val files: List<String>,
    val conflicts: List<FileConflict>,
    val baseCommit: String?,
    val sourceCommit: String?,
    val targetCommit: String?
)

/**
 * 文件冲突信息
 */
data class FileConflict(
    val filename: String,
    val ourVersion: String?,
    val theirVersion: String?,
    val baseVersion: String?,
    val conflictMarkers: List<ConflictMarker>
)

/**
 * 冲突标记信息
 */
data class ConflictMarker(
    val type: MarkerType,
    val content: String,
    val startLine: Int,
    val endLine: Int
)

/**
 * 冲突标记类型
 */
enum class MarkerType {
    START_OURS,
    END_OURS,
    START_THEIRS,
    END_THEIRS,
    BASE
}

/**
 * GitHub API响应基础类
 */
sealed class GitHubResponse<out T> {
    data class Success<T>(val data: T) : GitHubResponse<T>()
    data class Error(val message: String, val statusCode: Int? = null) : GitHubResponse<Nothing>()
    object Loading : GitHubResponse<Nothing>()
}

/**
 * GitHub API分页信息
 */
data class PaginationInfo(
    val page: Int,
    val perPage: Int,
    val total: Int,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrev: Boolean,
    val nextPage: Int?,
    val prevPage: Int?
)

/**
 * GitHub API速率限制信息
 */
data class RateLimit(
    val remaining: Int,
    val limit: Int,
    val reset: Date,
    val used: Int
)

/**
 * GitHub API认证信息
 */
data class AuthInfo(
    val token: String,
    val username: String,
    val scopes: List<String>,
    val tokenType: TokenType
)

/**
 * 令牌类型枚举
 */
enum class TokenType {
    OAUTH,
    PERSONAL_ACCESS_TOKEN,
    APP_TOKEN,
    INSTALLATION_TOKEN
}

/**
 * GitHub API请求配置
 */
data class APIRequestConfig(
    val baseUrl: String = "https://api.github.com",
    val timeout: Long = 30_000,
    val retries: Int = 3,
    val headers: Map<String, String> = emptyMap()
)

/**
 * GitHub搜索查询
 */
data class SearchQuery(
    val query: String,
    val sortBy: SearchSort? = null,
    val order: SearchOrder = SearchOrder.DESC,
    val perPage: Int = 30,
    val page: Int = 1
)

/**
 * 搜索排序方式
 */
enum class SearchSort(val value: String) {
    BEST_MATCH("best-match"),
    STARS("stars"),
    FORKS("forks"),
    HELP_WANTED_ISSUES("help-wanted-issues"),
    UPDATED("updated")
}

/**
 * 搜索排序顺序
 */
enum class SearchOrder(val value: String) {
    ASC("asc"),
    DESC("desc")
}

/**
 * GitHub Webhook配置
 */
data class WebhookConfig(
    val url: String,
    val contentType: ContentType = ContentType.JSON,
    val insecureSSL: Boolean = false,
    val secret: String? = null,
    val events: List<WebhookEvent>,
    val active: Boolean = true
)

/**
 * Webhook内容类型
 */
enum class ContentType(val value: String) {
    JSON("json"),
    FORM("form")
}

/**
 * Webhook事件类型
 */
enum class WebhookEvent(val value: String) {
    PUSH("push"),
    PULL_REQUEST("pull_request"),
    ISSUES("issues"),
    ISSUE_COMMENT("issue_comment"),
    CREATE("create"),
    DELETE("delete"),
    GOLLUM("gollum"),
    MEMBER("member"),
    PUBLIC("public"),
    RELEASE("release"),
    WATCH("watch")
}

/**
 * GitHub状态检查
 */
data class StatusCheck(
    val context: String,
    val state: CheckState,
    val description: String?,
    val targetUrl: String?,
    val createdAt: Date,
    val updatedAt: Date
)

/**
 * 检查状态枚举
 */
enum class CheckState(val value: String) {
    ERROR("error"),
    FAILURE("failure"),
    NEUTRAL("neutral"),
    PENDING("pending"),
    SUCCESS("success")
}

/**
 * GitHub部署状态
 */
data class Deployment(
    val id: Long,
    val ref: String,
    val task: String?,
    val environment: String,
    val description: String?,
    val creator: User,
    val createdAt: Date,
    val updatedAt: Date,
    val statuses: List<DeploymentStatus>
)

/**
 * 部署状态
 */
data class DeploymentStatus(
    val id: Long,
    val state: DeploymentState,
    val description: String?,
    val targetUrl: String?,
    val createdAt: Date,
    val creator: User
)

/**
 * 部署状态枚举
 */
enum class DeploymentState(val value: String) {
    ERROR("error"),
    FAILURE("failure"),
    INACTIVE("in_progress"),
    IN_PROGRESS("in_progress"),
    QUEUED("queued"),
    PENDING("pending"),
    SUCCESS("success")
}