package com.codemate.features.github.data.repository

import com.codemate.features.github.domain.model.*
import com.codemate.features.github.domain.repository.ChangelogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

/**
 * CHANGELOG Repository实现
 * 提供自动生成变更日志功能
 */
class ChangelogRepositoryImpl : ChangelogRepository {
    
    private val changelogEntries = ConcurrentHashMap<String, ChangelogEntry>()
    private val versionHistory = ConcurrentHashMap<String, MutableList<Version>>()
    private val releaseHistory = ConcurrentHashMap<String, MutableList<ReleaseInfo>>()
    
    override suspend fun generateChangelog(config: ChangelogConfig): Result<ChangelogEntry> {
        return withContext(Dispatchers.Default) {
            try {
                val entry = generateChangelogEntry(config)
                changelogEntries[entry.version.toString()] = entry
                Result.success(entry)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getChangelogHistory(
        repository: String,
        limit: Int
    ): Result<List<ChangelogEntry>> {
        return withContext(Dispatchers.IO) {
            try {
                val entries = changelogEntries.values
                    .sortedByDescending { it.date }
                    .take(limit)
                
                Result.success(entries)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun publishChangelog(
        repository: String,
        changelog: ChangelogEntry,
        asRelease: Boolean
    ): Result<ReleaseInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val releaseInfo = createReleaseInfo(changelog, repository, asRelease)
                
                // 保存发布信息
                releaseHistory.computeIfAbsent(repository) { mutableListOf() }
                    .add(releaseInfo)
                
                // 更新版本历史
                versionHistory.computeIfAbsent(repository) { mutableListOf() }
                    .add(changelog.version)
                
                Result.success(releaseInfo)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getVersions(
        repository: String,
        limit: Int
    ): Result<List<Version>> {
        return withContext(Dispatchers.IO) {
            try {
                val versions = versionHistory[repository]
                    ?.sortedWith(compareByDescending<Version> { it.major }
                        .thenByDescending { it.minor }
                        .thenByDescending { it.patch })
                    ?.take(limit)
                    ?: emptyList()
                
                Result.success(versions)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun createVersion(
        repository: String,
        version: Version,
        changelog: ChangelogEntry
    ): Result<ReleaseInfo> {
        return withContext(Dispatchers.IO) {
            try {
                val releaseInfo = createReleaseInfo(changelog, repository, true)
                
                // 保存版本和发布信息
                versionHistory.computeIfAbsent(repository) { mutableListOf() }
                    .add(version)
                
                releaseHistory.computeIfAbsent(repository) { mutableListOf() }
                    .add(releaseInfo)
                
                Result.success(releaseInfo)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun validateVersion(versionString: String): Result<Version> {
        return withContext(Dispatchers.Default) {
            try {
                val version = Version.fromString(versionString)
                Result.success(version)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    override suspend fun getNextVersion(
        repository: String,
        type: ReleaseType
    ): Result<Version> {
        return withContext(Dispatchers.IO) {
            try {
                val versions = versionHistory[repository]
                    ?.sortedWith(compareByDescending<Version> { it.major }
                        .thenByDescending { it.minor }
                        .thenByDescending { it.patch })
                
                val currentVersion = versions?.firstOrNull()
                val nextVersion = currentVersion?.next(type) 
                    ?: when (type) {
                        ReleaseType.MAJOR -> Version(1, 0, 0, null, null, null)
                        ReleaseType.MINOR -> Version(0, 1, 0, null, null, null)
                        ReleaseType.PATCH -> Version(0, 0, 1, null, null, null)
                        ReleaseType.PRERELEASE -> Version(0, 0, 1, "alpha.1", null, null)
                    }
                
                Result.success(nextVersion)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    private fun generateChangelogEntry(config: ChangelogConfig): ChangelogEntry {
        // 模拟生成提交历史
        val mockCommits = generateMockCommits()
        
        // 过滤提交类型
        val filteredCommits = mockCommits.filter { commit ->
            when {
                config.includeTypes.contains(commitType) -> true
                config.excludeTypes.contains(commitType) -> false
                else -> config.includeTypes.contains(commitType)
            }
        }
        
        // 分组策略
        val groupedCommits = when (config.groupBy) {
            GroupingStrategy.TYPE -> {
                filteredCommits.groupBy { getCommitType(it.message) }
                    .mapValues { it.value.map { commit -> commit } }
            }
            GroupingStrategy.SCOPE -> {
                filteredCommits.groupBy { extractScope(it.message) ?: "General" }
                    .mapValues { it.value.map { commit -> commit } }
            }
            GroupingStrategy.DATE -> {
                filteredCommits.groupBy { it.timestamp.toDateString() }
                    .mapValues { it.value.map { commit -> commit } }
            }
            else -> mapOf("All" to filteredCommits)
        }
        
        // 生成摘要
        val summary = ChangelogSummary(
            additions = filteredCommits.sumOf { it.stats?.additions ?: 0 },
            deletions = filteredCommits.sumOf { it.stats?.deletions ?: 0 },
            filesChanged = filteredCommits.flatMap { it.files?.map { it.filename } ?: emptyList() }
                .toSet().size,
            commitsCount = filteredCommits.size,
            breakingChanges = filteredCommits.count { it.message.contains("BREAKING") },
            features = filteredCommits.count { it.message.contains("feat") },
            fixes = filteredCommits.count { it.message.contains("fix") }
        )
        
        // 生成贡献者列表
        val contributors = filteredCommits.map { it.author.name }
            .distinct()
            .map { name ->
                User(
                    id = Random.nextLong(),
                    login = name,
                    name = name,
                    email = "$name@example.com",
                    avatarUrl = "https://avatars.githubusercontent.com/u/${Random.nextInt(1000, 9999)}",
                    htmlUrl = "https://github.com/$name",
                    type = UserType.USER,
                    siteAdmin = false,
                    company = null,
                    location = null,
                    publicRepos = Random.nextInt(0, 100),
                    publicGists = Random.nextInt(0, 20),
                    followers = Random.nextInt(0, 1000),
                    following = Random.nextInt(0, 500),
                    createdAt = java.util.Date()
                )
            }
        
        return ChangelogEntry(
            version = config.toVersion,
            date = java.util.Date(),
            commits = filteredCommits,
            groupedCommits = groupedCommits,
            summary = summary,
            contributors = contributors
        )
    }
    
    private fun generateMockCommits(): List<GitCommit> {
        val commitMessages = listOf(
            "feat: add user authentication feature",
            "fix: resolve memory leak in data processing",
            "docs: update API documentation",
            "style: improve code formatting",
            "refactor: optimize database queries",
            "perf: enhance loading performance",
            "test: add unit tests for auth module",
            "chore: update dependencies",
            "build: fix build pipeline",
            "ci: update GitHub Actions workflow",
            "feat(ui): add dark mode toggle",
            "fix(api): handle edge cases in validation",
            "BREAKING CHANGE: remove deprecated user endpoint",
            "docs: add migration guide",
            "refactor(controller): simplify user management"
        )
        
        return commitMessages.mapIndexed { index, message ->
            val stats = CommitStats(
                additions = Random.nextInt(1, 50),
                deletions = Random.nextInt(0, 20),
                total = Random.nextInt(1, 70)
            )
            
            val files = listOfNotNull(
                CommitFile(
                    filename = "src/main/java/com/example/UserService.java",
                    status = FileStatus.MODIFIED,
                    additions = stats.additions,
                    deletions = stats.deletions,
                    changes = stats.total,
                    patch = "@@ -1,10 +1,10 @@"
                ),
                CommitFile(
                    filename = "README.md",
                    status = FileStatus.MODIFIED,
                    additions = 5,
                    deletions = 2,
                    changes = 7,
                    patch = "@@ -15,5 +15,5 @@"
                )
            )
            
            GitCommit(
                sha = generateSampleSha(),
                message = message,
                author = CommitAuthor(
                    name = "Developer ${Random.nextInt(1, 10)}",
                    email = "dev${Random.nextInt(1, 10)}@example.com",
                    date = java.util.Date(System.currentTimeMillis() - index * 24 * 60 * 60 * 1000)
                ),
                committer = CommitAuthor(
                    name = "Developer ${Random.nextInt(1, 10)}",
                    email = "dev${Random.nextInt(1, 10)}@example.com",
                    date = java.util.Date(System.currentTimeMillis() - index * 24 * 60 * 60 * 1000)
                ),
                parents = listOf(generateSampleSha()),
                tree = generateSampleSha(),
                url = "https://api.github.com/repos/example/repo/commits/${generateSampleSha()}",
                stats = stats,
                files = files,
                timestamp = java.util.Date(System.currentTimeMillis() - index * 24 * 60 * 60 * 1000),
                branch = "main",
                repository = "example/repo"
            )
        }
    }
    
    private fun getCommitType(message: String): String {
        return when {
            message.contains("feat") -> "✨ Features"
            message.contains("fix") -> "🐛 Bug Fixes"
            message.contains("docs") -> "📚 Documentation"
            message.contains("style") -> "🎨 Style"
            message.contains("refactor") -> "♻️ Refactoring"
            message.contains("perf") -> "⚡ Performance"
            message.contains("test") -> "✅ Tests"
            message.contains("chore") -> "🔧 Chores"
            message.contains("build") -> "🏗️ Build"
            message.contains("ci") -> "👷 CI"
            message.contains("BREAKING") -> "🚨 Breaking Changes"
            else -> "📦 Other"
        }
    }
    
    private fun extractScope(message: String): String? {
        val match = Regex("\\(([^)]+)\\)").find(message)
        return match?.groupValues?.get(1)
    }
    
    private fun createReleaseInfo(
        changelog: ChangelogEntry,
        repository: String,
        asRelease: Boolean
    ): ReleaseInfo {
        val releaseName = "Version ${changelog.version}"
        val body = formatChangelogForGitHub(changelog)
        
        return ReleaseInfo(
            version = changelog.version,
            tag = "v${changelog.version}",
            name = releaseName,
            body = body,
            draft = !asRelease,
            prerelease = changelog.version.isPrerelease(),
            createdAt = java.util.Date(),
            publishedAt = if (asRelease) java.util.Date() else null,
            author = User(
                id = 1,
                login = "codemate-bot",
                name = "CodeMate Bot",
                email = "bot@codemate.com",
                avatarUrl = "https://avatars.githubusercontent.com/u/1",
                htmlUrl = "https://github.com/codemate-bot",
                type = UserType.USER,
                siteAdmin = false,
                company = "CodeMate",
                location = "Global",
                publicRepos = 100,
                publicGists = 10,
                followers = 1000,
                following = 100,
                createdAt = java.util.Date()
            ),
            assets = listOf(
                ReleaseAsset(
                    id = Random.nextLong(),
                    name = "codemate-mobile-${changelog.version}.apk",
                    size = Random.nextLong(1024 * 1024, 50 * 1024 * 1024), // 1MB to 50MB
                    downloadCount = Random.nextInt(0, 1000),
                    createdAt = java.util.Date(),
                    updatedAt = java.util.Date(),
                    browserDownloadUrl = "https://github.com/$repository/releases/download/v${changelog.version}/codemate-mobile-${changelog.version}.apk",
                    contentType = "application/vnd.android.package-archive"
                )
            ),
            htmlUrl = "https://github.com/$repository/releases/tag/v${changelog.version}",
            tarballUrl = "https://github.com/$repository/archive/v${changelog.version}.tar.gz",
            zipballUrl = "https://github.com/$repository/archive/v${changelog.version}.zip"
        )
    }
    
    private fun formatChangelogForGitHub(changelog: ChangelogEntry): String {
        val sb = StringBuilder()
        
        sb.append("# ${changelog.version}\n\n")
        sb.append("**Release Date:** ${formatDate(changelog.date)}\n\n")
        sb.append("## Summary\n\n")
        sb.append("- **Total Commits:** ${changelog.summary.commitsCount}\n")
        sb.append("- **Files Changed:** ${changelog.summary.filesChanged}\n")
        sb.append("- **Lines Added:** ${changelog.summary.additions}\n")
        sb.append("- **Lines Deleted:** ${changelog.summary.deletions}\n")
        sb.append("- **Breaking Changes:** ${changelog.summary.breakingChanges}\n\n")
        
        sb.append("## What's Changed\n\n")
        
        changelog.groupedCommits.forEach { (category, commits) ->
            if (commits.isNotEmpty()) {
                sb.append("### $category\n\n")
                commits.forEach { commit ->
                    val type = when {
                        commit.message.contains("feat") -> "✨"
                        commit.message.contains("fix") -> "🐛"
                        commit.message.contains("docs") -> "📚"
                        commit.message.contains("BREAKING") -> "🚨"
                        else -> "📦"
                    }
                    sb.append("- $type ${commit.message}\n")
                }
                sb.append("\n")
            }
        }
        
        if (changelog.contributors.isNotEmpty()) {
            sb.append("## Contributors\n\n")
            changelog.contributors.forEach { contributor ->
                sb.append("- @${contributor.login}\n")
            }
            sb.append("\n")
        }
        
        sb.append("## Assets\n\n")
        sb.append("- [Download APK](https://github.com/owner/repo/releases/download/v${changelog.version}/codemate-mobile-${changelog.version}.apk)\n")
        sb.append("- [View on GitHub](https://github.com/owner/repo/releases/tag/v${changelog.version})\n")
        
        return sb.toString()
    }
    
    private fun formatDate(date: java.util.Date): String {
        return java.text.SimpleDateFormat("MMMM dd, yyyy").format(date)
    }
    
    private fun generateSampleSha(): String {
        val chars = "0123456789abcdef"
        return (1..40).map { chars[Random.nextInt(chars.length)] }.joinToString("")
    }
    
    private fun Date.toDateString(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd").format(this)
    }
    
    /**
     * 获取CHANGELOG统计信息
     */
    suspend fun getChangelogStatistics(repository: String): ChangelogStatistics {
        val releases = releaseHistory[repository] ?: emptyList()
        val versions = versionHistory[repository] ?: emptyList()
        
        val totalReleases = releases.size
        val totalVersions = versions.size
        val latestVersion = versions.maxOrNull()
        
        val releasesThisYear = releases.count { release ->
            release.createdAt.year == java.util.Date().year
        }
        
        val averageCommitsPerRelease = if (totalReleases > 0) {
            releases.mapNotNull { release ->
                changelogEntries[release.version.toString()]
            }.map { it.summary.commitsCount }
                .average()
        } else {
            0.0
        }
        
        return ChangelogStatistics(
            totalReleases = totalReleases,
            totalVersions = totalVersions,
            latestVersion = latestVersion,
            releasesThisYear = releasesThisYear,
            averageCommitsPerRelease = averageCommitsPerRelease,
            mostActiveMonth = getMostActiveMonth(releases)
        )
    }
    
    private fun getMostActiveMonth(releases: List<ReleaseInfo>): String {
        val monthCounts = releases.groupBy { 
            java.text.SimpleDateFormat("yyyy-MM").format(it.createdAt) 
        }.mapValues { it.value.size }
        
        return monthCounts.maxByOrNull { it.value }?.key ?: "N/A"
    }
    
    /**
     * 清理过期的CHANGELOG条目
     */
    suspend fun cleanupOldEntries(olderThanDays: Int = 90) {
        val cutoffTime = java.util.Date(System.currentTimeMillis() - olderThanDays * 24 * 60 * 60 * 1000)
        changelogEntries.entries.removeAll { it.value.date.before(cutoffTime) }
    }
}

/**
 * CHANGELOG统计信息
 */
data class ChangelogStatistics(
    val totalReleases: Int,
    val totalVersions: Int,
    val latestVersion: Version?,
    val releasesThisYear: Int,
    val averageCommitsPerRelease: Double,
    val mostActiveMonth: String
)