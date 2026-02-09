package com.codemate.features.ai.data.repository

import com.codemate.features.ai.domain.entity.*
import com.codemate.features.ai.data.remote.AIRemoteDataSource
import com.codemate.features.ai.data.local.AILocalDataSource
import com.codemate.features.ai.data.mapper.AIMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations
import retrofit2.Response
import java.io.IOException

/**
 * AI功能Repository集成测试
 * 测试AI服务的本地缓存、远程调用和数据同步功能
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AIRepositoryImplTest {

    @Mock
    private lateinit var remoteDataSource: AIRemoteDataSource

    @Mock
    private lateinit var localDataSource: AILocalDataSource

    @Mock
    private lateinit var mapper: AIMapper

    private lateinit var repository: AIRepositoryImpl

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        repository = AIRepositoryImpl(
            remoteDataSource = remoteDataSource,
            localDataSource = localDataSource,
            mapper = mapper,
            dispatcher = testDispatcher
        )
    }

    @Test
    fun `sendChatMessage when online should fetch from remote and cache locally`() = testScope.runTest {
        // Given
        val request = AIChatRequest(
            message = "Hello AI",
            conversationId = "conv123",
            model = "gpt-3.5-turbo"
        )
        
        val remoteResponse = AIChatResponse(
            content = "Hello! How can I help you?",
            model = "gpt-3.5-turbo",
            usage = TokenUsage(
                promptTokens = 10,
                completionTokens = 20,
                totalTokens = 30
            )
        )
        
        val expectedResult = ChatMessage(
            content = "Hello! How can I help you?",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            messageId = "msg456"
        )

        // Mock network check
        `when`(localDataSource.isNetworkAvailable()).thenReturn(true)
        
        // Mock remote call
        `when`(remoteDataSource.sendChatMessage(any())).thenReturn(
            Response.success(remoteResponse)
        )
        
        // Mock mapper
        `when`(mapper.mapRemoteToDomain(remoteResponse)).thenReturn(expectedResult)

        // When
        val result = repository.sendChatMessage(request)

        // Then
        assert(result.isSuccess)
        assert(result.getOrNull() == expectedResult)
        
        verify(remoteDataSource).sendChatMessage(any())
        verify(localDataSource).saveChatMessage(any())
        verify(mapper).mapRemoteToDomain(remoteResponse)
    }

    @Test
    fun `sendChatMessage when offline should return cached data`() = testScope.runTest {
        // Given
        val request = AIChatRequest(
            message = "Hello AI",
            conversationId = "conv123"
        )
        
        val cachedMessage = ChatMessage(
            content = "Cached response",
            isUser = false,
            timestamp = System.currentTimeMillis(),
            messageId = "cached_msg"
        )

        // Mock offline state
        `when`(localDataSource.isNetworkAvailable()).thenReturn(false)
        `when`(localDataSource.getCachedChatResponse(request.conversationId)).thenReturn(cachedMessage)

        // When
        val result = repository.sendChatMessage(request)

        // Then
        assert(result.isSuccess)
        assert(result.getOrNull() == cachedMessage)
        
        verify(remoteDataSource, never()).sendChatMessage(any())
        verify(localDataSource).getCachedChatResponse(request.conversationId)
    }

    @Test
    fun `sendChatMessage when network error should return error result`() = testScope.runTest {
        // Given
        val request = AIChatRequest(
            message = "Hello AI",
            conversationId = "conv123"
        )

        // Mock network error
        `when`(localDataSource.isNetworkAvailable()).thenReturn(true)
        `when`(remoteDataSource.sendChatMessage(any())).thenThrow(IOException("Network error"))

        // When
        val result = repository.sendChatMessage(request)

        // Then
        assert(result.isFailure)
        assert(result.exceptionOrNull() is IOException)
        
        verify(remoteDataSource).sendChatMessage(any())
        verify(localDataSource, never()).saveChatMessage(any())
    }

    @Test
    fun `analyzeCode when successful should return analysis result`() = testScope.runTest {
        // Given
        val code = """
            fun main() {
                println("Hello World")
            }
        """.trimIndent()
        
        val analysisRequest = CodeAnalysisRequest(
            code = code,
            language = "kotlin",
            analysisType = AnalysisType.SYNTAX
        )
        
        val remoteAnalysis = CodeAnalysisResponse(
            issues = emptyList(),
            suggestions = listOf("代码结构良好"),
            complexity = AnalysisComplexity.SIMPLE,
            qualityScore = 0.95f
        )
        
        val expectedAnalysis = CodeAnalysisResult(
            issues = emptyList(),
            suggestions = listOf("代码结构良好"),
            complexity = AnalysisComplexity.SIMPLE,
            qualityScore = 0.95f
        )

        `when`(localDataSource.isNetworkAvailable()).thenReturn(true)
        `when`(remoteDataSource.analyzeCode(any())).thenReturn(
            Response.success(remoteAnalysis)
        )
        `when`(mapper.mapAnalysisRemoteToDomain(remoteAnalysis)).thenReturn(expectedAnalysis)

        // When
        val result = repository.analyzeCode(code, "kotlin", AnalysisType.SYNTAX)

        // Then
        assert(result.isSuccess)
        assert(result.getOrNull() == expectedAnalysis)
        
        verify(remoteDataSource).analyzeCode(any())
        verify(localDataSource).saveCodeAnalysis(any(), any())
    }

    @Test
    fun `getCodeCompletion should return completion suggestions`() = testScope.runTest {
        // Given
        val partialCode = "fun ma"
        val cursorPosition = 6
        
        val completionRequest = CodeCompletionRequest(
            partialCode = partialCode,
            cursorPosition = cursorPosition,
            language = "kotlin"
        )
        
        val remoteCompletion = CodeCompletionResponse(
            suggestions = listOf(
                CompletionSuggestion("main", "fun main()", CompletionType.FUNCTION),
                CompletionSuggestion("map", "map(transform: (T) -> R)", CompletionType.FUNCTION)
            ),
            context = "function_declaration"
        )
        
        val expectedSuggestions = listOf(
            CompletionSuggestion("main", "fun main()", CompletionType.FUNCTION),
            CompletionSuggestion("map", "map(transform: (T) -> R)", CompletionType.FUNCTION)
        )

        `when`(localDataSource.isNetworkAvailable()).thenReturn(true)
        `when`(remoteDataSource.getCodeCompletion(any())).thenReturn(
            Response.success(remoteCompletion)
        )
        `when`(mapper.mapCompletionRemoteToDomain(remoteCompletion)).thenReturn(expectedSuggestions)

        // When
        val result = repository.getCodeCompletion(partialCode, cursorPosition, "kotlin")

        // Then
        assert(result.isSuccess)
        assert(result.getOrNull() == expectedSuggestions)
        
        verify(remoteDataSource).getCodeCompletion(any())
    }

    @Test
    fun `getConversationHistory should return cached conversations`() = testScope.runTest {
        // Given
        val conversationId = "conv123"
        val cachedMessages = listOf(
            ChatMessage(
                content = "Hello",
                isUser = true,
                timestamp = 1000L,
                messageId = "msg1"
            ),
            ChatMessage(
                content = "Hi there!",
                isUser = false,
                timestamp = 2000L,
                messageId = "msg2"
            )
        )

        `when`(localDataSource.getConversationHistory(conversationId)).thenReturn(cachedMessages)

        // When
        val result = repository.getConversationHistory(conversationId)

        // Then
        assert(result.isSuccess)
        assert(result.getOrNull() == cachedMessages)
        
        verify(localDataSource).getConversationHistory(conversationId)
        verify(remoteDataSource, never()).getConversationHistory(any())
    }

    @Test
    fun `clearConversationHistory should remove cached conversation`() = testScope.runTest {
        // Given
        val conversationId = "conv123"

        // When
        val result = repository.clearConversationHistory(conversationId)

        // Then
        assert(result.isSuccess)
        
        verify(localDataSource).clearConversationHistory(conversationId)
        verify(remoteDataSource, never()).clearConversationHistory(any())
    }

    @Test
    fun `updateAIProvider should update provider configuration`() = testScope.runTest {
        // Given
        val newProvider = "openai"
        val providerConfig = AIProviderConfig(
            provider = "openai",
            apiKey = "sk-xxx",
            model = "gpt-3.5-turbo",
            maxTokens = 4000,
            temperature = 0.7f
        )

        `when`(localDataSource.saveAIProviderConfig(any())).thenReturn(Unit)

        // When
        val result = repository.updateAIProvider(newProvider, providerConfig)

        // Then
        assert(result.isSuccess)
        
        verify(localDataSource).saveAIProviderConfig(providerConfig)
        verify(remoteDataSource, never()).updateProviderConfig(any())
    }

    @Test
    fun `syncWithRemote when successful should update local cache`() = testScope.runTest {
        // Given
        val remoteConversations = listOf(
            AIConversation(
                id = "conv123",
                title = "Test Conversation",
                lastMessage = "Hello",
                timestamp = System.currentTimeMillis(),
                messageCount = 5
            )
        )

        `when`(localDataSource.isNetworkAvailable()).thenReturn(true)
        `when`(remoteDataSource.getConversations()).thenReturn(
            Response.success(remoteConversations)
        )
        `when`(localDataSource.saveConversations(any())).thenReturn(Unit)

        // When
        val result = repository.syncWithRemote()

        // Then
        assert(result.isSuccess)
        
        verify(remoteDataSource).getConversations()
        verify(localDataSource).saveConversations(remoteConversations)
    }

    @Test
    fun `getAIUsageStats should return usage statistics`() = testScope.runTest {
        // Given
        val usageStats = AIUsageStats(
            totalRequests = 100,
            totalTokens = 5000,
            dailyUsage = mapOf(
                "2024-01-01" to 200,
                "2024-01-02" to 300
            ),
            monthlyLimit = 10000,
            currentMonthUsage = 8000
        )

        `when`(localDataSource.getUsageStats()).thenReturn(usageStats)

        // When
        val result = repository.getAIUsageStats()

        // Then
        assert(result.isSuccess)
        assert(result.getOrNull() == usageStats)
        
        verify(localDataSource).getUsageStats()
    }
}
