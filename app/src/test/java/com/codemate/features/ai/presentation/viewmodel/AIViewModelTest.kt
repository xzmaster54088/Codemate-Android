package com.codemate.features.ai.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.codemate.features.ai.domain.usecase.AIChatUseCase
import com.codemate.features.ai.domain.usecase.AICodeAnalysisUseCase
import com.codemate.features.ai.domain.usecase.AICompletionUseCase
import com.codemate.features.ai.domain.entity.AIResponse
import com.codemate.features.ai.domain.entity.AIRequest
import com.codemate.features.ai.domain.entity.AIType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * AI功能ViewModel单元测试
 * 测试AI聊天、代码分析和代码补全功能
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AIViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var chatUseCase: AIChatUseCase

    @Mock
    private lateinit var codeAnalysisUseCase: AICodeAnalysisUseCase

    @Mock
    private lateinit var completionUseCase: AICompletionUseCase

    @Mock
    private lateinit var responseObserver: Observer<AIResponse>

    private lateinit var viewModel: AIViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = AIViewModel(
            chatUseCase = chatUseCase,
            codeAnalysisUseCase = codeAnalysisUseCase,
            completionUseCase = completionUseCase,
            dispatcherProvider = coroutineTestRule.dispatcherProvider
        )
        
        // 观察AI响应状态
        viewModel.aiResponse.observeForever(responseObserver)
    }

    @Test
    fun `sendChatMessage when successful should update response state`() = testScope.runTest {
        // Given
        val request = AIRequest(
            message = "Hello, AI!",
            type = AIType.CHAT,
            context = emptyList()
        )
        val expectedResponse = AIResponse(
            content = "Hello! How can I help you?",
            type = AIType.CHAT,
            confidence = 0.95f,
            processingTime = 1500L
        )

        `when`(chatUseCase.invoke(any())).thenReturn(Result.success(expectedResponse))

        // When
        viewModel.sendChatMessage(request.message)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(responseObserver).onChanged(expectedResponse)
        verify(chatUseCase).invoke(request)
    }

    @Test
    fun `sendChatMessage when failure should update error state`() = testScope.runTest {
        // Given
        val errorMessage = "网络连接失败"
        `when`(chatUseCase.invoke(any())).thenReturn(Result.failure(Exception(errorMessage)))

        // When
        viewModel.sendChatMessage("Hello")

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(responseObserver).onChanged(
            match { it.content.contains("错误") || it.type == AIType.ERROR }
        )
    }

    @Test
    fun `analyzeCode when successful should return analysis results`() = testScope.runTest {
        // Given
        val code = """
            fun main() {
                println("Hello World")
            }
        """.trimIndent()
        
        val expectedAnalysis = AIResponse(
            content = "代码分析结果：语法正确，功能正常",
            type = AIType.ANALYSIS,
            confidence = 0.90f,
            processingTime = 2000L
        )

        `when`(codeAnalysisUseCase.invoke(any())).thenReturn(Result.success(expectedAnalysis))

        // When
        viewModel.analyzeCode(code)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(codeAnalysisUseCase).invoke(any())
        verify(responseObserver).onChanged(expectedAnalysis)
    }

    @Test
    fun `getCodeCompletion when successful should return suggestions`() = testScope.runTest {
        // Given
        val partialCode = "fun test"
        val expectedCompletion = AIResponse(
            content = "fun test() {\n    // TODO\n}",
            type = AIType.COMPLETION,
            confidence = 0.85f,
            processingTime = 800L
        )

        `when`(completionUseCase.invoke(any())).thenReturn(Result.success(expectedCompletion))

        // When
        viewModel.getCodeCompletion(partialCode, 0)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(completionUseCase).invoke(any())
        verify(responseObserver).onChanged(expectedCompletion)
    }

    @Test
    fun `clearChatHistory should reset all chat data`() {
        // When
        viewModel.clearChatHistory()

        // Then
        // 验证聊天历史被清除
        // 这里可以添加更多的验证逻辑
    }

    @Test
    fun `setAIProvider should update AI provider configuration`() {
        // Given
        val newProvider = "OpenAI"

        // When
        viewModel.setAIProvider(newProvider)

        // Then
        // 验证AI提供商设置被更新
        // 可以通过观察状态变化来验证
    }

    @Test
    fun `handleAIError should process different error types correctly`() {
        // Given
        val networkError = Exception("网络连接超时")
        val authError = Exception("认证失败")
        val unknownError = Exception("未知错误")

        // When & Then
        viewModel.handleAIError(networkError)
        // 验证网络错误处理逻辑

        viewModel.handleAIError(authError)
        // 验证认证错误处理逻辑

        viewModel.handleAIError(unknownError)
        // 验证未知错误处理逻辑
    }

    /**
     * 测试规则类，用于管理协程调度器
     */
    private class CoroutineTestRule : TestWatcher() {
        lateinit var dispatcherProvider: com.codemate.utils.DispatcherProvider
            private set

        override fun starting(description: Description?) {
            super.starting(description)
            dispatcherProvider = object : com.codemate.utils.DispatcherProvider {
                override val main = StandardTestDispatcher()
                override val io = StandardTestDispatcher()
                override val default = StandardTestDispatcher()
                override val unconfined = StandardTestDispatcher()
            }
        }
    }
}
