package com.codemate.features.editor.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codemate.features.editor.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Stack

/**
 * 代码编辑器ViewModel
 * 负责管理编辑器的所有状态和业务逻辑
 */
class CodeEditorViewModel : ViewModel() {
    
    // 主状态
    private val _editorState = MutableStateFlow(EditorState())
    val editorState: StateFlow<EditorState> = _editorState.asStateFlow()
    
    // 当前编辑器状态
    var currentMode by mutableStateOf(EditorMode.INSERT)
        private set
    
    // 编辑历史
    private val undoStack = Stack<String>()
    private val redoStack = Stack<String>()
    
    // 选中文本
    private var selectedText by mutableStateOf("")
    
    // 代码分析
    private var currentAnalysis by mutableStateOf(CodeAnalysis())
    
    // 代码补全
    private var completionCache = mutableMapOf<String, List<CompletionItem>>()
    
    // 语法高亮缓存
    private var syntaxHighlightCache = mutableMapOf<String, List<SyntaxHighlight>>()
    
    init {
        // 初始化编辑器
        initializeEditor()
    }
    
    /**
     * 初始化编辑器
     */
    private fun initializeEditor() {
        viewModelScope.launch {
            loadDefaultSettings()
            loadSymbolBar()
            // 可以在这里加载更多初始化数据
        }
    }
    
    /**
     * 加载默认设置
     */
    private suspend fun loadDefaultSettings() {
        withContext(Dispatchers.IO) {
            // 从数据库或SharedPreferences加载设置
            val defaultSettings = EditorSettings()
            updateState { currentState ->
                currentState.copy(
                    editorSettings = defaultSettings,
                    undoStack = emptyList(),
                    redoStack = emptyList(),
                    isUndoEnabled = false,
                    isRedoEnabled = false
                )
            }
        }
    }
    
    /**
     * 更新代码内容
     */
    fun updateCode(newCode: String, preserveHistory: Boolean = true) {
        val currentCode = _editorState.value.code
        
        if (currentCode != newCode && preserveHistory) {
            // 保存到撤销栈
            if (currentCode.isNotEmpty()) {
                undoStack.push(currentCode)
            }
            // 清空重做栈
            redoStack.clear()
        }
        
        updateState { currentState ->
            currentState.copy(
                code = newCode,
                isUndoEnabled = undoStack.isNotEmpty(),
                isRedoEnabled = redoStack.isNotEmpty()
            )
        }
        
        // 触发语法高亮和代码分析
        performSyntaxHighlight()
        performCodeAnalysis()
    }
    
    /**
     * 更新光标位置
     */
    fun updateCursorPosition(position: Int) {
        updateState { currentState ->
            currentState.copy(
                cursorPosition = position,
                selectionStart = position,
                selectionEnd = position
            )
        }
    }
    
    /**
     * 更新选择范围
     */
    fun updateSelection(start: Int, end: Int) {
        val selectedText = if (start <= end) {
            _editorState.value.code.substring(start, end)
        } else {
            _editorState.value.code.substring(end, start)
        }
        
        this.selectedText = selectedText
        
        updateState { currentState ->
            currentState.copy(
                selectionStart = start,
                selectionEnd = end,
                selectedWord = if (selectedText.contains('\n')) "" else selectedText,
                selectedLine = getSelectedLine(start, end)
            )
        }
    }
    
    /**
     * 获取选中的行
     */
    private fun getSelectedLine(start: Int, end: Int): String {
        val code = _editorState.value.code
        val lines = code.split('\n')
        
        var lineStart = 0
        var currentLine = 0
        
        for (i in code.indices) {
            if (i == start) break
            if (code[i] == '\n') {
                lineStart = i + 1
                currentLine++
            }
        }
        
        var lineEnd = lineStart
        while (lineEnd < code.length && code[lineEnd] != '\n') {
            lineEnd++
        }
        
        return if (currentLine < lines.size) lines[currentLine] else ""
    }
    
    /**
     * 设置编程语言
     */
    fun setLanguage(language: EditorLanguage) {
        updateState { currentState ->
            currentState.copy(currentLanguage = language)
        }
        performSyntaxHighlight()
        loadLanguageCompletion()
    }
    
    /**
     * 设置主题
     */
    fun setTheme(theme: EditorTheme) {
        updateState { currentState ->
            currentState.copy(currentTheme = theme)
        }
    }
    
    /**
     * 设置字体大小
     */
    fun setFontSize(fontSize: Int) {
        val clampedSize = _editorState.value.editorSettings.fontSizeRange.clamp(fontSize)
        updateState { currentState ->
            currentState.copy(fontSize = clampedSize)
        }
    }
    
    /**
     * 撤销
     */
    fun undo() {
        if (undoStack.isNotEmpty()) {
            val currentCode = _editorState.value.code
            val previousCode = undoStack.pop()
            
            redoStack.push(currentCode)
            
            updateState { currentState ->
                currentState.copy(
                    code = previousCode,
                    isUndoEnabled = undoStack.isNotEmpty(),
                    isRedoEnabled = redoStack.isNotEmpty()
                )
            }
            
            performSyntaxHighlight()
            performCodeAnalysis()
        }
    }
    
    /**
     * 重做
     */
    fun redo() {
        if (redoStack.isNotEmpty()) {
            val currentCode = _editorState.value.code
            val nextCode = redoStack.pop()
            
            undoStack.push(currentCode)
            
            updateState { currentState ->
                currentState.copy(
                    code = nextCode,
                    isUndoEnabled = undoStack.isNotEmpty(),
                    isRedoEnabled = redoStack.isNotEmpty()
                )
            }
            
            performSyntaxHighlight()
            performCodeAnalysis()
        }
    }
    
    /**
     * 查找文本
     */
    fun findText(query: String) {
        if (query.isEmpty()) {
            updateState { currentState ->
                currentState.copy(
                    findQuery = "",
                    searchResults = emptyList(),
                    currentSearchIndex = -1
                )
            }
            return
        }
        
        val code = _editorState.value.code
        val results = mutableListOf<SearchResult>()
        
        var index = 0
        while (index < code.length) {
            val foundIndex = code.indexOf(query, index)
            if (foundIndex == -1) break
            
            // 计算行号和列号
            val lineNumber = code.substring(0, foundIndex).count { it == '\n' } + 1
            val lineStart = code.lastIndexOf('\n', foundIndex - 1) + 1
            val column = foundIndex - lineStart + 1
            
            results.add(SearchResult(
                start = foundIndex,
                end = foundIndex + query.length,
                lineNumber = lineNumber,
                column = column,
                text = code.substring(foundIndex, foundIndex + query.length)
            ))
            
            index = foundIndex + 1
        }
        
        updateState { currentState ->
            currentState.copy(
                findQuery = query,
                searchResults = results,
                currentSearchIndex = if (results.isNotEmpty()) 0 else -1
            )
        }
    }
    
    /**
     * 查找下一个
     */
    fun findNext() {
        val currentIndex = _editorState.value.currentSearchIndex
        val results = _editorState.value.searchResults
        
        if (results.isNotEmpty()) {
            val nextIndex = if (currentIndex < results.size - 1) currentIndex + 1 else 0
            updateState { currentState ->
                currentState.copy(currentSearchIndex = nextIndex)
            }
        }
    }
    
    /**
     * 查找上一个
     */
    fun findPrevious() {
        val currentIndex = _editorState.value.currentSearchIndex
        val results = _editorState.value.searchResults
        
        if (results.isNotEmpty()) {
            val previousIndex = if (currentIndex > 0) currentIndex - 1 else results.size - 1
            updateState { currentState ->
                currentState.copy(currentSearchIndex = previousIndex)
            }
        }
    }
    
    /**
     * 替换文本
     */
    fun replaceText(replacement: String) {
        val results = _editorState.value.searchResults
        val currentIndex = _editorState.value.currentSearchIndex
        
        if (results.isNotEmpty() && currentIndex >= 0) {
            val currentResult = results[currentIndex]
            val code = _editorState.value.code
            val newCode = code.substring(0, currentResult.start) + 
                         replacement + 
                         code.substring(currentResult.end)
            
            updateCode(newCode)
            
            // 重新查找以更新结果
            findText(_editorState.value.findQuery)
        }
    }
    
    /**
     * 替换所有
     */
    fun replaceAll(replacement: String) {
        val query = _editorState.value.findQuery
        val code = _editorState.value.code
        val newCode = code.replace(query, replacement)
        
        if (code != newCode) {
            updateCode(newCode)
            findText(query) // 重新查找
        }
    }
    
    /**
     * 执行语法高亮
     */
    private fun performSyntaxHighlight() {
        viewModelScope.launch {
            val code = _editorState.value.code
            val language = _editorState.value.currentLanguage
            
            withContext(Dispatchers.IO) {
                // 使用缓存避免重复计算
                val cacheKey = "$language:$code"
                val cached = syntaxHighlightCache[cacheKey]
                
                if (cached != null) {
                    updateState { currentState ->
                        currentState.copy(syntaxHighlights = cached)
                    }
                } else {
                    // 执行语法高亮分析
                    val highlights = analyzeSyntax(code, language)
                    syntaxHighlightCache[cacheKey] = highlights
                    
                    updateState { currentState ->
                        currentState.copy(syntaxHighlights = highlights)
                    }
                }
            }
        }
    }
    
    /**
     * 执行代码分析
     */
    private fun performCodeAnalysis() {
        viewModelScope.launch {
            val code = _editorState.value.code
            val language = _editorState.value.currentLanguage
            
            withContext(Dispatchers.IO) {
                // 这里可以实现真正的代码分析
                // 目前只是示例
                currentAnalysis = CodeAnalysis()
            }
        }
    }
    
    /**
     * 分析语法
     */
    private fun analyzeSyntax(code: String, language: EditorLanguage): List<SyntaxHighlight> {
        val highlights = mutableListOf<SyntaxHighlight>()
        
        // 简单的语法高亮实现
        // 在实际应用中，这里应该使用更复杂的分析引擎
        
        when (language) {
            EditorLanguage.KOTLIN -> {
                // Kotlin语法高亮
                highlights.addAll(findKeywords(code, EditorLanguage.KOTLIN.completionKeywords, SyntaxType.KEYWORD))
                // 字符串高亮
                highlights.addAll(findStrings(code, SyntaxType.STRING))
                // 注释高亮
                highlights.addAll(findComments(code, SyntaxType.COMMENT))
                // 数字高亮
                highlights.addAll(findNumbers(code, SyntaxType.NUMBER))
            }
            EditorLanguage.JAVA -> {
                highlights.addAll(findKeywords(code, EditorLanguage.JAVA.completionKeywords, SyntaxType.KEYWORD))
                highlights.addAll(findStrings(code, SyntaxType.STRING))
                highlights.addAll(findComments(code, SyntaxType.COMMENT))
                highlights.addAll(findNumbers(code, SyntaxType.NUMBER))
            }
            // 其他语言类似处理...
            else -> {
                // 默认高亮
                highlights.addAll(findKeywords(code, language.completionKeywords, SyntaxType.KEYWORD))
                highlights.addAll(findStrings(code, SyntaxType.STRING))
                highlights.addAll(findComments(code, SyntaxType.COMMENT))
                highlights.addAll(findNumbers(code, SyntaxType.NUMBER))
            }
        }
        
        return highlights.sortedBy { it.start }
    }
    
    /**
     * 查找关键词
     */
    private fun findKeywords(code: String, keywords: List<String>, type: SyntaxType): List<SyntaxHighlight> {
        val highlights = mutableListOf<SyntaxHighlight>()
        
        keywords.forEach { keyword ->
            var index = 0
            while (index < code.length) {
                index = code.indexOf(keyword, index)
                if (index == -1) break
                
                // 检查是否是完整的词（前后不是字母数字）
                val before = if (index > 0) code[index - 1] else ' '
                val after = if (index + keyword.length < code.length) code[index + keyword.length] else ' '
                
                if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) {
                    highlights.add(SyntaxHighlight(
                        start = index,
                        end = index + keyword.length,
                        style = getSpanStyleForType(type),
                        type = type
                    ))
                }
                
                index += keyword.length
            }
        }
        
        return highlights
    }
    
    /**
     * 查找字符串
     */
    private fun findStrings(code: String, type: SyntaxType): List<SyntaxHighlight> {
        val highlights = mutableListOf<SyntaxHighlight>()
        val regex = "\"[^\"]*\"|'[^']*'|`[^`]*`".toRegex()
        
        regex.findAll(code).forEach { match ->
            highlights.add(SyntaxHighlight(
                start = match.range.first,
                end = match.range.last + 1,
                style = getSpanStyleForType(type),
                type = type
            ))
        }
        
        return highlights
    }
    
    /**
     * 查找注释
     */
    private fun findComments(code: String, type: SyntaxType): List<SyntaxHighlight> {
        val highlights = mutableListOf<SyntaxHighlight>()
        
        // 单行注释
        val singleLineRegex = "//.*$".toRegex(RegexOption.MULTILINE)
        singleLineRegex.findAll(code).forEach { match ->
            highlights.add(SyntaxHighlight(
                start = match.range.first,
                end = match.range.last + 1,
                style = getSpanStyleForType(type),
                type = type
            ))
        }
        
        // 多行注释
        val multiLineRegex = "/\\*.*?\\*/".toRegex(RegexOption.DOT_MATCHES_ALL)
        multiLineRegex.findAll(code).forEach { match ->
            highlights.add(SyntaxHighlight(
                start = match.range.first,
                end = match.range.last + 1,
                style = getSpanStyleForType(type),
                type = type
            ))
        }
        
        return highlights
    }
    
    /**
     * 查找数字
     */
    private fun findNumbers(code: String, type: SyntaxType): List<SyntaxHighlight> {
        val highlights = mutableListOf<SyntaxHighlight>()
        val numberRegex = "\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFdDlL]?\\b".toRegex()
        
        numberRegex.findAll(code).forEach { match ->
            highlights.add(SyntaxHighlight(
                start = match.range.first,
                end = match.range.last + 1,
                style = getSpanStyleForType(type),
                type = type
            ))
        }
        
        return highlights
    }
    
    /**
     * 根据类型获取SpanStyle
     */
    private fun getSpanStyleForType(type: SyntaxType): SpanStyle {
        return when (type) {
            SyntaxType.KEYWORD -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF0000FF))
            SyntaxType.STRING -> SpanStyle(color = Color(0xFF008000))
            SyntaxType.COMMENT -> SpanStyle(color = Color(0xFF808080), fontStyle = FontStyle.Italic)
            SyntaxType.NUMBER -> SpanStyle(color = Color(0xFF800080))
            SyntaxType.CLASS -> SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFF8B4513))
            SyntaxType.FUNCTION -> SpanStyle(color = Color(0xFF4169E1))
            SyntaxType.VARIABLE -> SpanStyle(color = Color(0xFF2E8B57))
            SyntaxType.OPERATOR -> SpanStyle(fontWeight = FontWeight.Bold)
            else -> SpanStyle()
        }
    }
    
    /**
     * 加载语言补全
     */
    private fun loadLanguageCompletion() {
        val language = _editorState.value.currentLanguage
        val cacheKey = "completion_$language"
        
        if (!completionCache.containsKey(cacheKey)) {
            viewModelScope.launch {
                val completions = generateLanguageCompletion(language)
                completionCache[cacheKey] = completions
            }
        }
    }
    
    /**
     * 生成语言补全
     */
    private fun generateLanguageCompletion(language: EditorLanguage): List<CompletionItem> {
        val completions = mutableListOf<CompletionItem>()
        
        // 添加关键词
        language.completionKeywords.forEach { keyword ->
            completions.add(CompletionItem(
                text = keyword,
                displayText = keyword,
                type = CompletionType.KEYWORD,
                description = "$keyword keyword"
            ))
        }
        
        // 这里可以添加更多的补全项，包括函数、类等
        
        return completions.sortedBy { it.sortText }
    }
    
    /**
     * 显示代码补全
     */
    fun showCompletion(position: Int) {
        val code = _editorState.value.code
        val language = _editorState.value.currentLanguage
        
        // 获取当前单词
        val wordStart = findWordStart(code, position)
        val wordEnd = findWordEnd(code, position)
        val currentWord = code.substring(wordStart, position)
        
        val completions = completionCache["completion_$language"] ?: emptyList()
        val filteredCompletions = completions.filter { 
            it.text.startsWith(currentWord, ignoreCase = true)
        }
        
        updateState { currentState ->
            currentState.copy(
                codeCompletion = filteredCompletions,
                isCompletionVisible = filteredCompletions.isNotEmpty(),
                selectedCompletionIndex = 0
            )
        }
    }
    
    /**
     * 隐藏代码补全
     */
    fun hideCompletion() {
        updateState { currentState ->
            currentState.copy(
                isCompletionVisible = false,
                codeCompletion = emptyList()
            )
        }
    }
    
    /**
     * 选择补全项
     */
    fun selectCompletion(index: Int) {
        val completions = _editorState.value.codeCompletion
        if (index in completions.indices) {
            updateState { currentState ->
                currentState.copy(selectedCompletionIndex = index)
            }
        }
    }
    
    /**
     * 确认补全
     */
    fun confirmCompletion() {
        val completions = _editorState.value.codeCompletion
        val selectedIndex = _editorState.value.selectedCompletionIndex
        
        if (completions.isNotEmpty() && selectedIndex in completions.indices) {
            val selected = completions[selectedIndex]
            // 插入选中的补全项
            insertCompletion(selected)
        }
        
        hideCompletion()
    }
    
    /**
     * 插入补全项
     */
    private fun insertCompletion(completion: CompletionItem) {
        val code = _editorState.value.code
        val position = _editorState.value.cursorPosition
        
        // 找到当前单词的开始位置
        val wordStart = findWordStart(code, position)
        
        val newCode = code.substring(0, wordStart) + 
                     completion.insertText + 
                     code.substring(position)
        
        updateCode(newCode)
        updateCursorPosition(wordStart + completion.insertText.length)
    }
    
    /**
     * 查找单词开始位置
     */
    private fun findWordStart(code: String, position: Int): Int {
        var start = position - 1
        while (start >= 0 && (code[start].isLetterOrDigit() || code[start] == '_')) {
            start--
        }
        return start + 1
    }
    
    /**
     * 查找单词结束位置
     */
    private fun findWordEnd(code: String, position: Int): Int {
        var end = position
        while (end < code.length && (code[end].isLetterOrDigit() || code[end] == '_')) {
            end++
        }
        return end
    }
    
    /**
     * 处理触摸手势
     */
    fun handleGesture(gestureType: GestureType, position: Offset) {
        when (gestureType) {
            GestureType.DOUBLE_TAP -> handleDoubleTap(position)
            GestureType.TRIPLE_TAP -> handleTripleTap(position)
            GestureType.LONG_PRESS -> handleLongPress(position)
            GestureType.DRAG_START -> handleDragStart(position)
            GestureType.DRAG_MOVE -> handleDragMove(position)
            GestureType.DRAG_END -> handleDragEnd()
            else -> { /* 其他手势处理 */ }
        }
    }
    
    /**
     * 处理双击
     */
    private fun handleDoubleTap(position: Offset) {
        // 选中单词
        val code = _editorState.value.code
        val positionInText = offsetToTextPosition(code, position.x.toInt())
        
        val wordStart = findWordStart(code, positionInText)
        val wordEnd = findWordEnd(code, positionInText)
        
        updateSelection(wordStart, wordEnd)
    }
    
    /**
     * 处理三击
     */
    private fun handleTripleTap(position: Offset) {
        // 选中整行
        val code = _editorState.value.code
        val positionInText = offsetToTextPosition(code, position.x.toInt())
        
        val lineStart = findLineStart(code, positionInText)
        val lineEnd = findLineEnd(code, positionInText)
        
        updateSelection(lineStart, lineEnd)
    }
    
    /**
     * 处理长按
     */
    private fun handleLongPress(position: Offset) {
        updateState { currentState ->
            currentState.copy(
                longPressMenuVisible = true,
                longPressMenuPosition = position
            )
        }
    }
    
    /**
     * 处理拖拽开始
     */
    private fun handleDragStart(position: Offset) {
        // 设置拖拽起点
        updateCursorPosition(offsetToTextPosition(_editorState.value.code, position.x.toInt()))
    }
    
    /**
     * 处理拖拽移动
     */
    private fun handleDragMove(position: Offset) {
        val code = _editorState.value.code
        val start = _editorState.value.selectionStart
        val end = offsetToTextPosition(code, position.x.toInt())
        
        updateSelection(start, end)
    }
    
    /**
     * 处理拖拽结束
     */
    private fun handleDragEnd() {
        // 清理拖拽状态
    }
    
    /**
     * 偏移量转文本位置
     */
    private fun offsetToTextPosition(code: String, xOffset: Int): Int {
        // 简单的实现，实际应用中需要考虑字体宽度、行高等
        return xOffset.coerceIn(0, code.length)
    }
    
    /**
     * 查找行开始位置
     */
    private fun findLineStart(code: String, position: Int): Int {
        var start = position
        while (start > 0 && code[start - 1] != '\n') {
            start--
        }
        return start
    }
    
    /**
     * 查找行结束位置
     */
    private fun findLineEnd(code: String, position: Int): Int {
        var end = position
        while (end < code.length && code[end] != '\n') {
            end++
        }
        return end
    }
    
    /**
     * 隐藏长按菜单
     */
    fun hideLongPressMenu() {
        updateState { currentState ->
            currentState.copy(longPressMenuVisible = false)
        }
    }
    
    /**
     * 显示/隐藏符号栏
     */
    fun toggleSymbolBar(position: Offset? = null) {
        updateState { currentState ->
            val visible = !currentState.symbolBarVisible
            currentState.copy(
                symbolBarVisible = visible,
                symbolBarPosition = position ?: Offset(0f, 0f)
            )
        }
    }
    
    /**
     * 插入符号
     */
    fun insertSymbol(symbol: Symbol) {
        val code = _editorState.value.code
        val position = _editorState.value.cursorPosition
        
        val newCode = code.substring(0, position) + 
                     symbol.text + 
                     code.substring(position)
        
        updateCode(newCode)
        updateCursorPosition(position + symbol.text.length)
        
        // 如果是成对符号，插入成对的
        if (isPairedSymbol(symbol.text)) {
            insertPairedSymbol(symbol.text, position)
        }
    }
    
    /**
     * 检查是否是成对符号
     */
    private fun isPairedSymbol(symbol: String): Boolean {
        val pairedSymbols = setOf("()", "[]", "{}", "\"\"", "''", "``", "<>")
        return pairedSymbols.contains(symbol)
    }
    
    /**
     * 插入成对符号
     */
    private fun insertPairedSymbol(symbol: String, position: Int) {
        val pairs = mapOf(
            "(" to ")",
            "[" to "]",
            "{" to "}",
            "\"" to "\"",
            "'" to "'",
            "`" to "`",
            "<" to ">"
        )
        
        val endSymbol = pairs[symbol]
        if (endSymbol != null) {
            val code = _editorState.value.code
            val newCode = code.substring(0, position + 1) + 
                         endSymbol + 
                         code.substring(position + 1)
            updateCode(newCode)
        }
    }
    
    /**
     * 加载符号栏
     */
    private suspend fun loadSymbolBar() {
        withContext(Dispatchers.IO) {
            val symbols = generateSymbolBar()
            updateState { currentState ->
                currentState.copy() // 符号栏将在UI层处理
            }
        }
    }
    
    /**
     * 生成符号栏符号
     */
    private fun generateSymbolBar(): List<Symbol> {
        val symbols = mutableListOf<Symbol>()
        
        // 操作符
        symbols.addAll(listOf(
            Symbol("+", "+", "Addition", SymbolCategory.OPERATORS),
            Symbol("-", "-", "Subtraction", SymbolCategory.OPERATORS),
            Symbol("*", "*", "Multiplication", SymbolCategory.OPERATORS),
            Symbol("/", "/", "Division", SymbolCategory.OPERATORS),
            Symbol("%", "%", "Modulo", SymbolCategory.OPERATORS),
            Symbol("=", "=", "Assignment", SymbolCategory.OPERATORS),
            Symbol("==", "==", "Equality", SymbolCategory.OPERATORS),
            Symbol("!=", "!=", "Inequality", SymbolCategory.OPERATORS),
            Symbol(">", ">", "Greater than", SymbolCategory.OPERATORS),
            Symbol("<", "<", "Less than", SymbolCategory.OPERATORS),
            Symbol("&&", "&&", "Logical AND", SymbolCategory.OPERATORS),
            Symbol("||", "||", "Logical OR", SymbolCategory.OPERATORS)
        ))
        
        // 括号
        symbols.addAll(listOf(
            Symbol("(", "(", "Left parenthesis", SymbolCategory.BRACKETS),
            Symbol(")", ")", "Right parenthesis", SymbolCategory.BRACKETS),
            Symbol("[", "[", "Left bracket", SymbolCategory.BRACKETS),
            Symbol("]", "]", "Right bracket", SymbolCategory.BRACKETS),
            Symbol("{", "{", "Left brace", SymbolCategory.BRACKETS),
            Symbol("}", "}", "Right brace", SymbolCategory.BRACKETS),
            Symbol("<", "<", "Left angle", SymbolCategory.BRACKETS),
            Symbol(">", ">", "Right angle", SymbolCategory.BRACKETS)
        ))
        
        // 引号
        symbols.addAll(listOf(
            Symbol("\"", "\"", "Double quote", SymbolCategory.QUOTES),
            Symbol("'", "'", "Single quote", SymbolCategory.QUOTES),
            Symbol("`", "`", "Backtick", SymbolCategory.QUOTES)
        ))
        
        // 特殊符号
        symbols.addAll(listOf(
            Symbol("?", "?", "Question mark", SymbolCategory.SPECIAL),
            Symbol("!", "!", "Exclamation mark", SymbolCategory.SPECIAL),
            Symbol(":", ":", "Colon", SymbolCategory.PUNCTUATION),
            Symbol(";", ";", "Semicolon", SymbolCategory.PUNCTUATION),
            Symbol(".", ".", "Dot", SymbolCategory.PUNCTUATION),
            Symbol(",", ",", "Comma", SymbolCategory.PUNCTUATION),
            Symbol("->", "→", "Arrow", SymbolCategory.ARROWS),
            Symbol("=>", "⇒", "Double arrow", SymbolCategory.ARROWS),
            Symbol("::", "::", "Scope resolution", SymbolCategory.ARROWS)
        ))
        
        return symbols
    }
    
    /**
     * 更新编辑器设置
     */
    fun updateEditorSettings(settings: EditorSettings) {
        updateState { currentState ->
            currentState.copy(editorSettings = settings)
        }
    }
    
    /**
     * 设置编辑器模式
     */
    fun setEditorMode(mode: EditorMode) {
        currentMode = mode
        updateState { currentState ->
            currentState.copy() // 模式状态在ViewModel中维护
        }
    }
    
    /**
     * 获取当前错误和警告
     */
    fun getCurrentDiagnostics(): CodeAnalysis {
        return currentAnalysis
    }
    
    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        // 清理缓存
        completionCache.clear()
        syntaxHighlightCache.clear()
    }
    
    /**
     * 更新状态的辅助函数
     */
    private fun updateState(update: (EditorState) -> EditorState) {
        _editorState.value = update(_editorState.value)
    }
}