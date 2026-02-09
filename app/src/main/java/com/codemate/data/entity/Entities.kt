package com.codemate.data.entity

import androidx.room.*
import androidx.room.PrimaryKey
import java.util.Date

/**
 * 项目实体类
 * 代表CodeMate中的项目概念
 */
@Entity(
    tableName = "projects",
    indices = [Index(value = ["name"]), Index(value = ["created_at"])]
)
data class Project(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "type")
    val type: ProjectType,
    
    @ColumnInfo(name = "language")
    val language: String,
    
    @ColumnInfo(name = "framework")
    val framework: String? = null,
    
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    
    @ColumnInfo(name = "tags")
    val tags: String = "", // JSON格式存储标签
    
    @ColumnInfo(name = "settings")
    val settings: String = "{}", // JSON格式存储项目设置
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date(),
    
    @ColumnInfo(name = "last_accessed")
    val lastAccessed: Date = Date()
)

/**
 * 代码片段实体类
 * 存储代码片段和相关元数据
 */
@Entity(
    tableName = "snippets",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["language"]),
        Index(value = ["created_at"])
    ]
)
data class Snippet(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "description")
    val description: String? = null,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "language")
    val language: String,
    
    @ColumnInfo(name = "tags")
    val tags: String = "",
    
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    
    @ColumnInfo(name = "is_public")
    val isPublic: Boolean = false,
    
    @ColumnInfo(name = "line_count")
    val lineCount: Int = 0,
    
    @ColumnInfo(name = "character_count")
    val characterCount: Int = 0,
    
    @ColumnInfo(name = "file_path")
    val filePath: String? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date()
)

/**
 * 对话实体类
 * 存储用户与AI助手的对话记录
 */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["created_at"])
    ]
)
data class Conversation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "project_id")
    val projectId: Long?,
    
    @ColumnInfo(name = "title")
    val title: String,
    
    @ColumnInfo(name = "context")
    val context: String? = null, // 对话上下文信息
    
    @ColumnInfo(name = "message_count")
    val messageCount: Int = 0,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),
    
    @ColumnInfo(name = "updated_at")
    val updatedAt: Date = Date(),
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true
)

/**
 * 对话消息实体类
 * 存储对话中的具体消息
 */
@Entity(
    tableName = "conversation_messages",
    foreignKeys = [
        ForeignKey(
            entity = Conversation::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["conversation_id"]),
        Index(value = ["created_at"])
    ]
)
data class ConversationMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "conversation_id")
    val conversationId: Long,
    
    @ColumnInfo(name = "role")
    val role: MessageRole,
    
    @ColumnInfo(name = "content")
    val content: String,
    
    @ColumnInfo(name = "metadata")
    val metadata: String = "{}", // JSON格式存储消息元数据
    
    @ColumnInfo(name = "tokens_used")
    val tokensUsed: Int = 0,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date()
)

/**
 * API密钥实体类
 * 存储加密后的API密钥信息
 */
@Entity(
    tableName = "api_keys",
    indices = [Index(value = ["provider"])]
)
data class ApiKey(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "provider")
    val provider: ApiProvider,
    
    @ColumnInfo(name = "name")
    val name: String,
    
    @ColumnInfo(name = "encrypted_key")
    val encryptedKey: String, // 加密后的API密钥
    
    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,
    
    @ColumnInfo(name = "usage_count")
    val usageCount: Long = 0,
    
    @ColumnInfo(name = "last_used")
    val lastUsed: Date? = null,
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date(),
    
    @ColumnInfo(name = "expires_at")
    val expiresAt: Date? = null
)

/**
 * Git仓库实体类
 * 存储Git仓库信息
 */
@Entity(
    tableName = "git_repos",
    foreignKeys = [
        ForeignKey(
            entity = Project::class,
            parentColumns = ["id"],
            childColumns = ["project_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["project_id"]),
        Index(value = ["remote_url"])
    ]
)
data class GitRepo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    @ColumnInfo(name = "project_id")
    val projectId: Long,
    
    @ColumnInfo(name = "remote_url")
    val remoteUrl: String,
    
    @ColumnInfo(name = "local_path")
    val localPath: String,
    
    @ColumnInfo(name = "branch")
    val branch: String = "main",
    
    @ColumnInfo(name = "last_commit_hash")
    val lastCommitHash: String? = null,
    
    @ColumnInfo(name = "last_sync")
    val lastSync: Date? = null,
    
    @ColumnInfo(name = "is_sync_enabled")
    val isSyncEnabled: Boolean = false,
    
    @ColumnInfo(name = "credentials")
    val credentials: String? = null, // 加密后的凭据信息
    
    @ColumnInfo(name = "created_at")
    val createdAt: Date = Date()
)

/**
 * 项目类型枚举
 */
enum class ProjectType {
    MOBILE, // 移动应用
    WEB,    // Web应用
    DESKTOP, // 桌面应用
    LIBRARY, // 库/框架
    SCRIPT, // 脚本
    OTHER   // 其他
}

/**
 * 消息角色枚举
 */
enum class MessageRole {
    USER,      // 用户消息
    ASSISTANT, // AI助手回复
    SYSTEM     // 系统消息
}

/**
 * API提供商枚举
 */
enum class ApiProvider {
    OPENAI,
    ANTHROPIC,
    GOOGLE,
    AZURE,
    LOCAL,
    CUSTOM
}