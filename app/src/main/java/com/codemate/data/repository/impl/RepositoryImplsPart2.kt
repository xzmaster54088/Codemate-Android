package com.codemate.data.repository.impl

import com.codemate.data.dao.*
import com.codemate.data.entity.*
import com.codemate.data.repository.*
import kotlinx.coroutines.flow.Flow
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * API密钥仓库实现类
 */
@Singleton
class ApiKeyRepositoryImpl @Inject constructor(
    private val apiKeyDao: ApiKeyDao
) : ApiKeyRepository {

    override suspend fun addApiKey(apiKey: ApiKey): Long {
        return try {
            apiKeyDao.insertApiKey(apiKey)
        } catch (e: Exception) {
            throw ApiKeyRepositoryException("Failed to add API key: ${apiKey.name}", e)
        }
    }

    override fun getAllApiKeys(): Flow<List<ApiKey>> {
        return apiKeyDao.getAllApiKeys()
    }

    override fun getActiveApiKeys(): Flow<List<ApiKey>> {
        return apiKeyDao.getActiveApiKeys()
    }

    override suspend fun getApiKeysByProvider(provider: ApiProvider): List<ApiKey> {
        return try {
            apiKeyDao.getApiKeysByProvider(provider)
        } catch (e: Exception) {
            throw ApiKeyRepositoryException("Failed to get API keys by provider: $provider", e)
        }
    }

    override suspend fun updateApiKey(apiKey: ApiKey): Boolean {
        return try {
            apiKeyDao.updateApiKey(apiKey) > 0
        } catch (e: Exception) {
            throw ApiKeyRepositoryException("Failed to update API key: ${apiKey.name}", e)
        }
    }

    override suspend fun deleteApiKey(apiKey: ApiKey): Boolean {
        return try {
            apiKeyDao.deleteApiKey(apiKey) > 0
        } catch (e: Exception) {
            throw ApiKeyRepositoryException("Failed to delete API key: ${apiKey.name}", e)
        }
    }

    override suspend fun toggleActiveStatus(id: Long): Boolean {
        return try {
            val apiKey = apiKeyDao.getApiKeyById(id) ?: return false
            apiKeyDao.updateActiveStatus(id, !apiKey.isActive) > 0
        } catch (e: Exception) {
            throw ApiKeyRepositoryException("Failed to toggle active status for API key: $id", e)
        }
    }

    override suspend fun updateUsageStats(id: Long): Boolean {
        return try {
            apiKeyDao.updateUsageStats(id, Date()) > 0
        } catch (e: Exception) {
            throw ApiKeyRepositoryException("Failed to update usage stats for API key: $id", e)
        }
    }

    override suspend fun getExpiringApiKeys(): List<ApiKey> {
        return try {
            val warningDate = Date(System.currentTimeMillis() + 7 * 24 * 60 * 60 * 1000L) // 7天后
            apiKeyDao.getExpiringApiKeys(warningDate)
        } catch (e: Exception) {
            throw ApiKeyRepositoryException("Failed to get expiring API keys", e)
        }
    }
}

/**
 * Git仓库仓库实现类
 */
@Singleton
class GitRepositoryImpl @Inject constructor(
    private val gitRepoDao: GitRepoDao
) : GitRepository {

    override suspend fun addGitRepo(gitRepo: GitRepo): Long {
        return try {
            gitRepoDao.insertGitRepo(gitRepo)
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to add Git repo: ${gitRepo.remoteUrl}", e)
        }
    }

    override fun getAllGitRepos(): Flow<List<GitRepo>> {
        return gitRepoDao.getAllGitRepos()
    }

    override suspend fun getGitRepoByProject(projectId: Long): GitRepo? {
        return try {
            gitRepoDao.getGitRepoByProject(projectId)
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to get Git repo for project: $projectId", e)
        }
    }

    override fun getSyncEnabledRepos(): Flow<List<GitRepo>> {
        return gitRepoDao.getSyncEnabledRepos()
    }

    override suspend fun updateGitRepo(gitRepo: GitRepo): Boolean {
        return try {
            gitRepoDao.updateGitRepo(gitRepo) > 0
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to update Git repo: ${gitRepo.remoteUrl}", e)
        }
    }

    override suspend fun deleteGitRepo(gitRepo: GitRepo): Boolean {
        return try {
            gitRepoDao.deleteGitRepo(gitRepo) > 0
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to delete Git repo: ${gitRepo.remoteUrl}", e)
        }
    }

    override suspend fun updateSyncInfo(id: Long, commitHash: String): Boolean {
        return try {
            gitRepoDao.updateSyncInfo(id, commitHash, Date()) > 0
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to update sync info for Git repo: $id", e)
        }
    }

    override suspend fun toggleSyncStatus(id: Long): Boolean {
        return try {
            val gitRepo = gitRepoDao.getGitRepoById(id) ?: return false
            gitRepoDao.updateSyncStatus(id, !gitRepo.isSyncEnabled) > 0
        } catch (e: Exception) {
            throw GitRepositoryException("Failed to toggle sync status for Git repo: $id", e)
        }
    }
}

/**
 * Repository异常基类
 */
sealed class RepositoryException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 项目仓库异常
 */
class ProjectRepositoryException(message: String, cause: Throwable? = null) : RepositoryException(message, cause)

/**
 * 代码片段仓库异常
 */
class SnippetRepositoryException(message: String, cause: Throwable? = null) : RepositoryException(message, cause)

/**
 * 对话仓库异常
 */
class ConversationRepositoryException(message: String, cause: Throwable? = null) : RepositoryException(message, cause)

/**
 * 对话消息仓库异常
 */
class ConversationMessageRepositoryException(message: String, cause: Throwable? = null) : RepositoryException(message, cause)

/**
 * API密钥仓库异常
 */
class ApiKeyRepositoryException(message: String, cause: Throwable? = null) : RepositoryException(message, cause)

/**
 * Git仓库异常
 */
class GitRepositoryException(message: String, cause: Throwable? = null) : RepositoryException(message, cause)