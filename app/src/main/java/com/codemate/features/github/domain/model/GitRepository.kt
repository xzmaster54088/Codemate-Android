package com.codemate.features.github.domain.model

import java.util.Date

/**
 * Git仓库实体模型
 * 遵循Clean Architecture的实体层设计
 */
data class GitRepository(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val owner: String,
    val private: Boolean,
    val htmlUrl: String,
    val cloneUrl: String,
    val sshUrl: String,
    val defaultBranch: String,
    val createdAt: Date,
    val updatedAt: Date,
    val pushedAt: Date?,
    val language: String?,
    val stargazersCount: Int,
    val watchersCount: Int,
    val forksCount: Int,
    val openIssuesCount: Int,
    val size: Int,
    val topics: List<String>
)

/**
 * Git仓库状态枚举
 */
enum class RepositoryStatus {
    LOADING,
    READY,
    SYNCING,
    CONFLICT,
    ERROR
}

/**
 * Git仓库权限枚举
 */
enum class RepositoryPermission {
    ADMIN,
    MAINTAIN,
    WRITE,
    READ,
    NONE
}

/**
 * Git仓库过滤器
 */
data class RepositoryFilter(
    val query: String? = null,
    val language: String? = null,
    val sortBy: SortBy = SortBy.UPDATED,
    val sortOrder: SortOrder = SortOrder.DESC,
    val visibility: RepositoryVisibility? = null,
    val affiliation: List<String>? = null
) {
    enum class SortBy {
        STARS,
        FORKS,
        HELP_WANTED_ISSUES,
        UPDATED,
        CREATED
    }
    
    enum class SortOrder {
        ASC,
        DESC
    }
    
    enum class RepositoryVisibility {
        ALL,
        PUBLIC,
        PRIVATE
    }
}