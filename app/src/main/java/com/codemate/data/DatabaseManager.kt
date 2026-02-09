package com.codemate.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * CodeMate Mobile 数据库管理器
 * 负责管理Room数据库的创建、升级和访问
 */
class DatabaseManager private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {

    // 数据库实例（单例）
    @Volatile
    private var INSTANCE: CodeMateDatabase? = null

    /**
     * 获取数据库实例
     * 使用双检查锁模式确保线程安全
     */
    fun getDatabase(): CodeMateDatabase {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildDatabase().also { INSTANCE = it }
        }
    }

    /**
     * 构建数据库实例
     */
    private fun buildDatabase(): CodeMateDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            CodeMateDatabase::class.java,
            "codemate_database"
        )
            // 数据库创建回调
            .addCallback(DatabaseCallback(scope))
            // 允许在主线程查询（仅用于简单查询）
            .allowMainThreadQueries()
            // 添加数据库迁移策略
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            // 数据库降级策略（仅用于开发环境）
            .fallbackToDestructiveMigration()
            .build()
    }

    /**
     * 清理数据库资源
     */
    fun clearDatabase() {
        INSTANCE?.close()
        INSTANCE = null
    }

    /**
     * 数据库创建回调
     */
    private class DatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            // 可以在这里执行数据库创建后的初始化操作
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: DatabaseManager? = null

        /**
         * 获取数据库管理器实例
         */
        fun getInstance(context: Context, scope: CoroutineScope): DatabaseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DatabaseManager(context, scope).also { INSTANCE = it }
            }
        }
    }
}