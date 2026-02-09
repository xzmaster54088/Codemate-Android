package com.codemate.features.editor.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codemate.features.editor.data.EditorLanguage
import com.codemate.features.editor.data.EditorTheme
import com.codemate.features.editor.ui.CodeEditor
import com.codemate.features.editor.viewmodel.CodeEditorViewModel

/**
 * CodeEditor使用示例
 * 展示如何在实际应用中使用自定义代码编辑器
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeEditorExample() {
    var selectedCode by remember { mutableStateOf(getSampleKotlinCode()) }
    var selectedLanguage by remember { mutableStateOf(EditorLanguage.KOTLIN) }
    var selectedTheme by remember { mutableStateOf(EditorTheme.LIGHT) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 顶部工具栏
        TopAppBar(
            title = { Text("CodeMate Mobile Editor") },
            actions = {
                // 语言选择下拉菜单
                LanguageSelector(
                    selectedLanguage = selectedLanguage,
                    onLanguageSelected = { selectedLanguage = it }
                )
                
                // 主题选择下拉菜单
                ThemeSelector(
                    selectedTheme = selectedTheme,
                    onThemeSelected = { selectedTheme = it }
                )
                
                // 代码示例按钮
                IconButton(onClick = { selectedCode = getSampleKotlinCode() }) {
                    Icon(Icons.Default.Code, contentDescription = "Sample Code")
                }
                
                // 保存按钮
                IconButton(onClick = { /* 保存代码逻辑 */ }) {
                    Icon(Icons.Default.Save, contentDescription = "Save")
                }
            }
        )
        
        // 主编辑器
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CodeEditor(
                initialCode = selectedCode,
                language = selectedLanguage,
                theme = selectedTheme,
                onCodeChanged = { code -> selectedCode = code },
                onLanguageChanged = { language -> selectedLanguage = language },
                onThemeChanged = { theme -> selectedTheme = theme },
                modifier = Modifier.fillMaxSize()
            )
        }
        
        // 底部状态栏
        StatusBar(
            language = selectedLanguage,
            theme = selectedTheme,
            codeLength = selectedCode.length,
            lineCount = selectedCode.count { it == '\n' } + 1
        )
    }
}

/**
 * 语言选择器
 */
@Composable
private fun LanguageSelector(
    selectedLanguage: EditorLanguage,
    onLanguageSelected: (EditorLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(Icons.Default.Language, contentDescription = "Language")
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            EditorLanguage.values().forEach { language ->
                DropdownMenuItem(
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    },
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(language.displayName)
                            Text(
                                text = language.fileExtension,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * 主题选择器
 */
@Composable
private fun ThemeSelector(
    selectedTheme: EditorTheme,
    onThemeSelected: (EditorTheme) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        IconButton(onClick = { expanded = !expanded }) {
            Icon(Icons.Default.Palette, contentDescription = "Theme")
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            EditorTheme.values().forEach { theme ->
                DropdownMenuItem(
                    onClick = {
                        onThemeSelected(theme)
                        expanded = false
                    },
                    text = { Text(theme.displayName) }
                )
            }
        }
    }
}

/**
 * 状态栏
 */
@Composable
private fun StatusBar(
    language: EditorLanguage,
    theme: EditorTheme,
    codeLength: Int,
    lineCount: Int
) {
    Surface(
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 语言信息
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = language.displayName,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // 主题信息
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = theme.displayName,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // 代码统计
            Text(
                text = "$codeLength chars • $lineCount lines",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

/**
 * 高级编辑器示例
 * 包含自定义工具栏和侧边栏
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedCodeEditorExample() {
    val viewModel: CodeEditorViewModel = viewModel()
    var sidebarVisible by remember { mutableStateOf(false) }
    var selectedCode by remember { mutableStateOf(getSampleKotlinCode()) }
    
    Row(modifier = Modifier.fillMaxSize()) {
        // 侧边栏
        if (sidebarVisible) {
            Surface(
                modifier = Modifier.width(280.dp),
                tonalElevation = 2.dp
            ) {
                FileExplorer(
                    onFileSelected = { fileName ->
                        selectedCode = getSampleCode(fileName)
                    }
                )
            }
        }
        
        // 主编辑区域
        Column(modifier = Modifier.weight(1f)) {
            // 自定义工具栏
            TopAppBar(
                title = { Text("Advanced Editor") },
                navigationIcon = {
                    IconButton(onClick = { sidebarVisible = !sidebarVisible }) {
                        Icon(Icons.Default.Menu, contentDescription = "Toggle Sidebar")
                    }
                },
                actions = {
                    // 撤销重做
                    IconButton(
                        onClick = { viewModel.undo() },
                        enabled = viewModel.editorState.value.isUndoEnabled
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    
                    IconButton(
                        onClick = { viewModel.redo() },
                        enabled = viewModel.editorState.value.isRedoEnabled
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                    
                    // 查找替换
                    IconButton(onClick = { /* 打开查找对话框 */ }) {
                        Icon(Icons.Default.Search, contentDescription = "Find")
                    }
                    
                    // 符号栏
                    IconButton(onClick = { viewModel.toggleSymbolBar() }) {
                        Icon(Icons.Default.Keyboard, contentDescription = "Symbol Bar")
                    }
                    
                    // 设置
                    IconButton(onClick = { /* 打开设置 */ }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
            
            // 编辑器
            CodeEditor(
                viewModel = viewModel,
                initialCode = selectedCode,
                onCodeChanged = { code -> selectedCode = code },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * 文件浏览器组件
 */
@Composable
private fun FileExplorer(
    onFileSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val sampleFiles = remember {
        listOf(
            "MainActivity.kt",
            "CodeEditor.kt",
            "utils/SyntaxHighlighter.kt",
            "data/EditorState.kt",
            "README.md"
        )
    }
    
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(sampleFiles) { fileName ->
            ListItem(
                headlineContent = { Text(fileName) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null
                    )
                },
                onClick = { onFileSelected(fileName) }
            )
        }
    }
}

/**
 * 性能监控示例
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PerformanceMonitoredEditor() {
    val viewModel: CodeEditorViewModel = viewModel()
    var showPerformanceStats by remember { mutableStateOf(false) }
    var selectedCode by remember { mutableStateOf(getSampleKotlinCode()) }
    
    Box {
        Column {
            TopAppBar(
                title = { Text("Performance Monitor") },
                actions = {
                    IconButton(onClick = { showPerformanceStats = !showPerformanceStats }) {
                        Icon(Icons.Default.Analytics, contentDescription = "Performance")
                    }
                }
            )
            
            CodeEditor(
                initialCode = selectedCode,
                onCodeChanged = { code -> selectedCode = code },
                modifier = Modifier.weight(1f)
            )
        }
        
        // 性能统计悬浮窗
        if (showPerformanceStats) {
            Card(
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopEnd),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Performance Stats",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    
                    // 这里可以显示实际的性能统计数据
                    // 例如：缓存大小、内存使用、渲染时间等
                    
                    Text(
                        text = "Syntax Cache: 25 items",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Completion Cache: 15 items",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Memory Usage: 12.5 MB",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    Button(
                        onClick = { showPerformanceStats = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

/**
 * 获取示例Kotlin代码
 */
private fun getSampleKotlinCode(): String {
    return """
        package com.example.editor
        
        import androidx.compose.runtime.*
        import androidx.compose.ui.Modifier
        
        /**
         * 自定义代码编辑器组件
         */
        @Composable
        fun CodeEditor(
            initialCode: String = "",
            modifier: Modifier = Modifier
        ) {
            var code by remember { mutableStateOf(initialCode) }
            
            Column(modifier = modifier) {
                // 编辑器工具栏
                EditorToolbar(
                    onSave = { saveCode(code) },
                    onFormat = { code = formatCode(code) }
                )
                
                // 代码输入区域
                CodeInputArea(
                    code = code,
                    onCodeChange = { newCode -> code = newCode }
                )
                
                // 状态栏
                StatusBar(
                    lineCount = code.count { it == '\n' } + 1,
                    characterCount = code.length
                )
            }
        }
        
        /**
         * 保存代码到文件
         */
        private fun saveCode(code: String) {
            // 文件保存逻辑
            println("Saving code: ${'$'}{code.length} characters")
        }
        
        /**
         * 格式化代码
         */
        private fun formatCode(code: String): String {
            // 简单的代码格式化逻辑
            return code.lines()
                .map { it.trim() }
                .joinToString("\n")
        }
    """.trimIndent()
}

/**
 * 根据文件名获取示例代码
 */
private fun getSampleCode(fileName: String): String {
    return when {
        fileName.endsWith(".kt") -> getSampleKotlinCode()
        fileName.endsWith(".java") -> getSampleJavaCode()
        fileName.endsWith(".py") -> getSamplePythonCode()
        fileName.endsWith(".js") -> getSampleJavaScriptCode()
        fileName.endsWith(".xml") -> getSampleXmlCode()
        fileName.endsWith(".json") -> getSampleJsonCode()
        else -> "// $fileName\n// This is a sample file"
    }
}

private fun getSampleJavaCode(): String {
    return """
        public class CodeEditor {
            private String code;
            
            public CodeEditor(String initialCode) {
                this.code = initialCode;
            }
            
            public void setCode(String code) {
                this.code = code;
                notifyCodeChanged();
            }
            
            public String getCode() {
                return code;
            }
            
            private void notifyCodeChanged() {
                // 通知代码变更
            }
        }
    """.trimIndent()
}

private fun getSamplePythonCode(): String {
    return """
        class CodeEditor:
            def __init__(self, initial_code=""):
                self.code = initial_code
            
            def set_code(self, code):
                self.code = code
                self.notify_code_changed()
            
            def get_code(self):
                return self.code
            
            def notify_code_changed(self):
                print(f"Code changed: {len(self.code)} characters")
        """
}

private fun getSampleJavaScriptCode(): String {
    return """
        class CodeEditor {
            constructor(initialCode = '') {
                this.code = initialCode;
            }
            
            setCode(code) {
                this.code = code;
                this.notifyCodeChanged();
            }
            
            getCode() {
                return this.code;
            }
            
            notifyCodeChanged() {
                console.log(`Code changed: ${this.code.length} characters`);
            }
        }
        """
}

private fun getSampleXmlCode(): String {
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <CodeEditor xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:padding="16dp">
            
            <Toolbar
                android:id="@+id/toolbar"
                android:layout_width="match_parent"
                android:layout_height="?attr/actionBarSize" />
                
            <EditText
                android:id="@+id/codeInput"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="1"
                android:fontFamily="monospace"
                android:textSize="14sp"
                android:background="@null" />
                
        </CodeEditor>
    """.trimIndent()
}

private fun getSampleJsonCode(): String {
    return """
        {
            "editor": {
                "name": "CodeMate Mobile",
                "version": "1.0.0",
                "features": [
                    "syntax_highlighting",
                    "code_completion",
                    "touch_gestures",
                    "virtual_scrolling"
                ],
                "settings": {
                    "auto_indent": true,
                    "auto_complete": true,
                    "show_line_numbers": true,
                    "theme": "light"
                },
                "performance": {
                    "cache_enabled": true,
                    "virtual_scroll": true,
                    "battery_optimization": true
                }
            }
        }
    """.trimIndent()
}