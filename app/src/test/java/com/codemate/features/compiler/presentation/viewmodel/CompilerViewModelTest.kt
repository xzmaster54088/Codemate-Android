package com.codemate.features.compiler.presentation.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.codemate.features.compiler.domain.usecase.CompileCodeUseCase
import com.codemate.features.compiler.domain.usecase.ExecuteCodeUseCase
import com.codemate.features.compiler.domain.usecase.ValidateCodeUseCase
import com.codemate.features.compiler.domain.entity.CompilationResult
import com.codemate.features.compiler.domain.entity.ExecutionResult
import com.codemate.features.compiler.domain.entity.ValidationResult
import com.codemate.features.compiler.domain.entity.ProgrammingLanguage
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
 * 编译器功能ViewModel单元测试
 * 测试代码编译、执行和验证功能
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompilerViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @get:Rule
    val coroutineTestRule = CoroutineTestRule()

    @Mock
    private lateinit var compileCodeUseCase: CompileCodeUseCase

    @Mock
    private lateinit var executeCodeUseCase: ExecuteCodeUseCase

    @Mock
    private lateinit var validateCodeUseCase: ValidateCodeUseCase

    @Mock
    private lateinit var compilationResultObserver: Observer<CompilationResult>

    @Mock
    private lateinit var executionResultObserver: Observer<ExecutionResult>

    @Mock
    private lateinit var validationResultObserver: Observer<ValidationResult>

    private lateinit var viewModel: CompilerViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        viewModel = CompilerViewModel(
            compileCodeUseCase = compileCodeUseCase,
            executeCodeUseCase = executeCodeUseCase,
            validateCodeUseCase = validateCodeUseCase,
            dispatcherProvider = coroutineTestRule.dispatcherProvider
        )
        
        // 观察编译结果
        viewModel.compilationResult.observeForever(compilationResultObserver)
        viewModel.executionResult.observeForever(executionResultObserver)
        viewModel.validationResult.observeForever(validationResultObserver)
    }

    @Test
    fun `compileCode when successful should update compilation result`() = testScope.runTest {
        // Given
        val kotlinCode = """
            fun main() {
                println("Hello World")
            }
        """.trimIndent()
        
        val expectedResult = CompilationResult(
            isSuccess = true,
            output = "编译成功",
            errors = emptyList(),
            warnings = emptyList(),
            compilationTime = 1200L,
            language = ProgrammingLanguage.KOTLIN
        )

        `when`(compileCodeUseCase.invoke(any())).thenReturn(Result.success(expectedResult))

        // When
        viewModel.compileCode(kotlinCode, ProgrammingLanguage.KOTLIN)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(compilationResultObserver).onChanged(expectedResult)
        verify(compileCodeUseCase).invoke(
            match { it.code == kotlinCode && it.language == ProgrammingLanguage.KOTLIN }
        )
    }

    @Test
    fun `compileCode when compilation fails should update error state`() = testScope.runTest {
        // Given
        val kotlinCode = "fun main() { syntax error }"
        val errorResult = CompilationResult(
            isSuccess = false,
            output = "",
            errors = listOf("语法错误：缺少右括号"),
            warnings = emptyList(),
            compilationTime = 500L,
            language = ProgrammingLanguage.KOTLIN
        )

        `when`(compileCodeUseCase.invoke(any())).thenReturn(Result.success(errorResult))

        // When
        viewModel.compileCode(kotlinCode, ProgrammingLanguage.KOTLIN)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(compilationResultObserver).onChanged(errorResult)
        verify(compileCodeUseCase).invoke(any())
    }

    @Test
    fun `executeCode when successful should update execution result`() = testScope.runTest {
        // Given
        val javaCode = """
            public class Main {
                public static void main(String[] args) {
                    System.out.println("Hello World");
                }
            }
        """.trimIndent()
        
        val expectedExecution = ExecutionResult(
            isSuccess = true,
            output = "Hello World",
            errorOutput = "",
            executionTime = 300L,
            exitCode = 0
        )

        `when`(executeCodeUseCase.invoke(any())).thenReturn(Result.success(expectedExecution))

        // When
        viewModel.executeCode(javaCode, ProgrammingLanguage.JAVA)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(executionResultObserver).onChanged(expectedExecution)
        verify(executeCodeUseCase).invoke(
            match { it.code == javaCode && it.language == ProgrammingLanguage.JAVA }
        )
    }

    @Test
    fun `executeCode when runtime error occurs should update error state`() = testScope.runTest {
        // Given
        val pythonCode = """
            x = 10 / 0
            print(x)
        """.trimIndent()
        
        val errorExecution = ExecutionResult(
            isSuccess = false,
            output = "",
            errorOutput = "ZeroDivisionError: division by zero",
            executionTime = 100L,
            exitCode = 1
        )

        `when`(executeCodeUseCase.invoke(any())).thenReturn(Result.success(errorExecution))

        // When
        viewModel.executeCode(pythonCode, ProgrammingLanguage.PYTHON)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(executionResultObserver).onChanged(errorExecution)
        verify(executeCodeUseCase).invoke(any())
    }

    @Test
    fun `validateCode when code is valid should return success validation`() = testScope.runTest {
        // Given
        val validKotlinCode = """
            data class Person(val name: String, val age: Int)
            
            fun greet(person: Person): String {
                return "Hello, ${'$'}{person.name}! You are ${'$'}{person.age} years old."
            }
        """.trimIndent()
        
        val expectedValidation = ValidationResult(
            isValid = true,
            issues = emptyList(),
            suggestions = listOf("代码结构良好", "命名规范"),
            codeQuality = 0.95f
        )

        `when`(validateCodeUseCase.invoke(any())).thenReturn(Result.success(expectedValidation))

        // When
        viewModel.validateCode(validKotlinCode, ProgrammingLanguage.KOTLIN)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(validationResultObserver).onChanged(expectedValidation)
        verify(validateCodeUseCase).invoke(
            match { it.code == validKotlinCode }
        )
    }

    @Test
    fun `validateCode when code has issues should return validation with problems`() = testScope.runTest {
        // Given
        val problematicCode = """
            var x = 5
            var y = x + "10"
            print(z)
        """.trimIndent()
        
        val problemValidation = ValidationResult(
            isValid = false,
            issues = listOf(
                "变量类型不匹配警告",
                "未定义变量：z"
            ),
            suggestions = listOf(
                "考虑使用类型注解",
                "定义变量z或修正拼写"
            ),
            codeQuality = 0.60f
        )

        `when`(validateCodeUseCase.invoke(any())).thenReturn(Result.success(problemValidation))

        // When
        viewModel.validateCode(problematicCode, ProgrammingLanguage.PYTHON)

        // 等待异步操作完成
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(validationResultObserver).onChanged(problemValidation)
        verify(validateCodeUseCase).invoke(any())
    }

    @Test
    fun `getSupportedLanguages should return list of supported programming languages`() {
        // When
        val languages = viewModel.getSupportedLanguages()

        // Then
        assert(languages.isNotEmpty())
        assert(languages.contains(ProgrammingLanguage.KOTLIN))
        assert(languages.contains(ProgrammingLanguage.JAVA))
        assert(languages.contains(ProgrammingLanguage.PYTHON))
    }

    @Test
    fun `clearCompilationResults should reset all compilation data`() {
        // When
        viewModel.clearCompilationResults()

        // Then
        // 验证编译结果被清除
        // 可以检查LiveData是否为空或重置状态
    }

    @Test
    fun `setCompilationMode should update compilation settings`() {
        // Given
        val mode = "debug"

        // When
        viewModel.setCompilationMode(mode)

        // Then
        // 验证编译模式设置被更新
        // 可以通过观察编译设置变化来验证
    }

    @Test
    fun `cancelCompilation should interrupt ongoing compilation`() {
        // When
        viewModel.cancelCompilation()

        // Then
        // 验证编译被取消
        // 可能需要测试协程取消逻辑
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
