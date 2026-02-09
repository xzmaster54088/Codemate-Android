package com.codemate.features.ai.data.repository

import com.codemate.features.ai.domain.entity.*
import com.codemate.features.ai.domain.repository.AIConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI配置仓储实现
 */
@Singleton
class AIConfigRepositoryImpl @Inject constructor(
    private val configManager: AIConfigManager
) : AIConfigRepository {
    
    override suspend fun getAllConfigs(): List<AIConfig> {
        return configManager.getAllConfigs().toList()
    }
    
    override suspend fun getConfig(id: String): AIConfig? {
        return configManager.getConfig(id)
    }
    
    override suspend fun createConfig(config: AIConfig): AIConfig {
        configManager.saveConfig(config)
        return config
    }
    
    override suspend fun updateConfig(config: AIConfig): AIConfig {
        configManager.saveConfig(config)
        return config
    }
    
    override suspend fun deleteConfig(id: String): Boolean {
        return configManager.deleteConfig(id)
    }
    
    override suspend fun getDefaultConfig(): AIConfig? {
        return configManager.getDefaultConfig()
    }
    
    override suspend fun setDefaultConfig(id: String): Boolean {
        return configManager.setDefaultConfig(id)
    }
    
    override suspend fun getAllLocalModels(): List<LocalLLMModel> {
        // 简化实现，返回模拟数据
        return emptyList()
    }
    
    override suspend fun getLocalModel(id: String): LocalLLMModel? {
        return null
    }
    
    override suspend fun addLocalModel(model: LocalLLMModel): LocalLLMModel {
        return model
    }
    
    override suspend fun deleteLocalModel(id: String): Boolean {
        return true
    }
    
    override suspend fun isModelDownloaded(modelId: String): Boolean {
        return false
    }
    
    override suspend fun getModelDownloadProgress(modelId: String): Float {
        return 0.0f
    }
    
    override suspend fun saveApiKey(provider: AIProvider, key: String): Boolean {
        return configManager.saveApiKey(provider, key)
    }
    
    override suspend fun getApiKey(provider: AIProvider): String? {
        return configManager.getApiKey(provider)
    }
    
    override suspend fun deleteApiKey(provider: AIProvider): Boolean {
        return configManager.deleteApiKey(provider)
    }
    
    override suspend fun validateApiKey(provider: AIProvider, key: String): Boolean {
        return configManager.validateApiKey(provider, key)
    }
    
    override suspend fun saveUserPreference(key: String, value: Any): Boolean {
        return configManager.saveUserPreference(key, value)
    }
    
    override suspend fun <T> getUserPreference(key: String, defaultValue: T?): T? {
        return configManager.getUserPreference(key, defaultValue)
    }
    
    override suspend fun deleteUserPreference(key: String): Boolean {
        return configManager.deleteUserPreference(key)
    }
    
    override suspend fun getAllUserPreferences(): Map<String, Any> {
        return configManager.getAllUserPreferences()
    }
}