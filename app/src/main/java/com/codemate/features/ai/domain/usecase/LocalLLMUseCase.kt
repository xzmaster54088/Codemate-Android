package com.codemate.features.ai.domain.usecase

import com.codemate.features.ai.domain.entity.*
import com.codemate.features.ai.domain.repository.AIConfigRepository
import com.codemate.features.ai.domain.repository.AIServiceRepository
import kotlinx.coroutines.flow.Flow

/**
 * 本地LLM用例
 * 处理本地模型推理的业务逻辑
 */
class LocalLLMUseCase(
    private val aiServiceRepository: AIServiceRepository,
    private val configRepository: AIConfigRepository
) {
    
    /**
     * 执行本地LLM推理
     */
    suspend fun executeLocalLLM(
        modelId: String,
        inputText: String,
        context: ConversationContext,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): Result<LocalLLMResponse> {
        return try {
            // 检查模型是否已下载
            if (!configRepository.isModelDownloaded(modelId)) {
                return Result.failure(Exception("模型未下载"))
            }
            
            val request = LocalLLMRequest(
                modelType = AIModelType.LOCAL_LLM,
                context = context,
                modelId = modelId,
                inputText = inputText,
                maxTokens = maxTokens,
                temperature = temperature,
                topP = topP
            )
            
            val response = aiServiceRepository.executeLocalLLM(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 执行流式本地LLM推理
     */
    fun executeStreamingLocalLLM(
        modelId: String,
        inputText: String,
        context: ConversationContext,
        maxTokens: Int = 512,
        temperature: Float = 0.7f,
        topP: Float = 0.9f
    ): Flow<StreamingResponse> {
        val request = LocalLLMRequest(
            modelType = AIModelType.LOCAL_LLM,
            context = context,
            modelId = modelId,
            inputText = inputText,
            maxTokens = maxTokens,
            temperature = temperature,
            topP = topP
        )
        
        return aiServiceRepository.executeStreamingLocalLLM(request)
    }
    
    /**
     * 获取所有本地模型
     */
    suspend fun getAllLocalModels(): Result<List<LocalLLMModel>> {
        return try {
            val models = configRepository.getAllLocalModels()
            Result.success(models)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取本地模型
     */
    suspend fun getLocalModel(id: String): Result<LocalLLMModel> {
        return try {
            val model = configRepository.getLocalModel(id)
            if (model != null) {
                Result.success(model)
            } else {
                Result.failure(Exception("模型不存在"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 添加本地模型
     */
    suspend fun addLocalModel(model: LocalLLMModel): Result<LocalLLMModel> {
        return try {
            val addedModel = configRepository.addLocalModel(model)
            Result.success(addedModel)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 删除本地模型
     */
    suspend fun deleteLocalModel(id: String): Result<Boolean> {
        return try {
            val success = configRepository.deleteLocalModel(id)
            Result.success(success)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 检查模型是否已下载
     */
    suspend fun isModelDownloaded(modelId: String): Result<Boolean> {
        return try {
            val isDownloaded = configRepository.isModelDownloaded(modelId)
            Result.success(isDownloaded)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取模型下载进度
     */
    suspend fun getModelDownloadProgress(modelId: String): Result<Float> {
        return try {
            val progress = configRepository.getModelDownloadProgress(modelId)
            Result.success(progress)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 本地模型推理优化建议
     */
    suspend fun getOptimizationRecommendations(modelId: String): Result<OptimizationRecommendations> {
        return try {
            val model = configRepository.getLocalModel(modelId)
            if (model != null) {
                val recommendations = when (model.quantization) {
                    QuantizationType.NONE -> {
                        OptimizationRecommendations(
                            recommendedQuantization = QuantizationType.INT8,
                            reason = "量化可以显著减少内存使用并提高推理速度",
                            estimatedMemorySaving = 0.5f,
                            estimatedSpeedImprovement = 1.2f
                        )
                    }
                    QuantizationType.INT8 -> {
                        OptimizationRecommendations(
                            recommendedQuantization = QuantizationType.INT4,
                            reason = "进一步量化可以获得更好的性能",
                            estimatedMemorySaving = 0.3f,
                            estimatedSpeedImprovement = 1.1f
                        )
                    }
                    QuantizationType.INT4 -> {
                        OptimizationRecommendations(
                            recommendedQuantization = null,
                            reason = "模型已经处于最优量化状态",
                            estimatedMemorySaving = 0.0f,
                            estimatedSpeedImprovement = 0.0f
                        )
                    }
                    QuantizationType.FP16 -> {
                        OptimizationRecommendations(
                            recommendedQuantization = QuantizationType.INT8,
                            reason = "半精度浮点可以转换为量化格式以提高性能",
                            estimatedMemorySaving = 0.5f,
                            estimatedSpeedImprovement = 1.3f
                        )
                    }
                }
                Result.success(recommendations)
            } else {
                Result.failure(Exception("模型不存在"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 计算推理资源需求
     */
    suspend fun calculateResourceRequirements(
        modelId: String,
        maxTokens: Int,
        batchSize: Int = 1
    ): Result<ResourceRequirements> {
        return try {
            val model = configRepository.getLocalModel(modelId)
            if (model != null) {
                // 简化的资源计算逻辑
                val baseMemory = when (model.quantization) {
                    QuantizationType.NONE -> model.contextLength * 4 * 2 // 4 bytes per float, 2x for overhead
                    QuantizationType.INT8 -> model.contextLength * 1 * 2 // 1 byte per int8, 2x for overhead
                    QuantizationType.INT4 -> model.contextLength * 0.5 * 2 // 0.5 bytes per int4, 2x for overhead
                    QuantizationType.FP16 -> model.contextLength * 2 * 2 // 2 bytes per float16, 2x for overhead
                }
                
                val requiredMemory = baseMemory * (maxTokens / 1000.0f) * batchSize
                val estimatedGFlops = (model.contextLength * maxTokens * 1.5).toLong() // 简化的FLOP计算
                
                val requirements = ResourceRequirements(
                    requiredMemoryMB = (requiredMemory / (1024 * 1024)).toInt(),
                    estimatedGFlops = estimatedGFlops,
                    estimatedTimePerToken = (maxTokens * 0.1).toInt(), // 毫秒
                    recommendedBatchSize = if (requiredMemory > 512 * 1024 * 1024) 1 else 4 // 512MB阈值
                )
                
                Result.success(requirements)
            } else {
                Result.failure(Exception("模型不存在"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 优化建议
 */
data class OptimizationRecommendations(
    val recommendedQuantization: QuantizationType?,
    val reason: String,
    val estimatedMemorySaving: Float,
    val estimatedSpeedImprovement: Float
)

/**
 * 资源需求
 */
data class ResourceRequirements(
    val requiredMemoryMB: Int,
    val estimatedGFlops: Long,
    val estimatedTimePerToken: Int, // 毫秒
    val recommendedBatchSize: Int
)