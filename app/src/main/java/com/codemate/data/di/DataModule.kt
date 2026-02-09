package com.codemate.data.di

import android.content.Context
import androidx.room.Room
import com.codemate.data.dao.*
import com.codemate.data.database.CodeMateDatabase
import com.codemate.data.repository.*
import com.codemate.data.repository.impl.*
import com.codemate.data.security.ApiKeyEncryptionService
import com.codemate.data.security.EncryptionManager
import com.codemate.data.security.GitCredentialEncryptionService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * 数据层依赖注入模块
 * 提供所有数据层组件的依赖注入配置
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    /**
     * 提供应用上下文
     */
    @Provides
    @Singleton
    fun provideApplicationContext(@ApplicationContext context: Context): Context {
        return context
    }

    /**
     * 提供数据库实例
     */
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CodeMateDatabase {
        return Room.databaseBuilder(
            context,
            CodeMateDatabase::class.java,
            "codemate_database"
        )
        .addCallback(DatabaseCallback())
        .addMigrations(com.codemate.data.database.MIGRATION_1_2, com.codemate.data.database.MIGRATION_2_3)
        .fallbackToDestructiveMigration()
        .build()
    }

    /**
     * 提供应用作用域的协程作用域
     */
    @Provides
    @Singleton
    fun provideCoroutineScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
    }

    // DAO提供者
    @Provides
    fun provideProjectDao(database: CodeMateDatabase): ProjectDao {
        return database.projectDao()
    }

    @Provides
    fun provideSnippetDao(database: CodeMateDatabase): SnippetDao {
        return database.snippetDao()
    }

    @Provides
    fun provideConversationDao(database: CodeMateDatabase): ConversationDao {
        return database.conversationDao()
    }

    @Provides
    fun provideConversationMessageDao(database: CodeMateDatabase): ConversationMessageDao {
        return database.conversationMessageDao()
    }

    @Provides
    fun provideApiKeyDao(database: CodeMateDatabase): ApiKeyDao {
        return database.apiKeyDao()
    }

    @Provides
    fun provideGitRepoDao(database: CodeMateDatabase): GitRepoDao {
        return database.gitRepoDao()
    }

    // Repository提供者
    @Provides
    @Singleton
    fun provideProjectRepository(
        projectDao: ProjectDao,
        snippetDao: SnippetDao,
        conversationDao: ConversationDao
    ): ProjectRepository {
        return ProjectRepositoryImpl(projectDao, snippetDao, conversationDao)
    }

    @Provides
    @Singleton
    fun provideSnippetRepository(snippetDao: SnippetDao): SnippetRepository {
        return SnippetRepositoryImpl(snippetDao)
    }

    @Provides
    @Singleton
    fun provideConversationRepository(
        conversationDao: ConversationDao,
        messageDao: ConversationMessageDao
    ): ConversationRepository {
        return ConversationRepositoryImpl(conversationDao, messageDao)
    }

    @Provides
    @Singleton
    fun provideConversationMessageRepository(messageDao: ConversationMessageDao): ConversationMessageRepository {
        return ConversationMessageRepositoryImpl(messageDao)
    }

    @Provides
    @Singleton
    fun provideApiKeyRepository(apiKeyDao: ApiKeyDao): ApiKeyRepository {
        return ApiKeyRepositoryImpl(apiKeyDao)
    }

    @Provides
    @Singleton
    fun provideGitRepository(gitRepoDao: GitRepoDao): GitRepository {
        return GitRepositoryImpl(gitRepoDao)
    }

    // 安全组件提供者
    @Provides
    @Singleton
    fun provideEncryptionManager(@ApplicationContext context: Context): EncryptionManager {
        return EncryptionManager(context)
    }

    @Provides
    @Singleton
    fun provideApiKeyEncryptionService(encryptionManager: EncryptionManager): ApiKeyEncryptionService {
        return ApiKeyEncryptionService(encryptionManager)
    }

    @Provides
    @Singleton
    fun provideGitCredentialEncryptionService(encryptionManager: EncryptionManager): GitCredentialEncryptionService {
        return GitCredentialEncryptionService(encryptionManager)
    }
}

/**
 * 数据库创建回调
 */
private class DatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // 数据库创建时的初始化操作
        // 例如：创建默认数据、设置触发器等
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        // 数据库打开时的操作
        // 例如：执行一些维护操作
    }
}