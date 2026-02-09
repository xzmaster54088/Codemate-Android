package com.codemate.features.editor.data

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color

/**
 * 语法高亮项
 */
data class SyntaxHighlight(
    val start: Int,
    val end: Int,
    val style: SpanStyle,
    val type: SyntaxType
)

/**
 * 语法类型
 */
enum class SyntaxType {
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER,
    OPERATOR,
    FUNCTION,
    CLASS,
    VARIABLE,
    TYPE,
    IMPORT,
    ANNOTATION,
    TAG,
    ATTRIBUTE,
    VALUE,
    ERROR,
    WARNING,
    INFO
}

/**
 * 搜索结果
 */
data class SearchResult(
    val start: Int,
    val end: Int,
    val lineNumber: Int,
    val column: Int,
    val text: String
)

/**
 * 代码补全项
 */
data class CompletionItem(
    val text: String,
    val displayText: String,
    val type: CompletionType,
    val description: String,
    val insertText: String = text,
    val sortText: String = text
)

/**
 * 补全类型
 */
enum class CompletionType {
    KEYWORD,
    FUNCTION,
    VARIABLE,
    CLASS,
    INTERFACE,
    ENUM,
    TYPE,
    MODULE,
    PROPERTY,
    PARAMETER,
    TEXT,
    SNIPPET
}

/**
 * 编辑器错误
 */
data class EditorError(
    val message: String,
    val line: Int,
    val column: Int,
    val severity: ErrorSeverity,
    val type: ErrorType
)

/**
 * 编辑器警告
 */
data class EditorWarning(
    val message: String,
    val line: Int,
    val column: Int,
    val severity: WarningSeverity,
    val type: WarningType
)

/**
 * 错误严重程度
 */
enum class ErrorSeverity {
    ERROR, CRITICAL
}

/**
 * 警告严重程度
 */
enum class WarningSeverity {
    WARNING, INFO, HINT
}

/**
 * 错误类型
 */
enum class ErrorType {
    SYNTAX_ERROR,
    TYPE_ERROR,
    UNDEFINED_VARIABLE,
    UNDEFINED_FUNCTION,
    MISSING_BRACKET,
    INVALID_SYNTAX
}

/**
 * 警告类型
 */
enum class WarningType {
    UNUSED_VARIABLE,
    UNUSED_FUNCTION,
    DEPRECATED_USAGE,
    UNREACHABLE_CODE,
    POTENTIAL_NULL_POINTER
}

/**
 * 符号栏符号
 */
data class Symbol(
    val text: String,
    val display: String,
    val description: String,
    val category: SymbolCategory
)

/**
 * 符号分类
 */
enum class SymbolCategory {
    OPERATORS,       // +, -, *, /, %, =, ==, !=, etc.
    BRACKETS,        // (), {}, [], <>
    ARROWS,          // ->, =>, ::, etc.
    QUOTES,          // ", ', `
    SPECIAL,         // ?, !, @, #, $, %, etc.
    MATH,            // ∑, ∫, √, π, etc.
    ARROWS_LARGE,    // ←, →, ↑, ↓, etc.
    PUNCTUATION,     // ., ,, ;, :, etc.
}

/**
 * 触摸手势类型
 */
enum class GestureType {
    TAP,
    DOUBLE_TAP,
    TRIPLE_TAP,
    LONG_PRESS,
    DRAG_START,
    DRAG_MOVE,
    DRAG_END,
    PINCH_START,
    PINCH_MOVE,
    PINCH_END
}

/**
 * 触摸事件
 */
data class TouchEvent(
    val type: GestureType,
    val position: Offset,
    val timestamp: Long,
    val pointers: List<Pointer> = emptyList()
)

/**
 * 触摸指针
 */
data class Pointer(
    val id: Int,
    val position: Offset,
    val pressure: Float = 1.0f,
    val size: Float = 1.0f
)

/**
 * 编辑操作类型
 */
enum class EditActionType {
    INSERT,
    DELETE,
    REPLACE,
    FORMAT,
    INDENT,
    COMMENT,
    UNCOMMENT
}

/**
 * 编辑操作
 */
data class EditAction(
    val type: EditActionType,
    val start: Int,
    val end: Int,
    val text: String,
    val timestamp: Long
)

/**
 * 编辑器模式
 */
enum class EditorMode {
    INSERT,      // 插入模式
    VISUAL,      // 可视模式
    VISUAL_LINE, // 行可视模式
    REPLACE      // 替换模式
}

/**
 * 代码片段
 */
data class CodeSnippet(
    val trigger: String,
    val name: String,
    val description: String,
    val code: String,
    val language: EditorLanguage,
    val category: SnippetCategory
)

/**
 * 代码片段分类
 */
enum class SnippetCategory {
    FUNCTION,
    CLASS,
    CONTROL_FLOW,
    LOOP,
    TRY_CATCH,
    IMPORT,
    TEMPLATE,
    CUSTOM
}

/**
 * 代码分析结果
 */
data class CodeAnalysis(
    val errors: List<EditorError> = emptyList(),
    val warnings: List<EditorWarning> = emptyList(),
    val suggestions: List<CodeSuggestion> = emptyList(),
    val symbols: List<SymbolInfo> = emptyList()
)

/**
 * 代码建议
 */
data class CodeSuggestion(
    val message: String,
    val range: IntRange,
    val severity: SuggestionSeverity,
    val action: SuggestionAction
)

/**
 * 建议严重程度
 */
enum class SuggestionSeverity {
    ERROR, WARNING, INFO, HINT
}

/**
 * 建议动作
 */
data class SuggestionAction(
    val title: String,
    val description: String,
    val edit: EditAction
)

/**
 * 符号信息
 */
data class SymbolInfo(
    val name: String,
    val type: SymbolType,
    val range: IntRange,
    val documentation: String? = null
)

/**
 * 符号类型
 */
enum class SymbolType {
    FUNCTION,
    CLASS,
    INTERFACE,
    VARIABLE,
    CONSTANT,
    PROPERTY,
    ENUM,
    NAMESPACE,
    MODULE
}