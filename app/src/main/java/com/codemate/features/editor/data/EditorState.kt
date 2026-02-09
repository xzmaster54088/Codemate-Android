package com.codemate.features.editor.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * 编辑器状态数据类
 * 管理代码编辑器的所有状态信息
 */
data class EditorState(
    // 内容相关
    val code: String = "",
    val cursorPosition: Int = 0,
    val selectionStart: Int = 0,
    val selectionEnd: Int = 0,
    
    // 语言和主题相关
    val currentLanguage: EditorLanguage = EditorLanguage.KOTLIN,
    val currentTheme: EditorTheme = EditorTheme.LIGHT,
    val fontSize: Int = 14,
    val fontFamily: EditorFont = EditorFont.DEFAULT,
    
    // 语法高亮相关
    val syntaxHighlights: List<SyntaxHighlight> = emptyList(),
    
    // 编辑功能相关
    val undoStack: List<String> = emptyList(),
    val redoStack: List<String> = emptyList(),
    val isUndoEnabled: Boolean = false,
    val isRedoEnabled: Boolean = false,
    
    // 查找替换相关
    val findQuery: String = "",
    val replaceQuery: String = "",
    val searchResults: List<SearchResult> = emptyList(),
    val currentSearchIndex: Int = -1,
    
    // 补全相关
    val codeCompletion: List<CompletionItem> = emptyList(),
    val isCompletionVisible: Boolean = false,
    val selectedCompletionIndex: Int = 0,
    
    // 手势相关
    val selectedWord: String = "",
    val selectedLine: String = "",
    val longPressMenuVisible: Boolean = false,
    val longPressMenuPosition: Offset = Offset(0f, 0f),
    
    // 虚拟符号栏相关
    val symbolBarVisible: Boolean = false,
    val symbolBarPosition: Offset = Offset(0f, 0f),
    
    // 性能优化相关
    val isVirtualScrolling: Boolean = true,
    val visibleLineStart: Int = 0,
    val visibleLineEnd: Int = 0,
    
    // 错误和警告
    val errors: List<EditorError> = emptyList(),
    val warnings: List<EditorWarning> = emptyList(),
    
    // 编辑器设置
    val editorSettings: EditorSettings = EditorSettings()
)

/**
 * 支持的编程语言
 */
enum class EditorLanguage(
    val displayName: String,
    val fileExtension: String,
    val syntaxFile: String,
    val completionKeywords: List<String>
) {
    KOTLIN("Kotlin", ".kt", "kotlin.json", kotlinKeywords),
    JAVA("Java", ".java", "java.json", javaKeywords),
    PYTHON("Python", ".py", "python.json", pythonKeywords),
    JAVASCRIPT("JavaScript", ".js", "javascript.json", javascriptKeywords),
    TYPESCRIPT("TypeScript", ".ts", "typescript.json", typescriptKeywords),
    CPP("C++", ".cpp", "cpp.json", cppKeywords),
    CSHARP("C#", ".cs", "csharp.json", csharpKeywords),
    GO("Go", ".go", "go.json", goKeywords),
    RUST("Rust", ".rs", "rust.json", rustKeywords),
    SWIFT("Swift", ".swift", "swift.json", swiftKeywords),
    XML("XML", ".xml", "xml.json", xmlKeywords),
    JSON("JSON", ".json", "json.json", jsonKeywords),
    YAML("YAML", ".yaml", "yaml.json", yamlKeywords),
    MARKDOWN("Markdown", ".md", "markdown.json", markdownKeywords);
    
    companion object {
        private val kotlinKeywords = listOf("fun", "val", "var", "if", "else", "for", "while", "return", "class", "interface", "enum", "data", "object", "companion", "by", "is", "in", "as", "null", "true", "false", "when", "try", "catch", "finally", "throw", "import", "package")
        private val javaKeywords = listOf("public", "private", "protected", "static", "final", "class", "interface", "enum", "abstract", "void", "int", "long", "double", "float", "boolean", "char", "String", "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "try", "catch", "finally", "throw", "throws", "import", "package", "extends", "implements", "new", "this", "super", "null", "true", "false")
        private val pythonKeywords = listOf("def", "class", "if", "elif", "else", "for", "while", "try", "except", "finally", "with", "as", "import", "from", "return", "yield", "break", "continue", "pass", "lambda", "and", "or", "not", "in", "is", "None", "True", "False")
        private val javascriptKeywords = listOf("function", "var", "let", "const", "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "try", "catch", "finally", "throw", "new", "this", "super", "class", "extends", "import", "export", "default", "null", "undefined", "true", "false", "typeof", "instanceof", "in", "of", "await", "async")
        private val typescriptKeywords = javascriptKeywords + listOf("interface", "type", "enum", "namespace", "declare", "public", "private", "protected", "readonly", "implements", "extends", "keyof", "typeof", "as", "is", "any", "unknown", "void", "never")
        private val cppKeywords = listOf("auto", "bool", "char", "class", "const", "constexpr", "double", "else", "enum", "explicit", "export", "extern", "float", "for", "friend", "goto", "if", "inline", "int", "long", "mutable", "namespace", "new", "operator", "private", "protected", "public", "register", "return", "short", "signed", "sizeof", "static", "struct", "switch", "template", "this", "throw", "try", "typedef", "typeid", "typename", "union", "unsigned", "using", "virtual", "void", "volatile", "wchar_t", "while")
        private val csharpKeywords = listOf("abstract", "as", "base", "bool", "break", "byte", "case", "catch", "char", "checked", "class", "const", "continue", "decimal", "default", "delegate", "do", "double", "else", "enum", "event", "explicit", "extern", "false", "finally", "fixed", "float", "for", "foreach", "goto", "if", "implicit", "in", "int", "interface", "internal", "is", "lock", "long", "namespace", "new", "null", "object", "operator", "out", "override", "params", "private", "protected", "public", "readonly", "ref", "return", "sbyte", "sealed", "short", "sizeof", "stackalloc", "static", "string", "struct", "switch", "this", "throw", "true", "try", "typeof", "uint", "ulong", "unchecked", "unsafe", "ushort", "using", "virtual", "void", "volatile", "while")
        private val goKeywords = listOf("break", "case", "chan", "const", "continue", "default", "defer", "else", "fallthrough", "for", "func", "go", "goto", "if", "import", "interface", "map", "package", "range", "return", "select", "struct", "switch", "type", "var")
        private val rustKeywords = listOf("as", "async", "await", "break", "const", "continue", "crate", "dyn", "else", "enum", "extern", "false", "fn", "for", "if", "impl", "in", "let", "loop", "match", "mod", "mut", "pub", "ref", "return", "self", "Self", "static", "struct", "super", "trait", "true", "type", "unsafe", "use", "where", "while")
        private val swiftKeywords = listOf("associatedtype", "class", "deinit", "enum", "extension", "fileprivate", "func", "import", "init", "inout", "internal", "let", "open", "operator", "private", "protocol", "public", "rethrows", "static", "struct", "subscript", "typealias", "var", "break", "case", "continue", "default", "do", "else", "fallthrough", "for", "guard", "if", "in", "repeat", "return", "switch", "where", "while", "as", "Any", "catch", "false", "is", "nil", "rethrows", "super", "self", "Self", "throw", "throws", "true", "try")
        private val xmlKeywords = listOf("xml", "version", "encoding", "standalone", "doctype", "element", "attribute", "notation", "entity", "text", "comment", "cdata", "pi", "namespace")
        private val jsonKeywords = listOf("true", "false", "null")
        private val yamlKeywords = listOf("true", "false", "null", "yes", "no", "on", "off")
        private val markdownKeywords = listOf("#", "##", "###", "####", "#####", "######", "*", "**", "***", "_", "__", "___", "`", "```", ">", ">>", "-", "+", "*", "1.", "1)", "[]", "()", "![]", "[text](url)", "![alt text](url)", "|", "---", "***", "___")
    }
}

/**
 * 编辑器主题
 */
enum class EditorTheme(
    val displayName: String,
    val backgroundColor: Color,
    val textColor: Color,
    val selectionColor: Color,
    val cursorColor: Color
) {
    LIGHT("Light", Color.White, Color.Black, Color(0x330000FF), Color.Blue),
    DARK("Dark", Color(0xFF121212), Color.White, Color(0x33FFFFFF), Color.White),
    SOLARIZED_LIGHT("Solarized Light", Color(0xFFFDF6E3), Color(0xFF657B83), Color(0x33B58900), Color(0xFFB58900)),
    SOLARIZED_DARK("Solarized Dark", Color(0xFF002B36), Color(0xFF93A1A1), Color(0x33FDF6E3), Color(0xFF268BD2)),
    MONOKAI("Monokai", Color(0xFF272822), Color(0xFFF8F8F2), Color(0x33F92672), Color(0xFFF92672)),
    GITHUB("GitHub", Color(0xFFFFFFFF), Color(0xFF24292E), Color(0x33C8E1FF), Color(0xFF0366D6))
}

/**
 * 编辑器字体
 */
enum class EditorFont(
    val displayName: String,
    val fontFamily: FontFamily
) {
    DEFAULT("Default", FontFamily.Default),
    MONOSPACE("Monospace", FontFamily.Monospace),
    JETBRAINS_MONO("JetBrains Mono", FontFamily.Monospace),
    FIRA_CODE("Fira Code", FontFamily.Monospace)
}

/**
 * 编辑器设置
 */
data class EditorSettings(
    val autoIndent: Boolean = true,
    val autoComplete: Boolean = true,
    val showLineNumbers: Boolean = true,
    val showWhitespace: Boolean = false,
    val wordWrap: Boolean = true,
    val tabSize: Int = 4,
    val useSpaces: Boolean = true,
    val showMiniMap: Boolean = false,
    val highlightCurrentLine: Boolean = true,
    val enableVirtualScrolling: Boolean = true,
    val maxUndoStackSize: Int = 100,
    val fontSizeRange: IntRange = 8..72
)

/**
 * 偏移量
 */
data class Offset(val x: Float, val y: Float)