package com.codemate.features.ai.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.codemate.features.ai.domain.entity.*
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI配置管理器
 * 负责AI配置的存储、读取和管理
 */
@Singleton
class AIConfigManager @Inject constructor(
    private val context: Context,
    private val encryptionManager: EncryptionManager
) {
    private val preferences: SharedPreferences = context.getSharedPreferences("ai_config", Context.MODE_PRIVATE)
    private val apiKeysPrefs: SharedPreferences = context.getSharedPreferences("ai_api_keys", Context.MODE_PRIVATE)
    
    companion object {
        private const val CONFIGS_KEY = "ai_configs"
        private const val DEFAULT_CONFIG_KEY = "default_config_id"
        private const val USER_PREFERENCES_KEY = "user_preferences"
    }
    
    /**
     * 保存AI配置
     */
    suspend fun saveConfig(config: AIConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val configs = getAllConfigs().toMutableSet()
            configs.add(config)
            
            val configsJson = GsonUtils.gson.toJson(configs)
            preferences.edit()
                .putString(CONFIGS_KEY, configsJson)
                .apply()
            
            if (config.isDefault) {
                setDefaultConfig(config.id)
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取AI配置
     */
    suspend fun getConfig(id: String): AIConfig? = withContext(Dispatchers.IO) {
        try {
            val configsJson = preferences.getString(CONFIGS_KEY, null) ?: return@withContext null
            
            val type = object : TypeToken<Set<AIConfig>>() {}.type
            val configs: Set<AIConfig> = GsonUtils.gson.fromJson(configsJson, type)
            
            configs.find { it.id == id }
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 获取所有AI配置
     */
    suspend fun getAllConfigs(): Set<AIConfig> = withContext(Dispatchers.IO) {
        try {
            val configsJson = preferences.getString(CONFIGS_KEY, null) ?: return@withContext emptySet()
            
            val type = object : TypeToken<Set<AIConfig>>() {}.type
            val configs: Set<AIConfig> = GsonUtils.gson.fromJson(configsJson, type)
            
            configs
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    /**
     * 删除AI配置
     */
    suspend fun deleteConfig(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val configs = getAllConfigs().toMutableSet()
            val removed = configs.removeIf { it.id == id }
            
            if (removed) {
                val configsJson = GsonUtils.gson.toJson(configs)
                preferences.edit()
                    .putString(CONFIGS_KEY, configsJson)
                    .apply()
                
                preferences.edit().remove(DEFAULT_CONFIG_KEY).apply()
            }
            
            removed
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 设置默认配置
     */
    suspend fun setDefaultConfig(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            preferences.edit().putString(DEFAULT_CONFIG_KEY, id).apply()
            
            val configs = getAllConfigs().map { config ->
                if (config.id == id) {
                    config.copy(isDefault = true)
                } else {
                    config.copy(isDefault = false)
                }
            }.toSet()
            
            val configsJson = GsonUtils.gson.toJson(configs)
            preferences.edit()
                .putString(CONFIGS_KEY, configsJson)
                .apply()
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取默认配置
     */
    suspend fun getDefaultConfig(): AIConfig? = withContext(Dispatchers.IO) {
        try {
            val defaultId = preferences.getString(DEFAULT_CONFIG_KEY, null) ?: return@withContext null
            getConfig(defaultId)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 保存API密钥
     */
    suspend fun saveApiKey(provider: AIProvider, key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val encryptedKey = encryptionManager.encrypt(key)
            apiKeysPrefs.edit()
                .putString(provider.name, encryptedKey)
                .apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取API密钥
     */
    suspend fun getApiKey(provider: AIProvider): String? = withContext(Dispatchers.IO) {
        try {
            val encryptedKey = apiKeysPrefs.getString(provider.name, null) ?: return@withContext null
            encryptionManager.decrypt(encryptedKey)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 删除API密钥
     */
    suspend fun deleteApiKey(provider: AIProvider): Boolean = withContext(Dispatchers.IO) {
        try {
            apiKeysPrefs.edit().remove(provider.name).apply()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 验证API密钥
     */
    suspend fun validateApiKey(provider: AIProvider, key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                AIProvider.OPENAI -> key.startsWith("sk-") && key.length > 20
                AIProvider.ANTHROPIC -> key.startsWith("sk-ant-") && key.length > 50
                AIProvider.LOCAL -> true
                AIProvider.CUSTOM -> true
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 保存用户偏好
     */
    suspend fun saveUserPreference(key: String, value: Any): Boolean = withContext(Dispatchers.IO) {
        try {
            val preferencesJson = preferences.getString(USER_PREFERENCES_KEY, "{}")
            val preferences = GsonUtils.gson.fromJson(preferencesJson, Map::class.java).toMutableMap()
            
            preferences[key] = value
            
            val updatedJson = GsonUtils.gson.toJson(preferences)
            preferences.edit()
                .putString(USER_PREFERENCES_KEY, updatedJson)
                .apply()
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取用户偏好
     */
    suspend fun <T> getUserPreference(key: String, defaultValue: T? = null): T? = withContext(Dispatchers.IO) {
        try {
            val preferencesJson = preferences.getString(USER_PREFERENCES_KEY, "{}")
            val preferences = GsonUtils.gson.fromJson(preferencesJson, Map::class.java)
            
            @Suppress("UNCHECKED_CAST")
            preferences[key] as? T ?: defaultValue
        } catch (e: Exception) {
            defaultValue
        }
    }
    
    /**
     * 获取所有用户偏好
     */
    suspend fun getAllUserPreferences(): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val preferencesJson = preferences.getString(USER_PREFERENCES_KEY, "{}")
            GsonUtils.gson.fromJson(preferencesJson, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * 清除所有配置
     */
    suspend fun clearAllConfigs(): Boolean = withContext(Dispatchers.IO) {
        try {
            preferences.edit()
                .remove(CONFIGS_KEY)
                .remove(DEFAULT_CONFIG_KEY)
                .remove(USER_PREFERENCES_KEY)
                .apply()
            
            apiKeysPrefs.edit().clear().apply()
            
            true
        } catch (e: Exception) {
            false
        }
    }
}