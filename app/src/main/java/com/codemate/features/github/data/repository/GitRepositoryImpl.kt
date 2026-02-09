package com.codemate.features.github.data.repository

import com.codemate.features.github.data.remote.GitCommandExecutor
import com.codemate.features.github.domain.model.*
import com.codemate.features.github.domain.repository.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Git Repository实现
 * 提供Git操作的本地数据访问
 */
class GitRepositoryImpl(
    private val gitCommandExecutor: GitCommandExecutor
) : GitRepository {
    
    override suspend fun initializeRepository(path: String, config: GitConfig): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.initializeRepository(path, config)
        }
    }
    
    override suspend fun cloneRepository(url: String, path: String, branch: String?): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.cloneRepository(url, path, branch)
        }
    }
    
    override suspend fun addFiles(path: String, files: List<String>): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.addFiles(path, files)
        }
    }
    
    override suspend fun commitChanges(path: String, message: String, author: String?): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.commitChanges(path, message, author)
        }
    }
    
    override suspend fun pushChanges(path: String, branch: String?, force: Boolean): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.pushChanges(path, branch, force)
        }
    }
    
    override suspend fun pullChanges(path: String, branch: String?, rebase: Boolean): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.pullChanges(path, branch, rebase)
        }
    }
    
    override suspend fun getStatus(path: String): Result<GitStatus> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.getStatus(path)
        }
    }
    
    override suspend fun createBranch(path: String, branchName: String, checkout: Boolean): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.createBranch(path, branchName, checkout)
        }
    }
    
    override suspend fun checkoutBranch(path: String, branchName: String, create: Boolean): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.checkoutBranch(path, branchName, create)
        }
    }
    
    override suspend fun mergeBranch(path: String, sourceBranch: String, targetBranch: String?): Result<MergeResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.mergeBranch(path, sourceBranch, targetBranch)
        }
    }
    
    override suspend fun getBranches(path: String): Result<List<GitBranch>> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.getBranches(path)
        }
    }
    
    override suspend fun getCommitHistory(
        path: String,
        branch: String?,
        since: Date?,
        until: Date?,
        maxCount: Int
    ): Result<List<GitCommit>> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.getCommitHistory(path, branch, since, until, maxCount)
        }
    }
    
    override suspend fun getCommit(path: String, sha: String): Result<GitCommit> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.getCommit(path, sha)
        }
    }
    
    override suspend fun getDiff(
        path: String,
        from: String?,
        to: String?,
        file: String?
    ): Result<GitDiff> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.getDiff(path, from, to, file)
        }
    }
    
    override suspend fun resetChanges(path: String, mode: ResetMode, commit: String?): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.resetChanges(path, mode, commit)
        }
    }
    
    override suspend fun stashChanges(path: String, message: String?): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.stashChanges(path, message)
        }
    }
    
    override suspend fun applyStash(path: String, stashRef: String?): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.applyStash(path, stashRef)
        }
    }
    
    override suspend fun createTag(path: String, tagName: String, message: String?, commit: String?): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.createTag(path, tagName, message, commit)
        }
    }
    
    override suspend fun getTags(path: String): Result<List<GitTag>> {
        return withContext(Dispatchers.IO) {
            gitCommandExecutor.getTags(path)
        }
    }
}