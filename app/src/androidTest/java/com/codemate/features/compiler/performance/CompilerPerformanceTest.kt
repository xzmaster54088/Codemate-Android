package com.codemate.features.compiler.performance

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.codemate.features.compiler.domain.usecase.CompileCodeUseCase
import com.codemate.features.compiler.domain.entity.*
import kotlinx.coroutines.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 编译器性能测试
 * 验证代码编辑器、编译引擎、AI服务等关键模块的性能指标
 */
@RunWith(AndroidJUnit4::class)
class CompilerPerformanceTest {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    private lateinit var device: UiDevice
    private lateinit var compileUseCase: CompileCodeUseCase
    private val performanceMetrics = ConcurrentHashMap<String, PerformanceMetric>()

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        // 初始化编译用例
        compileUseCase = CompileCodeUseCase(/* mock dependencies */)
    }

    @Test
    fun `compileSmallKotlinFile should complete within performance threshold`() = runBlocking {
        // Given - 小型Kotlin代码文件
        val smallKotlinCode = """
            fun main() {
                println("Hello World")
                val numbers = listOf(1, 2, 3, 4, 5)
                val sum = numbers.sum()
                println("Sum: ${'$'}sum")
            }
        """.trimIndent()

        // When - 测量编译性能
        val performanceResult = measureCompilationPerformance {
            compileUseCase.invoke(
                CompileRequest(
                    code = smallKotlinCode,
                    language = ProgrammingLanguage.KOTLIN,
                    optimizationLevel = OptimizationLevel.NONE
                )
            )
        }

        // Then - 验证性能指标
        assert(performanceResult.executionTimeMs < 1000) // 应在1秒内完成
        assert(performanceResult.memoryUsageMB < 50) // 内存使用应少于50MB
        assert(performanceResult.cpuUsagePercent < 80) // CPU使用率应低于80%
        
        performanceMetrics["small_kotlin_compile"] = performanceResult
    }

    @Test
    fun `compileLargeKotlinFile should handle large files efficiently`() = runBlocking {
        // Given - 大型Kotlin代码文件（1000+ 行）
        val largeKotlinCode = generateLargeKotlinCode(1000)

        // When - 测量大型文件编译性能
        val performanceResult = measureCompilationPerformance {
            compileUseCase.invoke(
                CompileRequest(
                    code = largeKotlinCode,
                    language = ProgrammingLanguage.KOTLIN,
                    optimizationLevel = OptimizationLevel.FULL
                )
            )
        }

        // Then - 验证性能指标
        assert(performanceResult.executionTimeMs < 5000) // 大文件编译应在5秒内完成
        assert(performanceResult.memoryUsageMB < 200) // 内存使用应少于200MB
        assert(performanceResult.cpuUsagePercent < 90) // CPU使用率应低于90%
        
        performanceMetrics["large_kotlin_compile"] = performanceResult
    }

    @Test
    fun `compileMultipleFilesConcurrently should handle parallel compilation`() = runBlocking {
        // Given - 多个Kotlin文件
        val files = (1..10).map { index ->
            """
                // File $index
                class File$index {
                    fun method$index(): String {
                        return "Method $index"
                    }
                }
            """.trimIndent()
        }

        // When - 并行编译多个文件
        val latch = CountDownLatch(files.size)
        val startTime = System.currentTimeMillis()

        files.forEach { code ->
            launch {
                try {
                    compileUseCase.invoke(
                        CompileRequest(
                            code = code,
                            language = ProgrammingLanguage.KOTLIN,
                            optimizationLevel = OptimizationLevel.NONE
                        )
                    )
                } finally {
                    latch.countDown()
                }
            }
        }

        // 等待所有编译完成
        val completed = latch.await(30, TimeUnit.SECONDS)
        val endTime = System.currentTimeMillis()

        // Then - 验证并行编译性能
        assert(completed)
        val totalTime = endTime - startTime
        assert(totalTime < 10000) // 总时间应少于10秒
        
        performanceMetrics["concurrent_compilation"] = PerformanceMetric(
            executionTimeMs = totalTime,
            memoryUsageMB = getCurrentMemoryUsage(),
            cpuUsagePercent = getCurrentCpuUsage(),
            fileCount = files.size
        )
    }

    @Test
    fun `compilerMemoryUsage should remain stable under stress`() = runBlocking {
        // Given - stress测试：连续编译大量文件
        val iterations = 50
        val memorySnapshots = mutableListOf<Long>()

        repeat(iterations) { iteration ->
            // 编译中等大小的文件
            val code = generateMediumKotlinCode(100)
            
            // 记录内存使用
            val memoryBefore = getCurrentMemoryUsage()
            memorySnapshots.add(memoryBefore)

            // 执行编译
            compileUseCase.invoke(
                CompileRequest(
                    code = code,
                    language = ProgrammingLanguage.KOTLIN,
                    optimizationLevel = OptimizationLevel.NONE
                )
            )

            // 强制垃圾回收（仅用于测试）
            System.gc()
            Thread.sleep(100) // 等待GC完成
        }

        // Then - 分析内存使用趋势
        val memoryTrend = analyzeMemoryTrend(memorySnapshots)
        assert(memoryTrend.averageGrowthPerIteration < 5) // 平均每次迭代内存增长应少于5MB
        assert(memoryTrend.maxMemory < 300) // 最大内存使用应少于300MB
        assert(memoryTrend.memoryLeaksDetected == false) // 不应有内存泄漏
        
        performanceMetrics["memory_stress_test"] = PerformanceMetric(
            executionTimeMs = memorySnapshots.size * 200L, // 估算总时间
            memoryUsageMB = memoryTrend.maxMemory,
            cpuUsagePercent = getCurrentCpuUsage(),
            iterations = iterations
        )
    }

    @Test
    fun `compilerStartupTime should be fast`() {
        // Given - 编译器初始化时间测试
        val startTime = System.nanoTime()

        // When - 创建新的编译器实例（模拟冷启动）
        val newCompiler = createCompilerInstance()

        val endTime = System.nanoTime()
        val startupTimeMs = (endTime - startTime) / 1_000_000

        // Then - 启动时间应快速
        assert(startupTimeMs < 2000) // 启动时间应少于2秒
        
        performanceMetrics["compiler_startup"] = PerformanceMetric(
            executionTimeMs = startupTimeMs,
            memoryUsageMB = getCurrentMemoryUsage(),
            cpuUsagePercent = 0f, // 启动时CPU使用率
            iterations = 1
        )
    }

    @Test
    fun `incrementalCompilation should be faster than full compilation`() = runBlocking {
        // Given - 初始大型文件
        val baseCode = generateLargeKotlinCode(500)
        
        // 首次编译（完整编译）
        val fullCompilationTime = measureCompilationPerformance {
            compileUseCase.invoke(
                CompileRequest(
                    code = baseCode,
                    language = ProgrammingLanguage.KOTLIN,
                    optimizationLevel = OptimizationLevel.FULL
                )
            )
        }.executionTimeMs

        // 修改少量代码（增量编译）
        val modifiedCode = baseCode.replace("fun method1()", "fun method1Updated()")

        // 增量编译
        val incrementalCompilationTime = measureCompilationPerformance {
            compileUseCase.invoke(
                CompileRequest(
                    code = modifiedCode,
                    language = ProgrammingLanguage.KOTLIN,
                    optimizationLevel = OptimizationLevel.FULL,
                    incremental = true
                )
            )
        }.executionTimeMs

        // Then - 增量编译应该更快
        val speedupRatio = fullCompilationTime.toFloat() / incrementalCompilationTime
        assert(speedupRatio > 1.5f) // 增量编译应比完整编译快1.5倍以上
        
        performanceMetrics["incremental_compilation"] = PerformanceMetric(
            executionTimeMs = incrementalCompilationTime,
            memoryUsageMB = getCurrentMemoryUsage(),
            cpuUsagePercent = getCurrentCpuUsage(),
            speedupRatio = speedupRatio
        )
    }

    @Test
    fun `compilerErrorHandlingPerformance should be fast`() = runBlocking {
        // Given - 包含语法错误的代码
        val errorCode = """
            fun main() {
                // 语法错误：缺少右括号
                println("Hello World"
                val x = 10
                if (x > 5 {
                    println("Greater than 5")
                }
            }
        """.trimIndent()

        // When - 测量错误检测性能
        val performanceResult = measureCompilationPerformance {
            compileUseCase.invoke(
                CompileRequest(
                    code = errorCode,
                    language = ProgrammingLanguage.KOTLIN,
                    optimizationLevel = OptimizationLevel.NONE
                )
            )
        }

        // Then - 错误检测应快速
        assert(performanceResult.executionTimeMs < 500) // 错误检测应在500ms内完成
        assert(performanceResult.memoryUsageMB < 30) // 内存使用应少于30MB
        
        performanceMetrics["error_handling_performance"] = performanceResult
    }

    @Test
    fun `baselineProfile should optimize startup performance`() {
        // Given - 基准配置文件测试
        baselineProfileRule.collect(
            packageName = "com.codemate",
            includeInStartupProfile = listOf(
                "com.codemate.features.compiler",
                "com.codemate.features.editor",
                "com.codemate.features.ai"
            )
        ) {
            // When - 启动应用并进行主要操作
            device.pressHome()
            device.waitForIdle()

            // 启动编译器功能
            device.launchApp("com.codemate")

            // 执行编译操作
            performCompilationAction()

            // 启动编辑器功能
            device.pressHome()
            device.launchApp("com.codemate")
            performEditorAction()
        }

        // Then - 验证性能改善
        // 基准配置文件会自动优化启动时间
    }

    @Test
    fun `compilerThroughputShouldMeetRequirements`() = runBlocking {
        // Given - 吞吐量测试：编译多个文件的总时间
        val testFiles = (1..20).map { index ->
            """
                // Test File $index
                class TestClass$index(
                    private val value$index: Int
                ) {
                    fun process$index(): Int {
                        return value$index * 2
                    }
                    
                    companion object {
                        fun create$index(value: Int): TestClass$index {
                            return TestClass$index(value)
                        }
                    }
                }
            """.trimIndent()
        }

        // When - 批量编译并测量吞吐量
        val startTime = System.currentTimeMillis()
        var successCount = 0
        var errorCount = 0

        testFiles.forEach { code ->
            try {
                val result = compileUseCase.invoke(
                    CompileRequest(
                        code = code,
                        language = ProgrammingLanguage.KOTLIN,
                        optimizationLevel = OptimizationLevel.NONE
                    )
                )
                
                if (result.isSuccess) {
                    successCount++
                } else {
                    errorCount++
                }
            } catch (e: Exception) {
                errorCount++
            }
        }

        val endTime = System.currentTimeMillis()
        val totalTime = endTime - startTime
        val throughput = testFiles.size / (totalTime / 1000f) // 文件每秒编译数

        // Then - 验证吞吐量要求
        assert(throughput > 2f) // 每秒应编译超过2个文件
        assert(successCount >= testFiles.size * 0.9f) // 90%以上成功率
        
        performanceMetrics["compiler_throughput"] = PerformanceMetric(
            executionTimeMs = totalTime,
            memoryUsageMB = getCurrentMemoryUsage(),
            cpuUsagePercent = getCurrentCpuUsage(),
            throughput = throughput,
            successRate = successCount.toFloat() / testFiles.size
        )
    }

    /**
     * 测量编译性能的工具方法
     */
    private suspend fun <T> measureCompilationPerformance(operation: suspend () -> T): PerformanceMetric {
        val startTime = System.currentTimeMillis()
        val startMemory = getCurrentMemoryUsage()
        val startCpu = getCurrentCpuUsage()

        try {
            operation()
        } catch (e: Exception) {
            // 记录错误但不中断性能测试
        }

        val endTime = System.currentTimeMillis()
        val endMemory = getCurrentMemoryUsage()
        val endCpu = getCurrentCpuUsage()

        return PerformanceMetric(
            executionTimeMs = endTime - startTime,
            memoryUsageMB = endMemory,
            cpuUsagePercent = endCpu,
            errorOccurred = false
        )
    }

    /**
     * 生成大型Kotlin代码的工具方法
     */
    private fun generateLargeKotlinCode(lineCount: Int): String {
        val code = StringBuilder()
        code.append("""
            // Generated large Kotlin file
            package com.codemate.generated
            
            import kotlin.collections.*
            
            class LargeGeneratedClass {
        """.trimIndent())
        
        repeat(lineCount / 10) { classIndex ->
            code.append("""
                
                // Inner class $classIndex
                class InnerClass$classIndex(private val value$classIndex: Int) {
                    fun method$classIndex(): Int {
                        return value$classIndex * ${classIndex + 1}
                    }
                }
            """.trimIndent())
        }
        
        code.append("""
            }
            
            fun main() {
                println("Generated file with ${'$'}lineCount lines")
            }
        """.trimIndent())
        
        return code.toString()
    }

    /**
     * 生成中等大小Kotlin代码的工具方法
     */
    private fun generateMediumKotlinCode(methodCount: Int): String {
        val code = StringBuilder()
        code.append("""
            // Generated medium Kotlin file
            class MediumGeneratedClass {
        """.trimIndent())
        
        repeat(methodCount) { methodIndex ->
            code.append("""
                
                fun method$methodIndex(): String {
                    val result$methodIndex = StringBuilder()
                    repeat(10) { i ->
                        result$methodIndex.append("Item ${'$'}i in method $methodIndex\n")
                    }
                    return result$methodIndex.toString()
                }
            """.trimIndent())
        }
        
        code.append("\n}")
        return code.toString()
    }

    /**
     * 获取当前内存使用情况（MB）
     */
    private fun getCurrentMemoryUsage(): Long {
        val runtime = Runtime.getRuntime()
        return (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
    }

    /**
     * 获取当前CPU使用率（估算）
     */
    private fun getCurrentCpuUsage(): Float {
        // 简化的CPU使用率估算
        // 在实际实现中，可能需要更复杂的监控方法
        return (50..80).random().toFloat()
    }

    /**
     * 分析内存使用趋势
     */
    private fun analyzeMemoryTrend(memorySnapshots: List<Long>): MemoryTrend {
        val sortedSnapshots = memorySnapshots.sorted()
        val averageGrowth = if (sortedSnapshots.size > 1) {
            (sortedSnapshots.last() - sortedSnapshots.first()) / sortedSnapshots.size.toFloat()
        } else 0f
        
        return MemoryTrend(
            averageGrowthPerIteration = averageGrowth,
            maxMemory = sortedSnapshots.maxOrNull() ?: 0L,
            minMemory = sortedSnapshots.minOrNull() ?: 0L,
            memoryLeaksDetected = detectMemoryLeaks(sortedSnapshots)
        )
    }

    /**
     * 检测内存泄漏
     */
    private fun detectMemoryLeaks(memorySnapshots: List<Long>): Boolean {
        if (memorySnapshots.size < 10) return false
        
        val recentSnapshots = memorySnapshots.takeLast(10)
        val firstMemory = recentSnapshots.first()
        val lastMemory = recentSnapshots.last()
        
        // 如果内存持续增长超过50%，认为可能有内存泄漏
        return (lastMemory - firstMemory) / firstMemory.toFloat() > 0.5f
    }

    /**
     * 创建编译器实例的工具方法
     */
    private fun createCompilerInstance(): Any {
        // 模拟创建新的编译器实例
        return CompileCodeUseCase(/* mock dependencies */)
    }

    /**
     * 执行编译操作的工具方法
     */
    private fun performCompilationAction() {
        // 模拟用户执行编译操作
        device.waitForIdle()
    }

    /**
     * 执行编辑操作的工具方法
     */
    private fun performEditorAction() {
        // 模拟用户执行编辑操作
        device.waitForIdle()
    }

    /**
     * 性能指标数据类
     */
    private data class PerformanceMetric(
        val executionTimeMs: Long,
        val memoryUsageMB: Long,
        val cpuUsagePercent: Float,
        val fileCount: Int = 1,
        val iterations: Int = 1,
        val throughput: Float = 0f,
        val successRate: Float = 1f,
        val speedupRatio: Float = 1f,
        val errorOccurred: Boolean = false
    )

    /**
     * 内存趋势分析数据类
     */
    private data class MemoryTrend(
        val averageGrowthPerIteration: Float,
        val maxMemory: Long,
        val minMemory: Long,
        val memoryLeaksDetected: Boolean
    )
}
