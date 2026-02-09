package com.codemate.features.editor.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.codemate.features.editor.data.*
import com.codemate.features.editor.viewmodel.CodeEditorViewModel
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.min

/**
 * 主代码编辑器组件
 */
@Composable
fun CodeEditor(
    modifier: Modifier = Modifier,
    viewModel: CodeEditorViewModel = viewModel(),
    initialCode: String = "",
    onCodeChanged: (String) -> Unit = {},
    onLanguageChanged: (EditorLanguage) -> Unit = {},
    onThemeChanged: (EditorTheme) -> Unit = {}
) {
    val editorState by viewModel.editorState.collectAsState()
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    
    // 初始化代码
    LaunchedEffect(initialCode) {
        if (initialCode.isNotEmpty() && editorState.code != initialCode) {
            viewModel.updateCode(initialCode)
        }
    }
    
    // 代码变更监听
    LaunchedEffect(editorState.code) {
        if (editorState.code != initialCode) {
            onCodeChanged(editorState.code)
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(editorState.currentTheme.backgroundColor)
        ) {
            // 编辑器头部（工具栏）
            EditorToolbar(
                editorState = editorState,
                onLanguageChanged = { language ->
                    viewModel.setLanguage(language)
                    onLanguageChanged(language)
                },
                onThemeChanged = { theme ->
                    viewModel.setTheme(theme)
                    onThemeChanged(theme)
                },
                onFontSizeChanged = viewModel::setFontSize,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
                onFind = { /* 打开查找对话框 */ },
                onReplace = { /* 打开替换对话框 */ },
                onSymbolBarToggle = { position ->
                    viewModel.toggleSymbolBar(position)
                }
            )
            
            // 编辑器主体
            EditorContent(
                editorState = editorState,
                viewModel = viewModel,
                modifier = Modifier.weight(1f)
            )
            
            // 符号栏
            if (editorState.symbolBarVisible) {
                SymbolBar(
                    onSymbolClick = viewModel::insertSymbol,
                    onClose = { viewModel.toggleSymbolBar() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            
            // 代码补全
            if (editorState.isCompletionVisible) {
                CodeCompletionPopup(
                    completions = editorState.codeCompletion,
                    selectedIndex = editorState.selectedCompletionIndex,
                    onSelect = viewModel::selectCompletion,
                    onConfirm = viewModel::confirmCompletion,
                    onDismiss = viewModel::hideCompletion
                )
            }
            
            // 长按菜单
            if (editorState.longPressMenuVisible) {
                LongPressMenu(
                    position = editorState.longPressMenuPosition,
                    selectedText = editorState.selectedWord,
                    onAction = { action ->
                        when (action) {
                            LongPressAction.COPY -> {
                                // 复制选中文本
                                viewModel.hideLongPressMenu()
                            }
                            LongPressAction.PASTE -> {
                                // 粘贴文本
                                viewModel.hideLongPressMenu()
                            }
                            LongPressAction.CUT -> {
                                // 剪切选中文本
                                viewModel.hideLongPressMenu()
                            }
                            LongPressAction.SELECT_ALL -> {
                                // 全选
                                viewModel.updateSelection(0, editorState.code.length)
                                viewModel.hideLongPressMenu()
                            }
                        }
                    },
                    onDismiss = viewModel::hideLongPressMenu
                )
            }
        }
        
        // 查找替换对话框
        FindReplaceDialog(
            visible = false, // 这里可以添加状态控制
            findQuery = editorState.findQuery,
            replaceQuery = editorState.replaceQuery,
            searchResults = editorState.searchResults,
            currentSearchIndex = editorState.currentSearchIndex,
            onFindQueryChanged = viewModel::findText,
            onReplaceQueryChanged = { /* 更新替换查询 */ },
            onFindNext = viewModel::findNext,
            onFindPrevious = viewModel::findPrevious,
            onReplace = viewModel::replaceText,
            onReplaceAll = viewModel::replaceAll,
            onDismiss = { /* 关闭对话框 */ }
        )
    }
}

/**
 * 编辑器工具栏
 */
@Composable
private fun EditorToolbar(
    editorState: EditorState,
    onLanguageChanged: (EditorLanguage) -> Unit,
    onThemeChanged: (EditorTheme) -> Unit,
    onFontSizeChanged: (Int) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFind: () -> Unit,
    onReplace: () -> Unit,
    onSymbolBarToggle: (Offset?) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp
    ) {
        Column {
            // 主工具栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧按钮组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 语言选择
                    LanguageDropdown(
                        currentLanguage = editorState.currentLanguage,
                        onLanguageSelected = onLanguageChanged
                    )
                    
                    Divider(modifier = Modifier
                        .height(24.dp)
                        .width(1.dp))
                    
                    // 撤销/重做
                    IconButton(
                        onClick = onUndo,
                        enabled = editorState.isUndoEnabled
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    
                    IconButton(
                        onClick = onRedo,
                        enabled = editorState.isRedoEnabled
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                    
                    Divider(modifier = Modifier
                        .height(24.dp)
                        .width(1.dp))
                    
                    // 查找替换
                    IconButton(onClick = onFind) {
                        Icon(Icons.Default.Search, contentDescription = "Find")
                    }
                    
                    IconButton(onClick = onReplace) {
                        Icon(Icons.Default.FindReplace, contentDescription = "Replace")
                    }
                }
                
                // 右侧按钮组
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 字体大小调节
                    FontSizeControl(
                        fontSize = editorState.fontSize,
                        onFontSizeChanged = onFontSizeChanged
                    )
                    
                    // 主题切换
                    ThemeDropdown(
                        currentTheme = editorState.currentTheme,
                        onThemeSelected = onThemeChanged
                    )
                    
                    // 符号栏切换
                    IconButton(
                        onClick = { onSymbolBarToggle(null) }
                    ) {
                        Icon(
                            if (editorState.symbolBarVisible) Icons.Default.KeyboardHide 
                            else Icons.Default.Keyboard,
                            contentDescription = "Toggle Symbol Bar"
                        )
                    }
                }
            }
            
            // 错误和警告状态栏
            if (editorState.errors.isNotEmpty() || editorState.warnings.isNotEmpty()) {
                StatusBar(
                    errors = editorState.errors,
                    warnings = editorState.warnings,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(Color(0xFFFFEBEE)) // 浅红色背景
                )
            }
        }
    }
}

/**
 * 语言选择下拉菜单
 */
@Composable
private fun LanguageDropdown(
    currentLanguage: EditorLanguage,
    onLanguageSelected: (EditorLanguage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                text = currentLanguage.displayName,
                fontSize = 12.sp
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
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
                        Text(
                            text = language.displayName,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }
    }
}

/**
 * 主题选择下拉菜单
 */
@Composable
private fun ThemeDropdown(
    currentTheme: EditorTheme,
    onThemeSelected: (EditorTheme) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        OutlinedButton(
            onClick = { expanded = !expanded },
            modifier = Modifier.height(36.dp)
        ) {
            Text(
                text = currentTheme.displayName,
                fontSize = 12.sp
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
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
                    text = {
                        Text(
                            text = theme.displayName,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }
    }
}

/**
 * 字体大小控制
 */
@Composable
private fun FontSizeControl(
    fontSize: Int,
    onFontSizeChanged: (Int) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        IconButton(
            onClick = { onFontSizeChanged(fontSize - 1) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Decrease Font Size")
        }
        
        Text(
            text = fontSize.toString(),
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        
        IconButton(
            onClick = { onFontSizeChanged(fontSize + 1) },
            modifier = Modifier.size(24.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Increase Font Size")
        }
    }
}

/**
 * 状态栏
 */
@Composable
private fun StatusBar(
    errors: List<EditorError>,
    warnings: List<EditorWarning>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 错误数量
        if (errors.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = "Errors",
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = errors.size.toString(),
                    fontSize = 12.sp,
                    color = Color(0xFFD32F2F)
                )
            }
        }
        
        // 警告数量
        if (warnings.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Warnings",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = warnings.size.toString(),
                    fontSize = 12.sp,
                    color = Color(0xFFFF9800)
                )
            }
        }
        
        // 光标位置
        Text(
            text = "Line 1, Col 1", // 这里应该显示真实的光标位置
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.weight(1f)
        )
        
        // 当前语言
        Text(
            text = "Plain Text", // 这里应该显示当前语言
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

/**
 * 编辑器主体内容
 */
@Composable
private fun EditorContent(
    editorState: EditorState,
    viewModel: CodeEditorViewModel,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberLazyListState()
    
    Column(modifier = modifier) {
        // 行号和编辑器区域
        Row(modifier = Modifier.weight(1f)) {
            // 行号区域
            LineNumberColumn(
                editorState = editorState,
                scrollState = scrollState,
                modifier = Modifier.width(48.dp)
            )
            
            // 代码编辑器区域
            CodeEditorArea(
                editorState = editorState,
                viewModel = viewModel,
                scrollState = scrollState,
                modifier = Modifier.weight(1f)
            )
        }
        
        // 小地图（如果启用）
        if (editorState.editorSettings.showMiniMap) {
            MiniMap(
                code = editorState.code,
                currentPosition = editorState.cursorPosition,
                highlights = editorState.syntaxHighlights,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .background(Color(0xFFF5F5F5))
            )
        }
    }
}

/**
 * 行号列
 */
@Composable
private fun LineNumberColumn(
    editorState: EditorState,
    scrollState: LazyListState,
    modifier: Modifier = Modifier
) {
    val code = editorState.code
    val lineCount = code.count { it == '\n' } + 1
    val lines = remember(code) { code.split('\n') }
    
    Column(
        modifier = modifier
            .background(Color(0xFFF8F8F8))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.End
    ) {
        LazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            items(lineCount) { lineIndex ->
                Text(
                    text = (lineIndex + 1).toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Right,
                    fontSize = editorState.fontSize.sp,
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

/**
 * 代码编辑器区域
 */
@Composable
private fun CodeEditorArea(
    editorState: EditorState,
    viewModel: CodeEditorViewModel,
    scrollState: LazyListState,
    modifier: Modifier = Modifier
) {
    val textFieldValue = remember(editorState.code) {
        TextFieldValue(editorState.code)
    }
    
    val density = LocalDensity.current
    
    BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            viewModel.updateCode(newValue.text)
        },
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { offset ->
                        // 处理点击
                        val position = offsetToTextPosition(offset, density)
                        viewModel.updateCursorPosition(position)
                    },
                    onDoubleTap = { offset ->
                        // 处理双击
                        viewModel.handleGesture(GestureType.DOUBLE_TAP, offset)
                    },
                    onLongPress = { offset ->
                        // 处理长按
                        viewModel.handleGesture(GestureType.LONG_PRESS, offset)
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    val newPosition = Offset(
                        change.position.x,
                        change.position.y
                    )
                    viewModel.handleGesture(GestureType.DRAG_MOVE, newPosition)
                }
            },
        textStyle = TextStyle(
            fontSize = editorState.fontSize.sp,
            fontFamily = editorState.fontFamily.fontFamily,
            color = editorState.currentTheme.textColor
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.None
        ),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // 渲染语法高亮的代码
                SyntaxHighlightedText(
                    text = editorState.code,
                    highlights = editorState.syntaxHighlights,
                    fontSize = editorState.fontSize.sp,
                    fontFamily = editorState.fontFamily.fontFamily,
                    color = editorState.currentTheme.textColor,
                    modifier = Modifier.fillMaxSize()
                )
                
                // 渲染光标
                Cursor(
                    position = editorState.cursorPosition,
                    color = editorState.currentTheme.cursorColor,
                    fontSize = editorState.fontSize.sp
                )
                
                // 渲染选择区域
                SelectionHighlight(
                    selectionStart = editorState.selectionStart,
                    selectionEnd = editorState.selectionEnd,
                    color = editorState.currentTheme.selectionColor,
                    fontSize = editorState.fontSize.sp
                )
                
                // 渲染内联文本字段（用于处理输入）
                innerTextField()
            }
        }
    )
}

/**
 * 语法高亮文本
 */
@Composable
private fun SyntaxHighlightedText(
    text: String,
    highlights: List<SyntaxHighlight>,
    fontSize: androidx.compose.ui.unit.TextUnit,
    fontFamily: FontFamily,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (text.isEmpty()) return
    
    val annotatedString = buildAnnotatedString {
        append(text)
        
        // 应用语法高亮
        highlights.forEach { highlight ->
            addStyle(
                style = highlight.style,
                start = highlight.start,
                end = highlight.end
            )
        }
    }
    
    Text(
        text = annotatedString,
        modifier = modifier,
        fontSize = fontSize,
        fontFamily = fontFamily,
        color = color
    )
}

/**
 * 光标
 */
@Composable
private fun Cursor(
    position: Int,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    // 这里可以实现一个闪烁的光标
    // 简化实现，使用竖线
    Box(
        modifier = Modifier
            .width(2.dp)
            .height((fontSize.value * 1.2).dp)
            .background(color)
    )
}

/**
 * 选择高亮
 */
@Composable
private fun SelectionHighlight(
    selectionStart: Int,
    selectionEnd: Int,
    color: Color,
    fontSize: androidx.compose.ui.unit.TextUnit
) {
    if (selectionStart == selectionEnd) return
    
    // 这里可以绘制选择区域的背景
    // 简化实现
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height((fontSize.value * 1.2).dp)
            .background(color.copy(alpha = 0.3f))
    )
}

/**
 * 小地图
 */
@Composable
private fun MiniMap(
    code: String,
    currentPosition: Int,
    highlights: List<SyntaxHighlight>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        // 绘制代码的迷你版本
        // 这里可以实现代码的缩略图
    }
}

/**
 * 偏移量转文本位置
 */
private fun offsetToTextPosition(
    offset: Offset,
    density: Density
): Int {
    // 简单的实现，实际应用中需要考虑字体大小、行高等
    return offset.x.toInt().coerceIn(0, 1000) // 临时限制
}

/**
 * 长按动作
 */
enum class LongPressAction {
    COPY, PASTE, CUT, SELECT_ALL
}

/**
 * 长按菜单
 */
@Composable
private fun LongPressMenu(
    position: Offset,
    selectedText: String,
    onAction: (LongPressAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable { onDismiss() }
    ) {
        Card(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedText.isNotEmpty()) {
                    MenuItem(
                        icon = Icons.Default.ContentCopy,
                        text = "Copy",
                        onClick = { onAction(LongPressAction.COPY) }
                    )
                    
                    MenuItem(
                        icon = Icons.Default.ContentCut,
                        text = "Cut",
                        onClick = { onAction(LongPressAction.CUT) }
                    )
                }
                
                MenuItem(
                    icon = Icons.Default.ContentPaste,
                    text = "Paste",
                    onClick = { onAction(LongPressAction.PASTE) }
                )
                
                Divider()
                
                MenuItem(
                    icon = Icons.Default.SelectAll,
                    text = "Select All",
                    onClick = { onAction(LongPressAction.SELECT_ALL) }
                )
            }
        }
    }
}

/**
 * 菜单项
 */
@Composable
private fun MenuItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Text(text)
    }
}