package com.codemate.features.editor.utils

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.codemate.features.editor.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 语法高亮引擎
 * 负责分析代码并生成高亮信息
 */
class SyntaxHighlighterEngine {
    
    private val keywordPatterns = mutableMapOf<EditorLanguage, KeywordPattern>()
    private val stringPatterns = mutableMapOf<EditorLanguage, StringPattern>()
    private val commentPatterns = mutableMapOf<EditorLanguage, CommentPattern>()
    private val numberPatterns = mutableMapOf<EditorLanguage, NumberPattern>()
    
    init {
        initializeLanguagePatterns()
    }
    
    /**
     * 初始化语言模式
     */
    private fun initializeLanguagePatterns() {
        // Kotlin 模式
        keywordPatterns[EditorLanguage.KOTLIN] = KeywordPattern(
            keywords = EditorLanguage.KOTLIN.completionKeywords,
            caseSensitive = true
        )
        
        stringPatterns[EditorLanguage.KOTLIN] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'", "\"\"\".*?\"\"\""
            )
        )
        
        commentPatterns[EditorLanguage.KOTLIN] = CommentPattern(
            singleLine = "//.*",
            multiLine = "/\\*.*?\\*/"
        )
        
        // Java 模式
        keywordPatterns[EditorLanguage.JAVA] = KeywordPattern(
            keywords = EditorLanguage.JAVA.completionKeywords,
            caseSensitive = true
        )
        
        stringPatterns[EditorLanguage.JAVA] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'"
            )
        )
        
        commentPatterns[EditorLanguage.JAVA] = CommentPattern(
            singleLine = "//.*",
            multiLine = "/\\*.*?\\*/"
        )
        
        // JavaScript 模式
        keywordPatterns[EditorLanguage.JAVASCRIPT] = KeywordPattern(
            keywords = EditorLanguage.JAVASCRIPT.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.JAVASCRIPT] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'", "`[^`]*`"
            )
        )
        
        commentPatterns[EditorLanguage.JAVASCRIPT] = CommentPattern(
            singleLine = "//.*",
            multiLine = "/\\*.*?\\*/"
        )
        
        // TypeScript 模式
        keywordPatterns[EditorLanguage.TYPESCRIPT] = KeywordPattern(
            keywords = EditorLanguage.TYPESCRIPT.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.TYPESCRIPT] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'", "`[^`]*`"
            )
        )
        
        commentPatterns[EditorLanguage.TYPESCRIPT] = CommentPattern(
            singleLine = "//.*",
            multiLine = "/\\*.*?\\*/"
        )
        
        // Python 模式
        keywordPatterns[EditorLanguage.PYTHON] = KeywordPattern(
            keywords = EditorLanguage.PYTHON.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.PYTHON] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'", "\"\"\".*?\"\"\"", "'''.*?'''"
            )
        )
        
        commentPatterns[EditorLanguage.PYTHON] = CommentPattern(
            singleLine = "#.*",
            multiLine = "\"\"\".*?\"\"\""
        )
        
        // C++ 模式
        keywordPatterns[EditorLanguage.CPP] = KeywordPattern(
            keywords = EditorLanguage.CPP.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.CPP] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'"
            )
        )
        
        commentPatterns[EditorLanguage.CPP] = CommentPattern(
            singleLine = "//.*",
            multiLine = "/\\*.*?\\*/"
        )
        
        // C# 模式
        keywordPatterns[EditorLanguage.CSHARP] = KeywordPattern(
            keywords = EditorLanguage.CSHARP.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.CSHARP] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'"
            )
        )
        
        commentPatterns[EditorLanguage.CSHARP] = CommentPattern(
            singleLine = "//.*",
            multiLine = "/\\*.*?\\*/"
        )
        
        // Go 模式
        keywordPatterns[EditorLanguage.GO] = KeywordPattern(
            keywords = EditorLanguage.GO.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.GO] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "`[^`]*`"
            )
        )
        
        commentPatterns[EditorLanguage.GO] = CommentPattern(
            singleLine = "//.*"
        )
        
        // Rust 模式
        keywordPatterns[EditorLanguage.RUST] = KeywordPattern(
            keywords = EditorLanguage.RUST.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.RUST] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'"
            )
        )
        
        commentPatterns[EditorLanguage.RUST] = CommentPattern(
            singleLine = "//.*",
            multiLine = "/\\*.*?\\*/"
        )
        
        // Swift 模式
        keywordPatterns[EditorLanguage.SWIFT] = KeywordPattern(
            keywords = EditorLanguage.SWIFT.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.SWIFT] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'", "\"\"\".*?\"\"\""
            )
        )
        
        commentPatterns[EditorLanguage.SWIFT] = CommentPattern(
            singleLine = "//.*",
            multiLine = "/\\*.*?\\*/"
        )
        
        // XML 模式
        keywordPatterns[EditorLanguage.XML] = KeywordPattern(
            keywords = EditorLanguage.XML.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.XML] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'"
            )
        )
        
        commentPatterns[EditorLanguage.XML] = CommentPattern(
            singleLine = "<!--.*?-->",
            multiLine = "<!--.*?-->"
        )
        
        // JSON 模式
        keywordPatterns[EditorLanguage.JSON] = KeywordPattern(
            keywords = EditorLanguage.JSON.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.JSON] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\""
            )
        )
        
        // YAML 模式
        keywordPatterns[EditorLanguage.YAML] = KeywordPattern(
            keywords = EditorLanguage.YAML.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.YAML] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'"
            )
        )
        
        commentPatterns[EditorLanguage.YAML] = CommentPattern(
            singleLine = "#.*"
        )
        
        // Markdown 模式
        keywordPatterns[EditorLanguage.MARKDOWN] = KeywordPattern(
            keywords = EditorLanguage.MARKDOWN.completionKeywords,
            caseSensitive = false
        )
        
        stringPatterns[EditorLanguage.MARKDOWN] = StringPattern(
            patterns = listOf(
                "\"[^\"]*\"", "'[^']*'", "`[^`]*`"
            )
        )
        
        commentPatterns[EditorLanguage.MARKDOWN] = CommentPattern(
            singleLine = "<!--.*?-->"
        )
    }
    
    /**
     * 执行语法高亮
     */
    suspend fun highlightSyntax(
        code: String,
        language: EditorLanguage,
        theme: EditorTheme
    ): List<SyntaxHighlight> = withContext(Dispatchers.IO) {
        val highlights = mutableListOf<SyntaxHighlight>()
        
        try {
            // 关键词高亮
            highlights.addAll(highlightKeywords(code, language, theme))
            
            // 字符串高亮
            highlights.addAll(highlightStrings(code, language, theme))
            
            // 注释高亮
            highlights.addAll(highlightComments(code, language, theme))
            
            // 数字高亮
            highlights.addAll(highlightNumbers(code, language, theme))
            
            // 函数调用高亮
            highlights.addAll(highlightFunctions(code, language, theme))
            
            // 类名高亮
            highlights.addAll(highlightClasses(code, language, theme))
            
            // XML/HTML 标签高亮
            if (language == EditorLanguage.XML) {
                highlights.addAll(highlightXmlTags(code, theme))
            }
            
            // JSON 键值对高亮
            if (language == EditorLanguage.JSON) {
                highlights.addAll(highlightJson(code, theme))
            }
            
        } catch (e: Exception) {
            // 静默处理错误，避免阻塞UI
            e.printStackTrace()
        }
        
        // 按起始位置排序
        highlights.sortedBy { it.start }
    }
    
    /**
     * 高亮关键词
     */
    private fun highlightKeywords(
        code: String,
        language: EditorLanguage,
        theme: EditorTheme
    ): List<SyntaxHighlight> {
        val pattern = keywordPatterns[language] ?: return emptyList()
        val highlights = mutableListOf<SyntaxHighlight>()
        
        pattern.keywords.forEach { keyword ->
            var index = 0
            while (index < code.length) {
                val foundIndex = if (pattern.caseSensitive) {
                    code.indexOf(keyword, index)
                } else {
                    code.indexOf(keyword, index, ignoreCase = true)
                }
                
                if (foundIndex == -1) break
                
                // 检查是否是完整的词
                val before = if (foundIndex > 0) code[foundIndex - 1] else ' '
                val after = if (foundIndex + keyword.length < code.length) 
                           code[foundIndex + keyword.length] else ' '
                
                if (!before.isLetterOrDigit() && before != '_' && 
                    !after.isLetterOrDigit() && after != '_') {
                    
                    highlights.add(SyntaxHighlight(
                        start = foundIndex,
                        end = foundIndex + keyword.length,
                        style = getKeywordStyle(theme),
                        type = SyntaxType.KEYWORD
                    ))
                }
                
                index = foundIndex + keyword.length
            }
        }
        
        return highlights
    }
    
    /**
     * 高亮字符串
     */
    private fun highlightStrings(
        code: String,
        language: EditorLanguage,
        theme: EditorTheme
    ): List<SyntaxHighlight> {
        val pattern = stringPatterns[language] ?: return emptyList()
        val highlights = mutableListOf<SyntaxHighlight>()
        
        pattern.patterns.forEach { stringPattern ->
            val regex = stringPattern.toRegex(RegexOption.DOT_MATCHES_ALL)
            regex.findAll(code).forEach { match ->
                highlights.add(SyntaxHighlight(
                    start = match.range.first,
                    end = match.range.last + 1,
                    style = getStringStyle(theme),
                    type = SyntaxType.STRING
                ))
            }
        }
        
        return highlights
    }
    
    /**
     * 高亮注释
     */
    private fun highlightComments(
        code: String,
        language: EditorLanguage,
        theme: EditorTheme
    ): List<SyntaxHighlight> {
        val pattern = commentPatterns[language] ?: return emptyList()
        val highlights = mutableListOf<SyntaxHighlight>()
        
        // 单行注释
        pattern.singleLine?.let { singleLinePattern ->
            val regex = singleLinePattern.toRegex(RegexOption.MULTILINE)
            regex.findAll(code).forEach { match ->
                highlights.add(SyntaxHighlight(
                    start = match.range.first,
                    end = match.range.last + 1,
                    style = getCommentStyle(theme),
                    type = SyntaxType.COMMENT
                ))
            }
        }
        
        // 多行注释
        pattern.multiLine?.let { multiLinePattern ->
            val regex = multiLinePattern.toRegex(RegexOption.DOT_MATCHES_ALL)
            regex.findAll(code).forEach { match ->
                highlights.add(SyntaxHighlight(
                    start = match.range.first,
                    end = match.range.last + 1,
                    style = getCommentStyle(theme),
                    type = SyntaxType.COMMENT
                ))
            }
        }
        
        return highlights
    }
    
    /**
     * 高亮数字
     */
    private fun highlightNumbers(
        code: String,
        language: EditorLanguage,
        theme: EditorTheme
    ): List<SyntaxHighlight> {
        val highlights = mutableListOf<SyntaxHighlight>()
        
        // 各种数字格式的正则表达式
        val numberPatterns = listOf(
            "\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b", // 十进制
            "\\b0[xX][0-9a-fA-F]+[lL]?\\b", // 十六进制
            "\\b0[bB][01]+[lL]?\\b", // 二进制
            "\\b0[oO][0-7]+[lL]?\\b" // 八进制
        )
        
        numberPatterns.forEach { pattern ->
            val regex = pattern.toRegex()
            regex.findAll(code).forEach { match ->
                highlights.add(SyntaxHighlight(
                    start = match.range.first,
                    end = match.range.last + 1,
                    style = getNumberStyle(theme),
                    type = SyntaxType.NUMBER
                ))
            }
        }
        
        return highlights
    }
    
    /**
     * 高亮函数调用
     */
    private fun highlightFunctions(
        code: String,
        language: EditorLanguage,
        theme: EditorTheme
    ): List<SyntaxHighlight> {
        val highlights = mutableListOf<SyntaxHighlight>()
        
        // 函数调用模式：标识符后跟(
        val functionPattern = "\\b([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(".toRegex()
        
        functionPattern.findAll(code).forEach { match ->
            val functionName = match.groupValues[1]
            
            // 排除关键字
            val keywordSet = keywordPatterns[language]?.keywords?.toSet() ?: emptySet()
            if (functionName !in keywordSet) {
                highlights.add(SyntaxHighlight(
                    start = match.range.first,
                    end = match.range.first + functionName.length,
                    style = getFunctionStyle(theme),
                    type = SyntaxType.FUNCTION
                ))
            }
        }
        
        return highlights
    }
    
    /**
     * 高亮类名
     */
    private fun highlightClasses(
        code: String,
        language: EditorLanguage,
        theme: EditorTheme
    ): List<SyntaxHighlight> {
        val highlights = mutableListOf<SyntaxHighlight>()
        
        // 类定义模式
        val classPatterns = when (language) {
            EditorLanguage.JAVA, EditorLanguage.KOTLIN, EditorLanguage.CPP, 
            EditorLanguage.CSHARP, EditorLanguage.SWIFT -> {
                listOf(
                    "\\b(?:class|interface|struct)\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex(),
                    "\\benum\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
                )
            }
            EditorLanguage.JAVASCRIPT, EditorLanguage.TYPESCRIPT -> {
                listOf(
                    "\\bclass\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
                )
            }
            EditorLanguage.PYTHON -> {
                listOf(
                    "\\bclass\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
                )
            }
            EditorLanguage.GO -> {
                listOf(
                    "\\btype\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s+(?:struct|interface)".toRegex()
                )
            }
            EditorLanguage.RUST -> {
                listOf(
                    "\\b(?:struct|enum|class|trait)\\s+([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()
                )
            }
            else -> emptyList()
        }
        
        classPatterns.forEach { pattern ->
            pattern.findAll(code).forEach { match ->
                val className = match.groupValues[1]
                highlights.add(SyntaxHighlight(
                    start = match.range.first,
                    end = match.range.first + className.length,
                    style = getClassStyle(theme),
                    type = SyntaxType.CLASS
                ))
            }
        }
        
        return highlights
    }
    
    /**
     * 高亮XML标签
     */
    private fun highlightXmlTags(
        code: String,
        theme: EditorTheme
    ): List<SyntaxHighlight> {
        val highlights = mutableListOf<SyntaxHighlight>()
        
        // 标签名
        val tagPattern = "<([a-zA-Z][a-zA-Z0-9:_.-]*)".toRegex()
        tagPattern.findAll(code).forEach { match ->
            val tagName = match.groupValues[1]
            highlights.add(SyntaxHighlight(
                start = match.range.first,
                end = match.range.first + tagName.length + 1, // 包含<
                style = getTagStyle(theme),
                type = SyntaxType.TAG
            ))
        }
        
        // 属性名
        val attributePattern = "\\s([a-zA-Z][a-zA-Z0-9:_.-]*)\\s*=".toRegex()
        attributePattern.findAll(code).forEach { match ->
            val attributeName = match.groupValues[1]
            highlights.add(SyntaxHighlight(
                start = match.range.first,
                end = match.range.first + attributeName.length + 1,
                style = getAttributeStyle(theme),
                type = SyntaxType.ATTRIBUTE
            ))
        }
        
        return highlights
    }
    
    /**
     * 高亮JSON
     */
    private fun highlightJson(
        code: String,
        theme: EditorTheme
    ): List<SyntaxHighlight> {
        val highlights = mutableListOf<SyntaxHighlight>()
        
        // JSON键（字符串后的冒号前的部分）
        val jsonKeyPattern = "\"([^\"]+)\"\\s*:".toRegex()
        jsonKeyPattern.findAll(code).forEach { match ->
            val keyName = match.groupValues[1]
            highlights.add(SyntaxHighlight(
                start = match.range.first,
                end = match.range.first + keyName.length + 1, // 包含"
                style = getJsonKeyStyle(theme),
                type = SyntaxType.KEYWORD
            ))
        }
        
        // JSON值
        val jsonValuePattern = ":\\s*\"([^\"]+)\"".toRegex()
        jsonValuePattern.findAll(code).forEach { match ->
            val value = match.groupValues[1]
            highlights.add(SyntaxHighlight(
                start = match.range.first + 2, // 跳过:和"
                end = match.range.first + 2 + value.length,
                style = getJsonValueStyle(theme),
                type = SyntaxType.VALUE
            ))
        }
        
        return highlights
    }
    
    // 样式获取方法
    private fun getKeywordStyle(theme: EditorTheme): SpanStyle {
        return when (theme) {
            EditorTheme.LIGHT -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0000FF))
            EditorTheme.DARK -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFF6B9D))
            EditorTheme.SOLARIZED_LIGHT -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF859900))
            EditorTheme.SOLARIZED_DARK -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF859900))
            EditorTheme.MONOKAI -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFF92672))
            EditorTheme.GITHUB -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF6F42C1))
        }
    }
    
    private fun getStringStyle(theme: EditorTheme): SpanStyle {
        return when (theme) {
            EditorTheme.LIGHT -> SpanStyle(color = Color(0xFF008000))
            EditorTheme.DARK -> SpanStyle(color = Color(0xFFE6DB74))
            EditorTheme.SOLARIZED_LIGHT -> SpanStyle(color = Color(0xFF2AA198))
            EditorTheme.SOLARIZED_DARK -> SpanStyle(color = Color(0xFFE6DB74))
            EditorTheme.MONOKAI -> SpanStyle(color = Color(0xFFE6DB74))
            EditorTheme.GITHUB -> SpanStyle(color = Color(0xFF032F62))
        }
    }
    
    private fun getCommentStyle(theme: EditorTheme): SpanStyle {
        return when (theme) {
            EditorTheme.LIGHT -> SpanStyle(color = Color(0xFF808080), fontStyle = FontStyle.Italic)
            EditorTheme.DARK -> SpanStyle(color = Color(0xFF75715E), fontStyle = FontStyle.Italic)
            EditorTheme.SOLARIZED_LIGHT -> SpanStyle(color = Color(0xFF93A1A1), fontStyle = FontStyle.Italic)
            EditorTheme.SOLARIZED_DARK -> SpanStyle(color = Color(0xFF586E75), fontStyle = FontStyle.Italic)
            EditorTheme.MONOKAI -> SpanStyle(color = Color(0xFF75715E), fontStyle = FontStyle.Italic)
            EditorTheme.GITHUB -> SpanStyle(color = Color(0xFF6A737D), fontStyle = FontStyle.Italic)
        }
    }
    
    private fun getNumberStyle(theme: EditorTheme): SpanStyle {
        return when (theme) {
            EditorTheme.LIGHT -> SpanStyle(color = Color(0xFF800080))
            EditorTheme.DARK -> SpanStyle(color = Color(0xFFAE81FF))
            EditorTheme.SOLARIZED_LIGHT -> SpanStyle(color = Color(0xFFD33682))
            EditorTheme.SOLARIZED_DARK -> SpanStyle(color = Color(0xFFAE81FF))
            EditorTheme.MONOKAI -> SpanStyle(color = Color(0xFFAE81FF))
            EditorTheme.GITHUB -> SpanStyle(color = Color(0xFF005CC5))
        }
    }
    
    private fun getFunctionStyle(theme: EditorTheme): SpanStyle {
        return when (theme) {
            EditorTheme.LIGHT -> SpanStyle(color = Color(0xFF4169E1))
            EditorTheme.DARK -> SpanStyle(color = Color(0xFFA6E22E))
            EditorTheme.SOLARIZED_LIGHT -> SpanStyle(color = Color(0xFF268BD2))
            EditorTheme.SOLARIZED_DARK -> SpanStyle(color = Color(0xFF268BD2))
            EditorTheme.MONOKAI -> SpanStyle(color = Color(0xFFA6E22E))
            EditorTheme.GITHUB -> SpanStyle(color = Color(0xFF6F42C1))
        }
    }
    
    private fun getClassStyle(theme: EditorTheme): SpanStyle {
        return when (theme) {
            EditorTheme.LIGHT -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF8B4513))
            EditorTheme.DARK -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFE6DB74))
            EditorTheme.SOLARIZED_LIGHT -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFB58900))
            EditorTheme.SOLARIZED_DARK -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFB58900))
            EditorTheme.MONOKAI -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFF92672))
            EditorTheme.GITHUB -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF6F42C1))
        }
    }
    
    private fun getTagStyle(theme: EditorTheme): SpanStyle {
        return when (theme) {
            EditorTheme.LIGHT -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0000FF))
            EditorTheme.DARK -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF66D9EF))
            EditorTheme.SOLARIZED_LIGHT -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF268BD2))
            EditorTheme.SOLARIZED_DARK -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF66D9EF))
            EditorTheme.MONOKAI -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFA6E22E))
            EditorTheme.GITHUB -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF032F92))
        }
    }
    
    private fun getAttributeStyle(theme: EditorTheme): SpanStyle {
        return when (theme) {
            EditorTheme.LIGHT -> SpanStyle(color = Color(0xFF8B4513))
            EditorTheme.DARK -> SpanStyle(color = Color(0xFFFFA500))
            EditorTheme.SOLARIZED_LIGHT -> SpanStyle(color = Color(0xFFB58900))
            EditorTheme.SOLARIZED_DARK -> SpanStyle(color = Color(0xFFB58900))
            EditorTheme.MONOKAI -> SpanStyle(color = Color(0xFFFFA500))
            EditorTheme.GITHUB -> SpanStyle(color = Color(0xFF6F42C1))
        }
    }
    
    private fun getJsonKeyStyle(theme: EditorTheme): SpanStyle {
        return getKeywordStyle(theme)
    }
    
    private fun getJsonValueStyle(theme: EditorTheme): SpanStyle {
        return when (theme) {
            EditorTheme.LIGHT -> SpanStyle(color = Color(0xFF008000))
            EditorTheme.DARK -> SpanStyle(color = Color(0xFFE6DB74))
            EditorTheme.SOLARIZED_LIGHT -> SpanStyle(color = Color(0xFF2AA198))
            EditorTheme.SOLARIZED_DARK -> SpanStyle(color = Color(0xFFE6DB74))
            EditorTheme.MONOKAI -> SpanStyle(color = Color(0xFFE6DB74))
            EditorTheme.GITHUB -> SpanStyle(color = Color(0xFF032F62))
        }
    }
}

/**
 * 关键词模式
 */
private data class KeywordPattern(
    val keywords: List<String>,
    val caseSensitive: Boolean = true
)

/**
 * 字符串模式
 */
private data class StringPattern(
    val patterns: List<String>
)

/**
 * 注释模式
 */
private data class CommentPattern(
    val singleLine: String? = null,
    val multiLine: String? = null
)

/**
 * 数字模式
 */
private data class NumberPattern(
    val patterns: List<String>
)