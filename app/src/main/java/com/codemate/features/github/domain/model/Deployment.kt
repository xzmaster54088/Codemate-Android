package com.codemate.features.github.domain.model

import java.util.Date

/**
 * 部署配置
 */
data class DeploymentConfig(
    val id: String,
    val name: String,
    val environment: String,
    val repository: String,
    val branch: String,
    val workflow: String,
    val triggers: List<DeploymentTrigger>,
    val environmentVariables: Map<String, String>,
    val secrets: List<String>,
    val artifacts: List<ArtifactConfig>,
    val notifications: NotificationConfig,
    val createdAt: Date,
    val updatedAt: Date
)

/**
 * 部署触发器
 */
data class DeploymentTrigger(
    val type: TriggerType,
    val condition: String,
    val parameters: Map<String, Any>
)

/**
 * 触发器类型枚举
 */
enum class TriggerType {
    MANUAL,
    PUSH,
    PULL_REQUEST,
    SCHEDULE,
    WEBHOOK
}

/**
 * 制品配置
 */
data class ArtifactConfig(
    val name: String,
    val type: ArtifactType,
    val path: String,
    val retention: Int // days
)

/**
 * 制品类型枚举
 */
enum class ArtifactType {
    APK,
    IPA,
    JAR,
    WAR,
    ZIP,
    TAR,
    CUSTOM
}

/**
 * 通知配置
 */
data class NotificationConfig(
    val email: List<String>,
    val slack: SlackConfig?,
    val teams: TeamsConfig?,
    val discord: DiscordConfig?
)

/**
 * Slack通知配置
 */
data class SlackConfig(
    val webhook: String,
    val channel: String,
    val username: String = "GitHub Bot"
)

/**
 * Teams通知配置
 */
data class TeamsConfig(
    val webhook: String,
    val channel: String
)

/**
 * Discord通知配置
 */
data class DiscordConfig(
    val webhook: String,
    val channel: String
)

/**
 * 部署执行记录
 */
data class DeploymentExecution(
    val id: String,
    val configId: String,
    val version: String,
    val status: DeploymentExecutionStatus,
    val triggeredBy: User,
    val startTime: Date,
    val endTime: Date?,
    val steps: List<DeploymentStep>,
    val logs: List<DeploymentLog>,
    val artifacts: List<DeploymentArtifact>,
    val url: String?
)

/**
 * 部署执行状态枚举
 */
enum class DeploymentExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILURE,
    CANCELLED,
    TIMEOUT
}

/**
 * 部署步骤
 */
data class DeploymentStep(
    val id: String,
    val name: String,
    val status: DeploymentStepStatus,
    val startTime: Date,
    val endTime: Date?,
    val logs: List<DeploymentLog>,
    val metrics: StepMetrics
)

/**
 * 部署步骤状态枚举
 */
enum class DeploymentStepStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILURE,
    SKIPPED
}

/**
 * 步骤度量指标
 */
data class StepMetrics(
    val duration: Long, // milliseconds
    val memoryUsage: Long, // MB
    val cpuUsage: Float, // percentage
    val networkIO: Long, // bytes
    val diskIO: Long // bytes
)

/**
 * 部署日志
 */
data class DeploymentLog(
    val timestamp: Date,
    val level: LogLevel,
    val message: String,
    val source: String?
)

/**
 * 日志级别枚举
 */
enum class LogLevel(val value: String) {
    DEBUG("debug"),
    INFO("info"),
    WARN("warn"),
    ERROR("error"),
    FATAL("fatal")
}

/**
 * 部署制品
 */
data class DeploymentArtifact(
    val name: String,
    val type: ArtifactType,
    val size: Long,
    val downloadUrl: String,
    val createdAt: Date
)

/**
 * CHANGELOG生成配置
 */
data class ChangelogConfig(
    val repository: String,
    val fromVersion: Version?,
    val toVersion: Version,
    val includeTypes: List<CommitType>,
    val excludeTypes: List<CommitType> = listOf(CommitType.MERGE),
    val groupBy: GroupingStrategy = GroupingStrategy.TYPE,
    val format: ChangelogFormat = ChangelogFormat.MARKDOWN,
    val template: String?,
    val customSections: Map<String, List<String>>,
    val showStats: Boolean = true,
    val showContributors: Boolean = true
)

/**
 * 分组策略枚举
 */
enum class GroupingStrategy {
    TYPE,
    SCOPE,
    DATE,
    NONE
}

/**
 * CHANGELOG格式枚举
 */
enum class ChangelogFormat {
    MARKDOWN,
    HTML,
    JSON,
    XML
}

/**
 * CHANGELOG条目
 */
data class ChangelogEntry(
    val version: Version,
    val date: Date,
    val commits: List<GitCommit>,
    val groupedCommits: Map<String, List<GitCommit>>,
    val summary: ChangelogSummary,
    val contributors: List<User>
)

/**
 * CHANGELOG摘要
 */
data class ChangelogSummary(
    val additions: Int,
    val deletions: Int,
    val filesChanged: Int,
    val commitsCount: Int,
    val breakingChanges: Int,
    val features: Int,
    val fixes: Int
)

/**
 * CHANGELOG模板
 */
data class ChangelogTemplate(
    val name: String,
    val format: ChangelogFormat,
    val content: String,
    val variables: List<String>,
    val styles: Map<String, String>
)

/**
 * 语义化版本规则
 */
data class SemanticVersioningRules(
    val breakingChangePattern: Regex = Regex("(BREAKING CHANGE|breaking change|!):"),
    val featurePattern: Regex = Regex("^(feat|feature)(\\(.+\\))?(!)?:"),
    val fixPattern: Regex = Regex("^(fix|bug)(\\(.+\\))?(!)?:"),
    val docsPattern: Regex = Regex("^(docs|documentation)(\\(.+\\))?(!)?:"),
    val stylePattern: Regex = Regex("^(style)(\\(.+\\))?(!)?:"),
    val refactorPattern: Regex = Regex("^(refactor)(\\(.+\\))?(!)?:"),
    val perfPattern: Regex = Regex("^(perf|performance)(\\(.+\\))?(!)?:"),
    val testPattern: Regex = Regex("^(test|tests|testing)(\\(.+\\))?(!)?:"),
    val chorePattern: Regex = Regex("^(chore|build|ci)(\\(.+\\))?(!)?:"),
    val revertPattern: Regex = Regex("^(revert)(\\(.+\\))?(!)?:")
)

/**
 * 版本发布信息
 */
data class ReleaseInfo(
    val version: Version,
    val tag: String,
    val name: String,
    val body: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val createdAt: Date,
    val publishedAt: Date?,
    val author: User,
    val assets: List<ReleaseAsset>,
    val htmlUrl: String,
    val tarballUrl: String,
    val zipballUrl: String
)

/**
 * 发布制品
 */
data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val downloadCount: Int,
    val createdAt: Date,
    val updatedAt: Date,
    val browserDownloadUrl: String,
    val contentType: String
)

/**
 * GitHub Actions工作流
 */
data class WorkflowInfo(
    val id: Long,
    val name: String,
    val path: String,
    val state: WorkflowState,
    val createdAt: Date,
    val updatedAt: Date,
    val url: String,
    val htmlUrl: String,
    val badgeUrl: String,
    val runs: List<WorkflowRun>
)

/**
 * 工作流状态枚举
 */
enum class WorkflowState(val value: String) {
    ACTIVE("active"),
    DELETED("deleted"),
    DISABLED("disabled"),
    DISABLED_FOREVER("disabled_forever")
}

/**
 * 工作流运行
 */
data class WorkflowRun(
    val id: Long,
    val name: String,
    val status: WorkflowRunStatus,
    val conclusion: WorkflowRunConclusion?,
    val workflowId: Long,
    val checkSuiteId: Long,
    val checkSuiteNodeId: String,
    val headBranch: String,
    val headSha: String,
    val path: String,
    val runNumber: Int,
    val event: WorkflowEvent,
    val displayTitle: String,
    val jobsUrl: String,
    val logsUrl: String,
    val checkUrl: String,
    val createdAt: Date,
    val updatedAt: Date,
    val runStartedAt: Date,
    val runAttempt: Int
)

/**
 * 工作流运行状态枚举
 */
enum class WorkflowRunStatus(val value: String) {
    QUEUED("queued"),
    IN_PROGRESS("in_progress"),
    COMPLETED("completed")
}

/**
 * 工作流运行结论枚举
 */
enum class WorkflowRunConclusion(val value: String) {
    SUCCESS("success"),
    FAILURE("failure"),
    NEUTRAL("neutral"),
    CANCELLED("cancelled"),
    TIMED_OUT("timed_out"),
    ACTION_REQUIRED("action_required")
}

/**
 * 工作流事件枚举
 */
enum class WorkflowEvent(val value: String) {
    CREATE("create"),
    DELETE("delete"),
    FORK("fork"),
    GOLLUM("gollum"),
    ISSUE_COMMENT("issue_comment"),
    ISSUES("issues"),
    MEMBER("member"),
    PUBLIC("public"),
    PULL_REQUEST("pull_request"),
    PULL_REQUEST_REVIEW("pull_request_review"),
    PULL_REQUEST_REVIEW_COMMENT("pull_request_review_comment"),
    PUSH("push"),
    REGISTRY_PACKAGE("registry_package"),
    RELEASE("release"),
    SCHEDULE("schedule"),
    WATCH("watch"),
    WORKFLOW_CALL("workflow_call"),
    WORKFLOW_DISPATCH("workflow_dispatch")
}