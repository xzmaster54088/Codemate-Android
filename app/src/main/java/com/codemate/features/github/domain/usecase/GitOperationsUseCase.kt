package com.codemate.features.github.domain.usecase

import com.codemate.features.github.domain.model.*
import com.codemate.features.github.domain.repository.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Git操作用例
 * 处理所有Git相关的业务逻辑
 */
class GitOperationsUseCase(
    private val gitRepository: GitRepository,
    private val githubRepository: GitHubRepository
) {
    
    /**
     * 快速克隆并初始化仓库
     */
    suspend fun cloneAndInitialize(
        repositoryUrl: String,
        localPath: String,
        branch: String? = null,
        config: GitConfig
    ): Result<GitRepository> {
        return try {
            // 克隆仓库
            val cloneResult = gitRepository.cloneRepository(repositoryUrl, localPath, branch)
            if (cloneResult.isFailure) {
                return Result.failure(cloneResult.exceptionOrNull()!!)
            }
            
            // 配置Git
            val configResult = gitRepository.initializeRepository(localPath, config)
            if (configResult.isFailure) {
                return Result.failure(configResult.exceptionOrNull()!!)
            }
            
            // 获取仓库信息
            val repoInfo = githubRepository.getRepository(
                owner = repositoryUrl.substringAfter("/").substringBefore(".git"),
                repo = repositoryUrl.substringAfterLast("/").removeSuffix(".git")
            )
            
            if (repoInfo.isSuccess) {
                Result.success(repoInfo.getOrNull()!!)
            } else {
                Result.failure(Exception("Failed to get repository info"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 执行完整的Git工作流（add -> commit -> push）
     */
    suspend fun commitAndPush(
        localPath: String,
        files: List<String>,
        message: String,
        branch: String? = null,
        author: String? = null
    ): Result<GitCommandResult> {
        return try {
            // 添加文件到暂存区
            val addResult = gitRepository.addFiles(localPath, files)
            if (addResult.isFailure) {
                return Result.failure(addResult.exceptionOrNull()!!)
            }
            
            // 提交更改
            val commitResult = gitRepository.commitChanges(localPath, message, author)
            if (commitResult.isFailure) {
                return Result.failure(commitResult.exceptionOrNull()!!)
            }
            
            // 推送更改
            val pushResult = gitRepository.pushChanges(localPath, branch)
            if (pushResult.isFailure) {
                return Result.failure(pushResult.exceptionOrNull()!!)
            }
            
            Result.success(pushResult.getOrNull()!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 同步远程更改
     */
    suspend fun syncWithRemote(
        localPath: String,
        branch: String? = null,
        rebase: Boolean = false
    ): Result<SyncResult> {
        return try {
            // 拉取远程更改
            val pullResult = gitRepository.pullChanges(localPath, branch, rebase)
            if (pullResult.isFailure) {
                return Result.failure(pullResult.exceptionOrNull()!!)
            }
            
            // 获取仓库状态
            val statusResult = gitRepository.getStatus(localPath)
            if (statusResult.isFailure) {
                return Result.failure(statusResult.exceptionOrNull()!!)
            }
            
            val status = statusResult.getOrNull()!!
            Result.success(
                SyncResult(
                    success = true,
                    status = status,
                    message = "Sync completed successfully"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 创建新分支并切换
     */
    suspend fun createAndSwitchBranch(
        localPath: String,
        branchName: String,
        fromBranch: String? = null
    ): Result<GitCommandResult> {
        return try {
            // 如果指定了源分支，先切换到源分支
            if (fromBranch != null) {
                val checkoutResult = gitRepository.checkoutBranch(localPath, fromBranch)
                if (checkoutResult.isFailure) {
                    return Result.failure(checkoutResult.exceptionOrNull()!!)
                }
            }
            
            // 创建并切换到新分支
            val branchResult = gitRepository.checkoutBranch(localPath, branchName, create = true)
            if (branchResult.isFailure) {
                return Result.failure(branchResult.exceptionOrNull()!!)
            }
            
            Result.success(branchResult.getOrNull()!!)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 解决合并冲突
     */
    suspend fun resolveMergeConflicts(
        localPath: String,
        strategy: ConflictResolutionStrategy = ConflictResolutionStrategy.OURS
    ): Result<ConflictResolutionResult> {
        return try {
            // 获取冲突信息
            val statusResult = gitRepository.getStatus(localPath)
            if (statusResult.isFailure) {
                return Result.failure(statusResult.exceptionOrNull()!!)
            }
            
            val status = statusResult.getOrNull()!!
            if (status.conflictedFiles.isEmpty()) {
                return Result.success(
                    ConflictResolutionResult(
                        success = true,
                        conflicts = emptyList(),
                        message = "No conflicts found"
                    )
                )
            }
            
            // 根据策略解决冲突
            val resolutionResult = when (strategy) {
                ConflictResolutionStrategy.OURS -> resolveWithOurs(localPath, status.conflictedFiles)
                ConflictResolutionStrategy.THEIRS -> resolveWithTheirs(localPath, status.conflictedFiles)
                ConflictResolutionStrategy.MANUAL -> Result.failure(Exception("Manual resolution required"))
            }
            
            if (resolutionResult.isFailure) {
                return Result.failure(resolutionResult.exceptionOrNull()!!)
            }
            
            // 标记冲突已解决
            val addResult = gitRepository.addFiles(localPath, status.conflictedFiles)
            if (addResult.isFailure) {
                return Result.failure(addResult.exceptionOrNull()!!)
            }
            
            Result.success(
                ConflictResolutionResult(
                    success = true,
                    conflicts = status.conflictedFiles.map { FileConflict(it, null, null, null, emptyList()) },
                    message = "Conflicts resolved successfully"
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取分支比较信息
     */
    suspend fun compareBranches(
        localPath: String,
        sourceBranch: String,
        targetBranch: String
    ): Result<BranchComparison> {
        return try {
            // 切换到目标分支
            gitRepository.checkoutBranch(localPath, targetBranch)
            
            // 获取差异
            val diffResult = gitRepository.getDiff(localPath, sourceBranch, targetBranch)
            if (diffResult.isFailure) {
                return Result.failure(diffResult.exceptionOrNull()!!)
            }
            
            val diff = diffResult.getOrNull()!!
            
            // 获取源分支信息
            val sourceBranchResult = gitRepository.getBranches(localPath)
            if (sourceBranchResult.isFailure) {
                return Result.failure(sourceBranchResult.exceptionOrNull()!!)
            }
            
            val branches = sourceBranchResult.getOrNull()!!
            val source = branches.find { it.name == sourceBranch }
            val target = branches.find { it.name == targetBranch }
            
            Result.success(
                BranchComparison(
                    sourceBranch = sourceBranch,
                    targetBranch = targetBranch,
                    sourceSha = source?.sha,
                    targetSha = target?.sha,
                    diff = diff,
                    aheadBy = target?.aheadBy ?: 0,
                    behindBy = target?.behindBy ?: 0,
                    hasConflicts = false // 需要通过实际合并检查来确定
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun resolveWithOurs(path: String, files: List<String>): Result<GitCommandResult> {
        // 解决策略：保留我们的版本
        return gitRepository.resetChanges(path, ResetMode.HARD, "HEAD")
    }
    
    private suspend fun resolveWithTheirs(path: String, files: List<String>): Result<GitCommandResult> {
        // 解决策略：保留他们的版本
        return gitRepository.resetChanges(path, ResetMode.HARD, "HEAD~1")
    }
}

/**
 * 同步结果
 */
data class SyncResult(
    val success: Boolean,
    val status: GitStatus,
    val message: String
)

/**
 * 冲突解决策略枚举
 */
enum class ConflictResolutionStrategy {
    OURS,
    THEIRS,
    MANUAL,
    AUTO_MERGE
}

/**
 * 冲突解决结果
 */
data class ConflictResolutionResult(
    val success: Boolean,
    val conflicts: List<FileConflict>,
    val message: String
)

/**
 * 分支比较信息
 */
data class BranchComparison(
    val sourceBranch: String,
    val targetBranch: String,
    val sourceSha: String?,
    val targetSha: String?,
    val diff: GitDiff,
    val aheadBy: Int,
    val behindBy: Int,
    val hasConflicts: Boolean
)