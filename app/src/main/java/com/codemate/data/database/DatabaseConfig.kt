package com.codemate.data.database

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.codemate.data.entity.*
import java.util.*

/**
 * Date类型转换器
 * 用于Room数据库中的Date类型与Long类型之间的转换
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}

/**
 * JSON类型转换器
 * 用于Room数据库中复杂对象的JSON序列化/反序列化
 */
class JsonConverter {
    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return value.joinToString(",")
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return if (value.isEmpty()) emptyList() else value.split(",")
    }

    @TypeConverter
    fun fromMap(value: Map<String, String>): String {
        return kotlinx.serialization.json.Json.encodeToString(value)
    }

    @TypeConverter
    fun toMap(value: String): Map<String, String> {
        return try {
            kotlinx.serialization.json.Json.decodeFromString(value)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}

/**
 * CodeMate数据库主类
 * 包含所有实体类的数据库定义
 */
@Database(
    entities = [
        Project::class,
        Snippet::class,
        Conversation::class,
        ConversationMessage::class,
        ApiKey::class,
        GitRepo::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class, JsonConverter::class)
abstract class CodeMateDatabase : RoomDatabase() {
    
    /**
     * 项目数据访问对象
     */
    abstract fun projectDao(): ProjectDao
    
    /**
     * 代码片段数据访问对象
     */
    abstract fun snippetDao(): SnippetDao
    
    /**
     * 对话数据访问对象
     */
    abstract fun conversationDao(): ConversationDao
    
    /**
     * 对话消息数据访问对象
     */
    abstract fun conversationMessageDao(): ConversationMessageDao
    
    /**
     * API密钥数据访问对象
     */
    abstract fun apiKeyDao(): ApiKeyDao
    
    /**
     * Git仓库数据访问对象
     */
    abstract fun gitRepoDao(): GitRepoDao
}

/**
 * 数据库迁移策略
 * 版本1到版本2的迁移：添加新字段和索引
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 为projects表添加新字段
        database.execSQL("""
            ALTER TABLE projects 
            ADD COLUMN last_accessed INTEGER NOT NULL DEFAULT ${Date().time}
        """.trimIndent())
        
        // 为snippets表添加新字段
        database.execSQL("""
            ALTER TABLE snippets 
            ADD COLUMN line_count INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE snippets 
            ADD COLUMN character_count INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE snippets 
            ADD COLUMN file_path TEXT
        """.trimIndent())
        
        // 为api_keys表添加新字段
        database.execSQL("""
            ALTER TABLE api_keys 
            ADD COLUMN usage_count INTEGER NOT NULL DEFAULT 0
        """.trimIndent())
        
        database.execSQL("""
            ALTER TABLE api_keys 
            ADD COLUMN last_used INTEGER
        """.trimIndent())
        
        // 创建新索引
        database.execSQL("CREATE INDEX IF NOT EXISTS index_projects_last_accessed ON projects(last_accessed)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_snippets_line_count ON snippets(line_count)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_api_keys_usage_count ON api_keys(usage_count)")
    }
}

/**
 * 版本2到版本3的迁移：添加Git仓库支持
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 创建git_repos表
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS git_repos (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                project_id INTEGER NOT NULL,
                remote_url TEXT NOT NULL,
                local_path TEXT NOT NULL,
                branch TEXT NOT NULL DEFAULT 'main',
                last_commit_hash TEXT,
                last_sync INTEGER,
                is_sync_enabled INTEGER NOT NULL DEFAULT 0,
                credentials TEXT,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
            )
        """.trimIndent())
        
        // 创建索引
        database.execSQL("CREATE INDEX IF NOT EXISTS index_git_repos_project_id ON git_repos(project_id)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_git_repos_remote_url ON git_repos(remote_url)")
        database.execSQL("CREATE INDEX IF NOT EXISTS index_git_repos_last_sync ON git_repos(last_sync)")
        
        // 更新conversations表
        database.execSQL("""
            ALTER TABLE conversations 
            ADD COLUMN is_active INTEGER NOT NULL DEFAULT 1
        """.trimIndent())
        
        // 创建新索引
        database.execSQL("CREATE INDEX IF NOT EXISTS index_conversations_is_active ON conversations(is_active)")
        
        // 清理旧数据
        database.execSQL("VACUUM")
    }
}