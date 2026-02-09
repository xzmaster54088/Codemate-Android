package com.codemate.features.ai.domain.usecase

import com.codemate.features.ai.domain.entity.*
import com.codemate.features.ai.domain.repository.AIRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * AI功能UseCase单元测试
 * 测试AI聊天、代码分析和代码补全的业务逻辑
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AIUseCaseTest {

    @Mock
    private lateinit var aiRepository: AIRepository

    private lateinit var chatUseCase: AIChatUseCase
    private lateinit var codeAnalysisUseCase: AICodeAnalysisUseCase
    private lateinit var completionUseCase: AICompletionUseCase

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        chatUseCase = AIChatUseCase(aiRepository)
        codeAnalysisUseCase = AICodeAnalysisUseCase(aiRepository)
        completionUseCase = AICompletionUseCase(aiRepository)
    }

    @Test
    fun `AIChatUseCase invoke when successful should return AI response`() = testScope.runTest {
        // Given
        val request = AIRequest(
            message = "Hello AI",
            type = AIType.CHAT,
            context = emptyList()
        )
        
        val expectedResponse = AIResponse(
            content = "Hello! How can I help you?",
            type = AIType.CHAT,
            confidence = 0.95f,
            processingTime = 1500L
        )

        `when`(aiRepository.sendChatMessage(any())).thenReturn(
            Result.success(
                ChatMessage(
                    content = "Hello! How can I help you?",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    messageId = "msg123"
                )
            )
        )

        // When
        val result = chatUseCase.invoke(request)

        // Then
        assert(result.isSuccess)
        result.getOrNull()?.let { response ->
            assert(response.content.isNotEmpty())
            assert(response.type == AIType.CHAT)
        }
        
        verify(aiRepository).sendChatMessage(any())
    }

    @Test
    fun `AIChatUseCase invoke when repository fails should return error`() = testScope.runTest {
        // Given
        val request = AIRequest(
            message = "Hello AI",
            type = AIType.CHAT,
            context = emptyList()
        )
        
        val errorMessage = "Network connection failed"
        `when`(aiRepository.sendChatMessage(any())).thenReturn(
            Result.failure(Exception(errorMessage))
        )

        // When
        val result = chatUseCase.invoke(request)

        // Then
        assert(result.isFailure)
        assert(result.exceptionOrNull()?.message?.contains("Network connection failed") == true)
        
        verify(aiRepository).sendChatMessage(any())
    }

    @Test
    fun `AICodeAnalysisUseCase invoke when successful should return analysis result`() = testScope.runTest {
        // Given
        val request = AICodeAnalysisRequest(
            code = """
                fun main() {
                    println("Hello World")
                }
            """.trimIndent(),
            language = "kotlin",
            analysisType = AnalysisType.SYNTAX
        )
        
        val expectedAnalysis = CodeAnalysisResult(
            issues = emptyList(),
            suggestions = listOf("代码结构良好"),
            complexity = AnalysisComplexity.SIMPLE,
            qualityScore = 0.90f
        )

        `when`(aiRepository.analyzeCode(any(), any(), any())).thenReturn(
            Result.success(expectedAnalysis)
        )

        // When
        val result = codeAnalysisUseCase.invoke(request)

        // Then
        assert(result.isSuccess)
        result.getOrNull()?.let { analysis ->
            assert(analysis.suggestions.isNotEmpty())
            assert(analysis.complexity == AnalysisComplexity.SIMPLE)
        }
        
        verify(aiRepository).analyzeCode(
            match { it.code == request.code },
            eq(request.language),
            eq(request.analysisType)
        )
    }

    @Test
    fun `AICodeAnalysisUseCase invoke with syntax error should detect issues`() = testScope.runTest {
        // Given
        val problematicCode = """
            fun main() {
                println("Hello World"
                // 缺少右括号
            }
        """.trimIndent()
        
        val request = AICodeAnalysisRequest(
            code = problematicCode,
            language = "kotlin",
            analysisType = AnalysisType.SYNTAX
        )
        
        val expectedAnalysis = CodeAnalysisResult(
            issues = listOf(
                CodeIssue(
                    type = IssueType.SYNTAX_ERROR,
                    message = "缺少右括号",
                    line = 3,
                    column = 20,
                    severity = Severity.HIGH
                )
            ),
            suggestions = listOf("添加缺少的右括号"),
            complexity = AnalysisComplexity.SIMPLE,
            qualityScore = 0.60f
        )

        `when`(aiRepository.analyzeCode(any(), any(), any())).thenReturn(
            Result.success(expectedAnalysis)
        )

        // When
        val result = codeAnalysisUseCase.invoke(request)

        // Then
        assert(result.isSuccess)
        result.getOrNull()?.let { analysis ->
            assert(analysis.issues.isNotEmpty())
            assert(analysis.issues.first().type == IssueType.SYNTAX_ERROR)
            assert(analysis.qualityScore < 0.80f)
        }
        
        verify(aiRepository).analyzeCode(any(), any(), any())
    }

    @Test
    fun `AICompletionUseCase invoke when successful should return completion suggestions`() = testScope.runTest {
        // Given
        val request = AICompletionRequest(
            partialCode = "fun ma",
            cursorPosition = 6,
            language = "kotlin"
        )
        
        val expectedSuggestions = listOf(
            CompletionSuggestion("main", "fun main()", CompletionType.FUNCTION),
            CompletionSuggestion("map", "map(transform: (T) -> R)", CompletionType.FUNCTION),
            CompletionSuggestion("max", "maxOf(a: T, b: T)", CompletionType.FUNCTION)
        )

        `when`(aiRepository.getCodeCompletion(any(), any(), any())).thenReturn(
            Result.success(expectedSuggestions)
        )

        // When
        val result = completionUseCase.invoke(request)

        // Then
        assert(result.isSuccess)
        result.getOrNull()?.let { suggestions ->
            assert(suggestions.isNotEmpty())
            assert(suggestions.any { it.text.contains("main") })
            assert(suggestions.size >= 2)
        }
        
        verify(aiRepository).getCodeCompletion(
            eq(request.partialCode),
            eq(request.cursorPosition),
            eq(request.language)
        )
    }

    @Test
    fun `AICompletionUseCase invoke when no suggestions should return empty list`() = testScope.runTest {
        // Given
        val request = AICompletionRequest(
            partialCode = "xyz123",
            cursorPosition = 6,
            language = "kotlin"
        )
        
        val emptySuggestions = emptyList<CompletionSuggestion>()

        `when`(aiRepository.getCodeCompletion(any(), any(), any())).thenReturn(
            Result.success(emptySuggestions)
        )

        // When
        val result = completionUseCase.invoke(request)

        // Then
        assert(result.isSuccess)
        result.getOrNull()?.let { suggestions ->
            assert(suggestions.isEmpty())
        }
        
        verify(aiRepository).getCodeCompletion(any(), any(), any())
    }

    @Test
    fun `AIChatUseCase with conversation history should include context`() = testScope.runTest {
        // Given
        val context = listOf(
            ChatMessage(
                content = "What is Kotlin?",
                isUser = true,
                timestamp = 1000L,
                messageId = "msg1"
            ),
            ChatMessage(
                content = "Kotlin is a modern programming language...",
                isUser = false,
                timestamp = 2000L,
                messageId = "msg2"
            )
        )
        
        val request = AIRequest(
            message = "Tell me more about it",
            type = AIType.CHAT,
            context = context
        )
        
        val expectedResponse = AIResponse(
            content = "Kotlin is a statically typed programming language...",
            type = AIType.CHAT,
            confidence = 0.90f,
            processingTime = 1200L
        )

        `when`(aiRepository.sendChatMessage(any())).thenReturn(
            Result.success(
                ChatMessage(
                    content = "Kotlin is a statically typed programming language...",
                    isUser = false,
                    timestamp = System.currentTimeMillis(),
                    messageId = "msg3"
                )
            )
        )

        // When
        val result = chatUseCase.invoke(request)

        // Then
        assert(result.isSuccess)
        result.getOrNull()?.let { response ->
            assert(response.content.contains("Kotlin"))
        }
        
        verify(aiRepository).sendChatMessage(
            match { requestWithContext ->
                requestWithContext.message == request.message &&
                requestWithContext.conversationId != null
            }
        )
    }

    @Test
    fun `AICodeAnalysisUseCase for performance analysis should return performance metrics`() = testScope.runTest {
        // Given
        val code = """
            fun inefficientFunction(n: Int): Int {
                var result = 0
                for (i in 0 until n) {
                    for (j in 0 until n) {
                        result += i * j
                    }
                }
                return result
            }
        """.trimIndent()
        
        val request = AICodeAnalysisRequest(
            code = code,
            language = "kotlin",
            analysisType = AnalysisType.PERFORMANCE
        )
        
        val expectedAnalysis = CodeAnalysisResult(
            issues = listOf(
                CodeIssue(
                    type = IssueType.PERFORMANCE,
                    message = "嵌套循环可能导致性能问题",
                    line = 4,
                    severity = Severity.MEDIUM
                )
            ),
            suggestions = listOf(
                "考虑使用更高效的算法",
                "使用公式计算代替嵌套循环"
            ),
            complexity = AnalysisComplexity.COMPLEX,
            qualityScore = 0.70f
        )

        `when`(aiRepository.analyzeCode(any(), any(), any())).thenReturn(
            Result.success(expectedAnalysis)
        )

        // When
        val result = codeAnalysisUseCase.invoke(request)

        // Then
        assert(result.isSuccess)
        result.getOrNull()?.let { analysis ->
            assert(analysis.issues.any { it.type == IssueType.PERFORMANCE })
            assert(analysis.complexity == AnalysisComplexity.COMPLEX)
            assert(analysis.suggestions.isNotEmpty())
        }
        
        verify(aiRepository).analyzeCode(any(), any(), eq(AnalysisType.PERFORMANCE))
    }

    @Test
    fun `AICompletionUseCase with context should provide context-aware completions`() = testScope.runTest {
        // Given
        val contextCode = """
            class Person {
                val name: String
                val age: Int
                
                constructor(name: String, age: Int) {
                    this.name = name
                    this.age = age
                }
            }
        """.trimIndent()
        
        val request = AICompletionRequest(
            partialCode = "fun createPerson",
            cursorPosition = 15,
            language = "kotlin",
            context = contextCode
        )
        
        val expectedSuggestions = listOf(
            CompletionSuggestion(
                "createPerson", 
                "fun createPerson(name: String, age: Int): Person", 
                CompletionType.FUNCTION
            )
        )

        `when`(aiRepository.getCodeCompletion(any(), any(), any())).thenReturn(
            Result.success(expectedSuggestions)
        )

        // When
        val result = completionUseCase.invoke(request)

        // Then
        assert(result.isSuccess)
        result.getOrNull()?.let { suggestions ->
            assert(suggestions.isNotEmpty())
            assert(suggestions.first().text.contains("createPerson"))
        }
        
        verify(aiRepository).getCodeCompletion(
            eq(request.partialCode),
            eq(request.cursorPosition),
            eq(request.language)
        )
    }
}
