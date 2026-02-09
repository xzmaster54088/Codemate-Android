package com.codemate.features.github.data.remote

import com.codemate.features.github.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.Date

/**
 * Git命令执行器
 * 封装Git命令操作，支持所有常用的Git操作
 */
class GitCommandExecutor {
    
    private val gitCommand: String = "git"
    
    /**
     * 初始化Git仓库
     */
    suspend fun initializeRepository(
        path: String,
        config: GitConfig
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val result = executeCommand(path, "init")
                if (result.success) {
                    // 配置Git用户信息
                    configureUser(path, config)
                }
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 克隆仓库
     */
    suspend fun cloneRepository(
        url: String,
        path: String,
        branch: String? = null
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildString {
                    append("clone ")
                    append("\"$url\" ")
                    append("\"$path\"")
                    branch?.let {
                        append(" -b $it")
                    }
                }
                
                val result = executeCommand(System.getProperty("user.home"), command)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 添加文件到暂存区
     */
    suspend fun addFiles(
        path: String,
        files: List<String>
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val fileList = files.joinToString(" ") { "\"$it\"" }
                val command = "add $fileList"
                
                val result = executeCommand(path, command)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 提交更改
     */
    suspend fun commitChanges(
        path: String,
        message: String,
        author: String? = null
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val command = "commit -m \"$message\""
                val result = executeCommand(path, command, author = author)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 推送更改
     */
    suspend fun pushChanges(
        path: String,
        branch: String? = null,
        force: Boolean = false
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildString {
                    append("push")
                    if (force) append(" --force")
                    branch?.let { append(" origin $it") }
                }
                
                val result = executeCommand(path, command)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 拉取更改
     */
    suspend fun pullChanges(
        path: String,
        branch: String? = null,
        rebase: Boolean = false
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildString {
                    append("pull")
                    if (rebase) append(" --rebase")
                    branch?.let { append(" origin $it") }
                }
                
                val result = executeCommand(path, command)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 获取仓库状态
     */
    suspend fun getStatus(path: String): Result<GitStatus> {
        return withContext(Dispatchers.IO) {
            try {
                val statusResult = executeCommand(path, "status --porcelain")
                
                if (!statusResult.success) {
                    return@withContext Result.failure(Exception(statusResult.error))
                }
                
                val status = parseGitStatus(statusResult.output, path)
                Result.success(status)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 创建分支
     */
    suspend fun createBranch(
        path: String,
        branchName: String,
        checkout: Boolean = true
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildString {
                    append("branch $branchName")
                    if (checkout) append(" && git checkout $branchName")
                }
                
                val result = executeCommand(path, command)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 切换分支
     */
    suspend fun checkoutBranch(
        path: String,
        branchName: String,
        create: Boolean = false
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val command = if (create) {
                    "checkout -b $branchName"
                } else {
                    "checkout $branchName"
                }
                
                val result = executeCommand(path, command)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 合并分支
     */
    suspend fun mergeBranch(
        path: String,
        sourceBranch: String,
        targetBranch: String? = null
    ): Result<MergeResult> {
        return withContext(Dispatchers.IO) {
            try {
                // 如果指定了目标分支，先切换过去
                if (targetBranch != null) {
                    val checkoutResult = executeCommand(path, "checkout $targetBranch")
                    if (!checkoutResult.success) {
                        return@withContext Result.failure(Exception(checkoutResult.error))
                    }
                }
                
                val mergeResult = executeCommand(path, "merge $sourceBranch --no-ff --no-commit")
                
                val conflicts = if (mergeResult.output.contains("CONFLICT")) {
                    parseConflicts(mergeResult.output)
                } else {
                    emptyList()
                }
                
                val success = conflicts.isEmpty() && mergeResult.success
                
                Result.success(
                    MergeResult(
                        success = success,
                        commitSha = if (success) extractCommitSha(mergeResult.output) else null,
                        conflicts = if (conflicts.isNotEmpty()) conflicts else null,
                        message = if (success) "Merge completed successfully" 
                                else "Merge conflicts detected"
                    )
                )
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 获取分支列表
     */
    suspend fun getBranches(path: String): Result<List<GitBranch>> {
        return withContext(Dispatchers.IO) {
            try {
                val result = executeCommand(path, "branch -av --no-color")
                
                if (!result.success) {
                    return@withContext Result.failure(Exception(result.error))
                }
                
                val branches = parseBranches(result.output)
                Result.success(branches)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 获取提交历史
     */
    suspend fun getCommitHistory(
        path: String,
        branch: String? = null,
        since: Date? = null,
        until: Date? = null,
        maxCount: Int = 50
    ): Result<List<GitCommit>> {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildString {
                    append("log --pretty=format:\"%H|%an|%ae|%ad|%s|%b|%P|%t\" --date=iso --max-count=$maxCount")
                    branch?.let { append(" $it") }
                    since?.let { append(" --since=\"${it.toISO8601()}\"") }
                    until?.let { append(" --until=\"${it.toISO8601()}\"") }
                }
                
                val result = executeCommand(path, command)
                
                if (!result.success) {
                    return@withContext Result.failure(Exception(result.error))
                }
                
                val commits = parseCommitHistory(result.output)
                Result.success(commits)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 获取特定提交
     */
    suspend fun getCommit(path: String, sha: String): Result<GitCommit> {
        return withContext(Dispatchers.IO) {
            try {
                val command = "show --pretty=format:\"%H|%an|%ae|%ad|%s|%b|%P|%t\" --date=iso --name-status --stat $sha"
                
                val result = executeCommand(path, command)
                
                if (!result.success) {
                    return@withContext Result.failure(Exception(result.error))
                }
                
                val commit = parseSingleCommit(result.output)
                Result.success(commit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 获取差异
     */
    suspend fun getDiff(
        path: String,
        from: String? = null,
        to: String? = null,
        file: String? = null
    ): Result<GitDiff> {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildString {
                    append("diff")
                    from?.let { append(" $it") }
                    to?.let { append(" $it") }
                    file?.let { append(" -- $it") }
                    append(" --numstat")
                }
                
                val result = executeCommand(path, command)
                
                if (!result.success) {
                    return@withContext Result.failure(Exception(result.error))
                }
                
                val diff = parseGitDiff(result.output)
                Result.success(diff)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 重置更改
     */
    suspend fun resetChanges(
        path: String,
        mode: ResetMode,
        commit: String? = null
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildString {
                    append("reset")
                    append(" --${mode.name.lowercase()}")
                    commit?.let { append(" $it") }
                }
                
                val result = executeCommand(path, command)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 储藏更改
     */
    suspend fun stashChanges(
        path: String,
        message: String? = null
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildString {
                    append("stash")
                    message?.let { append(" push -m \"$it\"") }
                }
                
                val result = executeCommand(path, command)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 应用储藏
     */
    suspend fun applyStash(
        path: String,
        stashRef: String? = null
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildString {
                    append("stash apply")
                    stashRef?.let { append(" $it") }
                }
                
                val result = executeCommand(path, command)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 创建标签
     */
    suspend fun createTag(
        path: String,
        tagName: String,
        message: String? = null,
        commit: String? = null
    ): Result<GitCommandResult> {
        return withContext(Dispatchers.IO) {
            try {
                val command = buildString {
                    append("tag")
                    message?.let { append(" -a $tagName -m \"$it\"") }
                    commit?.let { append(" $it") }
                }
                
                val result = executeCommand(path, command)
                Result.success(result)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 获取标签列表
     */
    suspend fun getTags(path: String): Result<List<GitTag>> {
        return withContext(Dispatchers.IO) {
            try {
                val result = executeCommand(path, "tag -l --format=\"%(refname:short)|%(objectname)|%(objecttype)|%(taggerdate:iso)|%(taggername)|%(taggeremail)|%(contents)\"")
                
                if (!result.success) {
                    return@withContext Result.failure(Exception(result.error))
                }
                
                val tags = parseTags(result.output)
                Result.success(tags)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
    
    /**
     * 执行Git命令的核心方法
     */
    private suspend fun executeCommand(
        workingDir: String,
        command: String,
        author: String? = null
    ): GitCommandResult = withContext(Dispatchers.IO) {
        try {
            val processBuilder = ProcessBuilder(gitCommand, *command.split(" ").toTypedArray())
            processBuilder.directory(File(workingDir))
            processBuilder.redirectErrorStream(true)
            
            val process = processBuilder.start()
            
            val output = StringBuilder()
            val error = StringBuilder()
            
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            
            val exitCode = process.waitFor()
            val success = exitCode == 0
            
            GitCommandResult(
                success = success,
                output = output.toString().trim(),
                error = if (success) null else error.toString().trim(),
                exitCode = exitCode,
                command = "$gitCommand $command",
                timestamp = Date()
            )
        } catch (e: Exception) {
            GitCommandResult(
                success = false,
                output = "",
                error = e.message,
                exitCode = -1,
                command = "$gitCommand $command",
                timestamp = Date()
            )
        }
    }
    
    /**
     * 配置Git用户信息
     */
    private suspend fun configureUser(
        path: String,
        config: GitConfig
    ): GitCommandResult {
        val nameResult = executeCommand(path, "config user.name \"${config.userName}\"")
        val emailResult = executeCommand(path, "config user.email \"${config.userEmail}\"")
        
        return GitCommandResult(
            success = nameResult.success && emailResult.success,
            output = "Git user configured",
            error = if (!nameResult.success) nameResult.error 
                   else if (!emailResult.success) emailResult.error 
                   else null,
            exitCode = if (nameResult.success && emailResult.success) 0 else 1,
            command = "config user",
            timestamp = Date()
        )
    }
    
    /**
     * 解析Git状态
     */
    private fun parseGitStatus(output: String, path: String): GitStatus {
        val stagedFiles = mutableListOf<String>()
        val modifiedFiles = mutableListOf<String>()
        val untrackedFiles = mutableListOf<String>()
        val conflictedFiles = mutableListOf<String>()
        
        var currentBranch = "main"
        var aheadBy = 0
        var behindBy = 0
        
        val lines = output.lines()
        
        for (line in lines) {
            when {
                line.startsWith("## ") -> {
                    // 解析分支信息
                    parseBranchStatus(line, currentBranch)
                }
                line.startsWith("A ") -> {
                    stagedFiles.add(line.substring(2))
                }
                line.startsWith("M ") -> {
                    modifiedFiles.add(line.substring(2))
                }
                line.startsWith("D ") -> {
                    // 删除的文件也算作修改
                    modifiedFiles.add(line.substring(2))
                }
                line.startsWith("R ") -> {
                    val parts = line.split(" ")
                    if (parts.size >= 2) {
                        modifiedFiles.add(parts.last())
                    }
                }
                line.startsWith("C ") -> {
                    val parts = line.split(" ")
                    if (parts.size >= 2) {
                        modifiedFiles.add(parts.last())
                    }
                }
                line.startsWith("?") -> {
                    untrackedFiles.add(line.substring(2))
                }
                line.startsWith("UU") -> {
                    conflictedFiles.add(line.substring(3))
                }
            }
        }
        
        return GitStatus(
            branch = currentBranch,
            aheadBy = aheadBy,
            behindBy = behindBy,
            stagedFiles = stagedFiles,
            modifiedFiles = modifiedFiles,
            untrackedFiles = untrackedFiles,
            conflictedFiles = conflictedFiles,
            clean = stagedFiles.isEmpty() && modifiedFiles.isEmpty() && 
                   untrackedFiles.isEmpty() && conflictedFiles.isEmpty()
        )
    }
    
    private fun parseBranchStatus(line: String, currentBranch: String) {
        // 解析分支状态信息
        val match = Regex("##\\s+(\\S+)(?:\\.\\.\\.(\\S+)\\s+\\[(?:ahead\\s+(\\d+))?(?:,\\s*behind\\s+(\\d+))?\\])?").find(line)
        match?.let { matchResult ->
            currentBranch = matchResult.groupValues[1]
            // 解析ahead/behind信息
            // 这里可以添加更多解析逻辑
        }
    }
    
    private fun parseConflicts(output: String): List<String> {
        val conflicts = mutableListOf<String>()
        val lines = output.lines()
        
        for (line in lines) {
            if (line.startsWith("CONFLICT")) {
                conflicts.add(line)
            }
        }
        
        return conflicts
    }
    
    private fun extractCommitSha(output: String): String? {
        val match = Regex("\\[.*\\]\\s+([a-f0-9]{7,40})").find(output)
        return match?.groupValues?.get(1)
    }
    
    private fun parseBranches(output: String): List<GitBranch> {
        val branches = mutableListOf<GitBranch>()
        val lines = output.lines()
        
        for (line in lines) {
            if (line.isNotEmpty()) {
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 2) {
                    val name = parts[0].removePrefix("*").removePrefix("+").trim()
                    val sha = parts[1]
                    val isCurrent = parts[0].contains("*")
                    
                    branches.add(
                        GitBranch(
                            name = name,
                            sha = sha,
                            protected = false, // 需要额外查询
                            commit = GitCommit(
                                sha = sha,
                                message = "",
                                author = CommitAuthor("", "", Date()),
                                committer = CommitAuthor("", "", Date()),
                                parents = emptyList(),
                                tree = "",
                                url = "",
                                stats = null,
                                files = null,
                                timestamp = Date(),
                                branch = name,
                                repository = ""
                            ),
                            merged = null,
                            aheadBy = 0,
                            behindBy = 0
                        )
                    )
                }
            }
        }
        
        return branches
    }
    
    private fun parseCommitHistory(output: String): List<GitCommit> {
        val commits = mutableListOf<GitCommit>()
        val lines = output.lines()
        
        for (line in lines) {
            if (line.isNotEmpty()) {
                val parts = line.split("|")
                if (parts.size >= 6) {
                    commits.add(
                        GitCommit(
                            sha = parts[0],
                            message = parts[4],
                            author = CommitAuthor(parts[1], parts[2], Date(parts[3])),
                            committer = CommitAuthor(parts[1], parts[2], Date(parts[3])),
                            parents = parts[6].split(" "),
                            tree = parts[7],
                            url = "",
                            stats = null,
                            files = null,
                            timestamp = Date(parts[3]),
                            branch = "",
                            repository = ""
                        )
                    )
                }
            }
        }
        
        return commits
    }
    
    private fun parseSingleCommit(output: String): GitCommit {
        val lines = output.lines()
        val headerLine = lines[0]
        val parts = headerLine.split("|")
        
        return GitCommit(
            sha = parts[0],
            message = parts[4],
            author = CommitAuthor(parts[1], parts[2], Date(parts[3])),
            committer = CommitAuthor(parts[1], parts[2], Date(parts[3])),
            parents = parts[6].split(" "),
            tree = parts[7],
            url = "",
            stats = null,
            files = null,
            timestamp = Date(parts[3]),
            branch = "",
            repository = ""
        )
    }
    
    private fun parseGitDiff(output: String): GitDiff {
        var additions = 0
        var deletions = 0
        val files = mutableListOf<GitDiffFile>()
        
        val lines = output.lines()
        
        for (line in lines) {
            if (line.isNotEmpty() && !line.startsWith("diff")) {
                val parts = line.split("\\t".toRegex())
                if (parts.size >= 3) {
                    val fileAdditions = parts[0].toIntOrNull() ?: 0
                    val fileDeletions = parts[1].toIntOrNull() ?: 0
                    val filename = parts[2]
                    
                    additions += fileAdditions
                    deletions += fileDeletions
                    
                    files.add(
                        GitDiffFile(
                            filename = filename,
                            oldMode = null,
                            newMode = null,
                            deletedFileMode = null,
                            newFileMode = null,
                            rename = false,
                            copy = false,
                            binary = false,
                            shaBefore = null,
                            shaAfter = null,
                            patches = emptyList()
                        )
                    )
                }
            }
        }
        
        return GitDiff(
            additions = additions,
            deletions = deletions,
            files = files
        )
    }
    
    private fun parseTags(output: String): List<GitTag> {
        val tags = mutableListOf<GitTag>()
        val lines = output.lines()
        
        for (line in lines) {
            if (line.isNotEmpty()) {
                val parts = line.split("|")
                if (parts.size >= 6) {
                    tags.add(
                        GitTag(
                            name = parts[0],
                            sha = parts[1],
                            url = "",
                            message = parts[6],
                            tagger = CommitAuthor(parts[4], parts[5], Date(parts[3])),
                            object = GitObject(
                                type = ObjectType.valueOf(parts[2].uppercase()),
                                sha = parts[1],
                                url = ""
                            ),
                            verification = null
                        )
                    )
                }
            }
        }
        
        return tags
    }
    
    private fun Date.toISO8601(): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'").format(this)
    }
}