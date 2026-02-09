package com.codemate.features.ai.utils

import android.content.Context
import android.content.SharedPreferences
import com.codemate.features.ai.domain.entity.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI模型配置管理器
 * 管理不同AI模型的配置和参数
 */
@Singleton
class AIModelConfigManager @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("ai_model_configs", Context.MODE_PRIVATE)
    
    companion object {
        private const val KEY_MODEL_CONFIGS = "model_configs"
        private const val KEY_DEFAULT_MODEL = "default_model"
        private const val KEY_MODEL_RANKINGS = "model_rankings"
    }
    
    /**
     * 保存模型配置
     */
    fun saveModelConfig(config: AIModelConfig) {
        val configs = getAllModelConfigs().toMutableList()
        val existingIndex = configs.indexOfFirst { it.id == config.id }
        
        if (existingIndex >= 0) {
            configs[existingIndex] = config
        } else {
            configs.add(config)
        }
        
        val json = gson.toJson(configs)
        prefs.edit().putString(KEY_MODEL_CONFIGS, json).apply()
    }
    
    /**
     * 获取模型配置
     */
    fun getModelConfig(modelId: String): AIModelConfig? {
        val configs = getAllModelConfigs()
        return configs.find { it.id == modelId }
    }
    
    /**
     * 获取所有模型配置
     */
    fun getAllModelConfigs(): List<AIModelConfig> {
        val json = prefs.getString(KEY_MODEL_CONFIGS, null) ?: return getDefaultConfigs()
        return try {
            gson.fromJson(json, object : TypeToken<List<AIModelConfig>>() {}.type)
        } catch (e: Exception) {
            getDefaultConfigs()
        }
    }
    
    /**
     * 删除模型配置
     */
    fun removeModelConfig(modelId: String) {
        val configs = getAllModelConfigs().filterNot { it.id == modelId }
        val json = gson.toJson(configs)
        prefs.edit().putString(KEY_MODEL_CONFIGS, json).apply()
    }
    
    /**
     * 设置默认模型
     */
    fun setDefaultModel(modelId: String) {
        prefs.edit().putString(KEY_DEFAULT_MODEL, modelId).apply()
    }
    
    /**
     * 获取默认模型
     */
    fun getDefaultModel(): AIModelConfig? {
        val defaultId = prefs.getString(KEY_DEFAULT_MODEL, null)
        return defaultId?.let { getModelConfig(it) } ?: getAllModelConfigs().firstOrNull()
    }
    
    /**
     * 获取模型推荐排序
     */
    fun getModelRankings(): Map<String, Int> {
        val json = prefs.getString(KEY_MODEL_RANKINGS, null) ?: return emptyMap()
        return try {
            gson.fromJson(json, object : TypeToken<Map<String, Int>>() {}.type)
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    /**
     * 更新模型使用统计
     */
    fun updateModelUsage(modelId: String) {
        val rankings = getModelRankings().toMutableMap()
        rankings[modelId] = (rankings[modelId] ?: 0) + 1
        val json = gson.toJson(rankings)
        prefs.edit().putString(KEY_MODEL_RANKINGS, json).apply()
    }
    
    /**
     * 获取推荐模型列表
     */
    fun getRecommendedModels(): List<AIModelConfig> {
        val configs = getAllModelConfigs()
        val rankings = getModelRankings()
        
        return configs.sortedByDescending { rankings[it.id] ?: 0 }
    }
    
    /**
     * 验证模型配置
     */
    fun validateModelConfig(config: AIModelConfig): ValidationResult {
        val issues = mutableListOf<String>()
        
        // 验证API密钥
        if (config.apiKey.isBlank()) {
            issues.add("API密钥不能为空")
        }
        
        // 验证参数范围
        if (config.parameters["temperature"]?.let { it is Float && (it < 0f || it > 2f) } == true) {
            issues.add("temperature参数必须在0-2之间")
        }
        
        if (config.parameters["max_tokens"]?.let { it is Int && (it < 1 || it > 32000) } == true) {
            issues.add("max_tokens参数必须在1-32000之间")
        }
        
        // 验证模型类型
        if (config.modelType !in getSupportedModelTypes()) {
            issues.add("不支持的模型类型: ${config.modelType}")
        }
        
        return ValidationResult(
            isValid = issues.isEmpty(),
            issues = issues
        )
    }
    
    /**
     * 导出配置
     */
    fun exportConfigs(): String {
        val configs = getAllModelConfigs()
        return gson.toJson(configs)
    }
    
    /**
     * 导入配置
     */
    fun importConfigs(json: String): ImportResult {
        return try {
            val configs = gson.fromJson(json, object : TypeToken<List<AIModelConfig>>() {}.type)
            val validConfigs = configs.filter { validateModelConfig(it).isValid }
            
            // 清除现有配置并导入新配置
            prefs.edit().putString(KEY_MODEL_CONFIGS, gson.toJson(validConfigs)).apply()
            
            ImportResult(
                success = true,
                importedCount = validConfigs.size,
                errors = emptyList()
            )
        } catch (e: Exception) {
            ImportResult(
                success = false,
                importedCount = 0,
                errors = listOf("导入失败: ${e.message}")
            )
        }
    }
    
    /**
     * 获取默认配置
     */
    private fun getDefaultConfigs(): List<AIModelConfig> {
        return listOf(
            AIModelConfig(
                id = "gpt-4",
                name = "GPT-4",
                description = "OpenAI GPT-4模型",
                provider = AIProvider.OPENAI,
                modelType = AIModelType.OPENAI_GPT_4,
                apiKey = "",
                baseUrl = AIConstants.OPENAI_BASE_URL,
                parameters = mapOf(
                    "temperature" to 0.7f,
                    "max_tokens" to 2048,
                    "top_p" to 1.0f
                ),
                isEnabled = true,
                isDefault = true
            ),
            AIModelConfig(
                id = "gpt-3.5-turbo",
                name = "GPT-3.5 Turbo",
                description = "OpenAI GPT-3.5 Turbo模型",
                provider = AIProvider.OPENAI,
                modelType = AIModelType.OPENAI_GPT_35_TURBO,
                apiKey = "",
                baseUrl = AIConstants.OPENAI_BASE_URL,
                parameters = mapOf(
                    "temperature" to 0.7f,
                    "max_tokens" to 2048,
                    "top_p" to 1.0f
                ),
                isEnabled = true,
                isDefault = false
            ),
            AIModelConfig(
                id = "claude-3-opus",
                name = "Claude 3 Opus",
                description = "Anthropic Claude 3 Opus模型",
                provider = AIProvider.ANTHROPIC,
                modelType = AIModelType.ANTHROPIC_CLAUDE_3_OPUS,
                apiKey = "",
                baseUrl = AIConstants.ANTHROPIC_BASE_URL,
                parameters = mapOf(
                    "temperature" to 0.7f,
                    "max_tokens" to 2048,
                    "top_p" to 1.0f
                ),
                isEnabled = true,
                isDefault = false
            ),
            AIModelConfig(
                id = "local-llm",
                name = "本地LLM",
                description = "本地ONNX模型",
                provider = AIProvider.LOCAL,
                modelType = AIModelType.LOCAL_OLLAMA,
                apiKey = "",
                baseUrl = "",
                parameters = mapOf(
                    "temperature" to 0.7f,
                    "max_tokens" to 1024,
                    "top_p" to 0.9f
                ),
                isEnabled = true,
                isDefault = false
            )
        )
    }
    
    /**
     * 获取支持的模型类型
     */
    private fun getSupportedModelTypes(): List<AIModelType> {
        return listOf(
            AIModelType.OPENAI_GPT_4,
            AIModelType.OPENAI_GPT_35_TURBO,
            AIModelType.ANTHROPIC_CLAUDE_3_OPUS,
            AIModelType.ANTHROPIC_CLAUDE_3_SONNET,
            AIModelType.ANTHROPIC_CLAUDE_3_HAIKU,
            AIModelType.LOCAL_OLLAMA,
            AIModelType.LOCAL_TRANSFORMER
        )
    }
}

/**
 * AI模型配置
 */
data class AIModelConfig(
    val id: String,
    val name: String,
    val description: String,
    val provider: AIProvider,
    val modelType: AIModelType,
    var apiKey: String,
    val baseUrl: String,
    val parameters: Map<String, Any>,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 验证结果
 */
data class ValidationResult(
    val isValid: Boolean,
    val issues: List<String>
)

/**
 * 导入结果
 */
data class ImportResult(
    val success: Boolean,
    val importedCount: Int,
    val errors: List<String>
)

/**
 * AI提供商枚举扩展
 */
val AIProvider.displayName: String
    get() = when (this) {
        AIProvider.OPENAI -> "OpenAI"
        AIProvider.ANTHROPIC -> "Anthropic"
        AIProvider.LOCAL -> "本地模型"
        AIProvider.CUSTOM -> "自定义"
    }

val AIProvider.iconRes: String
    get() = when (this) {
        AIProvider.OPENAI -> "ic_openai"
        AIProvider.ANTHROPIC -> "ic_anthropic"
        AIProvider.LOCAL -> "ic_local"
        AIProvider.CUSTOM -> "ic_custom"
    }

/**
 * AI模型类型扩展
 */
val AIModelType.displayName: String
    get() = when (this) {
        AIModelType.OPENAI_GPT_4 -> "GPT-4"
        AIModelType.OPENAI_GPT_35_TURBO -> "GPT-3.5 Turbo"
        AIModelType.ANTHROPIC_CLAUDE_3_OPUS -> "Claude 3 Opus"
        AIModelType.ANTHROPIC_CLAUDE_3_SONNET -> "Claude 3 Sonnet"
        AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> "Claude 3 Haiku"
        AIModelType.LOCAL_OLLAMA -> "本地Ollama"
        AIModelType.LOCAL_TRANSFORMER -> "本地Transformer"
    }

val AIModelType.maxContextLength: Int
    get() = when (this) {
        AIModelType.OPENAI_GPT_4 -> 8192
        AIModelType.OPENAI_GPT_35_TURBO -> 4096
        AIModelType.ANTHROPIC_CLAUDE_3_OPUS -> 200000
        AIModelType.ANTHROPIC_CLAUDE_3_SONNET -> 200000
        AIModelType.ANTHROPIC_CLAUDE_3_HAIKU -> 200000
        AIModelType.LOCAL_OLLAMA -> 4096
        AIModelType.LOCAL_TRANSFORMER -> 2048
    }

val AIModelType.supportsStreaming: Boolean
    get() = when (this) {
        AIModelType.LOCAL_TRANSFORMER -> false
        else -> true
    }