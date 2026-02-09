package com.codemate.features.github.domain.model

import java.util.Date

/**
 * Git分支实体模型
 * 支持分支操作和管理
 */
data class GitBranch(
    val name: String,
    val sha: String,
    val protected: Boolean,
    val commit: GitCommit,
    val merged: Boolean? = null,
    val baseBranch: String? = null,
    val aheadBy: Int = 0,
    val behindBy: Int = 0
)

/**
 * 分支状态枚举
 */
enum class BranchStatus {
    CLEAN,
    DIRTY,
    AHEAD,
    BEHIND,
    DIVERGED,
    CONFLICT
}

/**
 * 分支过滤器
 */
data class BranchFilter(
    val query: String? = null,
    val protected: Boolean? = null,
    val merged: Boolean? = null
)

/**
 * 分支操作类型
 */
enum class BranchOperation {
    CREATE,
    DELETE,
    SWITCH,
    MERGE,
    REBASE,
    PUSH,
    PULL
}

/**
 * Git标签实体模型
 */
data class GitTag(
    val name: String,
    val sha: String,
    val url: String,
    val message: String?,
    val tagger: CommitAuthor,
    val object: GitObject,
    val verification: TagVerification?
)

/**
 * Git对象
 */
data class GitObject(
    val type: ObjectType,
    val sha: String,
    val url: String
)

/**
 * 对象类型枚举
 */
enum class ObjectType {
    COMMIT,
    TREE,
    BLOB,
    TAG
}

/**
 * 标签验证信息
 */
data class TagVerification(
    val verified: Boolean,
    val reason: String,
    val payload: String?,
    val signature: String?
)

/**
 * 版本信息
 */
data class Version(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: String?,
    val build: String?,
    val tag: String?
) {
    companion object {
        /**
         * 从语义化版本字符串解析版本
         */
        fun fromString(version: String): Version {
            val regex = Regex("(\\d+)\\.(\\d+)\\.(\\d+)(?:-([\\dA-Za-z-]+(?:\\.[\\dA-Za-z-]+)*))?(?:\\+([\\dA-Za-z-]+(?:\\.[\\dA-Za-z-]+)*))?")
            val match = regex.matchEntire(version) ?: throw IllegalArgumentException("Invalid version format: $version")
            
            return Version(
                major = match.groupValues[1].toInt(),
                minor = match.groupValues[2].toInt(),
                patch = match.groupValues[3].toInt(),
                prerelease = match.groupValues[4].takeIf { it.isNotEmpty() },
                build = match.groupValues[5].takeIf { it.isNotEmpty() },
                tag = version
            )
        }
    }
    
    /**
     * 转换为语义化版本字符串
     */
    override fun toString(): String {
        val base = "$major.$minor.$patch"
        val prereleasePart = prerelease?.let { "-$it" } ?: ""
        val buildPart = build?.let { "+$it" } ?: ""
        return base + prereleasePart + buildPart
    }
    
    /**
     * 获取下一版本号
     */
    fun next(type: ReleaseType): Version {
        return when (type) {
            ReleaseType.MAJOR -> copy(major = major + 1, minor = 0, patch = 0, prerelease = null, build = null)
            ReleaseType.MINOR -> copy(minor = minor + 1, patch = 0, prerelease = null, build = null)
            ReleaseType.PATCH -> copy(patch = patch + 1, prerelease = null, build = null)
            ReleaseType.PRERELEASE -> copy(prerelease = prerelease ?: "alpha.1", build = null)
        }
    }
    
    /**
     * 检查是否为预发布版本
     */
    fun isPrerelease(): Boolean = prerelease != null
    
    /**
     * 比较版本号
     */
    fun compareTo(other: Version): Int {
        if (major != other.major) return major.compareTo(other.major)
        if (minor != other.minor) return minor.compareTo(other.minor)
        if (patch != other.patch) return patch.compareTo(other.patch)
        
        val thisPrerelease = prerelease ?: ""
        val otherPrerelease = other.prerelease ?: ""
        
        return when {
            thisPrerelease.isEmpty() && otherPrerelease.isEmpty() -> 0
            thisPrerelease.isEmpty() -> 1
            otherPrerelease.isEmpty() -> -1
            else -> thisPrerelease.compareTo(otherPrerelease)
        }
    }
}

/**
 * 发布类型枚举
 */
enum class ReleaseType {
    MAJOR,     // 主版本 - 不兼容的API变更
    MINOR,     // 次版本 - 向后兼容的功能性新增
    PATCH,     // 修订版本 - 向后兼容的问题修正
    PRERELEASE // 预发布版本
}