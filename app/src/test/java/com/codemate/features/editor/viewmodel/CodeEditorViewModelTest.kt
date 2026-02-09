package com.codemate.features.editor.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.codemate.features.editor.domain.usecase.*
import com.codemate.features.editor.domain.entity.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

/**
 * 代码编辑器ViewModel单元测试
 * 测试代码编辑、保存、加载和语法高亮功能
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CodeEditorViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var saveFileUseCase: SaveFileUseCase

    @Mock
    private lateinit var loadFileUseCase: LoadFileUseCase

    @Mock
    private lateinit var syntaxHighlightUseCase: SyntaxHighlightUseCase

    @Mock
    private lateinit var autoCompleteUseCase: AutoCompleteUseCase

    @Mock
    private lateinit var codeAnalysisUseCase: EditorCodeAnalysisUseCase

    @Mock
    private lateinit var editorStateObserver: Observer<EditorState>

    @Mock
    private lateinit var syntaxHighlightingObserver: Observer<SyntaxHighlightingResult>

    @Mock
    private lateinit var autoCompleteObserver: Observer<AutoCompleteResult>

    private lateinit var viewModel: CodeEditorViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = CodeEditorViewModel(
            saveFileUseCase = saveFileUseCase,
            loadFileUseCase = loadFileUseCase,
            syntaxHighlightUseCase = syntaxHighlightUseCase,
            autoCompleteUseCase = autoCompleteUseCase,
            codeAnalysisUseCase = codeAnalysisUseCase,
            dispatcherProvider = coroutineTestRule.dispatcherProvider
        )
        
        // 观察编辑器状态
        viewModel.editorState.observeForever(editorStateObserver)
        viewModel.syntaxHighlighting.observeForever(syntaxHighlightingObserver)
        viewModel.autoComplete.observeForever(autoCompleteObserver)
    }

    @Test
    fun `loadFile when successful should update editor content`() = testScope.runTest {
        // Given
        val filePath = "/path/to/file.kt"
        val fileContent = """
            fun main() {
                println("Hello World")
            }
        """.trimIndent()
        
        val expectedState = EditorState(
            content = fileContent,
            filePath = filePath,
            isModified = false,
            cursorPosition = 0,
            language = ProgrammingLanguage.KOTLIN,
            isLoading = false
        )

        `when`(loadFileUseCase.invoke(any())).thenReturn(Result.success(fileContent))

        // When
        viewModel.loadFile(filePath)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(editorStateObserver).onChanged(
            match { it.content == fileContent && it.filePath == filePath }
        )
        verify(loadFileUseCase).invoke(
            match { it.filePath == filePath }
        )
    }

    @Test
    fun `loadFile when file not found should update error state`() = testScope.runTest {
        // Given
        val filePath = "/non/existent/file.kt"
        val errorMessage = "文件不存在"

        `when`(loadFileUseCase.invoke(any())).thenReturn(Result.failure(Exception(errorMessage)))

        // When
        viewModel.loadFile(filePath)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(editorStateObserver).onChanged(
            match { it.errorMessage?.contains("文件不存在") == true }
        )
    }

    @Test
    fun `saveFile when successful should update save state`() = testScope.runTest {
        // Given
        val filePath = "/path/to/file.kt"
        val content = "fun test() { }"
        val saveResult = SaveResult(
            isSuccess = true,
            filePath = filePath,
            saveTime = System.currentTimeMillis()
        )

        `when`(saveFileUseCase.invoke(any())).thenReturn(Result.success(saveResult))

        // When
        viewModel.saveFile(filePath, content)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(saveFileUseCase).invoke(
            match { it.filePath == filePath && it.content == content }
        )
        verify(editorStateObserver).onChanged(
            match { it.isModified == false }
        )
    }

    @Test
    fun `updateContent when text changes should update editor state`() {
        // Given
        val newContent = "fun updatedFunction() {\n    // Updated content\n}"

        // When
        viewModel.updateContent(newContent)

        // Then
        verify(editorStateObserver).onChanged(
            match { 
                it.content == newContent && 
                it.isModified == true && 
                it.cursorPosition == newContent.length 
            }
        )
    }

    @Test
    fun `setCursorPosition should update cursor location`() {
        // Given
        val position = 25

        // When
        viewModel.setCursorPosition(position)

        // Then
        verify(editorStateObserver).onChanged(
            match { it.cursorPosition == position }
        )
    }

    @Test
    fun `performSyntaxHighlight should return highlighted code`() = testScope.runTest {
        // Given
        val code = "fun main() { println(\"Hello\") }"
        val expectedHighlighting = SyntaxHighlightingResult(
            highlightedCode = "<span class='keyword'>fun</span> main() { <span class='string'>\"Hello\"</span> }",
            tokens = listOf(
                SyntaxToken("fun", TokenType.KEYWORD, 0, 3),
                SyntaxToken("main", TokenType.FUNCTION, 4, 8),
                SyntaxToken("\"Hello\"", TokenType.STRING, 16, 23)
            ),
            processingTime = 50L
        )

        `when`(syntaxHighlightUseCase.invoke(any())).thenReturn(Result.success(expectedHighlighting))

        // When
        viewModel.performSyntaxHighlight(code, ProgrammingLanguage.KOTLIN)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(syntaxHighlightUseCase).invoke(
            match { it.code == code && it.language == ProgrammingLanguage.KOTLIN }
        )
        verify(syntaxHighlightingObserver).onChanged(expectedHighlighting)
    }

    @Test
    fun `getAutoCompleteSuggestions should return completion options`() = testScope.runTest {
        // Given
        val partialCode = "fun ma"
        val cursorPosition = 6
        
        val expectedSuggestions = AutoCompleteResult(
            suggestions = listOf(
                CompletionSuggestion("main", "fun main()", CompletionType.FUNCTION),
                CompletionSuggestion("map", "map(transform: (T) -> R)", CompletionType.FUNCTION),
                CompletionSuggestion("max", "maxOf(a: T, b: T)", CompletionType.FUNCTION)
            ),
            selectedIndex = 0,
            processingTime = 30L
        )

        `when`(autoCompleteUseCase.invoke(any())).thenReturn(Result.success(expectedSuggestions))

        // When
        viewModel.getAutoCompleteSuggestions(partialCode, cursorPosition)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(autoCompleteUseCase).invoke(
            match { 
                it.partialCode == partialCode && 
                it.cursorPosition == cursorPosition 
            }
        )
        verify(autoCompleteObserver).onChanged(expectedSuggestions)
    }

    @Test
    fun `selectSuggestion should apply selected completion`() {
        // Given
        val suggestion = CompletionSuggestion(
            text = "main",
            displayText = "fun main()",
            type = CompletionType.FUNCTION
        )

        // When
        viewModel.selectSuggestion(suggestion)

        // Then
        verify(editorStateObserver).onChanged(
            match { 
                it.content.contains("main") && 
                it.isModified == true 
            }
        )
    }

    @Test
    fun `analyzeCode should perform code analysis`() = testScope.runTest {
        // Given
        val code = """
            fun testFunction() {
                val x = 10
                if (x > 5) {
                    println("Greater than 5")
                }
            }
        """.trimIndent()
        
        val expectedAnalysis = CodeAnalysisResult(
            complexity = CodeComplexity.SIMPLE,
            issues = emptyList(),
            suggestions = listOf("代码结构良好"),
            qualityScore = 0.90f
        )

        `when`(codeAnalysisUseCase.invoke(any())).thenReturn(Result.success(expectedAnalysis))

        // When
        viewModel.analyzeCode(code)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(codeAnalysisUseCase).invoke(
            match { it.code == code }
        )
    }

    @Test
    fun `formatCode should format the current code`() = testScope.runTest {
        // Given
        val unformattedCode = "fun test(){val x=10;println(x)}"
        val expectedFormatted = """
            fun test() {
                val x = 10
                println(x)
            }
        """.trimIndent()

        // 模拟格式化操作
        `when`(formatCodeUseCase.invoke(any())).thenReturn(Result.success(expectedFormatted))

        // When
        viewModel.formatCode(unformattedCode)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(editorStateObserver).onChanged(
            match { it.content == expectedFormatted }
        )
    }

    @Test
    fun `toggleTheme should switch between light and dark themes`() {
        // When
        viewModel.toggleTheme()

        // Then
        verify(editorStateObserver).onChanged(
            match { it.theme != null }
        )
    }

    @Test
    fun `setFontSize should update editor font size`() {
        // Given
        val newFontSize = 16

        // When
        viewModel.setFontSize(newFontSize)

        // Then
        verify(editorStateObserver).onChanged(
            match { it.fontSize == newFontSize }
        )
    }

    /**
     * 测试规则类，用于管理协程调度器
     */
    private class CoroutineTestRule : org.junit.rules.TestWatcher() {
        lateinit var dispatcherProvider: com.codemate.utils.DispatcherProvider
            private set

        override fun starting(description: org.junit.runner.Description?) {
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
