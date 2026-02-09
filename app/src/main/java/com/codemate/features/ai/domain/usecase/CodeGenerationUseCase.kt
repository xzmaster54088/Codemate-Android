package com.codemate.features.ai.domain.usecase

import com.codemate.features.ai.domain.entity.*
import com.codemate.features.ai.domain.repository.AIServiceRepository
import kotlinx.coroutines.flow.Flow

/**
 * AI代码生成用例
 * 处理代码生成和代码审查的业务逻辑
 */
class CodeGenerationUseCase(
    private val aiServiceRepository: AIServiceRepository
) {
    
    /**
     * 生成代码
     */
    suspend fun generateCode(
        prompt: String,
        language: String,
        context: ConversationContext,
        modelType: AIModelType,
        includeExplanation: Boolean = true
    ): Result<TextResponse> {
        return try {
            val request = CodeGenerationRequest(
                modelType = modelType,
                context = context,
                codePrompt = prompt,
                language = language,
                includeExplanation = includeExplanation
            )
            
            val response = aiServiceRepository.generateCode(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 生成流式代码
     */
    fun generateStreamingCode(
        prompt: String,
        language: String,
        context: ConversationContext,
        modelType: AIModelType,
        includeExplanation: Boolean = true
    ): Flow<StreamingResponse> {
        val request = CodeGenerationRequest(
            modelType = modelType,
            context = context,
            codePrompt = prompt,
            language = language,
            includeExplanation = includeExplanation
        )
        
        return aiServiceRepository.generateStreamingCode(request)
    }
    
    /**
     * 生成代码解释
     */
    suspend fun explainCode(
        code: String,
        language: String,
        context: ConversationContext,
        modelType: AIModelType
    ): Result<TextResponse> {
        return try {
            val prompt = """
                请解释以下$language代码的功能和实现原理：
                
                ```$language
                $code
                ```
                
                请提供详细的说明，包括：
                1. 代码的主要功能
                2. 关键算法或逻辑
                3. 可能的优化建议
                4. 使用注意事项
            """.trimIndent()
            
            val request = CodeGenerationRequest(
                modelType = modelType,
                context = context,
                codePrompt = prompt,
                language = language,
                includeExplanation = false
            )
            
            val response = aiServiceRepository.generateCode(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 代码审查
     */
    suspend fun reviewCode(
        code: String,
        language: String,
        context: ConversationContext,
        modelType: AIModelType
    ): Result<TextResponse> {
        return try {
            val prompt = """
                请审查以下$language代码，提供改进建议：
                
                ```$language
                $code
                ```
                
                请从以下方面进行审查：
                1. 代码质量和最佳实践
                2. 潜在的安全问题
                3. 性能优化建议
                4. 错误处理和边界情况
                5. 代码可读性和维护性
            """.trimIndent()
            
            val request = CodeGenerationRequest(
                modelType = modelType,
                context = context,
                codePrompt = prompt,
                language = language,
                includeExplanation = false
            )
            
            val response = aiServiceRepository.generateCode(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 代码重构建议
     */
    suspend fun refactorCode(
        code: String,
        language: String,
        target: String,
        context: ConversationContext,
        modelType: AIModelType
    ): Result<TextResponse> {
        return try {
            val prompt = """
                请将以下$language代码重构为$target：
                
                ```$language
                $code
                ```
                
                请提供：
                1. 重构后的代码
                2. 重构的详细说明
                3. 改进的地方
            """.trimIndent()
            
            val request = CodeGenerationRequest(
                modelType = modelType,
                context = context,
                codePrompt = prompt,
                language = language,
                includeExplanation = false
            )
            
            val response = aiServiceRepository.generateCode(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 生成单元测试
     */
    suspend fun generateUnitTests(
        code: String,
        language: String,
        testFramework: String,
        context: ConversationContext,
        modelType: AIModelType
    ): Result<TextResponse> {
        return try {
            val prompt = """
                请为以下$language代码生成$testFramework单元测试：
                
                ```$language
                $code
                ```
                
                请生成：
                1. 完整的测试用例
                2. 边界条件测试
                3. 错误情况测试
                4. 测试覆盖率说明
            """.trimIndent()
            
            val request = CodeGenerationRequest(
                modelType = modelType,
                context = context,
                codePrompt = prompt,
                language = language,
                includeExplanation = false
            )
            
            val response = aiServiceRepository.generateCode(request)
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}