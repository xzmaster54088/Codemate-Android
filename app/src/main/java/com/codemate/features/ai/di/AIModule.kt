package com.codemate.features.ai.di

import android.content.Context
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AI模块依赖注入模块
 * 提供所有AI相关组件的依赖注入，包括增强的工具类和优化服务
 */
@Module
@InstallIn(SingletonComponent::class)
object AIModule {
    
    // ===== 基础组件 =====
    
    /**
     * 提供Gson实例
     */
    @Provides
    @Singleton
    fun provideGson(): Gson {
        return com.codemate.features.ai.data.repository.GsonUtils.gson
    }
    
    /**
     * 提供AI配置管理器
     */
    @Provides
    @Singleton
    fun provideAIConfigManager(
        @ApplicationContext context: Context,
        encryptionManager: com.codemate.features.ai.data.repository.EncryptionManager
    ): com.codemate.features.ai.data.repository.AIConfigManager {
        return com.codemate.features.ai.data.repository.AIConfigManager(context, encryptionManager)
    }
    
    /**
     * 提供原始网络客户端（HttpURLConnection）
     */
    @Provides
    @Singleton
    fun provideNetworkClient(
        cacheManager: com.codemate.features.ai.data.repository.CacheManager,
        networkMonitor: com.codemate.features.ai.data.repository.NetworkMonitor
    ): com.codemate.features.ai.data.repository.NetworkClient {
        return com.codemate.features.ai.data.repository.NetworkClient(cacheManager, networkMonitor)
    }
    
    /**
     * 提供Retrofit网络客户端（增强版）
     */
    @Provides
    @Singleton
    fun provideRetrofitNetworkClient(
        gson: Gson,
        cacheManager: com.codemate.features.ai.data.repository.CacheManager,
        networkMonitor: com.codemate.features.ai.data.repository.NetworkMonitor
    ): com.codemate.features.ai.utils.RetrofitNetworkClient {
        return com.codemate.features.ai.utils.RetrofitNetworkClient(gson, cacheManager, networkMonitor)
    }
    
    // ===== 管理器和工具 =====
    
    /**
     * 提供重试管理器
     */
    @Provides
    @Singleton
    fun provideRetryManager(): com.codemate.features.ai.utils.RetryManager {
        return com.codemate.features.ai.utils.RetryManager()
    }
    
    /**
     * 提供AI安全过滤器
     */
    @Provides
    @Singleton
    fun provideAISafetyFilter(): com.codemate.features.ai.utils.AISafetyFilter {
        return com.codemate.features.ai.utils.AISafetyFilter()
    }
    
    /**
     * 提供AI指标收集器
     */
    @Provides
    @Singleton
    fun provideAIMetricsCollector(): com.codemate.features.ai.utils.AIMetricsCollector {
        return com.codemate.features.ai.utils.AIMetricsCollector()
    }
    
    /**
     * 提供AI日志记录器
     */
    @Provides
    @Singleton
    fun provideAILogger(
        metricsCollector: com.codemate.features.ai.utils.AIMetricsCollector
    ): com.codemate.features.ai.utils.AILogger {
        return com.codemate.features.ai.utils.AILogger(metricsCollector)
    }
    
    /**
     * 提供AI模型配置管理器
     */
    @Provides
    @Singleton
    fun provideAIModelConfigManager(
        @ApplicationContext context: Context,
        gson: Gson
    ): com.codemate.features.ai.utils.AIModelConfigManager {
        return com.codemate.features.ai.utils.AIModelConfigManager(context, gson)
    }
    
    /**
     * 提供缓存管理器
     */
    @Provides
    @Singleton
    fun provideCacheManager(
        @ApplicationContext context: Context
    ): com.codemate.features.ai.data.repository.CacheManager {
        return com.codemate.features.ai.data.repository.CacheManager(context, com.codemate.features.ai.data.repository.GsonUtils.gson)
    }
    
    /**
     * 提供内存管理器
     */
    @Provides
    @Singleton
    fun provideMemoryManager(
        @ApplicationContext context: Context
    ): com.codemate.features.ai.data.repository.MemoryManager {
        return com.codemate.features.ai.data.repository.MemoryManager(context)
    }
    
    /**
     * 提供ONNX运行时管理器
     */
    @Provides
    @Singleton
    fun provideONNXRuntimeManager(
        @ApplicationContext context: Context
    ): com.codemate.features.ai.data.repository.ONNXRuntimeManager {
        return com.codemate.features.ai.data.repository.ONNXRuntimeManager(context)
    }
    
    /**
     * 提供模型缓存
     */
    @Provides
    @Singleton
    fun provideModelCache(
        @ApplicationContext context: Context
    ): com.codemate.features.ai.data.repository.ModelCache {
        return com.codemate.features.ai.data.repository.ModelCache(context, com.codemate.features.ai.data.repository.GsonUtils.gson)
    }
    
    /**
     * 提供本地数据库
     */
    @Provides
    @Singleton
    fun provideLocalDatabase(
        @ApplicationContext context: Context
    ): com.codemate.features.ai.data.repository.LocalDatabase {
        return com.codemate.features.ai.data.repository.LocalDatabase(context, com.codemate.features.ai.data.repository.GsonUtils.gson)
    }
    
    /**
     * 提供对话管理器
     */
    @Provides
    @Singleton
    fun provideConversationManager(
        localDatabase: com.codemate.features.ai.data.repository.LocalDatabase,
        cacheManager: com.codemate.features.ai.data.repository.CacheManager
    ): com.codemate.features.ai.data.repository.ConversationManager {
        return com.codemate.features.ai.data.repository.ConversationManager(localDatabase, cacheManager)
    }
    
    /**
     * 提供服务监控器
     */
    @Provides
    @Singleton
    fun provideServiceMonitor(
        metricsDatabase: com.codemate.features.ai.data.repository.MetricsDatabase
    ): com.codemate.features.ai.data.repository.ServiceMonitor {
        return com.codemate.features.ai.data.repository.ServiceMonitor(metricsDatabase)
    }
    
    /**
     * 提供网络监控器
     */
    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context
    ): com.codemate.features.ai.data.repository.NetworkMonitor {
        return com.codemate.features.ai.data.repository.NetworkMonitor(context)
    }
    
    /**
     * 提供度量数据库
     */
    @Provides
    @Singleton
    fun provideMetricsDatabase(
        @ApplicationContext context: Context
    ): com.codemate.features.ai.data.repository.MetricsDatabase {
        return com.codemate.features.ai.data.repository.MetricsDatabase(context, com.codemate.features.ai.data.repository.GsonUtils.gson)
    }
    
    /**
     * 提供加密管理器
     */
    @Provides
    @Singleton
    fun provideEncryptionManager(
        @ApplicationContext context: Context
    ): com.codemate.features.ai.data.repository.EncryptionManager {
        return com.codemate.features.ai.data.repository.EncryptionManager(context)
    }
    
    // ===== AI服务 =====
    
    /**
     * 提供OpenAI服务（增强版）
     */
    @Provides
    @Singleton
    fun provideOpenAIService(
        networkClient: com.codemate.features.ai.data.repository.NetworkClient,
        retrofitClient: com.codemate.features.ai.utils.RetrofitNetworkClient,
        configManager: com.codemate.features.ai.data.repository.AIConfigManager,
        retryManager: com.codemate.features.ai.utils.RetryManager,
        safetyFilter: com.codemate.features.ai.utils.AISafetyFilter,
        logger: com.codemate.features.ai.utils.AILogger
    ): com.codemate.features.ai.data.repository.OpenAIService {
        return com.codemate.features.ai.data.repository.OpenAIService(networkClient, configManager, retryManager)
    }
    
    /**
     * 提供Claude服务（增强版）
     */
    @Provides
    @Singleton
    fun provideClaudeService(
        networkClient: com.codemate.features.ai.data.repository.NetworkClient,
        retrofitClient: com.codemate.features.ai.utils.RetrofitNetworkClient,
        configManager: com.codemate.features.ai.data.repository.AIConfigManager,
        retryManager: com.codemate.features.ai.utils.RetryManager,
        safetyFilter: com.codemate.features.ai.utils.AISafetyFilter,
        logger: com.codemate.features.ai.utils.AILogger
    ): com.codemate.features.ai.data.repository.ClaudeService {
        return com.codemate.features.ai.data.repository.ClaudeService(networkClient, configManager, retryManager)
    }
    
    /**
     * 提供本地LLM服务（增强版）
     */
    @Provides
    @Singleton
    fun provideLocalLLMService(
        onnxRuntimeManager: com.codemate.features.ai.data.repository.ONNXRuntimeManager,
        modelCache: com.codemate.features.ai.data.repository.ModelCache,
        memoryManager: com.codemate.features.ai.data.repository.MemoryManager,
        safetyFilter: com.codemate.features.ai.utils.AISafetyFilter,
        logger: com.codemate.features.ai.utils.AILogger
    ): com.codemate.features.ai.data.repository.LocalLLMService {
        return com.codemate.features.ai.data.repository.LocalLLMService(onnxRuntimeManager, modelCache, memoryManager)
    }
    
    // ===== 仓储层 =====
    
    /**
     * 提供AI服务仓储实现（增强版）
     */
    @Provides
    @Singleton
    fun provideAIServiceRepository(
        openAIService: com.codemate.features.ai.data.repository.OpenAIService,
        claudeService: com.codemate.features.ai.data.repository.ClaudeService,
        localLLMService: com.codemate.features.ai.data.repository.LocalLLMService,
        conversationManager: com.codemate.features.ai.data.repository.ConversationManager,
        serviceMonitor: com.codemate.features.ai.data.repository.ServiceMonitor,
        safetyFilter: com.codemate.features.ai.utils.AISafetyFilter,
        logger: com.codemate.features.ai.utils.AILogger
    ): com.codemate.features.ai.domain.repository.AIServiceRepository {
        return com.codemate.features.ai.data.repository.AIServiceRepositoryImpl(
            openAIService,
            claudeService,
            localLLMService,
            conversationManager,
            serviceMonitor
        )
    }
    
    /**
     * 提供AI配置仓储实现
     */
    @Provides
    @Singleton
    fun provideAIConfigRepository(
        configManager: com.codemate.features.ai.data.repository.AIConfigManager,
        modelConfigManager: com.codemate.features.ai.utils.AIModelConfigManager
    ): com.codemate.features.ai.domain.repository.AIConfigRepository {
        return com.codemate.features.ai.data.repository.AIConfigRepositoryImpl(configManager)
    }
    
    /**
     * 提供AI安全仓储实现
     */
    @Provides
    @Singleton
    fun provideAISafetyRepository(
        safetyFilter: com.codemate.features.ai.utils.AISafetyFilter,
        logger: com.codemate.features.ai.utils.AILogger
    ): com.codemate.features.ai.domain.repository.AISafetyRepository {
        return com.codemate.features.ai.data.repository.AISafetyRepositoryImpl()
    }
    
    // ===== 用例层 =====
    
    /**
     * 提供聊天用例（增强版）
     */
    @Provides
    @Singleton
    fun provideChatUseCase(
        aiServiceRepository: com.codemate.features.ai.domain.repository.AIServiceRepository,
        aiSafetyRepository: com.codemate.features.ai.domain.repository.AISafetyRepository,
        logger: com.codemate.features.ai.utils.AILogger
    ): com.codemate.features.ai.domain.usecase.ChatUseCase {
        return com.codemate.features.ai.domain.usecase.ChatUseCase(aiServiceRepository)
    }
    
    /**
     * 提供代码生成用例（增强版）
     */
    @Provides
    @Singleton
    fun provideCodeGenerationUseCase(
        aiServiceRepository: com.codemate.features.ai.domain.repository.AIServiceRepository,
        aiSafetyRepository: com.codemate.features.ai.domain.repository.AISafetyRepository,
        logger: com.codemate.features.ai.utils.AILogger
    ): com.codemate.features.ai.domain.usecase.CodeGenerationUseCase {
        return com.codemate.features.ai.domain.usecase.CodeGenerationUseCase(aiServiceRepository)
    }
    
    /**
     * 提供AI安全用例
     */
    @Provides
    @Singleton
    fun provideAISafetyUseCase(
        aiSafetyRepository: com.codemate.features.ai.domain.repository.AISafetyRepository
    ): com.codemate.features.ai.domain.usecase.AISafetyUseCase {
        return com.codemate.features.ai.domain.usecase.AISafetyUseCase(aiSafetyRepository)
    }
    
    /**
     * 提供本地LLM用例（增强版）
     */
    @Provides
    @Singleton
    fun provideLocalLLMUseCase(
        aiServiceRepository: com.codemate.features.ai.domain.repository.AIServiceRepository,
        aiConfigRepository: com.codemate.features.ai.domain.repository.AIConfigRepository,
        logger: com.codemate.features.ai.utils.AILogger
    ): com.codemate.features.ai.domain.usecase.LocalLLMUseCase {
        return com.codemate.features.ai.domain.usecase.LocalLLMUseCase(aiServiceRepository, aiConfigRepository)
    }
}