package com.codemate.features.github.domain.model

import java.util.Date

/**
 * GitHub Issue实体模型
 */
data class GitHubIssue(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String?,
    val state: IssueState,
    val labels: List<Label>,
    val milestone: Milestone?,
    val assignee: User?,
    val assignees: List<User>,
    val comments: Int,
    val createdAt: Date,
    val updatedAt: Date,
    val closedAt: Date?,
    val author: User,
    val commentsList: List<IssueComment>,
    val events: List<IssueEvent>,
    val htmlUrl: String,
    val repository: String,
    val locked: Boolean,
    val activeLockReason: LockReason?
)

/**
 * Issue状态枚举
 */
enum class IssueState(val value: String) {
    OPEN("open"),
    CLOSED("closed")
}

/**
 * 锁定原因枚举
 */
enum class LockReason(val value: String) {
    OFF_TOPIC("off-topic"),
    TOO_HEATED("too heated"),
    SPAM("spam"),
    RESOLVED("resolved")
}

/**
 * Issue评论实体模型
 */
data class IssueComment(
    val id: Long,
    val body: String,
    val user: User,
    val createdAt: Date,
    val updatedAt: Date,
    val htmlUrl: String
)

/**
 * Issue事件实体模型
 */
data class IssueEvent(
    val id: Long,
    val event: IssueEventType,
    val actor: User,
    val createdAt: Date,
    val commitId: String?,
    val commitUrl: String?
)

/**
 * Issue事件类型枚举
 */
enum class IssueEventType {
    CLOSED,
    REOPENED,
    SUBSCRIBED,
    UNSUBSCRIBED,
    LOCKED,
    UNLOCKED,
    MILESTONED,
    DEMILESTONED,
    LABELED,
    UNLABELED,
    ASSIGNED,
    UNASSIGNED,
    MENTIONED,
    REFERENCED,
    CANCELLED,
    MERGED
}

/**
 * Issue过滤器
 */
data class IssueFilter(
    val state: IssueState = IssueState.OPEN,
    val labels: List<String>? = null,
    val milestone: String? = null,
    val assignee: String? = null,
    val creator: String? = null,
    val mentioned: String? = null,
    val sortBy: SortBy = SortBy.CREATED,
    val since: Date? = null,
    val filterBy: FilterBy = FilterBy.ASSIGNED
) {
    enum class SortBy(val value: String) {
        CREATED("created"),
        UPDATED("updated"),
        COMMENTS("comments")
    }
    
    enum class FilterBy(val value: String) {
        ASSIGNED("assigned"),
        CREATED("created"),
        MENTIONED("mentioned"),
        SUBSCRIBED("subscribed"),
        ALL("all")
    }
}

/**
 * GitHub Pull Request实体模型
 */
data class GitHubPR(
    val id: Long,
    val number: Int,
    val title: String,
    val body: String?,
    val state: PRState,
    val base: PRBranch,
    val head: PRBranch,
    val user: User,
    val assignees: List<User>,
    val reviewers: List<User>,
    val requestedReviewers: List<User>,
    val labels: List<Label>,
    val milestone: Milestone?,
    val draft: Boolean,
    val mergeable: Boolean?,
    val mergeableState: MergeableState?,
    val merged: Boolean,
    val mergedAt: Date?,
    val mergedBy: User?,
    val comments: Int,
    val reviewComments: Int,
    val commits: Int,
    val additions: Int,
    val deletions: Int,
    val changedFiles: Int,
    val createdAt: Date,
    val updatedAt: Date,
    val closedAt: Date?,
    val htmlUrl: String,
    val diffUrl: String,
    val patchUrl: String,
    val commitsUrl: String,
    val commentsUrl: String,
    val reviewCommentsUrl: String,
    val reviews: List<PRReview>,
    val repository: String
)

/**
 * PR状态枚举
 */
enum class PRState(val value: String) {
    OPEN("open"),
    CLOSED("closed"),
    MERGED("merged")
}

/**
 * PR分支信息
 */
data class PRBranch(
    val label: String,
    val ref: String,
    val sha: String,
    val user: User,
    val repo: GitRepository
)

/**
 * 合并状态枚举
 */
enum class MergeableState(val value: String) {
    CLEAN("clean"),
    DIRTY("dirty"),
    UNSTABLE("unstable"),
    UNKNOWN("unknown")
}

/**
 * PR审查实体模型
 */
data class PRReview(
    val id: Long,
    val user: User,
    val body: String,
    val state: ReviewState,
    val submittedAt: Date,
    val htmlUrl: String
)

/**
 * 审查状态枚举
 */
enum class ReviewState(val value: String) {
    PENDING("pending"),
    APPROVED("approved"),
    CHANGES_REQUESTED("changes_requested"),
    COMMENTED("commented"),
    DISMISSED("dismissed")
}

/**
 * PR过滤器
 */
data class PRFilter(
    val state: PRState = PRState.OPEN,
    val head: String? = null,
    val base: String? = null,
    val sortBy: SortBy = SortBy.CREATED,
    val direction: SortDirection = SortDirection.DESC
) {
    enum class SortBy(val value: String) {
        CREATED("created"),
        UPDATED("updated")
    }
    
    enum class SortDirection(val value: String) {
        ASC("asc"),
        DESC("desc")
    }
}

/**
 * 标签实体模型
 */
data class Label(
    val id: Long,
    val nodeId: String,
    val name: String,
    val color: String,
    val description: String?,
    val default: Boolean
)

/**
 * 里程碑实体模型
 */
data class Milestone(
    val id: Long,
    val number: Int,
    val title: String,
    val description: String?,
    val state: MilestoneState,
    val openIssues: Int,
    val closedIssues: Int,
    val createdAt: Date,
    val updatedAt: Date,
    val dueOn: Date?,
    val htmlUrl: String
)

/**
 * 里程碑状态枚举
 */
enum class MilestoneState(val value: String) {
    OPEN("open"),
    CLOSED("closed")
}

/**
 * 用户实体模型
 */
data class User(
    val id: Long,
    val login: String,
    val name: String?,
    val email: String?,
    val avatarUrl: String,
    val htmlUrl: String,
    val type: UserType,
    val siteAdmin: Boolean,
    val company: String?,
    val location: String?,
    val publicRepos: Int,
    val publicGists: Int,
    val followers: Int,
    val following: Int,
    val createdAt: Date
)

/**
 * 用户类型枚举
 */
enum class UserType(val value: String) {
    USER("User"),
    ORGANIZATION("Organization")
}