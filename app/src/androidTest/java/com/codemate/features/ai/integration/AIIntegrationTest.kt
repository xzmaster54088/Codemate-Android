package com.codemate.features.ai.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.codemate.features.ai.data.remote.AIRemoteDataSource
import com.codemate.features.ai.data.remote.AIService
import com.codemate.features.ai.domain.entity.*
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * AI功能集成测试
 * 使用MockWebServer测试API调用、网络错误处理和响应解析
 */
@RunWith(AndroidJUnit4::class)
class AIIntegrationTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var aiService: AIService
    private lateinit var remoteDataSource: AIRemoteDataSource
    private val gson: Gson = GsonBuilder().create()

    @Before
    fun setup() {
        // 启动MockWebServer
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // 创建Retrofit实例
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

        aiService = retrofit.create(AIService::class.java)
        remoteDataSource = AIRemoteDataSource(aiService)
    }

    @After
    fun teardown() {
        // 关闭MockWebServer
        mockWebServer.shutdown()
    }

    @Test
    fun `sendChatMessage when API returns success should return parsed response`() = runBlocking {
        // Given
        val chatRequest = AIChatRequest(
            message = "Hello AI",
            conversationId = "conv123",
            model = "gpt-3.5-turbo"
        )
        
        val chatResponse = AIChatResponse(
            content = "Hello! How can I help you?",
            model = "gpt-3.5-turbo",
            usage = TokenUsage(
                promptTokens = 10,
                completionTokens = 20,
                totalTokens = 30
            )
        )

        // 模拟API响应
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(chatResponse))
        )

        // When
        val result = remoteDataSource.sendChatMessage(chatRequest)

        // Then
        assert(result.isSuccessful)
        result.body()?.let { response ->
            assert(response.content == "Hello! How can I help you?")
            assert(response.model == "gpt-3.5-turbo")
            assert(response.usage.totalTokens == 30)
        }
    }

    @Test
    fun `sendChatMessage when API returns error should handle error response`() = runBlocking {
        // Given
        val chatRequest = AIChatRequest(
            message = "Hello AI",
            conversationId = "conv123"
        )
        
        val errorResponse = mapOf(
            "error" to mapOf(
                "message" to "Invalid API key",
                "type" to "invalid_request_error",
                "code" to "invalid_api_key"
            )
        )

        // 模拟API错误响应
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody(gson.toJson(errorResponse))
        )

        // When
        val result = remoteDataSource.sendChatMessage(chatRequest)

        // Then
        assert(!result.isSuccessful)
        assert(result.code() == 401)
    }

    @Test
    fun `analyzeCode when API returns analysis should return parsed result`() = runBlocking {
        // Given
        val code = """
            fun main() {
                println("Hello World")
            }
        """.trimIndent()
        
        val analysisRequest = CodeAnalysisRequest(
            code = code,
            language = "kotlin",
            analysisType = "syntax"
        )
        
        val analysisResponse = CodeAnalysisResponse(
            issues = listOf(
                CodeIssue(
                    type = "syntax_error",
                    message = "Missing semicolon",
                    line = 2,
                    column = 15,
                    severity = "high"
                )
            ),
            suggestions = listOf("Add semicolon at end of line"),
            complexity = "simple",
            qualityScore = 0.75f
        )

        // 模拟API响应
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(analysisResponse))
        )

        // When
        val result = remoteDataSource.analyzeCode(analysisRequest)

        // Then
        assert(result.isSuccessful)
        result.body()?.let { response ->
            assert(response.issues.isNotEmpty())
            assert(response.issues.first().message == "Missing semicolon")
            assert(response.suggestions.isNotEmpty())
            assert(response.qualityScore == 0.75f)
        }
    }

    @Test
    fun `getCodeCompletion when API returns suggestions should return parsed completions`() = runBlocking {
        // Given
        val completionRequest = CodeCompletionRequest(
            partialCode = "fun ma",
            cursorPosition = 6,
            language = "kotlin"
        )
        
        val completionResponse = CodeCompletionResponse(
            suggestions = listOf(
                CompletionSuggestion(
                    text = "main",
                    displayText = "fun main()",
                    type = "function",
                    documentation = "Main function entry point"
                ),
                CompletionSuggestion(
                    text = "map",
                    displayText = "map(transform: (T) -> R)",
                    type = "function",
                    documentation = "Transform each element"
                )
            ),
            context = "function_declaration"
        )

        // 模拟API响应
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(completionResponse))
        )

        // When
        val result = remoteDataSource.getCodeCompletion(completionRequest)

        // Then
        assert(result.isSuccessful)
        result.body()?.let { response ->
            assert(response.suggestions.isNotEmpty())
            assert(response.suggestions.size == 2)
            assert(response.suggestions.any { it.text == "main" })
            assert(response.suggestions.any { it.text == "map" })
        }
    }

    @Test
    fun `sendChatMessage with network timeout should handle timeout gracefully`() = runBlocking {
        // Given
        val chatRequest = AIChatRequest(
            message = "Hello AI",
            conversationId = "conv123"
        )

        // 模拟网络超时
        mockWebServer.enqueue(
            MockResponse()
                .setBodyDelay(35, TimeUnit.SECONDS) // 超过30秒超时
                .setResponseCode(200)
                .setBody("{}")
        )

        // When & Then
        try {
            val result = remoteDataSource.sendChatMessage(chatRequest)
            // 如果没有抛出异常，验证超时处理
            assert(!result.isSuccessful || result.code() == 504)
        } catch (e: Exception) {
            // 预期的超时异常
            assert(e.message?.contains("timeout") == true || 
                   e.message?.contains("timed out") == true)
        }
    }

    @Test
    fun `multiple concurrent requests should handle properly`() = runBlocking {
        // Given
        val requests = (1..5).map { index ->
            AIChatRequest(
                message = "Message $index",
                conversationId = "conv$index"
            )
        }
        
        val chatResponse = AIChatResponse(
            content = "Response",
            model = "gpt-3.5-turbo",
            usage = TokenUsage(10, 20, 30)
        )

        // 模拟多个响应
        requests.forEach { _ ->
            mockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setBody(gson.toJson(chatResponse))
            )
        }

        // When
        val results = requests.map { request ->
            remoteDataSource.sendChatMessage(request)
        }

        // Then
        assert(results.size == 5)
        results.forEach { result ->
            assert(result.isSuccessful)
            result.body()?.let { response ->
                assert(response.content == "Response")
            }
        }
    }

    @Test
    fun `sendChatMessage with large payload should handle correctly`() = runBlocking {
        // Given
        val largeMessage = "A".repeat(10000) // 大消息体
        val chatRequest = AIChatRequest(
            message = largeMessage,
            conversationId = "conv123"
        )
        
        val chatResponse = AIChatResponse(
            content = "Large message received",
            model = "gpt-3.5-turbo",
            usage = TokenUsage(1000, 2000, 3000)
        )

        // 模拟API响应
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(chatResponse))
        )

        // When
        val result = remoteDataSource.sendChatMessage(chatRequest)

        // Then
        assert(result.isSuccessful)
        result.body()?.let { response ->
            assert(response.content == "Large message received")
            assert(response.usage.totalTokens == 3000)
        }
    }

    @Test
    fun `getConversations should return conversation list`() = runBlocking {
        // Given
        val conversations = listOf(
            AIConversation(
                id = "conv1",
                title = "Kotlin Tutorial",
                lastMessage = "How to create a class?",
                timestamp = 1640995200000L,
                messageCount = 10
            ),
            AIConversation(
                id = "conv2",
                title = "Java vs Kotlin",
                lastMessage = "Which is better?",
                timestamp = 1641081600000L,
                messageCount = 25
            )
        )

        // 模拟API响应
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(gson.toJson(conversations))
        )

        // When
        val result = remoteDataSource.getConversations()

        // Then
        assert(result.isSuccessful)
        result.body()?.let { response ->
            assert(response.size == 2)
            assert(response[0].title == "Kotlin Tutorial")
            assert(response[1].title == "Java vs Kotlin")
        }
    }

    @Test
    fun `API rate limiting should be handled properly`() = runBlocking {
        // Given
        val chatRequest = AIChatRequest(
            message = "Hello AI",
            conversationId = "conv123"
        )

        // 模拟速率限制响应
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(429)
                .setHeader("Retry-After", "60")
                .setBody("{\"error\": {\"message\": \"Rate limit exceeded\", \"type\": \"rate_limit_error\"}}")
        )

        // When
        val result = remoteDataSource.sendChatMessage(chatRequest)

        // Then
        assert(!result.isSuccessful)
        assert(result.code() == 429)
    }

    @Test
    fun `server maintenance should return maintenance response`() = runBlocking {
        // Given
        val chatRequest = AIChatRequest(
            message = "Hello AI",
            conversationId = "conv123"
        )

        // 模拟服务器维护响应
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("{\"error\": {\"message\": \"Service temporarily unavailable\", \"type\": \"maintenance\"}}")
        )

        // When
        val result = remoteDataSource.sendChatMessage(chatRequest)

        // Then
        assert(!result.isSuccessful)
        assert(result.code() == 503)
    }
}
