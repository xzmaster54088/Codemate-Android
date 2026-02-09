package com.codemate.features.compiler.core.parser

import android.util.Log
import com.codemate.features.compiler.domain.entities.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * 编译日志解析器
 * 负责解析编译过程中的错误、警告信息
 * 提供智能错误定位和建议修复方案
 */
class LogParser {
    companion object {
        private const val TAG = "LogParser"
        
        // 各种语言的错误模式
        private val ERROR_PATTERNS = mapOf(
            Language.JAVA to listOf(
                Pattern.compile("(\\w+\\.java):(\\d+):\\s+(\\w+):\\s+(.+)"),
                Pattern.compile("(\\w+\\.java):(\\d+):\\s+(.+)"),
                Pattern.compile("error:\\s*(.+)")
            ),
            Language.JAVASCRIPT to listOf(
                Pattern.compile("(\\w+\\.js):(\\d+):(\\d+):\\s*(error|warning):\\s*(.+)"),
                Pattern.compile("(\\w+\\.js):(\\d+):\\s*(error|warning):\\s*(.+)"),
                Pattern.compile("ReferenceError:\\s*(.+)"),
                Pattern.compile("SyntaxError:\\s*(.+)")
            ),
            Language.PYTHON to listOf(
                Pattern.compile("File\\s+\"([^\"]+)\",\\s*line\\s+(\\d+)(?:,\\s*in\\s+(\\w+))?"),
                Pattern.compile("(\\w+\\.py):(\\d+):\\s*(\\w+):\\s*(.+)"),
                Pattern.compile("(\\w+Error|\\w+Warning):\\s*(.+)")
            ),
            Language.CPP to listOf(
                Pattern.compile("(\\w+\\.(?:cpp|c|cc|h)):\\s*(\\d+):(\\d+):\\s*(\\w+):\\s*(.+)"),
                Pattern.compile("(\\w+\\.(?:cpp|c|cc|h)):\\s*(\\d+):\\s*(\\w+):\\s*(.+)"),
                Pattern.compile("(\\w+\\.(?:cpp|c|cc|h)):(\\d+):\\s*(.+)")
            ),
            Language.C to listOf(
                Pattern.compile("(\\w+\\.c):(\\d+):(\\d+):\\s*(\\w+):\\s*(.+)"),
                Pattern.compile("(\\w+\\.c):(\\d+):\\s*(\\w+):\\s*(.+)"),
                Pattern.compile("(\\w+\\.c):(\\d+):\\s*(.+)")
            ),
            Language.RUST to listOf(
                Pattern.compile("-->\\s*([^:]+):(\\d+):(\\d+)"),
                Pattern.compile("([^:]+):(\\d+):(\\d+):\\s*(\\w+):\\s*(.+)"),
                Pattern.compile("error\\[([^\\]]+)\\]:\\s*(.+)"),
                Pattern.compile("warning:\\s*(.+)")
            ),
            Language.GO to listOf(
                Pattern.compile("([^:]+):(\\d+):(\\d+):\\s*(\\w+):\\s*(.+)"),
                Pattern.compile("([^:]+):(\\d+):\\s*(\\w+):\\s*(.+)"),
                Pattern.compile("([^:]+):(\\d+):\\s*(.+)")
            )
        )
        
        // 常见错误代码和修复建议
        private val ERROR_SUGGESTIONS = mapOf(
            "CS1001" to ErrorSuggestion(
                title = "缺少标识符",
                description = "代码中缺少必要的标识符，可能是语法错误或拼写错误",
                fixCode = "请检查代码语法，确保所有必要的标识符都存在",
                confidence = 0.8
            ),
            "CS0104" to ErrorSuggestion(
                title = "类型冲突",
                description = "使用的标识符与现有类型冲突",
                fixCode = "请使用不同的标识符名称或导入正确的命名空间",
                confidence = 0.9
            ),
            "CS0103" to ErrorSuggestion(
                title = "名称不存在",
                description = "代码中引用的名称在当前上下文中不存在",
                fixCode = "请检查名称拼写、导入语句或声明是否正确",
                confidence = 0.8
            ),
            "CS0116" to ErrorSuggestion(
                title = "命名空间不能直接包含成员",
                description = "在命名空间级别定义了方法或属性",
                fixCode = "请将方法或属性移到类或结构体中",
                confidence = 0.9
            ),
            "CS1513" to ErrorSuggestion(
                title = "缺少右大括号",
                description = "代码块的左大括号没有对应的右大括号",
                fixCode = "请在适当位置添加右大括号 '}'",
                confidence = 0.95
            ),
            "CS1514" to ErrorSuggestion(
                title = "缺少左大括号",
                description = "代码块的右大括号没有对应的左大括号",
                fixCode = "请在适当位置添加左大括号 '{'",
                confidence = 0.95
            )
        )
        
        // 通用错误模式和建议
        private val COMMON_ERRORS = mapOf(
            "syntax error" to listOf(
                ErrorSuggestion(
                    title = "语法错误",
                    description = "代码存在语法错误，可能是缺少分号、括号不匹配等",
                    fixCode = "请检查代码语法，确保语法结构正确",
                    confidence = 0.9
                )
            ),
            "undefined" to listOf(
                ErrorSuggestion(
                    title = "未定义变量",
                    description = "引用了未定义的变量或函数",
                    fixCode = "请检查变量或函数是否已正确声明和导入",
                    confidence = 0.85
                )
            ),
            "not found" to listOf(
                ErrorSuggestion(
                    title = "找不到文件或资源",
                    description = "引用的文件或资源不存在",
                    fixCode = "请检查文件路径是否正确，文件是否存在",
                    confidence = 0.8
                )
            ),
            "permission denied" to listOf(
                ErrorSuggestion(
                    title = "权限拒绝",
                    description = "没有足够的权限访问文件或执行操作",
                    fixCode = "请检查文件权限或运行权限设置",
                    confidence = 0.9
                )
            )
        )
    }

    /**
     * 解析编译输出，提取错误和警告信息
     */
    suspend fun parseCompileOutput(
        output: String,
        errorOutput: String,
        language: Language
    ): ParseResult = withContext(Dispatchers.Default) {
        try {
            val allOutput = "$output\n$errorOutput"
            val lines = allOutput.lines()
            
            val errors = mutableListOf<CompileError>()
            val warnings = mutableListOf<CompileError>()
            
            lines.forEach { line ->
                parseLineForErrors(line, language)?.let { error ->
                    if (error.severity == ErrorSeverity.ERROR) {
                        errors.add(error)
                    } else if (error.severity == ErrorSeverity.WARNING) {
                        warnings.add(error)
                    }
                }
            }
            
            // 智能错误聚合和去重
            val aggregatedErrors = aggregateSimilarErrors(errors)
            val aggregatedWarnings = aggregateSimilarErrors(warnings)
            
            // 生成修复建议
            val enhancedErrors = aggregatedErrors.map { error ->
                enhanceWithSuggestions(error, language)
            }
            
            ParseResult(
                errors = enhancedErrors,
                warnings = aggregatedWarnings,
                success = aggregatedErrors.isEmpty(),
                totalLines = lines.size,
                processedLines = lines.size { it.isNotBlank() }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing compile output", e)
            ParseResult(
                errors = listOf(CompileError(
                    line = 0,
                    column = 0,
                    message = "Failed to parse output: ${e.message}",
                    severity = ErrorSeverity.ERROR
                )),
                warnings = emptyList(),
                success = false,
                totalLines = 0,
                processedLines = 0
            )
        }
    }

    /**
     * 解析单行错误信息
     */
    private fun parseLineForErrors(line: String, language: Language): CompileError? {
        val patterns = ERROR_PATTERNS[language] ?: return null
        
        patterns.forEach { pattern ->
            val matcher = pattern.matcher(line)
            if (matcher.find()) {
                return parseErrorFromMatcher(matcher, line, language)
            }
        }
        
        // 通用错误模式匹配
        return parseGenericError(line)
    }

    /**
     * 从正则匹配器中解析错误信息
     */
    private fun parseErrorFromMatcher(matcher: Matcher, line: String, language: Language): CompileError {
        return when (language) {
            Language.JAVA -> {
                if (matcher.groupCount() >= 4) {
                    CompileError(
                        line = matcher.group(2).toIntOrNull() ?: 0,
                        column = 0,
                        message = matcher.group(4),
                        severity = determineSeverity(matcher.group(3)),
                        file = matcher.group(1),
                        code = matcher.group(3)
                    )
                } else if (matcher.groupCount() >= 3) {
                    CompileError(
                        line = matcher.group(2).toIntOrNull() ?: 0,
                        column = 0,
                        message = matcher.group(3),
                        severity = ErrorSeverity.ERROR,
                        file = matcher.group(1)
                    )
                } else {
                    CompileError(
                        line = 0,
                        column = 0,
                        message = matcher.group(1),
                        severity = ErrorSeverity.ERROR
                    )
                }
            }
            
            Language.JAVASCRIPT -> {
                if (matcher.groupCount() >= 5) {
                    CompileError(
                        line = matcher.group(2).toIntOrNull() ?: 0,
                        column = matcher.group(3).toIntOrNull() ?: 0,
                        message = matcher.group(5),
                        severity = determineSeverity(matcher.group(4)),
                        file = matcher.group(1),
                        code = matcher.group(4)
                    )
                } else if (matcher.groupCount() >= 4) {
                    CompileError(
                        line = matcher.group(2).toIntOrNull() ?: 0,
                        column = 0,
                        message = matcher.group(4),
                        severity = determineSeverity(matcher.group(3)),
                        file = matcher.group(1)
                    )
                } else {
                    CompileError(
                        line = 0,
                        column = 0,
                        message = matcher.group(1),
                        severity = ErrorSeverity.ERROR
                    )
                }
            }
            
            Language.PYTHON -> {
                if (matcher.groupCount() >= 4) {
                    val lineNum = matcher.group(2).toIntOrNull() ?: 0
                    val functionName = matcher.group(3)
                    val message = matcher.group(4)
                    val formattedMessage = if (functionName != null) {
                        "$message (in function '$functionName')"
                    } else {
                        message
                    }
                    
                    CompileError(
                        line = lineNum,
                        column = 0,
                        message = formattedMessage,
                        severity = determineSeverity(""),
                        file = matcher.group(1)
                    )
                } else if (matcher.groupCount() >= 3) {
                    CompileError(
                        line = matcher.group(2).toIntOrNull() ?: 0,
                        column = 0,
                        message = matcher.group(4),
                        severity = determineSeverity(matcher.group(3)),
                        file = matcher.group(1)
                    )
                } else {
                    CompileError(
                        line = 0,
                        column = 0,
                        message = matcher.group(1),
                        severity = ErrorSeverity.ERROR
                    )
                }
            }
            
            Language.CPP, Language.C -> {
                if (matcher.groupCount() >= 5) {
                    CompileError(
                        line = matcher.group(2).toIntOrNull() ?: 0,
                        column = matcher.group(3).toIntOrNull() ?: 0,
                        message = matcher.group(5),
                        severity = determineSeverity(matcher.group(4)),
                        file = matcher.group(1),
                        code = matcher.group(4)
                    )
                } else if (matcher.groupCount() >= 4) {
                    CompileError(
                        line = matcher.group(2).toIntOrNull() ?: 0,
                        column = 0,
                        message = matcher.group(4),
                        severity = determineSeverity(matcher.group(3)),
                        file = matcher.group(1)
                    )
                } else {
                    CompileError(
                        line = matcher.group(1).toIntOrNull() ?: 0,
                        column = 0,
                        message = matcher.group(matcher.groupCount()),
                        severity = ErrorSeverity.ERROR,
                        file = ""
                    )
                }
            }
            
            Language.RUST -> {
                if (matcher.groupCount() >= 5) {
                    CompileError(
                        line = matcher.group(2).toIntOrNull() ?: 0,
                        column = matcher.group(3).toIntOrNull() ?: 0,
                        message = matcher.group(5),
                        severity = determineSeverity(matcher.group(4)),
                        file = matcher.group(1),
                        code = matcher.group(4)
                    )
                } else {
                    CompileError(
                        line = 0,
                        column = 0,
                        message = matcher.group(matcher.groupCount()),
                        severity = ErrorSeverity.ERROR
                    )
                }
            }
            
            Language.GO -> {
                if (matcher.groupCount() >= 5) {
                    CompileError(
                        line = matcher.group(2).toIntOrNull() ?: 0,
                        column = matcher.group(3).toIntOrNull() ?: 0,
                        message = matcher.group(5),
                        severity = determineSeverity(matcher.group(4)),
                        file = matcher.group(1),
                        code = matcher.group(4)
                    )
                } else if (matcher.groupCount() >= 4) {
                    CompileError(
                        line = matcher.group(2).toIntOrNull() ?: 0,
                        column = 0,
                        message = matcher.group(4),
                        severity = determineSeverity(matcher.group(3)),
                        file = matcher.group(1)
                    )
                } else {
                    CompileError(
                        line = matcher.group(1).toIntOrNull() ?: 0,
                        column = 0,
                        message = matcher.group(matcher.groupCount()),
                        severity = ErrorSeverity.ERROR,
                        file = ""
                    )
                }
            }
        }
    }

    /**
     * 解析通用错误信息
     */
    private fun parseGenericError(line: String): CompileError? {
        val lowerLine = line.lowercase()
        
        COMMON_ERRORS.forEach { (pattern, suggestions) ->
            if (lowerLine.contains(pattern)) {
                return CompileError(
                    line = 0,
                    column = 0,
                    message = line,
                    severity = if (pattern.contains("warning")) ErrorSeverity.WARNING else ErrorSeverity.ERROR,
                    suggestions = suggestions
                )
            }
        }
        
        return null
    }

    /**
     * 确定错误严重程度
     */
    private fun determineSeverity(type: String): ErrorSeverity {
        return when (type.lowercase()) {
            "error", "fatal" -> ErrorSeverity.ERROR
            "warning", "warn" -> ErrorSeverity.WARNING
            "info", "note" -> ErrorSeverity.INFO
            else -> ErrorSeverity.ERROR
        }
    }

    /**
     * 聚合相似的错误信息
     */
    private fun aggregateSimilarErrors(errors: List<CompileError>): List<CompileError> {
        val errorMap = mutableMapOf<String, MutableList<CompileError>>()
        
        errors.forEach { error ->
            val key = "${error.file}:${error.line}:${error.message.substring(0, minOf(50, error.message.length))}"
            errorMap.getOrPut(key) { mutableListOf() }.add(error)
        }
        
        return errorMap.values.map { similarErrors ->
            if (similarErrors.size > 1) {
                // 合并相似错误，保留第一个错误的信息但增加计数
                val first = similarErrors.first()
                first.copy(
                    message = "${first.message} (${similarErrors.size} similar errors)"
                )
            } else {
                similarErrors.first()
            }
        }
    }

    /**
     * 增强错误信息，添加修复建议
     */
    private fun enhanceWithSuggestions(error: CompileError, language: Language): CompileError {
        // 检查是否有预定义的错误建议
        error.code?.let { code ->
            ERROR_SUGGESTIONS[code]?.let { suggestion ->
                return error.copy(suggestions = listOf(suggestion))
            }
        }
        
        // 根据错误消息生成建议
        val message = error.message.lowercase()
        val suggestions = mutableListOf<ErrorSuggestion>()
        
        // 通用建议规则
        when {
            message.contains("syntax") -> {
                suggestions.add(ErrorSuggestion(
                    title = "语法错误",
                    description = "代码存在语法错误，请检查语法结构",
                    confidence = 0.8
                ))
            }
            message.contains("undefined") -> {
                suggestions.add(ErrorSuggestion(
                    title = "未定义标识符",
                    description = "引用的变量、函数或类未定义",
                    confidence = 0.85
                ))
            }
            message.contains("missing") || message.contains("缺少") -> {
                suggestions.add(ErrorSuggestion(
                    title = "缺少必要的语法元素",
                    description = "代码中缺少必要的语法元素，如分号、括号等",
                    confidence = 0.9
                ))
            }
            message.contains("type") -> {
                suggestions.add(ErrorSuggestion(
                    title = "类型错误",
                    description = "类型不匹配或类型不存在",
                    confidence = 0.8
                ))
            }
        }
        
        return if (suggestions.isNotEmpty()) {
            error.copy(suggestions = suggestions + error.suggestions)
        } else {
            error
        }
    }

    /**
     * 生成错误摘要报告
     */
    suspend fun generateErrorReport(
        parseResult: ParseResult,
        language: Language
    ): ErrorReport = withContext(Dispatchers.Default) {
        val summary = ErrorSummary(
            totalErrors = parseResult.errors.size,
            totalWarnings = parseResult.warnings.size,
            severityCounts = mapOf(
                ErrorSeverity.ERROR to parseResult.errors.count { it.severity == ErrorSeverity.ERROR },
                ErrorSeverity.WARNING to parseResult.errors.count { it.severity == ErrorSeverity.WARNING },
                ErrorSeverity.INFO to parseResult.errors.count { it.severity == ErrorSeverity.INFO }
            ),
            mostCommonErrors = getMostCommonErrors(parseResult.errors),
            filesWithErrors = parseResult.errors.map { it.file }.filter { it.isNotEmpty() }.distinct()
        )
        
        val recommendations = generateRecommendations(parseResult, language)
        
        ErrorReport(
            summary = summary,
            errors = parseResult.errors,
            warnings = parseResult.warnings,
            recommendations = recommendations,
            success = parseResult.success
        )
    }

    /**
     * 获取最常见的错误
     */
    private fun getMostCommonErrors(errors: List<CompileError>): List<String> {
        val errorCounts = errors.groupingBy { it.message }.eachCount()
        return errorCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { "${it.value}x: ${it.key}" }
    }

    /**
     * 生成修复建议
     */
    private fun generateRecommendations(parseResult: ParseResult, language: Language): List<String> {
        val recommendations = mutableListOf<String>()
        
        when {
            parseResult.errors.isEmpty() -> {
                recommendations.add("编译成功！没有发现错误。")
            }
            parseResult.errors.size == 1 -> {
                recommendations.add("发现1个错误，建议优先修复此错误。")
            }
            else -> {
                recommendations.add("发现${parseResult.errors.size}个错误，建议按严重程度顺序修复。")
            }
        }
        
        if (parseResult.warnings.isNotEmpty()) {
            recommendations.add("发现${parseResult.warnings.size}个警告，建议检查并修复。")
        }
        
        // 根据语言特定建议
        when (language) {
            Language.JAVA -> {
                recommendations.add("Java编译：确保所有类文件在正确的包结构中，并检查import语句。")
            }
            Language.JAVASCRIPT -> {
                recommendations.add("JavaScript编译：检查变量声明和函数作用域，确保使用严格模式。")
            }
            Language.PYTHON -> {
                recommendations.add("Python编译：检查缩进和语法结构，确保使用正确的Python版本。")
            }
            Language.CPP, Language.C -> {
                recommendations.add("C/C++编译：检查头文件包含、类型定义和函数声明。")
            }
            Language.RUST -> {
                recommendations.add("Rust编译：检查生命周期、借用检查器和trait实现。")
            }
            Language.GO -> {
                recommendations.add("Go编译：检查包声明、导入语句和函数签名。")
            }
        }
        
        return recommendations
    }
}

/**
 * 解析结果
 */
data class ParseResult(
    val errors: List<CompileError>,
    val warnings: List<CompileError>,
    val success: Boolean,
    val totalLines: Int,
    val processedLines: Int
)

/**
 * 错误摘要
 */
data class ErrorSummary(
    val totalErrors: Int,
    val totalWarnings: Int,
    val severityCounts: Map<ErrorSeverity, Int>,
    val mostCommonErrors: List<String>,
    val filesWithErrors: List<String>
)

/**
 * 错误报告
 */
data class ErrorReport(
    val summary: ErrorSummary,
    val errors: List<CompileError>,
    val warnings: List<CompileError>,
    val recommendations: List<String>,
    val success: Boolean
)