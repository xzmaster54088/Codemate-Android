package com.codemate.features.github.data.repository

import com.codemate.features.github.domain.model.*
import com.codemate.features.github.domain.repository.CollaborationRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 协作Repository实现
 * 提供实时协作和代码分享功能
 */
class CollaborationRepositoryImpl : CollaborationRepository {
    
    private val activeSessions = ConcurrentHashMap<String, CollaborationSession>()
    private val shareLinks = ConcurrentHashMap<String, ShareLink>()
    private val activityStreams = ConcurrentHashMap<String, Channel<SessionActivity>>()
    private val userSessions = ConcurrentHashMap<String, MutableSet<String>>() // userId -> sessionIds
    private val sessionActivities = ConcurrentHashMap<String, ConcurrentLinkedQueue<SessionActivity>>()
    private val mutex = Mutex()
    
    override suspend fun createSession(session: CollaborationSession): Result<CollaborationSession> {
        return try {
            mutex.withLock {
                activeSessions[session.id] = session
                userSessions.computeIfAbsent(session.owner.id.toString()) { mutableSetOf() }
                    .add(session.id)
                
                // 初始化活动流
                if (!activityStreams.containsKey(session.id)) {
                    activityStreams[session.id] = Channel<SessionActivity>(Channel.UNLIMITED)
                }
                
                // 初始化活动历史
                sessionActivities.computeIfAbsent(session.id) { ConcurrentLinkedQueue() }
            }
            
            Result.success(session)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun joinSession(sessionId: String, user: User): Result<CollaborationSession> {
        return try {
            mutex.withLock {
                val session = activeSessions[sessionId] 
                    ?: return@withLock Result.failure(Exception("Session not found"))
                
                // 添加用户到会话
                val updatedParticipants = session.participants.toMutableList()
                if (!updatedParticipants.any { it.id == user.id }) {
                    updatedParticipants.add(user)
                }
                
                val updatedSession = session.copy(participants = updatedParticipants)
                activeSessions[sessionId] = updatedSession
                
                // 更新用户会话映射
                userSessions.computeIfAbsent(user.id.toString()) { mutableSetOf() }
                    .add(sessionId)
                
                // 记录加入活动
                val joinActivity = SessionActivity(
                    id = generateActivityId(),
                    type = ActivityType.JOIN,
                    user = user,
                    timestamp = java.util.Date(),
                    data = mapOf("action" to "joined_session")
                )
                
                recordActivityInternal(sessionId, joinActivity)
            }
            
            Result.success(activeSessions[sessionId]!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun leaveSession(sessionId: String, userId: String): Result<Unit> {
        return try {
            mutex.withLock {
                val session = activeSessions[sessionId]
                    ?: return@withLock Result.failure(Exception("Session not found"))
                
                // 从参与者中移除用户
                val updatedParticipants = session.participants.filter { it.id.toString() != userId }
                val updatedSession = session.copy(participants = updatedParticipants)
                activeSessions[sessionId] = updatedSession
                
                // 从用户会话映射中移除
                userSessions[userId]?.remove(sessionId)
                
                // 如果会话为空且过期，标记为结束
                if (updatedParticipants.isEmpty() || java.util.Date().after(session.expiresAt)) {
                    activeSessions[sessionId] = updatedSession.copy(status = SessionStatus.ENDED)
                }
                
                // 记录离开活动
                val user = session.participants.find { it.id.toString() == userId }
                if (user != null) {
                    val leaveActivity = SessionActivity(
                        id = generateActivityId(),
                        type = ActivityType.LEAVE,
                        user = user,
                        timestamp = java.util.Date(),
                        data = mapOf("action" to "left_session")
                    )
                    
                    recordActivityInternal(sessionId, leaveActivity)
                }
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun updateSession(sessionId: String, updates: SessionUpdate): Result<CollaborationSession> {
        return try {
            mutex.withLock {
                val session = activeSessions[sessionId]
                    ?: return@withLock Result.failure(Exception("Session not found"))
                
                val updatedSession = session.copy(
                    name = updates.name ?: session.name,
                    description = updates.description ?: session.description,
                    status = updates.status ?: session.status,
                    settings = updates.settings ?: session.settings
                )
                
                activeSessions[sessionId] = updatedSession
            }
            
            Result.success(activeSessions[sessionId]!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getActiveSessions(userId: String): Result<List<CollaborationSession>> {
        return try {
            val sessionIds = userSessions[userId] ?: emptySet()
            val sessions = sessionIds.mapNotNull { activeSessions[it] }
                .filter { it.status == SessionStatus.ACTIVE }
            
            Result.success(sessions)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun createShareLink(
        repository: String,
        branch: String,
        files: List<String>,
        permissions: SharePermissions,
        expiresAt: Date
    ): Result<ShareLink> {
        return try {
            val shareLink = ShareLink(
                id = generateShareLinkId(),
                url = generateShareUrl(repository, branch, files),
                token = generateShareToken(),
                expiresAt = expiresAt,
                permissions = permissions
            )
            
            shareLinks[shareLink.token] = shareLink
            Result.success(shareLink)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun accessShareLink(token: String): Result<ShareLinkAccess> {
        return try {
            val shareLink = shareLinks[token]
                ?: return@try Result.failure(Exception("Invalid share link"))
            
            // 检查是否过期
            if (java.util.Date().after(shareLink.expiresAt)) {
                return@try Result.failure(Exception("Share link has expired"))
            }
            
            // 解析URL获取仓库和分支信息
            val urlParts = shareLink.url.split("/")
            val repository = urlParts.getOrNull(4)?.let { owner ->
                urlParts.getOrNull(5)?.let { repo -> "$owner/$repo" }
            } ?: "unknown"
            
            val branch = urlParts.getOrNull(7) ?: "main"
            val files = urlParts.drop(8) // 提取文件路径
            
            val access = ShareLinkAccess(
                link = shareLink,
                repository = repository,
                branch = branch,
                files = files,
                permissions = shareLink.permissions
            )
            
            // 更新访问计数
            shareLinks[token] = shareLink.copy(accessCount = shareLink.accessCount + 1)
            
            Result.success(access)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun recordActivity(activity: SessionActivity): Result<Unit> {
        return try {
            recordActivityInternal("", activity) // 空sessionId表示全局活动
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getActivityHistory(sessionId: String, limit: Int): Result<List<SessionActivity>> {
        return try {
            val activities = sessionActivities[sessionId]
                ?: return@try Result.success(emptyList())
            
            val history = activities.toList()
                .sortedByDescending { it.timestamp }
                .take(limit)
            
            Result.success(history)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override fun getActivityStream(sessionId: String): Flow<SessionActivity> {
        val channel = activityStreams[sessionId] 
            ?: Channel<SessionActivity>(Channel.UNLIMITED).also { 
                activityStreams[sessionId] = it 
            }
        
        return channel.receiveAsFlow()
    }
    
    private fun recordActivityInternal(sessionId: String, activity: SessionActivity) {
        // 记录到会话活动历史
        if (sessionId.isNotEmpty()) {
            sessionActivities.computeIfAbsent(sessionId) { ConcurrentLinkedQueue() }
                .offer(activity)
        }
        
        // 发送到活动流
        activityStreams[sessionId]?.trySend(activity)
    }
    
    private fun generateActivityId(): String {
        return "activity_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }
    
    private fun generateShareLinkId(): String {
        return "share_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}"
    }
    
    private fun generateShareToken(): String {
        return java.util.Base64.getEncoder().encodeToString(
            "token_${System.currentTimeMillis()}_${(Math.random() * 1000).toInt()}".toByteArray()
        ).replace("=", "").replace("/", "").replace("+", "")
    }
    
    private fun generateShareUrl(repository: String, branch: String, files: List<String>): String {
        val fileParam = if (files.isNotEmpty()) {
            "/${files.joinToString(",")}"
        } else ""
        return "https://github.com/$repository/blob/$branch$fileParam"
    }
    
    /**
     * 清理过期的会话和分享链接
     */
    suspend fun cleanupExpiredData() {
        mutex.withLock {
            val now = java.util.Date()
            
            // 清理过期的分享链接
            shareLinks.entries.removeAll { it.value.expiresAt.before(now) }
            
            // 清理过期的会话
            activeSessions.entries.removeAll { it.value.expiresAt.before(now) }
            
            // 关闭空的活动流
            activityStreams.entries.removeAll { (sessionId, _) ->
                !activeSessions.containsKey(sessionId)
            }
        }
    }
    
    /**
     * 获取协作统计信息
     */
    suspend fun getCollaborationStats(): CollaborationStats {
        return mutex.withLock {
            val activeSessionsCount = activeSessions.values.count { it.status == SessionStatus.ACTIVE }
            val totalParticipants = activeSessions.values.flatMap { it.participants }.distinct().size
            val totalActivities = sessionActivities.values.sumOf { it.size }
            
            val activityTypeDistribution = mutableMapOf<ActivityType, Int>()
            sessionActivities.values.forEach { activities ->
                activities.forEach { activity ->
                    activityTypeDistribution[activity.type] = 
                        activityTypeDistribution.getOrDefault(activity.type, 0) + 1
                }
            }
            
            val topContributors = activeSessions.values.flatMap { it.participants }
                .groupingBy { it.login }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
                .take(10)
                .map { UserContribution(it.first, it.second) }
            
            val averageSessionDuration = if (activeSessions.isNotEmpty()) {
                activeSessions.values.map { session ->
                    session.expiresAt.time - session.createdAt.time
                }.average().toLong()
            } else {
                0L
            }
            
            val mostActiveFiles = sessionActivities.values.flatMap { it }
                .mapNotNull { it.file }
                .groupingBy { it }
                .eachCount()
                .toList()
                .sortedByDescending { it.second }
                .take(10)
                .map { FileActivity(it.first, it.second) }
            
            CollaborationStats(
                totalSessions = activeSessionsCount,
                activeUsers = totalParticipants,
                totalActivities = totalActivities,
                activityTypeDistribution = activityTypeDistribution,
                topContributors = topContributors,
                averageSessionDuration = averageSessionDuration,
                mostActiveFiles = mostActiveFiles
            )
        }
    }
}