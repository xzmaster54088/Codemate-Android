package com.codemate.features.github.domain.usecase

import com.codemate.features.github.domain.model.*
import com.codemate.features.github.domain.repository.ChangelogRepository
import com.codemate.features.github.domain.repository.ClassificationRepository
import com.codemate.features.github.domain.repository.GitHubRepository
import com.codemate.features.github.domain.repository.GitRepository
import java.util.Date

/**
 * CHANGELOG生成用例
 * 根据commit历史自动生成版本更新日志
 */
class ChangelogGenerationUseCase(
    private val changelogRepository: ChangelogRepository,
    private val classificationRepository: ClassificationRepository,
    private val gitHubRepository: GitHubRepository,
    private val gitRepository: GitRepository
) {
    
    /**
     * 生成完整的CHANGELOG
     */
    suspend fun generateChangelog(
        repositoryOwner: String,
        repositoryName: String,
        fromVersion: Version? = null,
        toVersion: Version? = null,
        config: ChangelogConfig? = null
    ): Result<ChangelogEntry> {
        return try {
            val repository = "$repositoryOwner/$repositoryName"
            
            // 获取提交历史
            val commits = getCommitsForChangelog(repository, fromVersion, toVersion)
            if (commits.isFailure) {
                return Result.failure(commits.exceptionOrNull()!!)
            }
            
            val commitList = commits.getOrNull()!!
            
            // 分类提交
            val classifications = classifyCommits(commitList.map { it.message })
            if (classifications.isFailure) {
                return Result.failure(classifications.exceptionOrNull()!!)
            }
            
            val classificationList = classifications.getOrNull()!!
            
            // 生成CHANGELOG条目
            val changelogEntry = createChangelogEntry(
                commits = commitList,
                classifications = classificationList,
                fromVersion = fromVersion,
                toVersion = toVersion,
                repository = repository,
                config = config
            )
            
            Result.success(changelogEntry)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 生成发布说明
     */
    suspend fun generateReleaseNotes(
        repositoryOwner: String,
        repositoryName: String,
        version: Version,
        includeContributors: Boolean = true
    ): Result<String> {
        return try {
            val repository = "$repositoryOwner/$repositoryName"
            
            // 获取提交历史
            val commits = gitRepository.getCommitHistory(
                path = "/tmp/$repository", // 这里需要实际的仓库路径
                since = Date(System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000), // 过去30天
                maxCount = 100
            )
            
            if (commits.isFailure) {
                return Result.failure(commits.exceptionOrNull()!!)
            }
            
            val commitList = commits.getOrNull()!!
            
            // 分类提交
            val classifications = classificationRepository.classifyCommits(commitList.map { it.message })
            if (classifications.isFailure) {
                return Result.failure(classifications.exceptionOrNull()!!)
            }
            
            val classificationList = classifications.getOrNull()!!
            
            // 生成发布说明
            val releaseNotes = formatReleaseNotes(
                version = version,
                commits = commitList,
                classifications = classificationList,
                includeContributors = includeContributors
            )
            
            Result.success(releaseNotes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 生成语义化版本号
     */
    suspend fun determineNextVersion(
        repositoryOwner: String,
        repositoryName: String,
        currentVersion: Version? = null
    ): Result<Version> {
        return try {
            val repository = "$repositoryOwner/$repositoryName"
            
            // 获取最近的提交
            val commits = gitRepository.getCommitHistory(
                path = "/tmp/$repository", // 这里需要实际的仓库路径
                maxCount = 50
            )
            
            if (commits.isFailure) {
                return Result.failure(commits.exceptionOrNull()!!)
            }
            
            val commitList = commits.getOrNull()!!
            
            // 分类提交
            val classifications = classificationRepository.classifyCommits(commitList.map { it.message })
            if (classifications.isFailure) {
                return Result.failure(classifications.exceptionOrNull()!!)
            }
            
            val classificationList = classifications.getOrNull()!!
            
            // 分析变更类型
            val hasBreakingChanges = classificationList.any { it.breaking }
            val hasFeatures = classificationList.any { it.type == CommitType.FEATURE }
            val hasFixes = classificationList.any { it.type == CommitType.FIX }
            val hasRefactoring = classificationList.any { it.type == CommitType.REFACTOR }
            val hasDocumentation = classificationList.any { it.type == CommitType.DOCS }
            
            // 确定下一个版本
            val nextVersion = when {
                hasBreakingChanges -> currentVersion?.next(ReleaseType.MAJOR) 
                    ?: Version(1, 0, 0, null, null, null)
                hasFeatures -> currentVersion?.next(ReleaseType.MINOR)
                    ?: Version(0, 1, 0, null, null, null)
                hasFixes || hasRefactoring || hasDocumentation -> currentVersion?.next(ReleaseType.PATCH)
                    ?: Version(0, 0, 1, null, null, null)
                else -> currentVersion?.next(ReleaseType.PATCH)
                    ?: Version(0, 0, 1, null, null, null)
            }
            
            Result.success(nextVersion)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 验证版本号格式
     */
    suspend fun validateVersionFormat(versionString: String): Result<Version> {
        return try {
            val version = Version.fromString(versionString)
            Result.success(version)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取版本历史
     */
    suspend fun getVersionHistory(
        repositoryOwner: String,
        repositoryName: String,
        limit: Int = 20
    ): Result<List<Version>> {
        return try {
            val releasesResult = gitHubRepository.getReleases(
                owner = repositoryOwner,
                repo = repositoryName,
                perPage = limit
            )
            
            if (releasesResult.isFailure) {
                return Result.failure(releasesResult.exceptionOrNull()!!)
            }
            
            val releases = releasesResult.getOrNull()!!
            val versions = releases.data.mapNotNull { release ->
                try {
                    Version.fromString(release.version.toString())
                } catch (e: Exception) {
                    null
                }
            }
            
            Result.success(versions.sortedWith(compareByDescending { it }))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 发布CHANGELOG
     */
    suspend fun publishChangelog(
        repositoryOwner: String,
        repositoryName: String,
        changelogEntry: ChangelogEntry,
        asRelease: Boolean = true
    ): Result<ReleaseInfo> {
        return try {
            val result = changelogRepository.publishChangelog(
                repository = "$repositoryOwner/$repositoryName",
                changelog = changelogEntry,
                asRelease = asRelease
            )
            
            if (result.isFailure) {
                // 如果是发布失败，尝试直接创建GitHub release
                val releaseResult = gitHubRepository.createRelease(
                    owner = repositoryOwner,
                    repo = repositoryName,
                    tagName = changelogEntry.version.toString(),
                    targetCommitish = "main",
                    name = "Version ${changelogEntry.version}",
                    body = formatChangelogForGitHub(changelogEntry),
                    draft = false,
                    prerelease = changelogEntry.version.isPrerelease()
                )
                
                if (releaseResult.isSuccess) {
                    Result.success(releaseResult.getOrNull()!!)
                } else {
                    Result.failure(releaseResult.exceptionOrNull()!!)
                }
            } else {
                result
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 生成差异报告
     */
    suspend fun generateDiffReport(
        repositoryOwner: String,
        repositoryName: String,
        fromVersion: Version,
        toVersion: Version
    ): Result<DiffReport> {
        return try {
            // 获取两个版本之间的提交
            val commits = getCommitsBetweenVersions(
                repository = "$repositoryOwner/$repositoryName",
                fromVersion = fromVersion,
                toVersion = toVersion
            )
            
            if (commits.isFailure) {
                return Result.failure(commits.exceptionOrNull()!!)
            }
            
            val commitList = commits.getOrNull()!!
            
            // 分类提交
            val classifications = classificationRepository.classifyCommits(commitList.map { it.message })
            if (classifications.isFailure) {
                return Result.failure(classifications.exceptionOrNull()!!)
            }
            
            val classificationList = classifications.getOrNull()!!
            
            // 生成统计信息
            val stats = generateDiffStats(commitList, classificationList)
            
            Result.success(
                DiffReport(
                    fromVersion = fromVersion,
                    toVersion = toVersion,
                    commits = commitList,
                    classifications = classificationList,
                    statistics = stats
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun getCommitsForChangelog(
        repository: String,
        fromVersion: Version?,
        toVersion: Version?
    ): Result<List<GitCommit>> {
        return try {
            // 这里需要根据版本信息获取实际的提交范围
            val commits = gitRepository.getCommitHistory(
                path = "/tmp/$repository", // 需要实际的仓库路径
                maxCount = 100
            )
            
            if (commits.isFailure) {
                return Result.failure(commits.exceptionOrNull()!!)
            }
            
            var commitList = commits.getOrNull()!!
            
            // 如果指定了fromVersion和toVersion，需要过滤提交
            // 这里简化处理，实际实现中需要根据标签信息进行精确过滤
            if (fromVersion != null) {
                commitList = commitList.filter { it.message.contains(fromVersion.toString()) }
            }
            
            Result.success(commitList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private suspend fun classifyCommits(messages: List<String>): Result<List<CommitClassification>> {
        return try {
            val result = classificationRepository.classifyCommits(messages)
            if (result.isFailure) {
                // 降级到逐个分类
                val classifications = messages.map { message ->
                    classifySingleCommit(message)
                }
                Result.success(classifications)
            } else {
                result
            }
        } catch (e: Exception) {
            val classifications = messages.map { message ->
                classifySingleCommit(message)
            }
            Result.success(classifications)
        }
    }
    
    private fun classifySingleCommit(message: String): CommitClassification {
        // 简化的单提交分类逻辑
        val lowerMessage = message.lowercase()
        
        return when {
            lowerMessage.startsWith("feat") || lowerMessage.startsWith("feature") -> {
                CommitClassification(
                    type = CommitType.FEATURE,
                    scope = extractScope(message),
                    description = cleanDescription(message),
                    breaking = message.contains("!"),
                    confidence = 0.9f,
                    features = mapOf("keyword_match" to 1.0f)
                )
            }
            lowerMessage.startsWith("fix") -> {
                CommitClassification(
                    type = CommitType.FIX,
                    scope = extractScope(message),
                    description = cleanDescription(message),
                    breaking = false,
                    confidence = 0.9f,
                    features = mapOf("keyword_match" to 1.0f)
                )
            }
            lowerMessage.startsWith("docs") -> {
                CommitClassification(
                    type = CommitType.DOCS,
                    scope = extractScope(message),
                    description = cleanDescription(message),
                    breaking = false,
                    confidence = 0.8f,
                    features = mapOf("keyword_match" to 1.0f)
                )
            }
            else -> {
                CommitClassification(
                    type = CommitType.CHORE,
                    scope = extractScope(message),
                    description = cleanDescription(message),
                    breaking = false,
                    confidence = 0.3f,
                    features = mapOf("default" to 1.0f)
                )
            }
        }
    }
    
    private fun createChangelogEntry(
        commits: List<GitCommit>,
        classifications: List<CommitClassification>,
        fromVersion: Version?,
        toVersion: Version?,
        repository: String,
        config: ChangelogConfig?
    ): ChangelogEntry {
        val targetVersion = toVersion ?: Version(1, 0, 0, null, null, null)
        val groupedCommits = when (config?.groupBy) {
            GroupingStrategy.TYPE -> classifications.zip(commits).groupBy { it.first.type }
            GroupingStrategy.SCOPE -> classifications.zip(commits).groupBy { it.first.scope ?: "General" }
            GroupingStrategy.DATE -> commits.groupBy { it.timestamp.toDateString() }
            else -> mapOf("All" to commits.zip(classifications))
        }
        
        val summary = ChangelogSummary(
            additions = commits.sumOf { it.stats?.additions ?: 0 },
            deletions = commits.sumOf { it.stats?.deletions ?: 0 },
            filesChanged = commits.flatMap { it.files?.map { it.filename } ?: emptyList() }.toSet().size,
            commitsCount = commits.size,
            breakingChanges = classifications.count { it.breaking },
            features = classifications.count { it.type == CommitType.FEATURE },
            fixes = classifications.count { it.type == CommitType.FIX }
        )
        
        // 获取贡献者列表
        val contributors = commits.map { it.author.name }
            .distinct()
            .map { User(
                id = 0, login = it, name = it, email = null, 
                avatarUrl = "", htmlUrl = "", type = UserType.USER,
                siteAdmin = false, company = null, location = null,
                publicRepos = 0, publicGists = 0, followers = 0,
                following = 0, createdAt = Date()
            ) }
        
        return ChangelogEntry(
            version = targetVersion,
            date = Date(),
            commits = commits,
            groupedCommits = groupedCommits.mapValues { it.value.map { it.second } },
            summary = summary,
            contributors = contributors
        )
    }
    
    private fun formatReleaseNotes(
        version: Version,
        commits: List<GitCommit>,
        classifications: List<CommitClassification>,
        includeContributors: Boolean
    ): String {
        val sb = StringBuilder()
        
        sb.append("# Release Notes for Version ${version}\n\n")
        
        // 按类型分组
        val grouped = classifications.zip(commits).groupBy { it.first.type }
        
        for (type in CommitType.values()) {
            val typeCommits = grouped[type] ?: continue
            
            if (typeCommits.isEmpty()) continue
            
            sb.append("## ${type.emoji} ${type.label}\n\n")
            
            typeCommits.forEach { (classification, commit) ->
                val breakingMark = if (classification.breaking) "🚨 " else ""
                val scope = if (classification.scope != null) "(${classification.scope}) " else ""
                sb.append("- $breakingMark${scope}${classification.description}\n")
            }
            
            sb.append("\n")
        }
        
        if (includeContributors) {
            sb.append("## Contributors\n\n")
            val contributors = commits.map { it.author.name }.distinct()
            contributors.forEach { contributor ->
                sb.append("- @$contributor\n")
            }
            sb.append("\n")
        }
        
        // 统计信息
        sb.append("## Statistics\n\n")
        sb.append("- Total commits: ${commits.size}\n")
        sb.append("- Lines added: ${commits.sumOf { it.stats?.additions ?: 0 }}\n")
        sb.append("- Lines deleted: ${commits.sumOf { it.stats?.deletions ?: 0 }}\n")
        sb.append("- Breaking changes: ${classifications.count { it.breaking }}\n")
        
        return sb.toString()
    }
    
    private fun formatChangelogForGitHub(changelog: ChangelogEntry): String {
        return formatReleaseNotes(
            version = changelog.version,
            commits = changelog.commits,
            classifications = changelog.commits.map { classifySingleCommit(it.message) },
            includeContributors = true
        )
    }
    
    private suspend fun getCommitsBetweenVersions(
        repository: String,
        fromVersion: Version,
        toVersion: Version
    ): Result<List<GitCommit>> {
        return try {
            val commits = gitRepository.getCommitHistory(
                path = "/tmp/$repository",
                maxCount = 200
            )
            
            if (commits.isFailure) {
                return Result.failure(commits.exceptionOrNull()!!)
            }
            
            var commitList = commits.getOrNull()!!
            
            // 简化处理：过滤包含版本号的提交
            commitList = commitList.filter { commit ->
                commit.message.contains(fromVersion.toString()) || 
                commit.message.contains(toVersion.toString())
            }
            
            Result.success(commitList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun generateDiffStats(
        commits: List<GitCommit>,
        classifications: List<CommitClassification>
    ): DiffStatistics {
        return DiffStatistics(
            totalCommits = commits.size,
            totalAdditions = commits.sumOf { it.stats?.additions ?: 0 },
            totalDeletions = commits.sumOf { it.stats?.deletions ?: 0 },
            filesChanged = commits.flatMap { it.files?.map { it.filename } ?: emptyList() }.toSet().size,
            breakingChanges = classifications.count { it.breaking },
            typeDistribution = classifications.groupingBy { it.type }.eachCount()
        )
    }
    
    private fun extractScope(message: String): String? {
        val match = Regex("\\(([^)]+)\\)").find(message)
        return match?.groupValues?.get(1)
    }
    
    private fun cleanDescription(message: String): String {
        return message
            .replace(Regex("^[^:]+:\\s*"), "")
            .replace(Regex("\\([^)]+\\)"), "")
            .trim()
    }
    
    private fun Date.toDateString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd").format(this)
    }
}

/**
 * 差异报告
 */
data class DiffReport(
    val fromVersion: Version,
    val toVersion: Version,
    val commits: List<GitCommit>,
    val classifications: List<CommitClassification>,
    val statistics: DiffStatistics
)

/**
 * 差异统计
 */
data class DiffStatistics(
    val totalCommits: Int,
    val totalAdditions: Int,
    val totalDeletions: Int,
    val filesChanged: Int,
    val breakingChanges: Int,
    val typeDistribution: Map<CommitType, Int>
)