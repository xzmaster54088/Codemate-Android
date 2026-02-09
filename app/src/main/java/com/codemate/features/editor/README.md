---
AIGC:
    ContentProducer: Minimax Agent AI
    ContentPropagator: Minimax Agent AI
    Label: AIGC
    ProduceID: "00000000000000000000000000000000"
    PropagateID: "00000000000000000000000000000000"
    ReservedCode1: 3045022064845b10c6a6c310167257cfd530cf88823afd747ac224824a9778b916fda4d902210098a790ec82608f1375a73069cc51072ab1c57640ed8a78dc56d7737089305526
    ReservedCode2: 3046022100fe1a68eaba89b7f7c53b16539799b14f0fb6fb0b93b301653d2997b2565fbdc5022100cfc698f6243c3e5f4925a25b449db6e3b614b0778b8baf8055dcd485410b1599
---

# CodeMate Mobile 自定义代码编辑器

## 📱 项目概述

CodeMate Mobile 自定义代码编辑器是一个专为移动端设计的强大代码编辑器组件，基于 Jetpack Compose 和 Material Design 3 构建。该编辑器提供了完整的代码编辑功能，包括语法高亮、智能补全、触摸手势处理等，专为 Android 移动设备优化。

## ✨ 核心功能

### 🎨 多语言语法高亮
- **支持语言**: Kotlin, Java, Python, JavaScript, TypeScript, C++, C#, Go, Rust, Swift, XML, JSON, YAML, Markdown
- **智能语法分析**: 基于正则表达式的实时语法高亮
- **主题支持**: Light, Dark, Solarized Light, Solarized Dark, Monokai, GitHub 等 6 种内置主题
- **自定义颜色**: 每个主题都精心调校，确保在移动设备上具有良好的可读性

### 📱 移动端触摸手势
- **双击选择词**: 快速选择当前单词
- **三击选择行**: 快速选择整行代码
- **长按菜单**: 复制、粘贴、剪切、全选等常用操作
- **拖拽选择**: 精确的文本选择体验
- **捏合缩放**: 支持多点触控缩放编辑区域

### ⚡ 智能代码补全
- **关键词补全**: 自动补全编程语言关键字
- **代码片段**: 预设的常用代码模板（函数、类、控制流等）
- **符号补全**: 标准库和内置函数的智能补全
- **上下文感知**: 根据代码上下文提供相关补全建议
- **AI 预测**: 基于上下文的智能预测补全

### 🔧 虚拟符号栏
- **常用符号**: 快速输入编程常用符号
- **分类组织**: 按操作符、括号、引号、箭头、特殊符号等分类
- **成对插入**: 自动插入成对的括号、引号等
- **可定制**: 支持自定义常用符号

### 🔍 高级编辑功能
- **查找替换**: 支持正则表达式的全文查找替换
- **多结果导航**: 快速在搜索结果间跳转
- **撤销重做**: 无限撤销重做功能
- **智能缩进**: 自动缩进和对齐
- **行号显示**: 可选的行号显示
- **小地图**: 快速导航的大纲小地图

### 🎛️ 个性化设置
- **字体大小**: 8-72 字号调节
- **字体族**: 默认、等宽、JetBrains Mono、Fira Code
- **主题切换**: 一键切换编辑器主题
- **编辑器选项**: 可配置自动缩进、自动补全、换行等
- **性能优化**: 虚拟滚动、缓存管理等

### ⚡ 性能优化
- **虚拟滚动**: 只渲染可见行，大幅提升大文件性能
- **智能缓存**: 语法高亮和补全结果缓存
- **内存管理**: 自动清理过期缓存，内存使用监控
- **电池优化**: 根据设备状态自动调整性能设置
- **异步处理**: 所有耗时操作在后台线程执行

## 🏗️ 架构设计

### 数据层 (Data Layer)
```
📁 data/
├── EditorState.kt          # 编辑器状态管理
└── EditorData.kt           # 数据模型定义
```

### 业务层 (Domain Layer)
```
📁 viewmodel/
└── CodeEditorViewModel.kt  # 编辑器业务逻辑
```

### 表现层 (UI Layer)
```
📁 ui/
├── CodeEditor.kt           # 主编辑器组件
└── EditorComponents.kt     # 编辑器子组件
```

### 工具层 (Utils Layer)
```
📁 utils/
├── SyntaxHighlighterEngine.kt      # 语法高亮引擎
├── TouchGestureEngine.kt           # 触摸手势引擎
├── CodeCompletionEngine.kt         # 代码补全引擎
├── EditorPerformanceManager.kt     # 性能优化管理
└── EditorConfigManager.kt          # 配置管理
```

## 🚀 快速开始

### 1. 基础使用

```kotlin
@Composable
fun MyCodeEditor() {
    CodeEditorScreen(
        initialCode = """
            fun main() {
                println("Hello, CodeMate!")
            }
        """.trimIndent(),
        language = EditorLanguage.KOTLIN,
        theme = EditorTheme.LIGHT,
        onCodeChanged = { code ->
            // 处理代码变更
            Log.d("CodeEditor", "Code changed: ${code.length} characters")
        },
        onLanguageChanged = { language ->
            // 处理语言变更
            Log.d("CodeEditor", "Language changed: ${language.displayName}")
        },
        onThemeChanged = { theme ->
            // 处理主题变更
            Log.d("CodeEditor", "Theme changed: ${theme.displayName}")
        }
    )
}
```

### 2. 高级配置

```kotlin
@Composable
fun AdvancedCodeEditor() {
    val viewModel: CodeEditorViewModel = viewModel()
    
    CodeEditor(
        viewModel = viewModel,
        initialCode = yourCode,
        onCodeChanged = { code ->
            // 保存到文件
            viewModel.updateCode(code, preserveHistory = true)
        },
        modifier = Modifier.fillMaxSize()
    )
    
    // 自定义工具栏
    TopAppBar(
        title = { Text("Advanced Editor") },
        actions = {
            // 自定义工具栏按钮
            IconButton(onClick = { viewModel.toggleSymbolBar() }) {
                Icon(Icons.Default.Keyboard, contentDescription = "Symbol Bar")
            }
        }
    )
}
```

### 3. 性能监控

```kotlin
@Composable
fun CodeEditorWithPerformance() {
    val performanceManager = remember { EditorPerformanceManager() }
    
    LaunchedEffect(Unit) {
        // 定期更新性能统计
        while (true) {
            delay(1000) // 每秒更新
            val stats = performanceManager.getPerformanceStats()
            Log.d("Performance", "Cache sizes: ${stats.syntaxCacheSize}")
        }
    }
    
    CodeEditorScreen(
        // ... 配置参数
    )
}
```

## 📋 支持的编程语言

| 语言 | 文件扩展名 | 语法高亮 | 代码补全 | 代码片段 |
|------|------------|----------|----------|----------|
| Kotlin | .kt | ✅ | ✅ | ✅ |
| Java | .java | ✅ | ✅ | ✅ |
| Python | .py | ✅ | ✅ | ✅ |
| JavaScript | .js | ✅ | ✅ | ✅ |
| TypeScript | .ts | ✅ | ✅ | ✅ |
| C++ | .cpp, .cc, .cxx | ✅ | ✅ | ✅ |
| C# | .cs | ✅ | ✅ | ✅ |
| Go | .go | ✅ | ✅ | ✅ |
| Rust | .rs | ✅ | ✅ | ✅ |
| Swift | .swift | ✅ | ✅ | ✅ |
| XML | .xml | ✅ | ✅ | ❌ |
| JSON | .json | ✅ | ✅ | ❌ |
| YAML | .yaml, .yml | ✅ | ✅ | ❌ |
| Markdown | .md | ✅ | ✅ | ❌ |

## 🎨 主题系统

### 内置主题
- **Light**: 经典浅色主题，适合白天使用
- **Dark**: 深色主题，减少眼部疲劳
- **Solarized Light**: 科学计算器风格的浅色主题
- **Solarized Dark**: 科学计算器风格的深色主题
- **Monokai**: 经典的编程主题
- **GitHub**: GitHub 网站的编辑器主题

### 自定义主题
```kotlin
// 创建自定义主题
val customTheme = EditorTheme(
    displayName = "Custom Theme",
    backgroundColor = Color(0xFF1A1A1A),
    textColor = Color(0xFFEEEEEE),
    selectionColor = Color(0x33FFFFFF),
    cursorColor = Color(0xFF00FFFF)
)
```

## 📱 移动端优化

### 触摸手势
- **点击**: 移动光标
- **双击**: 选中单词
- **三击**: 选中整行
- **长按**: 显示上下文菜单
- **拖拽**: 选择文本范围
- **捏合**: 缩放编辑区域

### 性能优化
- **虚拟滚动**: 只渲染可见行
- **智能缓存**: 避免重复计算
- **内存管理**: 自动清理过期数据
- **电池优化**: 根据设备状态调整性能

## 🔧 配置选项

### 编辑器设置
```kotlin
val settings = EditorSettings(
    autoIndent = true,           // 自动缩进
    autoComplete = true,         // 自动补全
    showLineNumbers = true,      // 显示行号
    wordWrap = true,            // 自动换行
    tabSize = 4,                // 制表符大小
    useSpaces = true,           // 使用空格而非制表符
    showMiniMap = false,        // 显示小地图
    highlightCurrentLine = true, // 高亮当前行
    enableVirtualScrolling = true, // 启用虚拟滚动
    maxUndoStackSize = 100      // 最大撤销步数
)
```

### 性能设置
```kotlin
val performanceSettings = PerformanceSettings(
    cacheSize = 100,           // 缓存大小
    virtualScrollBuffer = 10,  // 虚拟滚动缓冲区
    enablePreloading = true,    // 启用预加载
    memoryThreshold = 50 * 1024 * 1024 // 内存阈值（50MB）
)
```

## 📦 依赖项

在 `build.gradle` 中添加必要的依赖：

```kotlin
dependencies {
    implementation "androidx.compose.ui:ui:$compose_version"
    implementation "androidx.compose.material3:material3:$material3_version"
    implementation "androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycle_version"
    implementation "androidx.navigation:navigation-compose:$nav_version"
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutines_version"
}
```

## 🛠️ 开发指南

### 添加新语言支持
1. 在 `EditorLanguage` 枚举中添加新语言
2. 在 `SyntaxHighlighterEngine` 中添加语法规则
3. 在 `CodeCompletionEngine` 中添加关键词和代码片段
4. 测试语法高亮和补全功能

### 添加新主题
1. 在 `EditorTheme` 枚举中添加新主题
2. 定义颜色方案
3. 在 `EditorThemeManager` 中添加动态颜色配置
4. 测试主题在不同语言下的显示效果

### 扩展触摸手势
1. 在 `GestureType` 枚举中添加新手势
2. 在 `TouchGestureEngine` 中实现手势识别
3. 在 `CodeEditorViewModel` 中添加处理逻辑
4. 更新 UI 组件以支持新手势

## 🐛 常见问题

### Q: 如何禁用语法高亮以提高性能？
A: 设置 `editorSettings.enableSyntaxHighlight = false` 或在配置管理器中关闭。

### Q: 如何添加自定义代码片段？
A: 扩展 `CodeCompletionEngine.initializeSnippets()` 方法，添加您的自定义片段。

### Q: 如何处理大文件（>10MB）？
A: 编辑器会自动启用虚拟滚动，建议同时关闭语法高亮和代码分析以获得最佳性能。

### Q: 如何自定义触摸手势？
A: 继承 `TouchGestureEngine` 类或使用 `CodeEditorViewModel.handleGesture` 方法。

## 📄 许可证

本项目采用 MIT 许可证。详情请参阅 LICENSE 文件。

## 🤝 贡献

欢迎贡献代码！请阅读 CONTRIBUTING.md 了解详细的贡献指南。

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- GitHub Issues: [项目 Issues 页面](https://github.com/your-org/codemate-mobile/issues)
- 邮箱: codemate@example.com
- 文档: [在线文档](https://docs.codemate.com)

---

**CodeMate Mobile 自定义代码编辑器** - 让移动端代码编辑更高效！ 🚀