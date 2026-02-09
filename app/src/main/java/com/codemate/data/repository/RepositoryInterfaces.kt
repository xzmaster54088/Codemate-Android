package com.codemate.data.repository

import com.codemate.data.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * 项目仓库接口
 * 定义项目相关的业务逻辑操作
 */
interface ProjectRepository {
    
    /**
     * 创建新项目
     */
    suspend fun createProject(project: Project): Long
    
    /**
     * 获取所有项目（实时数据）
     */
    fun getAllProjects(): Flow<List<Project>>
    
    /**
     * 获取所有项目（一次性数据）
     */
    suspend fun getAllProjectsOnce(): List<Project>
    
    /**
     * 根据ID获取项目
     */
    suspend fun getProjectById(id: Long): Project?
    
    /**
     * 根据类型获取项目
     */
    fun getProjectsByType(type: ProjectType): Flow<List<Project>>
    
    /**
     * 获取收藏的项目
     */
    fun getFavoriteProjects(): Flow<List<Project>>
    
    /**
     * 根据语言获取项目
     */
    fun getProjectsByLanguage(language: String): Flow<List<Project>>
    
    /**
     * 搜索项目
     */
    fun searchProjects(query: String): Flow<List<Project>>
    
    /**
     * 更新项目
     */
    suspend fun updateProject(project: Project): Boolean
    
    /**
     * 删除项目
     */
    suspend fun deleteProject(project: Project): Boolean
    
    /**
     * 更新项目最后访问时间
     */
    suspend fun updateLastAccessed(id: Long): Boolean
    
    /**
     * 切换项目收藏状态
     */
    suspend fun toggleFavorite(id: Long): Boolean
    
    /**
     * 获取项目统计信息
     */
    suspend fun getProjectStatistics(): ProjectStatistics
}

/**
 * 代码片段仓库接口
 */
interface SnippetRepository {
    
    /**
     * 创建代码片段
     */
    suspend fun createSnippet(snippet: Snippet): Long
    
    /**
     * 获取所有代码片段（实时数据）
     */
    fun getAllSnippets(): Flow<List<Snippet>>
    
    /**
     * 根据项目ID获取代码片段
     */
    fun getSnippetsByProject(projectId: Long): Flow<List<Snippet>>
    
    /**
     * 获取收藏的代码片段
     */
    fun getFavoriteSnippets(projectId: Long? = null): Flow<List<Snippet>>
    
    /**
     * 根据语言获取代码片段
     */
    fun getSnippetsByLanguage(language: String): Flow<List<Snippet>>
    
    /**
     * 搜索代码片段
     */
    fun searchSnippets(query: String, projectId: Long? = null): Flow<List<Snippet>>
    
    /**
     * 根据ID获取代码片段
     */
    suspend fun getSnippetById(id: Long): Snippet?
    
    /**
     * 获取最近的代码片段
     */
    fun getRecentSnippets(limit: Int = 20): Flow<List<Snippet>>
    
    /**
     * 更新代码片段
     */
    suspend fun updateSnippet(snippet: Snippet): Boolean
    
    /**
     * 删除代码片段
     */
    suspend fun deleteSnippet(snippet: Snippet): Boolean
    
    /**
     * 切换代码片段收藏状态
     */
    suspend fun toggleFavorite(id: Long): Boolean
    
    /**
     * 获取代码片段统计信息
     */
    suspend fun getSnippetStatistics(projectId: Long): SnippetStatistics
}

/**
 * 对话仓库接口
 */
interface ConversationRepository {
    
    /**
     * 创建新对话
     */
    suspend fun createConversation(conversation: Conversation): Long
    
    /**
     * 获取所有对话
     */
    fun getAllConversations(): Flow<List<Conversation>>
    
    /**
     * 根据项目ID获取对话
     */
    fun getConversationsByProject(projectId: Long): Flow<List<Conversation>>
    
    /**
     * 获取活跃的对话
     */
    fun getActiveConversations(): Flow<List<Conversation>>
    
    /**
     * 获取最近的对话
     */
    fun getRecentConversations(limit: Int = 10): Flow<List<Conversation>>
    
    /**
     * 根据ID获取对话
     */
    suspend fun getConversationById(id: Long): Conversation?
    
    /**
     * 更新对话
     */
    suspend fun updateConversation(conversation: Conversation): Boolean
    
    /**
     * 删除对话
     */
    suspend fun deleteConversation(conversation: Conversation): Boolean
    
    /**
     * 结束对话
     */
    suspend fun endConversation(id: Long): Boolean
}

/**
 * 对话消息仓库接口
 */
interface ConversationMessageRepository {
    
    /**
     * 发送消息
     */
    suspend fun sendMessage(message: ConversationMessage): Long
    
    /**
     * 获取对话的所有消息
     */
    fun getMessagesByConversation(conversationId: Long): Flow<List<ConversationMessage>>
    
    /**
     * 获取对话的最后一条消息
     */
    suspend fun getLastMessage(conversationId: Long): ConversationMessage?
    
    /**
     * 搜索消息
     */
    fun searchMessages(query: String): Flow<List<ConversationMessage>>
    
    /**
     * 更新消息
     */
    suspend fun updateMessage(message: ConversationMessage): Boolean
    
    /**
     * 删除消息
     */
    suspend fun deleteMessage(message: ConversationMessage): Boolean
    
    /**
     * 获取消息统计信息
     */
    suspend fun getMessageStatistics(conversationId: Long): MessageStatistics
}

/**
 * API密钥仓库接口
 */
interface ApiKeyRepository {
    
    /**
     * 添加API密钥
     */
    suspend fun addApiKey(apiKey: ApiKey): Long
    
    /**
     * 获取所有API密钥
     */
    fun getAllApiKeys(): Flow<List<ApiKey>>
    
    /**
     * 获取活跃的API密钥
     */
    fun getActiveApiKeys(): Flow<List<ApiKey>>
    
    /**
     * 根据提供商获取API密钥
     */
    suspend fun getApiKeysByProvider(provider: ApiProvider): List<ApiKey>
    
    /**
     * 更新API密钥
     */
    suspend fun updateApiKey(apiKey: ApiKey): Boolean
    
    /**
     * 删除API密钥
     */
    suspend fun deleteApiKey(apiKey: ApiKey): Boolean
    
    /**
     * 切换API密钥激活状态
     */
    suspend fun toggleActiveStatus(id: Long): Boolean
    
    /**
     * 更新使用统计
     */
    suspend fun updateUsageStats(id: Long): Boolean
    
    /**
     * 获取即将过期的API密钥
     */
    suspend fun getExpiringApiKeys(): List<ApiKey>
}

/**
 * Git仓库仓库接口
 */
interface GitRepository {
    
    /**
     * 添加Git仓库
     */
    suspend fun addGitRepo(gitRepo: GitRepo): Long
    
    /**
     * 获取所有Git仓库
     */
    fun getAllGitRepos(): Flow<List<GitRepo>>
    
    /**
     * 根据项目ID获取Git仓库
     */
    suspend fun getGitRepoByProject(projectId: Long): GitRepo?
    
    /**
     * 获取启用了同步的Git仓库
     */
    fun getSyncEnabledRepos(): Flow<List<GitRepo>>
    
    /**
     * 更新Git仓库
     */
    suspend fun updateGitRepo(gitRepo: GitRepo): Boolean
    
    /**
     * 删除Git仓库
     */
    suspend fun deleteGitRepo(gitRepo: GitRepo): Boolean
    
    /**
     * 更新同步信息
     */
    suspend fun updateSyncInfo(id: Long, commitHash: String): Boolean
    
    /**
     * 切换同步状态
     */
    suspend fun toggleSyncStatus(id: Long): Boolean
}

/**
 * 项目统计信息数据类
 */
data class ProjectStatistics(
    val totalProjects: Int,
    val favoriteProjects: Int,
    val projectsByType: Map<ProjectType, Int>,
    val projectsByLanguage: Map<String, Int>,
    val recentProjects: Int
)

/**
 * 代码片段统计信息数据类
 */
data class SnippetStatistics(
    val totalSnippets: Int,
    val favoriteSnippets: Int,
    val snippetsByLanguage: Map<String, Int>,
    val averageLineCount: Double,
    val recentSnippets: Int
)

/**
 * 消息统计信息数据类
 */
data class MessageStatistics(
    val totalMessages: Int,
    val userMessages: Int,
    val assistantMessages: Int,
    val totalTokensUsed: Int,
    val averageTokensPerMessage: Double
)