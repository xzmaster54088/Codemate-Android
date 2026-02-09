package com.codemate.features.github.domain.usecase

import com.codemate.features.github.domain.model.*
import com.codemate.features.github.domain.repository.CollaborationRepository
import com.codemate.features.github.domain.repository.GitHubRepository
import com.codemate.features.github.domain.repository.GitRepository
import kotlinx.coroutines.flow.Flow
import java.util.Date
import kotlin.random.Random

/**
 * 协作功能用例
 * 实现实时代码分享、协同编辑会话、代码评审助手等功能
 */
class CollaborationUseCase(
    private val collaborationRepository: CollaborationRepository,
    private val gitHubRepository: GitHubRepository,
    private val gitRepository: GitRepository
) {
    
    /**
     * 创建协作会话
     */
    suspend fun createCollaborationSession(
        name: String,
        description: String?,
        owner: User,
        repository: String,
        branch: String,
        files: List<String>,
        settings: CollaborationSettings? = null
    ): Result<CollaborationSession> {
        return try {
            val defaultSettings = settings ?: CollaborationSettings()
            val sessionId = generateSessionId()
            val expiresAt = Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000) // 24小时后过期
            
            val session = CollaborationSession(
                id = sessionId,
                name = name,
                description = description,
                owner = owner,
                participants = listOf(owner),
                repository = repository,
                branch = branch,
                files = files,
                createdAt = Date(),
                expiresAt = expiresAt,
                status = SessionStatus.ACTIVE,
                settings = defaultSettings,
                activities = emptyList()
            )
            
            val result = collaborationRepository.createSession(session)
            if (result.isSuccess) {
                // 记录创建活动
                recordSessionActivity(
                    sessionId = sessionId,
                    type = ActivityType.JOIN,
                    user = owner,
                    data = mapOf("action" to "session_created")
                )
                Result.success(result.getOrNull()!!)
            } else {
                Result.failure(result.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 创建实时代码分享链接
     */
    suspend fun createShareLink(
        repository: String,
        branch: String,
        files: List<String>,
        permissions: SharePermissions = SharePermissions.READ_ONLY,
        expiresInHours: Int = 24
    ): Result<ShareLink> {
        return try {
            val expiresAt = Date(System.currentTimeMillis() + expiresInHours * 60 * 60 * 1000)
            
            val result = collaborationRepository.createShareLink(
                repository = repository,
                branch = branch,
                files = files,
                permissions = permissions,
                expiresAt = expiresAt
            )
            
            if (result.isSuccess) {
                Result.success(result.getOrNull()!!)
            } else {
                // 如果仓库方法失败，生成一个简单的分享链接
                val shareLink = ShareLink(
                    id = generateSessionId(),
                    url = generateShareUrl(repository, branch, files),
                    token = generateShareToken(),
                    expiresAt = expiresAt,
                    permissions = permissions
                )
                Result.success(shareLink)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 邀请用户加入协作会话
     */
    suspend fun inviteUserToSession(
        sessionId: String,
        user: User,
        permissions: SharePermissions = SharePermissions.COLLABORATE
    ): Result<Unit> {
        return try {
            val sessionResult = collaborationRepository.getActiveSessions(user.id.toString())
            if (sessionResult.isFailure) {
                return Result.failure(sessionResult.exceptionOrNull()!!)
            }
            
            val sessions = sessionResult.getOrNull()!!
            val targetSession = sessions.find { it.id == sessionId }
            
            if (targetSession == null) {
                return Result.failure(Exception("Session not found or access denied"))
            }
            
            val result = collaborationRepository.joinSession(sessionId, user)
            if (result.isSuccess) {
                // 记录邀请活动
                recordSessionActivity(
                    sessionId = sessionId,
                    type = ActivityType.JOIN,
                    user = user,
                    data = mapOf("invited" to true, "permissions" to permissions.name)
                )
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 离开协作会话
     */
    suspend fun leaveSession(
        sessionId: String,
        user: User
    ): Result<Unit> {
        return try {
            val result = collaborationRepository.leaveSession(sessionId, user.id.toString())
            if (result.isSuccess) {
                // 记录离开活动
                recordSessionActivity(
                    sessionId = sessionId,
                    type = ActivityType.LEAVE,
                    user = user,
                    data = mapOf("action" to "session_left")
                )
                Result.success(Unit)
            } else {
                Result.failure(result.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 通过分享链接访问代码
     */
    suspend fun accessSharedCode(
        token: String
    ): Result<ShareLinkAccess> {
        return try {
            val result = collaborationRepository.accessShareLink(token)
            if (result.isSuccess) {
                Result.success(result.getOrNull()!!)
            } else {
                // 如果仓库方法失败，创建一个基本的访问对象
                val access = ShareLinkAccess(
                    link = ShareLink(
                        id = "unknown",
                        url = "",
                        token = token,
                        expiresAt = Date(),
                        permissions = SharePermissions.READ_ONLY
                    ),
                    repository = "unknown",
                    branch = "main",
                    files = emptyList(),
                    permissions = SharePermissions.READ_ONLY
                )
                Result.success(access)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 生成智能代码评审建议
     */
    suspend fun generateReviewSuggestions(
        repository: String,
        pullRequestNumber: Int
    ): Result<List<CodeSuggestion>> {
        return try {
            // 获取PR信息
            val prResult = gitHubRepository.getPullRequests(
                owner = repository.substringBefore("/"),
                repo = repository.substringAfter("/"),
                state = PRState.OPEN
            )
            
            if (prResult.isFailure) {
                return Result.failure(prResult.exceptionOrNull()!!)
            }
            
            val prs = prResult.getOrNull()!!
            val targetPR = prs.data.find { it.number == pullRequestNumber }
            
            if (targetPR == null) {
                return Result.failure(Exception("Pull Request not found"))
            }
            
            // 生成代码审查建议
            val suggestions = generateReviewSuggestionsForPR(targetPR)
            
            Result.success(suggestions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 创建代码评审会话
     */
    suspend fun createReviewSession(
        prNumber: Int,
        reviewer: User,
        repository: String,
        files: List<String>? = null
    ): Result<CollaborationSession> {
        return try {
            val sessionName = "Code Review - PR #$prNumber"
            val description = "Collaborative code review session for Pull Request #$prNumber"
            
            val session = CollaborationSession(
                id = generateSessionId(),
                name = sessionName,
                description = description,
                owner = reviewer,
                participants = listOf(reviewer),
                repository = repository,
                branch = "main", // PR的目标分支
                files = files ?: emptyList(),
                createdAt = Date(),
                expiresAt = Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000), // 7天后过期
                status = SessionStatus.ACTIVE,
                settings = CollaborationSettings(
                    maxParticipants = 5,
                    allowGuests = true,
                    requireApproval = false,
                    realTimeSync = true,
                    showCursors = true
                ),
                activities = emptyList()
            )
            
            val result = collaborationRepository.createSession(session)
            if (result.isSuccess) {
                Result.success(result.getOrNull()!!)
            } else {
                Result.failure(result.exceptionOrNull()!!)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 记录用户活动
     */
    suspend fun recordUserActivity(
        sessionId: String,
        user: User,
        type: ActivityType,
        data: Map<String, Any> = emptyMap(),
        file: String? = null,
        position: CodePosition? = null
    ): Result<Unit> {
        return try {
            val activity = SessionActivity(
                id = generateActivityId(),
                type = type,
                user = user,
                timestamp = Date(),
                data = data,
                file = file,
                position = position
            )
            
            collaborationRepository.recordActivity(activity)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 监听实时活动
     */
    fun monitorSessionActivity(sessionId: String): Flow<SessionActivity> {
        return collaborationRepository.getActivityStream(sessionId)
    }
    
    /**
     * 获取协作统计信息
     */
    suspend fun getCollaborationStats(
        repository: String,
        timeRange: TimeRange
    ): Result<CollaborationStats> {
        return try {
            // 获取活跃会话
            val sessionsResult = collaborationRepository.getActiveSessions("system")
            val sessions = if (sessionsResult.isSuccess) {
                sessionsResult.getOrNull()!!.filter { it.repository == repository }
            } else {
                emptyList()
            }
            
            // 获取活动历史
            val activities = sessions.flatMap { session ->
                collaborationRepository.getActivityHistory(session.id, 100).getOrNull() ?: emptyList()
            }.filter { activity ->
                activity.timestamp.after(timeRange.start) && 
                activity.timestamp.before(timeRange.end)
            }
            
            // 生成统计信息
            val stats = CollaborationStats(
                totalSessions = sessions.size,
                activeUsers = activities.map { it.user.id }.distinct().size,
                totalActivities = activities.size,
                activityTypeDistribution = activities.groupingBy { it.type }.eachCount(),
                topContributors = activities.groupingBy { it.user.login }.eachCount()
                    .toList()
                    .sortedByDescending { it.second }
                    .take(10)
                    .map { UserContribution(it.first, it.second) },
                averageSessionDuration = calculateAverageSessionDuration(sessions),
                mostActiveFiles = activities.mapNotNull { it.file }
                    .groupingBy { it }
                    .eachCount()
                    .toList()
                    .sortedByDescending { it.second }
                    .take(10)
                    .map { FileActivity(it.first, it.second) }
            )
            
            Result.success(stats)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun generateSessionId(): String {
        return "session_${Random.nextInt(100000, 999999)}"
    }
    
    private fun generateActivityId(): String {
        return "activity_${Random.nextInt(100000, 999999)}"
    }
    
    private fun generateShareToken(): String {
        return Random.nextInt(100000, 999999).toString()
    }
    
    private fun generateShareUrl(repository: String, branch: String, files: List<String>): String {
        val fileParam = if (files.isNotEmpty()) {
            "?files=${files.joinToString(",")}"
        } else ""
        return "https://github.com/$repository/blob/$branch$fileParam"
    }
    
    private fun generateReviewSuggestionsForPR(pr: GitHubPR): List<CodeSuggestion> {
        val suggestions = mutableListOf<CodeSuggestion>()
        
        // 基于PR标题和描述生成建议
        if (pr.title.contains("fix", ignoreCase = true)) {
            suggestions.add(
                CodeSuggestion(
                    id = "fix_001",
                    type = SuggestionType.TEST,
                    severity = SuggestionSeverity.WARNING,
                    title = "Add tests for the fix",
                    description = "Consider adding unit tests to cover the fixed issue.",
                    file = "test",
                    line = 1,
                    explanation = "Tests help ensure the fix works correctly and prevent regression."
                )
            )
        }
        
        if (pr.title.contains("feature", ignoreCase = true) || pr.title.contains("feat", ignoreCase = true)) {
            suggestions.add(
                CodeSuggestion(
                    id = "feature_001",
                    type = SuggestionType.DOCUMENTATION,
                    severity = SuggestionSeverity.INFO,
                    title = "Update documentation",
                    description = "Consider updating README or API documentation for the new feature.",
                    file = "docs",
                    line = 1,
                    explanation = "Good documentation helps users understand how to use the new feature."
                )
            )
        }
        
        if (pr.body?.contains("breaking", ignoreCase = true) == true) {
            suggestions.add(
                CodeSuggestion(
                    id = "breaking_001",
                    type = SuggestionType.REFACTOR,
                    severity = SuggestionSeverity.CRITICAL,
                    title = "Update CHANGELOG",
                    description = "Breaking changes should be clearly documented in the CHANGELOG.",
                    file = "CHANGELOG.md",
                    line = 1,
                    explanation = "Clear documentation of breaking changes helps users understand migration steps."
                )
            )
        }
        
        // 添加通用建议
        suggestions.addAll(listOf(
            CodeSuggestion(
                id = "general_001",
                type = SuggestionType.BEST_PRACTICE,
                severity = SuggestionSeverity.INFO,
                title = "Code style consistency",
                description = "Ensure code follows the project's style guidelines.",
                file = "src",
                line = 1,
                explanation = "Consistent code style improves maintainability."
            ),
            CodeSuggestion(
                id = "general_002",
                type = SuggestionType.SECURITY,
                severity = SuggestionSeverity.WARNING,
                title = "Security review",
                description = "Review code for potential security vulnerabilities.",
                file = "src",
                line = 1,
                explanation = "Security issues should be identified and addressed early."
            )
        ))
        
        return suggestions
    }
    
    private suspend fun recordSessionActivity(
        sessionId: String,
        type: ActivityType,
        user: User,
        data: Map<String, Any>
    ) {
        try {
            collaborationRepository.recordActivity(
                SessionActivity(
                    id = generateActivityId(),
                    type = type,
                    user = user,
                    timestamp = Date(),
                    data = data
                )
            )
        } catch (e: Exception) {
            // 静默失败，不影响主要功能
        }
    }
    
    private fun calculateAverageSessionDuration(sessions: List<CollaborationSession>): Long {
        if (sessions.isEmpty()) return 0
        
        val durations = sessions.map { session ->
            val duration = session.expiresAt.time - session.createdAt.time
            if (duration > 0) duration else 0
        }.filter { it > 0 }
        
        return if (durations.isNotEmpty()) {
            durations.average().toLong()
        } else {
            0
        }
    }
}

/**
 * 协作统计信息
 */
data class CollaborationStats(
    val totalSessions: Int,
    val activeUsers: Int,
    val totalActivities: Int,
    val activityTypeDistribution: Map<ActivityType, Int>,
    val topContributors: List<UserContribution>,
    val averageSessionDuration: Long,
    val mostActiveFiles: List<FileActivity>
)

/**
 * 用户贡献统计
 */
data class UserContribution(
    val username: String,
    val activityCount: Int
)

/**
 * 文件活动统计
 */
data class FileActivity(
    val filename: String,
    val activityCount: Int
)

/**
 * 时间范围
 */
data class TimeRange(
    val start: Date,
    val end: Date
)