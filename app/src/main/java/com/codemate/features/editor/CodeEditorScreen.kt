package com.codemate.features.editor

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codemate.features.editor.data.EditorLanguage
import com.codemate.features.editor.data.EditorTheme
import com.codemate.features.editor.ui.CodeEditor
import com.codemate.features.editor.viewmodel.CodeEditorViewModel

/**
 * CodeEditorScreen - 编辑器主屏幕组件
 * 
 * 这是CodeMate Mobile代码编辑器的主入口组件，集成了所有编辑器功能：
 * - 语法高亮支持多种编程语言
 * - 触摸手势处理（双击、三击、长按、拖拽）
 * - 虚拟符号栏快速输入
 * - 智能代码补全
 * - 查找替换功能
 * - 撤销重做功能
 * - 主题和字体设置
 * - 性能优化
 * 
 * @param initialCode 初始代码内容
 * @param language 初始编程语言
 * @param theme 初始主题
 * @param onCodeChanged 代码变更回调
 * @param onLanguageChanged 语言变更回调
 * @param onThemeChanged 主题变更回调
 * @param modifier 修饰符
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorScreen(
    initialCode: String = "",
    language: EditorLanguage = EditorLanguage.KOTLIN,
    theme: EditorTheme = EditorTheme.LIGHT,
    onCodeChanged: (String) -> Unit = {},
    onLanguageChanged: (EditorLanguage) -> Unit = {},
    onThemeChanged: (EditorTheme) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CodeEditorViewModel = viewModel()
) {
    // 监听ViewModel状态变化
    val editorState by viewModel.editorState.collectAsState()
    
    // 初始化代码
    LaunchedEffect(initialCode) {
        if (initialCode.isNotEmpty() && editorState.code != initialCode) {
            viewModel.updateCode(initialCode)
        }
    }
    
    // 初始化语言和主题
    LaunchedEffect(language) {
        if (editorState.currentLanguage != language) {
            viewModel.setLanguage(language)
        }
    }
    
    LaunchedEffect(theme) {
        if (editorState.currentTheme != theme) {
            viewModel.setTheme(theme)
        }
    }
    
    // 监听代码变更
    LaunchedEffect(editorState.code) {
        if (editorState.code != initialCode) {
            onCodeChanged(editorState.code)
        }
    }
    
    // 监听语言变更
    LaunchedEffect(editorState.currentLanguage) {
        if (editorState.currentLanguage != language) {
            onLanguageChanged(editorState.currentLanguage)
        }
    }
    
    // 监听主题变更
    LaunchedEffect(editorState.currentTheme) {
        if (editorState.currentTheme != theme) {
            onThemeChanged(editorState.currentTheme)
        }
    }
    
    // 主编辑器组件
    Box(modifier = modifier.fillMaxSize()) {
        CodeEditor(
            initialCode = initialCode,
            viewModel = viewModel,
            onCodeChanged = onCodeChanged,
            onLanguageChanged = { newLanguage ->
                viewModel.setLanguage(newLanguage)
                onLanguageChanged(newLanguage)
            },
            onThemeChanged = { newTheme ->
                viewModel.setTheme(newTheme)
                onThemeChanged(newTheme)
            },
            modifier = Modifier.fillMaxSize()
        )
        
        // 可选：添加浮动操作按钮或状态指示器
        if (editorState.errors.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.BottomEnd),
                tonalElevation = 4.dp,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.Error,
                        contentDescription = "Errors",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "${editorState.errors.size} error(s)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * CodeEditorScreen预览
 */
@Preview(showBackground = true)
@Composable
fun CodeEditorScreenPreview() {
    val sampleCode = """
        fun main() {
            println("Hello, CodeMate!")
            
            val name = "Kotlin"
            val message = "Welcome to $name"
            
            if (message.isNotEmpty()) {
                println(message)
            }
            
            for (i in 1..5) {
                println("Count: $i")
            }
        }
    """.trimIndent()
    
    MaterialTheme {
        CodeEditorScreen(
            initialCode = sampleCode,
            language = EditorLanguage.KOTLIN,
            theme = EditorTheme.LIGHT
        )
    }
}

/**
 * CodeEditorScreen暗色主题预览
 */
@Preview(showBackground = true)
@Composable
fun CodeEditorScreenDarkPreview() {
    val sampleCode = """
        def hello_world():
            print("Hello, World!")
            name = "Python"
            message = f"Welcome to {name}"
            
            if message:
                print(message)
                
            for i in range(5):
                print(f"Count: {i+1}")
    """.trimIndent()
    
    MaterialTheme(colorScheme = darkColorScheme()) {
        CodeEditorScreen(
            initialCode = sampleCode,
            language = EditorLanguage.PYTHON,
            theme = EditorTheme.DARK
        )
    }
}