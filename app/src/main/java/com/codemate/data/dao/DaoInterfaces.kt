package com.codemate.data.dao

import androidx.room.*
import androidx.lifecycle.LiveData
import com.codemate.data.entity.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * 项目数据访问对象接口
 * 提供项目的CRUD操作和复杂查询
 */
@Dao
interface ProjectDao {
    
    /**
     * 插入新项目
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: Project): Long
    
    /**
     * 批量插入项目
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<Project>): List<Long>
    
    /**
     * 更新项目
     */
    @Update
    suspend fun updateProject(project: Project): Int
    
    /**
     * 删除项目
     */
    @Delete
    suspend fun deleteProject(project: Project): Int
    
    /**
     * 根据ID获取项目
     */
    @Query("SELECT * FROM projects WHERE id = :id")
    suspend fun getProjectById(id: Long): Project?
    
    /**
     * 获取所有项目（实时数据）
     */
    @Query("SELECT * FROM projects ORDER BY last_accessed DESC")
    fun getAllProjects(): Flow<List<Project>>
    
    /**
     * 获取所有项目（一次性数据）
     */
    @Query("SELECT * FROM projects ORDER BY last_accessed DESC")
    suspend fun getAllProjectsOnce(): List<Project>
    
    /**
     * 根据类型获取项目
     */
    @Query("SELECT * FROM projects WHERE type = :type ORDER BY last_accessed DESC")
    fun getProjectsByType(type: ProjectType): Flow<List<Project>>
    
    /**
     * 获取收藏的项目
     */
    @Query("SELECT * FROM projects WHERE is_favorite = 1 ORDER BY last_accessed DESC")
    fun getFavoriteProjects(): Flow<List<Project>>
    
    /**
     * 根据语言获取项目
     */
    @Query("SELECT * FROM projects WHERE language = :language ORDER BY last_accessed DESC")
    fun getProjectsByLanguage(language: String): Flow<List<Project>>
    
    /**
     * 搜索项目（按名称和描述）
     */
    @Query("""
        SELECT * FROM projects 
        WHERE name LIKE '%' || :query || '%' 
        OR description LIKE '%' || :query || '%'
        ORDER BY last_accessed DESC
    """)
    fun searchProjects(query: String): Flow<List<Project>>
    
    /**
     * 更新项目最后访问时间
     */
    @Query("UPDATE projects SET last_accessed = :date WHERE id = :id")
    suspend fun updateLastAccessed(id: Long, date: Date): Int
    
    /**
     * 切换项目收藏状态
     */
    @Query("UPDATE projects SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean): Int
    
    /**
     * 获取项目统计信息
     */
    @Query("SELECT COUNT(*) FROM projects")
    suspend fun getProjectCount(): Int
    
    /**
     * 获取最近访问的项目
     */
    @Query("SELECT * FROM projects ORDER BY last_accessed DESC LIMIT :limit")
    fun getRecentProjects(limit: Int = 10): Flow<List<Project>>
}

/**
 * 代码片段数据访问对象接口
 */
@Dao
interface SnippetDao {
    
    /**
     * 插入代码片段
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippet(snippet: Snippet): Long
    
    /**
     * 批量插入代码片段
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnippets(snippets: List<Snippet>): List<Long>
    
    /**
     * 更新代码片段
     */
    @Update
    suspend fun updateSnippet(snippet: Snippet): Int
    
    /**
     * 删除代码片段
     */
    @Delete
    suspend fun deleteSnippet(snippet: Int): Int
    
    /**
     * 根据ID获取代码片段
     */
    @Query("SELECT * FROM snippets WHERE id = :id")
    suspend fun getSnippetById(id: Long): Snippet?
    
    /**
     * 根据项目ID获取所有代码片段
     */
    @Query("SELECT * FROM snippets WHERE project_id = :projectId ORDER BY updated_at DESC")
    fun getSnippetsByProject(projectId: Long): Flow<List<Snippet>>
    
    /**
     * 根据项目ID获取收藏的代码片段
     */
    @Query("SELECT * FROM snippets WHERE project_id = :projectId AND is_favorite = 1 ORDER BY updated_at DESC")
    fun getFavoriteSnippetsByProject(projectId: Long): Flow<List<Snippet>>
    
    /**
     * 根据语言获取代码片段
     */
    @Query("SELECT * FROM snippets WHERE language = :language ORDER BY updated_at DESC")
    fun getSnippetsByLanguage(language: String): Flow<List<Snippet>>
    
    /**
     * 搜索代码片段
     */
    @Query("""
        SELECT * FROM snippets 
        WHERE (title LIKE '%' || :query || '%' 
        OR content LIKE '%' || :query || '%' 
        OR description LIKE '%' || :query || '%')
        AND (:projectId IS NULL OR project_id = :projectId)
        ORDER BY updated_at DESC
    """)
    fun searchSnippets(query: String, projectId: Long? = null): Flow<List<Snippet>>
    
    /**
     * 获取最近的代码片段
     */
    @Query("SELECT * FROM snippets ORDER BY updated_at DESC LIMIT :limit")
    fun getRecentSnippets(limit: Int = 20): Flow<List<Snippet>>
    
    /**
     * 切换代码片段收藏状态
     */
    @Query("UPDATE snippets SET is_favorite = :isFavorite WHERE id = :id")
    suspend fun updateFavoriteStatus(id: Long, isFavorite: Boolean): Int
    
    /**
     * 获取代码片段统计信息
     */
    @Query("SELECT COUNT(*) FROM snippets WHERE project_id = :projectId")
    suspend fun getSnippetCountByProject(projectId: Long): Int
    
    /**
     * 获取代码片段语言统计
     */
    @Query("""
        SELECT language, COUNT(*) as count 
        FROM snippets 
        WHERE project_id = :projectId
        GROUP BY language 
        ORDER BY count DESC
    """)
    suspend fun getLanguageStatsByProject(projectId: Long): List<LanguageStat>
    
    /**
     * 清理孤立的代码片段（项目已删除但代码片段还存在）
     */
    @Query("DELETE FROM snippets WHERE project_id NOT IN (SELECT id FROM projects)")
    suspend fun deleteOrphanedSnippets(): Int
}

/**
 * 对话数据访问对象接口
 */
@Dao
interface ConversationDao {
    
    /**
     * 插入对话
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation): Long
    
    /**
     * 更新对话
     */
    @Update
    suspend fun updateConversation(conversation: Conversation): Int
    
    /**
     * 删除对话
     */
    @Delete
    suspend fun deleteConversation(conversation: Conversation): Int
    
    /**
     * 根据ID获取对话
     */
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationById(id: Long): Conversation?
    
    /**
     * 获取所有对话
     */
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC")
    fun getAllConversations(): Flow<List<Conversation>>
    
    /**
     * 根据项目ID获取对话
     */
    @Query("SELECT * FROM conversations WHERE project_id = :projectId ORDER BY updated_at DESC")
    fun getConversationsByProject(projectId: Long): Flow<List<Conversation>>
    
    /**
     * 获取活跃的对话
     */
    @Query("SELECT * FROM conversations WHERE is_active = 1 ORDER BY updated_at DESC")
    fun getActiveConversations(): Flow<List<Conversation>>
    
    /**
     * 获取最近的对话
     */
    @Query("SELECT * FROM conversations ORDER BY updated_at DESC LIMIT :limit")
    fun getRecentConversations(limit: Int = 10): Flow<List<Conversation>>
    
    /**
     * 更新对话消息数量
     */
    @Query("UPDATE conversations SET message_count = :count, updated_at = :date WHERE id = :id")
    suspend fun updateMessageCount(id: Long, count: Int, date: Date): Int
    
    /**
     * 标记对话为非活跃
     */
    @Query("UPDATE conversations SET is_active = 0 WHERE id = :id")
    suspend fun markAsInactive(id: Long): Int
}

/**
 * 对话消息数据访问对象接口
 */
@Dao
interface ConversationMessageDao {
    
    /**
     * 插入消息
     */
    @Insert
    suspend fun insertMessage(message: ConversationMessage): Long
    
    /**
     * 批量插入消息
     */
    @Insert
    suspend fun insertMessages(messages: List<ConversationMessage>): List<Long>
    
    /**
     * 更新消息
     */
    @Update
    suspend fun updateMessage(message: ConversationMessage): Int
    
    /**
     * 删除消息
     */
    @Delete
    suspend fun deleteMessage(message: ConversationMessage): Int
    
    /**
     * 根据对话ID获取所有消息
     */
    @Query("SELECT * FROM conversation_messages WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun getMessagesByConversation(conversationId: Long): Flow<List<ConversationMessage>>
    
    /**
     * 获取对话的最后一条消息
     */
    @Query("SELECT * FROM conversation_messages WHERE conversation_id = :conversationId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLastMessage(conversationId: Long): ConversationMessage?
    
    /**
     * 搜索消息内容
     */
    @Query("""
        SELECT * FROM conversation_messages 
        WHERE content LIKE '%' || :query || '%'
        ORDER BY created_at DESC
    """)
    fun searchMessages(query: String): Flow<List<ConversationMessage>>
    
    /**
     * 获取对话的消息数量
     */
    @Query("SELECT COUNT(*) FROM conversation_messages WHERE conversation_id = :conversationId")
    suspend fun getMessageCount(conversationId: Long): Int
    
    /**
     * 清理对话的所有消息
     */
    @Query("DELETE FROM conversation_messages WHERE conversation_id = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: Long): Int
}

/**
 * API密钥数据访问对象接口
 */
@Dao
interface ApiKeyDao {
    
    /**
     * 插入API密钥
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApiKey(apiKey: ApiKey): Long
    
    /**
     * 更新API密钥
     */
    @Update
    suspend fun updateApiKey(apiKey: ApiKey): Int
    
    /**
     * 删除API密钥
     */
    @Delete
    suspend fun deleteApiKey(apiKey: ApiKey): Int
    
    /**
     * 根据ID获取API密钥
     */
    @Query("SELECT * FROM api_keys WHERE id = :id")
    suspend fun getApiKeyById(id: Long): ApiKey?
    
    /**
     * 根据提供商获取API密钥
     */
    @Query("SELECT * FROM api_keys WHERE provider = :provider AND is_active = 1")
    suspend fun getApiKeysByProvider(provider: ApiProvider): List<ApiKey>
    
    /**
     * 获取活跃的API密钥
     */
    @Query("SELECT * FROM api_keys WHERE is_active = 1 ORDER BY last_used DESC")
    fun getActiveApiKeys(): Flow<List<ApiKey>>
    
    /**
     * 获取所有API密钥
     */
    @Query("SELECT * FROM api_keys ORDER BY created_at DESC")
    fun getAllApiKeys(): Flow<List<ApiKey>>
    
    /**
     * 更新API密钥使用统计
     */
    @Query("UPDATE api_keys SET usage_count = usage_count + 1, last_used = :date WHERE id = :id")
    suspend fun updateUsageStats(id: Long, date: Date): Int
    
    /**
     * 切换API密钥激活状态
     */
    @Query("UPDATE api_keys SET is_active = :isActive WHERE id = :id")
    suspend fun updateActiveStatus(id: Long, isActive: Boolean): Int
    
    /**
     * 获取即将过期的API密钥
     */
    @Query("SELECT * FROM api_keys WHERE expires_at IS NOT NULL AND expires_at < :date AND is_active = 1")
    suspend fun getExpiringApiKeys(date: Date): List<ApiKey>
}

/**
 * Git仓库数据访问对象接口
 */
@Dao
interface GitRepoDao {
    
    /**
     * 插入Git仓库
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGitRepo(gitRepo: GitRepo): Long
    
    /**
     * 更新Git仓库
     */
    @Update
    suspend fun updateGitRepo(gitRepo: GitRepo): Int
    
    /**
     * 删除Git仓库
     */
    @Delete
    suspend fun deleteGitRepo(gitRepo: GitRepo): Int
    
    /**
     * 根据ID获取Git仓库
     */
    @Query("SELECT * FROM git_repos WHERE id = :id")
    suspend fun getGitRepoById(id: Long): GitRepo?
    
    /**
     * 根据项目ID获取Git仓库
     */
    @Query("SELECT * FROM git_repos WHERE project_id = :projectId")
    suspend fun getGitRepoByProject(projectId: Long): GitRepo?
    
    /**
     * 获取所有Git仓库
     */
    @Query("SELECT * FROM git_repos ORDER BY last_sync DESC")
    fun getAllGitRepos(): Flow<List<GitRepo>>
    
    /**
     * 获取启用了同步的Git仓库
     */
    @Query("SELECT * FROM git_repos WHERE is_sync_enabled = 1 ORDER BY last_sync DESC")
    fun getSyncEnabledRepos(): Flow<List<GitRepo>>
    
    /**
     * 更新同步信息
     */
    @Query("UPDATE git_repos SET last_commit_hash = :commitHash, last_sync = :syncTime WHERE id = :id")
    suspend fun updateSyncInfo(id: Long, commitHash: String, syncTime: Date): Int
    
    /**
     * 切换同步状态
     */
    @Query("UPDATE git_repos SET is_sync_enabled = :enabled WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, enabled: Boolean): Int
}

/**
 * 语言统计结果类
 */
data class LanguageStat(
    val language: String,
    val count: Int
)