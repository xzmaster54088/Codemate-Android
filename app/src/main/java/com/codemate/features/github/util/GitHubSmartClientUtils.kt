package com.codemate.features.github.util

import com.codemate.features.github.domain.model.*

/**
 * GitHub智能客户端工具类
 * 提供各种辅助功能
 */
object GitHubSmartClientUtils {
    
    /**
     * 验证GitHub URL格式
     */
    fun isValidGitHubUrl(url: String): Boolean {
        val githubUrlPattern = Regex("https?://github\\.com/[\\w-]+/[\\w.-]+(?:\\.git)?")
        return githubUrlPattern.matches(url)
    }
    
    /**
     * 从GitHub URL中提取owner和repo
     */
    fun extractOwnerAndRepo(githubUrl: String): Pair<String, String>? {
        val match = Regex("https?://github\\.com/([^/]+)/([^/]+)").find(githubUrl)
        return match?.let {
            val owner = it.groupValues[1]
            val repo = it.groupValues[2].removeSuffix(".git")
            Pair(owner, repo)
        }
    }
    
    /**
     * 生成默认的Git配置
     */
    fun createDefaultGitConfig(userName: String, userEmail: String): GitConfig {
        return GitConfig(
            userName = userName,
            userEmail = userEmail,
            remoteUrl = "",
            defaultBranch = "main",
            editor = null
        )
    }
    
    /**
     * 格式化文件大小
     */
    fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.1f %s", size, units[unitIndex])
    }
    
    /**
     * 格式化相对时间
     */
    fun formatRelativeTime(date: java.util.Date): String {
        val now = java.util.Date()
        val diffInSeconds = (now.time - date.time) / 1000
        
        return when {
            diffInSeconds < 60 -> "just now"
            diffInSeconds < 3600 -> "${diffInSeconds / 60} minutes ago"
            diffInSeconds < 86400 -> "${diffInSeconds / 3600} hours ago"
            diffInSeconds < 2592000 -> "${diffInSeconds / 86400} days ago"
            diffInSeconds < 31536000 -> "${diffInSeconds / 2592000} months ago"
            else -> "${diffInSeconds / 31536000} years ago"
        }
    }
    
    /**
     * 获取提交类型对应的颜色
     */
    fun getCommitTypeColor(commitType: CommitType): String {
        return when (commitType) {
            CommitType.FEATURE -> "#10B981"
            CommitType.FIX -> "#EF4444"
            CommitType.DOCS -> "#3B82F6"
            CommitType.STYLE -> "#F59E0B"
            CommitType.REFACTOR -> "#6B7280"
            CommitType.PERF -> "#F97316"
            CommitType.TEST -> "#22C55E"
            CommitType.CHORE -> "#64748B"
            CommitType.BUILD -> "#DC2626"
            CommitType.CI -> "#7C3AED"
            CommitType.REVERT -> "#EF4444"
            CommitType.MERGE -> "#8B5CF6"
        }
    }
    
    /**
     * 获取严重程度对应的颜色
     */
    fun getSeverityColor(severity: SuggestionSeverity): String {
        return when (severity) {
            SuggestionSeverity.INFO -> "#3B82F6"
            SuggestionSeverity.WARNING -> "#F59E0B"
            SuggestionSeverity.ERROR -> "#EF4444"
            SuggestionSeverity.CRITICAL -> "#DC2626"
        }
    }
    
    /**
     * 生成随机颜色
     */
    fun generateRandomColor(): String {
        val colors = listOf(
            "#FF6B6B", "#4ECDC4", "#45B7D1", "#96CEB4", "#FECA57",
            "#FF9FF3", "#54A0FF", "#5F27CD", "#00D2D3", "#FF9F43",
            "#686DE0", "#4834D4", "#30336B", "#130F40", "#6C5CE7"
        )
        return colors.random()
    }
    
    /**
     * 验证Git配置
     */
    fun validateGitConfig(config: GitConfig): List<String> {
        val errors = mutableListOf<String>()
        
        if (config.userName.isBlank()) {
            errors.add("User name is required")
        }
        
        if (config.userEmail.isBlank()) {
            errors.add("User email is required")
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(config.userEmail).matches()) {
            errors.add("Invalid email format")
        }
        
        return errors
    }
    
    /**
     * 创建默认协作设置
     */
    fun createDefaultCollaborationSettings(): CollaborationSettings {
        return CollaborationSettings(
            maxParticipants = 10,
            allowGuests = false,
            requireApproval = false,
            autoSave = true,
            realTimeSync = true,
            showCursors = true
        )
    }
    
    /**
     * 计算代码质量评分颜色
     */
    fun getQualityScoreColor(score: Float): String {
        return when {
            score >= 80 -> "#22C55E" // Green
            score >= 60 -> "#F59E0B" // Orange
            score >= 40 -> "#EF4444" // Red
            else -> "#6B7280" // Gray
        }
    }
    
    /**
     * 格式化代码质量评分
     */
    fun formatQualityScore(score: Float): String {
        return String.format("%.1f%%", score)
    }
    
    /**
     * 生成随机ID
     */
    fun generateRandomId(prefix: String = "id"): String {
        val timestamp = System.currentTimeMillis()
        val random = kotlin.random.Random.nextInt(1000, 9999)
        return "${prefix}_${timestamp}_$random"
    }
    
    /**
     * 清理字符串，移除危险字符
     */
    fun sanitizeInput(input: String): String {
        return input
            .replace(Regex("[<>\"'&]"), "") // 移除危险字符
            .trim()
            .take(1000) // 限制长度
    }
    
    /**
     * 检查是否为有效的版本号
     */
    fun isValidVersion(version: String): Boolean {
        return try {
            Version.fromString(version)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 生成语义化的提交消息模板
     */
    fun generateCommitMessageTemplate(type: CommitType, scope: String? = null): String {
        val typePrefix = when (type) {
            CommitType.FEATURE -> "feat"
            CommitType.FIX -> "fix"
            CommitType.DOCS -> "docs"
            CommitType.STYLE -> "style"
            CommitType.REFACTOR -> "refactor"
            CommitType.PERF -> "perf"
            CommitType.TEST -> "test"
            CommitType.CHORE -> "chore"
            CommitType.BUILD -> "build"
            CommitType.CI -> "ci"
            CommitType.REVERT -> "revert"
            CommitType.MERGE -> "merge"
        }
        
        val scopePart = scope?.let { "($it)" } ?: ""
        return "${typePrefix}${scopePart}: your commit message here"
    }
    
    /**
     * 获取文件图标
     */
    fun getFileIcon(filename: String): String {
        return when {
            filename.endsWith(".java") -> "☕"
            filename.endsWith(".kt") -> "🟪"
            filename.endsWith(".js") -> "🟨"
            filename.endsWith(".ts") -> "🔷"
            filename.endsWith(".py") -> "🐍"
            filename.endsWith(".go") -> "🔵"
            filename.endsWith(".rs") -> "🦀"
            filename.endsWith(".cpp") || filename.endsWith(".cc") || filename.endsWith(".cxx") -> "⚙️"
            filename.endsWith(".c") -> "🔧"
            filename.endsWith(".html") -> "🌐"
            filename.endsWith(".css") -> "🎨"
            filename.endsWith(".scss") || filename.endsWith(".sass") -> "💅"
            filename.endsWith(".xml") -> "📄"
            filename.endsWith(".json") -> "📋"
            filename.endsWith(".md") -> "📝"
            filename.endsWith(".txt") -> "📃"
            filename.endsWith(".yml") || filename.endsWith(".yaml") -> "⚙️"
            filename.endsWith(".sh") -> "💻"
            filename.endsWith(".bat") || filename.endsWith(".cmd") -> "💻"
            filename.endsWith(".apk") -> "📱"
            filename.endsWith(".ipa") -> "📱"
            filename.endsWith(".exe") -> "💻"
            filename.endsWith(".dmg") -> "💻"
            filename.endsWith(".zip") || filename.endsWith(".tar") || filename.endsWith(".gz") -> "📦"
            else -> "📄"
        }
    }
    
    /**
     * 计算分支差异
     */
    fun calculateBranchDifference(
        aheadBy: Int,
        behindBy: Int
    ): Pair<String, Int> {
        return when {
            aheadBy > 0 && behindBy > 0 -> Pair("分歧", aheadBy + behindBy)
            aheadBy > 0 -> Pair("超前", aheadBy)
            behindBy > 0 -> Pair("落后", behindBy)
            else -> Pair("同步", 0)
        }
    }
    
    /**
     * 生成随机用户名
     */
    fun generateRandomUsername(): String {
        val adjectives = listOf(
            "Cool", "Smart", "Fast", "Creative", "Brilliant", "Amazing",
            "Epic", "Legendary", "Awesome", "Fantastic", "Incredible", "Wonderful"
        )
        
        val nouns = listOf(
            "Developer", "Coder", "Hacker", "Builder", "Creator", "Designer",
            "Architect", "Engineer", "Artist", "Writer", "Thinker", "Innovator"
        )
        
        val adjective = adjectives.random()
        val noun = nouns.random()
        val number = kotlin.random.Random.nextInt(100, 999)
        
        return "$adjective$noun$number"
    }
}