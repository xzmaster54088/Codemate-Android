package com.codemate.data.manager

import android.content.Context
import com.codemate.data.database.CodeMateDatabase
import com.codemate.data.entity.*
import com.codemate.data.repository.*
import com.codemate.data.security.ApiKeyEncryptionService
import com.codemate.data.security.GitCredentialEncryptionService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一数据管理器
 * 提供应用级别的数据访问接口，整合所有仓库功能
 */
@Singleton
class CodeMateDataManager @Inject constructor(
    private val projectRepository: ProjectRepository,
    private val snippetRepository: SnippetRepository,
    private val conversationRepository: ConversationRepository,
    private val messageRepository: ConversationMessageRepository,
    private val apiKeyRepository: ApiKeyRepository,
    private val gitRepository: GitRepository,
    private val apiKeyEncryptionService: ApiKeyEncryptionService,
    private val gitCredentialEncryptionService: GitCredentialEncryptionService,
    private val database: CodeMateDatabase,
    private val applicationScope: CoroutineScope,
    @ApplicationContext private val context: Context
) {

    /**
     * 初始化数据管理器
     * 必须在应用启动时调用
     */
    suspend fun initialize(): Boolean {
        return try {
            // 初始化加密管理器
            // 注意：这里需要处理Android版本兼容性问题
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 项目相关操作
     */
    object Projects {
        suspend fun create(
            name: String,
            description: String? = null,
            type: ProjectType,
            language: String,
            framework: String? = null
        ): Long {
            val project = Project(
                name = name,
                description = description,
                type = type,
                language = language,
                framework = framework
            )
            return projectRepository.createProject(project)
        }

        fun getAll(): Flow<List<Project>> = projectRepository.getAllProjects()
        suspend fun getAllOnce(): List<Project> = projectRepository.getAllProjectsOnce()
        suspend fun getById(id: Long): Project? = projectRepository.getProjectById(id)
        fun getByType(type: ProjectType): Flow<List<Project>> = projectRepository.getProjectsByType(type)
        fun getFavorites(): Flow<List<Project>> = projectRepository.getFavoriteProjects()
        fun search(query: String): Flow<List<Project>> = projectRepository.searchProjects(query)
        suspend fun update(project: Project): Boolean = projectRepository.updateProject(project)
        suspend fun delete(project: Project): Boolean = projectRepository.deleteProject(project)
        suspend fun toggleFavorite(id: Long): Boolean = projectRepository.toggleFavorite(id)
        suspend fun updateLastAccessed(id: Long): Boolean = projectRepository.updateLastAccessed(id)
        suspend fun getStatistics(): ProjectStatistics = projectRepository.getProjectStatistics()
    }

    /**
     * 代码片段相关操作
     */
    object Snippets {
        suspend fun create(
            projectId: Long,
            title: String,
            content: String,
            language: String,
            description: String? = null
        ): Long {
            val snippet = Snippet(
                projectId = projectId,
                title = title,
                content = content,
                language = language,
                description = description
            )
            return snippetRepository.createSnippet(snippet)
        }

        fun getAll(): Flow<List<Snippet>> = snippetRepository.getAllSnippets()
        fun getByProject(projectId: Long): Flow<List<Snippet>> = snippetRepository.getSnippetsByProject(projectId)
        fun getFavorites(projectId: Long? = null): Flow<List<Snippet>> = snippetRepository.getFavoriteSnippets(projectId)
        fun getByLanguage(language: String): Flow<List<Snippet>> = snippetRepository.getSnippetsByLanguage(language)
        fun search(query: String, projectId: Long? = null): Flow<List<Snippet>> = snippetRepository.searchSnippets(query, projectId)
        suspend fun getById(id: Long): Snippet? = snippetRepository.getSnippetById(id)
        fun getRecent(limit: Int = 20): Flow<List<Snippet>> = snippetRepository.getRecentSnippets(limit)
        suspend fun update(snippet: Snippet): Boolean = snippetRepository.updateSnippet(snippet)
        suspend fun delete(snippet: Snippet): Boolean = snippetRepository.deleteSnippet(snippet)
        suspend fun toggleFavorite(id: Long): Boolean = snippetRepository.toggleFavorite(id)
        suspend fun getStatistics(projectId: Long): SnippetStatistics = snippetRepository.getSnippetStatistics(projectId)
    }

    /**
     * 对话相关操作
     */
    object Conversations {
        suspend fun create(title: String, projectId: Long? = null, context: String? = null): Long {
            val conversation = Conversation(
                title = title,
                projectId = projectId,
                context = context
            )
            return conversationRepository.createConversation(conversation)
        }

        fun getAll(): Flow<List<Conversation>> = conversationRepository.getAllConversations()
        fun getByProject(projectId: Long): Flow<List<Conversation>> = conversationRepository.getConversationsByProject(projectId)
        fun getActive(): Flow<List<Conversation>> = conversationRepository.getActiveConversations()
        fun getRecent(limit: Int = 10): Flow<List<Conversation>> = conversationRepository.getRecentConversations(limit)
        suspend fun getById(id: Long): Conversation? = conversationRepository.getConversationById(id)
        suspend fun update(conversation: Conversation): Boolean = conversationRepository.updateConversation(conversation)
        suspend fun delete(conversation: Conversation): Boolean = conversationRepository.deleteConversation(conversation)
        suspend fun end(id: Long): Boolean = conversationRepository.endConversation(id)
    }

    /**
     * 对话消息相关操作
     */
    object Messages {
        suspend fun send(
            conversationId: Long,
            role: MessageRole,
            content: String,
            tokensUsed: Int = 0
        ): Long {
            val message = ConversationMessage(
                conversationId = conversationId,
                role = role,
                content = content,
                tokensUsed = tokensUsed
            )
            return messageRepository.sendMessage(message)
        }

        fun getByConversation(conversationId: Long): Flow<List<ConversationMessage>> = 
            messageRepository.getMessagesByConversation(conversationId)
        suspend fun getLast(conversationId: Long): ConversationMessage? = 
            messageRepository.getLastMessage(conversationId)
        fun search(query: String): Flow<List<ConversationMessage>> = messageRepository.searchMessages(query)
        suspend fun update(message: ConversationMessage): Boolean = messageRepository.updateMessage(message)
        suspend fun delete(message: ConversationMessage): Boolean = messageRepository.deleteMessage(message)
        suspend fun getStatistics(conversationId: Long): MessageStatistics = 
            messageRepository.getMessageStatistics(conversationId)
    }

    /**
     * API密钥相关操作
     */
    object ApiKeys {
        suspend fun add(
            provider: ApiProvider,
            name: String,
            encryptedKey: String
        ): Long {
            val apiKey = ApiKey(
                provider = provider,
                name = name,
                encryptedKey = encryptedKey
            )
            return apiKeyRepository.addApiKey(apiKey)
        }

        suspend fun addEncrypted(
            provider: ApiProvider,
            name: String,
            plainKey: String
        ): Result<Long> {
            return try {
                // 验证API密钥格式
                if (!apiKeyEncryptionService.validateApiKey(plainKey, provider)) {
                    return Result.failure(IllegalArgumentException("Invalid API key format for provider: $provider"))
                }

                // 加密API密钥
                val encryptedKey = apiKeyEncryptionService.encryptApiKey(plainKey)
                    ?: return Result.failure(Exception("Failed to encrypt API key"))

                // 保存到数据库
                val id = add(provider, name, encryptedKey)
                Result.success(id)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        fun getAll(): Flow<List<ApiKey>> = apiKeyRepository.getAllApiKeys()
        fun getActive(): Flow<List<ApiKey>> = apiKeyRepository.getActiveApiKeys()
        suspend fun getByProvider(provider: ApiProvider): List<ApiKey> = apiKeyRepository.getApiKeysByProvider(provider)
        suspend fun update(apiKey: ApiKey): Boolean = apiKeyRepository.updateApiKey(apiKey)
        suspend fun delete(apiKey: ApiKey): Boolean = apiKeyRepository.deleteApiKey(apiKey)
        suspend fun toggleActiveStatus(id: Long): Boolean = apiKeyRepository.toggleActiveStatus(id)
        suspend fun updateUsageStats(id: Long): Boolean = apiKeyRepository.updateUsageStats(id)
        suspend fun getExpiring(): List<ApiKey> = apiKeyRepository.getExpiringApiKeys()

        /**
         * 解密并获取API密钥明文
         */
        suspend fun getDecryptedKey(apiKey: ApiKey): String? {
            return apiKeyEncryptionService.decryptApiKey(apiKey.encryptedKey)
        }
    }

    /**
     * Git仓库相关操作
     */
    object GitRepos {
        suspend fun add(
            projectId: Long,
            remoteUrl: String,
            localPath: String,
            branch: String = "main",
            username: String? = null,
            password: String? = null
        ): Long {
            val gitRepo = GitRepo(
                projectId = projectId,
                remoteUrl = remoteUrl,
                localPath = localPath,
                branch = branch,
                credentials = null
            )

            // 如果提供了用户名和密码，进行加密
            val finalRepo = if (username != null && password != null) {
                val encryptedCredentials = runBlocking {
                    gitCredentialEncryptionService.encryptGitCredentials(username, password)
                }
                gitRepo.copy(credentials = encryptedCredentials)
            } else {
                gitRepo
            }

            return gitRepository.addGitRepo(finalRepo)
        }

        fun getAll(): Flow<List<GitRepo>> = gitRepository.getAllGitRepos()
        suspend fun getByProject(projectId: Long): GitRepo? = gitRepository.getGitRepoByProject(projectId)
        fun getSyncEnabled(): Flow<List<GitRepo>> = gitRepository.getSyncEnabledRepos()
        suspend fun update(gitRepo: GitRepo): Boolean = gitRepository.updateGitRepo(gitRepo)
        suspend fun delete(gitRepo: GitRepo): Boolean = gitRepository.deleteGitRepo(gitRepo)
        suspend fun updateSyncInfo(id: Long, commitHash: String): Boolean = gitRepository.updateSyncInfo(id, commitHash)
        suspend fun toggleSyncStatus(id: Long): Boolean = gitRepository.toggleSyncStatus(id)

        /**
         * 解密并获取Git凭据
         */
        suspend fun getDecryptedCredentials(gitRepo: GitRepo): GitCredentials? {
            return gitRepo.credentials?.let { encryptedCredentials ->
                gitCredentialEncryptionService.decryptGitCredentials(encryptedCredentials)
            }
        }
    }

    /**
     * 数据库维护操作
     */
    object Maintenance {
        /**
         * 清理数据库
         */
        suspend fun cleanDatabase(): Boolean {
            return try {
                // 这里可以添加清理逻辑
                database.close()
                true
            } catch (e: Exception) {
                false
            }
        }

        /**
         * 获取数据库统计信息
         */
        suspend fun getDatabaseStatistics(): DatabaseStatistics {
            return try {
                val projectStats = projectRepository.getProjectStatistics()
                val allProjects = projectRepository.getAllProjectsOnce()
                val totalSnippets = allProjects.sumOf { projectId ->
                    // 这里应该调用snippet的统计方法
                    0 // 临时实现
                }
                val activeConversations = conversationRepository.getAllConversations().first()
                    .count { it.isActive }
                val activeApiKeys = apiKeyRepository.getAllApiKeys().first()
                    .count { it.isActive }

                DatabaseStatistics(
                    totalProjects = projectStats.totalProjects,
                    totalSnippets = totalSnippets,
                    activeConversations = activeConversations,
                    activeApiKeys = activeApiKeys,
                    databaseSize = 0 // 需要通过文件系统获取
                )
            } catch (e: Exception) {
                DatabaseStatistics()
            }
        }
    }
}

/**
 * 数据库统计信息数据类
 */
data class DatabaseStatistics(
    val totalProjects: Int = 0,
    val totalSnippets: Int = 0,
    val activeConversations: Int = 0,
    val activeApiKeys: Int = 0,
    val databaseSize: Long = 0L
)

/**
 * 安全执行协程的辅助函数
 */
private suspend fun <T> runBlocking(block: suspend () -> T): T {
    return kotlinx.coroutines.runBlocking { block() }
}