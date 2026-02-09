package com.codemate.features.ai.domain.repository

import com.codemate.features.ai.domain.entity.*

/**
 * AI配置管理仓储接口
 */
interface AIConfigRepository {
    
    // ===== 配置管理 =====
    
    /**
     * 获取所有配置
     */
    suspend fun getAllConfigs(): List<AIConfig>
    
    /**
     * 获取配置
     */
    suspend fun getConfig(id: String): AIConfig?
    
    /**
     * 创建配置
     */
    suspend fun createConfig(config: AIConfig): AIConfig
    
    /**
     * 更新配置
     */
    suspend fun updateConfig(config: AIConfig): AIConfig
    
    /**
     * 删除配置
     */
    suspend fun deleteConfig(id: String): Boolean
    
    /**
     * 获取默认配置
     */
    suspend fun getDefaultConfig(): AIConfig?
    
    /**
     * 设置默认配置
     */
    suspend fun setDefaultConfig(id: String): Boolean
    
    // ===== 本地模型管理 =====
    
    /**
     * 获取所有本地模型
     */
    suspend fun getAllLocalModels(): List<LocalLLMModel>
    
    /**
     * 获取本地模型
     */
    suspend fun getLocalModel(id: String): LocalLLMModel?
    
    /**
     * 添加本地模型
     */
    suspend fun addLocalModel(model: LocalLLMModel): LocalLLMModel
    
    /**
     * 删除本地模型
     */
    suspend fun deleteLocalModel(id: String): Boolean
    
    /**
     * 检查模型是否已下载
     */
    suspend fun isModelDownloaded(modelId: String): Boolean
    
    /**
     * 获取模型下载进度
     */
    suspend fun getModelDownloadProgress(modelId: String): Float
    
    // ===== API密钥管理 =====
    
    /**
     * 保存API密钥
     */
    suspend fun saveApiKey(provider: AIProvider, key: String): Boolean
    
    /**
     * 获取API密钥
     */
    suspend fun getApiKey(provider: AIProvider): String?
    
    /**
     * 删除API密钥
     */
    suspend fun deleteApiKey(provider: AIProvider): Boolean
    
    /**
     * 验证API密钥
     */
    suspend fun validateApiKey(provider: AIProvider, key: String): Boolean
    
    // ===== 用户偏好设置 =====
    
    /**
     * 保存用户偏好
     */
    suspend fun saveUserPreference(key: String, value: Any): Boolean
    
    /**
     * 获取用户偏好
     */
    suspend fun <T> getUserPreference(key: String, defaultValue: T? = null): T?
    
    /**
     * 删除用户偏好
     */
    suspend fun deleteUserPreference(key: String): Boolean
    
    /**
     * 获取所有用户偏好
     */
    suspend fun getAllUserPreferences(): Map<String, Any>
}