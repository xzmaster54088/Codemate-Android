package com.codemate.features.editor.utils

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import com.codemate.features.editor.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * 代码补全引擎
 * 负责提供智能代码补全功能
 */
class CodeCompletionEngine {
    
    private val keywordCompletions = mutableMapOf<EditorLanguage, List<CompletionItem>>()
    private val snippetCompletions = mutableMapOf<EditorLanguage, List<CodeSnippet>>()
    private val symbolTable = mutableMapOf<EditorLanguage, SymbolTable>()
    
    init {
        initializeCompletions()
    }
    
    /**
     * 初始化补全数据
     */
    private fun initializeCompletions() {
        initializeKeywordCompletions()
        initializeSnippets()
        initializeSymbolTables()
    }
    
    /**
     * 初始化关键词补全
     */
    private fun initializeKeywordCompletions() {
        EditorLanguage.values().forEach { language ->
            val keywords = language.completionKeywords.map { keyword ->
                CompletionItem(
                    text = keyword,
                    displayText = keyword,
                    type = CompletionType.KEYWORD,
                    description = "$keyword keyword"
                )
            }
            keywordCompletions[language] = keywords
        }
    }
    
    /**
     * 初始化代码片段
     */
    private fun initializeSnippets() {
        // Kotlin 片段
        val kotlinSnippets = listOf(
            CodeSnippet(
                trigger = "fun",
                name = "Function",
                description = "Create a function",
                code = "fun ${1:functionName}(${2:parameters}): ${3:ReturnType} {\n    ${4:// body}\n}",
                language = EditorLanguage.KOTLIN,
                category = SnippetCategory.FUNCTION
            ),
            CodeSnippet(
                trigger = "class",
                name = "Class",
                description = "Create a class",
                code = "class ${1:ClassName} {\n    ${2:// properties and methods}\n}",
                language = EditorLanguage.KOTLIN,
                category = SnippetCategory.CLASS
            ),
            CodeSnippet(
                trigger = "if",
                name = "If statement",
                description = "Create an if statement",
                code = "if (${1:condition}) {\n    ${2:// code}\n}",
                language = EditorLanguage.KOTLIN,
                category = SnippetCategory.CONTROL_FLOW
            ),
            CodeSnippet(
                trigger = "for",
                name = "For loop",
                description = "Create a for loop",
                code = "for (${1:i} in ${2:range}) {\n    ${3:// code}\n}",
                language = EditorLanguage.KOTLIN,
                category = SnippetCategory.LOOP
            ),
            CodeSnippet(
                trigger = "when",
                name = "When expression",
                description = "Create a when expression",
                code = "when (${1:expression}) {\n    ${2:case} -> ${3:result}\n    else -> ${4:default}\n}",
                language = EditorLanguage.KOTLIN,
                category = SnippetCategory.CONTROL_FLOW
            ),
            CodeSnippet(
                trigger = "try",
                name = "Try-catch block",
                description = "Create a try-catch block",
                code = "try {\n    ${1:// code}\n} catch (e: ${2:Exception}) {\n    ${3:// handle exception}\n}",
                language = EditorLanguage.KOTLIN,
                category = SnippetCategory.TRY_CATCH
            )
        )
        snippetCompletions[EditorLanguage.KOTLIN] = kotlinSnippets
        
        // Java 片段
        val javaSnippets = listOf(
            CodeSnippet(
                trigger = "main",
                name = "Main method",
                description = "Create main method",
                code = "public static void main(String[] args) {\n    ${1:// code}\n}",
                language = EditorLanguage.JAVA,
                category = SnippetCategory.FUNCTION
            ),
            CodeSnippet(
                trigger = "class",
                name = "Class",
                description = "Create a class",
                code = "public class ${1:ClassName} {\n    ${2:// fields and methods}\n}",
                language = EditorLanguage.JAVA,
                category = SnippetCategory.CLASS
            ),
            CodeSnippet(
                trigger = "if",
                name = "If statement",
                description = "Create an if statement",
                code = "if (${1:condition}) {\n    ${2:// code}\n}",
                language = EditorLanguage.JAVA,
                category = SnippetCategory.CONTROL_FLOW
            ),
            CodeSnippet(
                trigger = "for",
                name = "For loop",
                description = "Create a for loop",
                code = "for (int ${1:i} = 0; ${1:i} < ${2:length}; ${1:i}++) {\n    ${3:// code}\n}",
                language = EditorLanguage.JAVA,
                category = SnippetCategory.LOOP
            ),
            CodeSnippet(
                trigger = "try",
                name = "Try-catch block",
                description = "Create a try-catch block",
                code = "try {\n    ${1:// code}\n} catch (${2:Exception} e) {\n    ${3:// handle exception}\n}",
                language = EditorLanguage.JAVA,
                category = SnippetCategory.TRY_CATCH
            )
        )
        snippetCompletions[EditorLanguage.JAVA] = javaSnippets
        
        // JavaScript 片段
        val jsSnippets = listOf(
            CodeSnippet(
                trigger = "func",
                name = "Function",
                description = "Create a function",
                code = "function ${1:functionName}(${2:parameters}) {\n    ${3:// code}\n}",
                language = EditorLanguage.JAVASCRIPT,
                category = SnippetCategory.FUNCTION
            ),
            CodeSnippet(
                trigger = "=>",
                name = "Arrow function",
                description = "Create an arrow function",
                code = "(${1:parameters}) => {\n    ${2:// code}\n}",
                language = EditorLanguage.JAVASCRIPT,
                category = SnippetCategory.FUNCTION
            ),
            CodeSnippet(
                trigger = "if",
                name = "If statement",
                description = "Create an if statement",
                code = "if (${1:condition}) {\n    ${2:// code}\n}",
                language = EditorLanguage.JAVASCRIPT,
                category = SnippetCategory.CONTROL_FLOW
            ),
            CodeSnippet(
                trigger = "for",
                name = "For loop",
                description = "Create a for loop",
                code = "for (let ${1:i} = 0; ${1:i} < ${2:length}; ${1:i}++) {\n    ${3:// code}\n}",
                language = EditorLanguage.JAVASCRIPT,
                category = SnippetCategory.LOOP
            ),
            CodeSnippet(
                trigger = "try",
                name = "Try-catch block",
                description = "Create a try-catch block",
                code = "try {\n    ${1:// code}\n} catch (${2:error}) {\n    ${3:// handle error}\n}",
                language = EditorLanguage.JAVASCRIPT,
                category = SnippetCategory.TRY_CATCH
            )
        )
        snippetCompletions[EditorLanguage.JAVASCRIPT] = jsSnippets
        
        // Python 片段
        val pythonSnippets = listOf(
            CodeSnippet(
                trigger = "def",
                name = "Function",
                description = "Create a function",
                code = "def ${1:function_name}(${2:parameters}):\n    ${3:// code}",
                language = EditorLanguage.PYTHON,
                category = SnippetCategory.FUNCTION
            ),
            CodeSnippet(
                trigger = "class",
                name = "Class",
                description = "Create a class",
                code = "class ${1:ClassName}:\n    ${2:// class definition}",
                language = EditorLanguage.PYTHON,
                category = SnippetCategory.CLASS
            ),
            CodeSnippet(
                trigger = "if",
                name = "If statement",
                description = "Create an if statement",
                code = "if ${1:condition}:\n    ${2:// code}",
                language = EditorLanguage.PYTHON,
                category = SnippetCategory.CONTROL_FLOW
            ),
            CodeSnippet(
                trigger = "for",
                name = "For loop",
                description = "Create a for loop",
                code = "for ${1:item} in ${2:collection}:\n    ${3:// code}",
                language = EditorLanguage.PYTHON,
                category = SnippetCategory.LOOP
            ),
            CodeSnippet(
                trigger = "try",
                name = "Try-except block",
                description = "Create a try-except block",
                code = "try:\n    ${1:// code}\nexcept ${2:Exception} as ${3:e}:\n    ${4:// handle exception}",
                language = EditorLanguage.PYTHON,
                category = SnippetCategory.TRY_CATCH
            )
        )
        snippetCompletions[EditorLanguage.PYTHON] = pythonSnippets
    }
    
    /**
     * 初始化符号表
     */
    private fun initializeSymbolTables() {
        // 这里可以初始化语言特定的符号表
        // 包括内置函数、类、变量等
        EditorLanguage.values().forEach { language ->
            symbolTable[language] = createSymbolTable(language)
        }
    }
    
    /**
     * 创建符号表
     */
    private fun createSymbolTable(language: EditorLanguage): SymbolTable {
        val symbols = mutableListOf<SymbolInfo>()
        
        when (language) {
            EditorLanguage.KOTLIN -> {
                // Kotlin 标准库符号
                symbols.addAll(listOf(
                    SymbolInfo("println", SymbolType.FUNCTION, 0..0, "Prints a line to the standard output"),
                    SymbolInfo("print", SymbolType.FUNCTION, 0..0, "Prints to the standard output"),
                    SymbolInfo("readLine", SymbolType.FUNCTION, 0..0, "Reads a line from the standard input"),
                    SymbolInfo("String", SymbolType.CLASS, 0..0, "String class"),
                    SymbolInfo("Int", SymbolType.CLASS, 0..0, "Int class"),
                    SymbolInfo("Double", SymbolType.CLASS, 0..0, "Double class"),
                    SymbolInfo("Boolean", SymbolType.CLASS, 0..0, "Boolean class"),
                    SymbolInfo("List", SymbolType.CLASS, 0..0, "List class"),
                    SymbolInfo("Map", SymbolType.CLASS, 0..0, "Map class"),
                    SymbolInfo("mutableListOf", SymbolType.FUNCTION, 0..0, "Creates a mutable list"),
                    SymbolInfo("mutableMapOf", SymbolType.FUNCTION, 0..0, "Creates a mutable map"),
                    SymbolInfo("rangeTo", SymbolType.FUNCTION, 0..0, "Creates a range")
                ))
            }
            EditorLanguage.JAVA -> {
                // Java 标准库符号
                symbols.addAll(listOf(
                    SymbolInfo("System.out.println", SymbolType.FUNCTION, 0..0, "Prints a line to the standard output"),
                    SymbolInfo("System.out.print", SymbolType.FUNCTION, 0..0, "Prints to the standard output"),
                    SymbolInfo("Scanner", SymbolType.CLASS, 0..0, "Scanner class for input"),
                    SymbolInfo("String", SymbolType.CLASS, 0..0, "String class"),
                    SymbolInfo("Integer", SymbolType.CLASS, 0..0, "Integer class"),
                    SymbolInfo("Double", SymbolType.CLASS, 0..0, "Double class"),
                    SymbolInfo("Boolean", SymbolType.CLASS, 0..0, "Boolean class"),
                    SymbolInfo("List", SymbolType.INTERFACE, 0..0, "List interface"),
                    SymbolInfo("Map", SymbolType.INTERFACE, 0..0, "Map interface"),
                    SymbolInfo("ArrayList", SymbolType.CLASS, 0..0, "ArrayList class"),
                    SymbolInfo("HashMap", SymbolType.CLASS, 0..0, "HashMap class")
                ))
            }
            EditorLanguage.JAVASCRIPT -> {
                // JavaScript 内置符号
                symbols.addAll(listOf(
                    SymbolInfo("console.log", SymbolType.FUNCTION, 0..0, "Logs to the console"),
                    SymbolInfo("console.error", SymbolType.FUNCTION, 0..0, "Logs error to the console"),
                    SymbolInfo("Array", SymbolType.CLASS, 0..0, "Array class"),
                    SymbolInfo("Object", SymbolType.CLASS, 0..0, "Object class"),
                    SymbolInfo("String", SymbolType.CLASS, 0..0, "String class"),
                    SymbolInfo("Number", SymbolType.CLASS, 0..0, "Number class"),
                    SymbolInfo("Boolean", SymbolType.CLASS, 0..0, "Boolean class"),
                    SymbolInfo("Date", SymbolType.CLASS, 0..0, "Date class"),
                    SymbolInfo("Math", SymbolType.OBJECT, 0..0, "Math object with mathematical functions"),
                    SymbolInfo("JSON", SymbolType.OBJECT, 0..0, "JSON object"),
                    SymbolInfo("fetch", SymbolType.FUNCTION, 0..0, "Fetch API for HTTP requests")
                ))
            }
            // 其他语言类似...
            else -> { /* 暂时空实现 */ }
        }
        
        return SymbolTable(symbols)
    }
    
    /**
     * 获取代码补全
     */
    suspend fun getCompletions(
        code: String,
        position: Int,
        language: EditorLanguage,
        maxResults: Int = 20
    ): List<CompletionItem> = withContext(Dispatchers.IO) {
        
        val completions = mutableListOf<CompletionItem>()
        
        try {
            // 获取当前单词
            val currentWord = getCurrentWord(code, position)
            
            // 获取上下文
            val context = getCompletionContext(code, position)
            
            // 1. 关键词补全
            completions.addAll(getKeywordCompletions(language, currentWord, maxResults))
            
            // 2. 代码片段补全
            completions.addAll(getSnippetCompletions(language, currentWord, maxResults))
            
            // 3. 符号表补全
            completions.addAll(getSymbolCompletions(language, currentWord, maxResults))
            
            // 4. 上下文感知补全
            completions.addAll(getContextAwareCompletions(code, position, language, context))
            
            // 5. 智能预测（AI 补全）
            completions.addAll(getPredictiveCompletions(code, position, language, context))
            
            // 去重并排序
            val uniqueCompletions = completions.distinctBy { it.text }
            val sortedCompletions = uniqueCompletions.sortedWith(
                compareByDescending<CompletionItem> { it.text.startsWith(currentWord, ignoreCase = true) }
                    .thenBy { it.text.length }
            )
            
            sortedCompletions.take(maxResults)
            
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * 获取当前单词
     */
    private fun getCurrentWord(code: String, position: Int): String {
        if (code.isEmpty() || position <= 0) return ""
        
        val start = max(0, findWordStart(code, position))
        val end = min(code.length, findWordEnd(code, position))
        
        return code.substring(start, end)
    }
    
    /**
     * 获取补全上下文
     */
    private fun getCompletionContext(code: String, position: Int): CompletionContext {
        val before = code.substring(max(0, position - 100), position)
        val after = code.substring(position, min(code.length, position + 100))
        val lineStart = findLineStart(code, position)
        val lineEnd = findLineEnd(code, position)
        val line = code.substring(lineStart, lineEnd)
        val lineNumber = code.substring(0, lineStart).count { it == '\n' } + 1
        
        return CompletionContext(
            before = before,
            after = after,
            line = line,
            position = position,
            lineNumber = lineNumber,
            column = position - lineStart + 1
        )
    }
    
    /**
     * 获取关键词补全
     */
    private fun getKeywordCompletions(
        language: EditorLanguage,
        currentWord: String,
        maxResults: Int
    ): List<CompletionItem> {
        if (currentWord.isEmpty()) return emptyList()
        
        val keywords = keywordCompletions[language] ?: emptyList()
        return keywords.filter { 
            it.text.startsWith(currentWord, ignoreCase = true) 
        }.take(maxResults)
    }
    
    /**
     * 获取代码片段补全
     */
    private fun getSnippetCompletions(
        language: EditorLanguage,
        currentWord: String,
        maxResults: Int
    ): List<CompletionItem> {
        if (currentWord.isEmpty()) return emptyList()
        
        val snippets = snippetCompletions[language] ?: emptyList()
        return snippets.filter { snippet ->
            snippet.trigger.startsWith(currentWord, ignoreCase = true)
        }.map { snippet ->
            CompletionItem(
                text = snippet.trigger,
                displayText = "${snippet.name} - ${snippet.description}",
                type = CompletionType.SNIPPET,
                description = snippet.description,
                insertText = snippet.code
            )
        }.take(maxResults)
    }
    
    /**
     * 获取符号补全
     */
    private fun getSymbolCompletions(
        language: EditorLanguage,
        currentWord: String,
        maxResults: Int
    ): List<CompletionItem> {
        if (currentWord.isEmpty()) return emptyList()
        
        val symbols = symbolTable[language]?.symbols ?: emptyList()
        return symbols.filter { symbol ->
            symbol.name.startsWith(currentWord, ignoreCase = true)
        }.map { symbol ->
            CompletionItem(
                text = symbol.name,
                displayText = symbol.name,
                type = CompletionType.fromSymbolType(symbol.type),
                description = symbol.documentation ?: "",
                sortText = symbol.name
            )
        }.take(maxResults)
    }
    
    /**
     * 获取上下文感知补全
     */
    private fun getContextAwareCompletions(
        code: String,
        position: Int,
        language: EditorLanguage,
        context: CompletionContext
    ): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        // 根据上下文判断可能的补全
        when {
            // 在点运算符后
            context.before.endsWith(".") -> {
                completions.addAll(getMemberCompletions(code, position, language))
            }
            // 在圆括号后
            context.before.endsWith("(") -> {
                completions.addAll(getParameterCompletions(context))
            }
            // 在方括号后
            context.before.endsWith("[") -> {
                completions.addAll(getIndexCompletions())
            }
            // 在花括号后
            context.before.endsWith("{") -> {
                completions.addAll(getBlockCompletions(language))
            }
            // 在引号内
            context.before.lastOrNull() in setOf('"', '\'', '`') -> {
                completions.addAll(getStringCompletions(language))
            }
            // 导入语句
            context.line.trim().startsWith("import", ignoreCase = true) -> {
                completions.addAll(getImportCompletions(language))
            }
        }
        
        return completions
    }
    
    /**
     * 获取成员补全
     */
    private fun getMemberCompletions(
        code: String,
        position: Int,
        language: EditorLanguage
    ): List<CompletionItem> {
        // 这里可以实现更复杂的成员查找
        // 目前返回示例数据
        return when (language) {
            EditorLanguage.JAVASCRIPT, EditorLanguage.TYPESCRIPT -> {
                listOf(
                    CompletionItem("length", "length", CompletionType.PROPERTY, "Array or string length"),
                    CompletionItem("push", "push()", CompletionType.FUNCTION, "Add element to array"),
                    CompletionItem("pop", "pop()", CompletionType.FUNCTION, "Remove last element from array"),
                    CompletionItem("map", "map()", CompletionType.FUNCTION, "Map over array"),
                    CompletionItem("filter", "filter()", CompletionType.FUNCTION, "Filter array"),
                    CompletionItem("reduce", "reduce()", CompletionType.FUNCTION, "Reduce array")
                )
            }
            else -> emptyList()
        }
    }
    
    /**
     * 获取参数补全
     */
    private fun getParameterCompletions(context: CompletionContext): List<CompletionItem> {
        // 根据函数签名提供参数补全
        return listOf(
            CompletionItem("param", "param", CompletionType.PARAMETER, "Function parameter")
        )
    }
    
    /**
     * 获取索引补全
     */
    private fun getIndexCompletions(): List<CompletionItem> {
        return listOf(
            CompletionItem("0", "0", CompletionType.TEXT, "Index 0"),
            CompletionItem("1", "1", CompletionType.TEXT, "Index 1"),
            CompletionItem("2", "2", CompletionType.TEXT, "Index 2")
        )
    }
    
    /**
     * 获取代码块补全
     */
    private fun getBlockCompletions(language: EditorLanguage): List<CompletionItem> {
        return when (language) {
            EditorLanguage.JAVA, EditorLanguage.KOTLIN -> {
                listOf(
                    CompletionItem("}", "}", CompletionType.TEXT, "Close brace")
                )
            }
            EditorLanguage.JAVASCRIPT, EditorLanguage.TYPESCRIPT -> {
                listOf(
                    CompletionItem("}", "}", CompletionType.TEXT, "Close brace")
                )
            }
            else -> emptyList()
        }
    }
    
    /**
     * 获取字符串补全
     */
    private fun getStringCompletions(language: EditorLanguage): List<CompletionItem> {
        return listOf(
            CompletionItem("\"", "\"", CompletionType.TEXT, "Close quote"),
            CompletionItem("\\n", "\\n", CompletionType.TEXT, "Newline"),
            CompletionItem("\\t", "\\t", CompletionType.TEXT, "Tab"),
            CompletionItem("\\\"", "\\\"", CompletionType.TEXT, "Escaped quote")
        )
    }
    
    /**
     * 获取导入补全
     */
    private fun getImportCompletions(language: EditorLanguage): List<CompletionItem> {
        return when (language) {
            EditorLanguage.JAVA -> {
                listOf(
                    CompletionItem("java.util.", "java.util.", CompletionType.MODULE, "Java utilities"),
                    CompletionItem("java.io.", "java.io.", CompletionType.MODULE, "Java I/O"),
                    CompletionItem("java.lang.", "java.lang.", CompletionType.MODULE, "Java language"),
                    CompletionItem("javax.swing.", "javax.swing.", CompletionType.MODULE, "Java Swing")
                )
            }
            EditorLanguage.JAVASCRIPT, EditorLanguage.TYPESCRIPT -> {
                listOf(
                    CompletionItem("react", "react", CompletionType.MODULE, "React library"),
                    CompletionItem("lodash", "lodash", CompletionType.MODULE, "Lodash utility"),
                    CompletionItem("axios", "axios", CompletionType.MODULE, "HTTP client"),
                    CompletionItem("moment", "moment", CompletionType.MODULE, "Date library")
                )
            }
            else -> emptyList()
        }
    }
    
    /**
     * 获取预测补全（AI 补全）
     */
    private fun getPredictiveCompletions(
        code: String,
        position: Int,
        language: EditorLanguage,
        context: CompletionContext
    ): List<CompletionItem> {
        // 这里可以实现基于机器学习的预测补全
        // 目前返回示例数据
        return when (language) {
            EditorLanguage.PYTHON -> {
                if (context.line.trim().endsWith(":")) {
                    listOf(
                        CompletionItem("pass", "pass", CompletionType.KEYWORD, "Pass statement"),
                        CompletionItem("print", "print()", CompletionType.FUNCTION, "Print function")
                    )
                } else emptyList()
            }
            else -> emptyList()
        }
    }
    
    // 辅助方法
    private fun findWordStart(code: String, position: Int): Int {
        var start = position - 1
        while (start >= 0 && (code[start].isLetterOrDigit() || code[start] == '_')) {
            start--
        }
        return start + 1
    }
    
    private fun findWordEnd(code: String, position: Int): Int {
        var end = position
        while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) {
            end++
        }
        return end
    }
    
    private fun findLineStart(code: String, position: Int): Int {
        var start = position
        while (start > 0 && code[start - 1] != '\n') {
            start--
        }
        return start
    }
    
    private fun findLineEnd(code: String, position: Int): Int {
        var end = position
        while (end < code.length && code[end] != '\n') {
            end++
        }
        return end
    }
}

/**
 * CompletionType 扩展
 */
private fun CompletionType.fromSymbolType(symbolType: SymbolType): CompletionType {
    return when (symbolType) {
        SymbolType.FUNCTION -> CompletionType.FUNCTION
        SymbolType.CLASS -> CompletionType.CLASS
        SymbolType.INTERFACE -> CompletionType.INTERFACE
        SymbolType.VARIABLE -> CompletionType.VARIABLE
        SymbolType.PROPERTY -> CompletionType.PROPERTY
        SymbolType.ENUM -> CompletionType.ENUM
        SymbolType.NAMESPACE, SymbolType.MODULE -> CompletionType.MODULE
        SymbolType.CONSTANT -> CompletionType.VARIABLE
    }
}

/**
 * 符号表
 */
private data class SymbolTable(
    val symbols: List<SymbolInfo>
)

/**
 * 补全上下文
 */
private data class CompletionContext(
    val before: String,
    val after: String,
    val line: String,
    val position: Int,
    val lineNumber: Int,
    val column: Int
)