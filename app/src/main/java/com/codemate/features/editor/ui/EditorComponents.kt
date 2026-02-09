package com.codemate.features.editor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codemate.features.editor.data.*

/**
 * 虚拟符号栏组件
 */
@Composable
fun SymbolBar(
    onSymbolClick: (Symbol) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val symbols = remember { generateAllSymbols() }
    val categories = remember { SymbolCategory.values() }
    var selectedCategory by remember { mutableStateOf(SymbolCategory.OPERATORS) }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Symbol Bar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            // 分类标签
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { category ->
                    FilterChip(
                        onClick = { selectedCategory = category },
                        label = { 
                            Text(
                                text = category.name.replace('_', ' '),
                                fontSize = 12.sp
                            ) 
                        },
                        selected = category == selectedCategory,
                        modifier = Modifier.height(32.dp)
                    )
                }
            }
            
            // 符号网格
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(symbols.filter { it.category == selectedCategory }) { symbol ->
                    SymbolButton(
                        symbol = symbol,
                        onClick = { onSymbolClick(symbol) }
                    )
                }
            }
        }
    }
}

/**
 * 符号按钮
 */
@Composable
private fun SymbolButton(
    symbol: Symbol,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .height(40.dp)
            .width(40.dp),
        contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF5F5F5),
            contentColor = Color(0xFF424242)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Text(
            text = symbol.display,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * 代码补全弹窗
 */
@Composable
fun CodeCompletionPopup(
    completions: List<CompletionItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (completions.isEmpty()) return
    
    Card(
        modifier = modifier
            .width(300.dp)
            .heightIn(max = 200.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        LazyColumn {
            items(completions) { completion ->
                CompletionItem(
                    completion = completion,
                    isSelected = completions.indexOf(completion) == selectedIndex,
                    onClick = {
                        onSelect(completions.indexOf(completion))
                        onConfirm()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(completions.indexOf(completion))
                            onConfirm()
                        }
                )
            }
        }
    }
}

/**
 * 补全项
 */
@Composable
private fun CompletionItem(
    completion: CompletionItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 补全类型图标
        CompletionTypeIcon(
            type = completion.type,
            modifier = Modifier.size(20.dp)
        )
        
        // 补全文本和描述
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = completion.displayText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary 
                       else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
            
            if (completion.description.isNotEmpty()) {
                Text(
                    text = completion.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }
        
        // 补全类型标签
        Text(
            text = completion.type.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * 补全类型图标
 */
@Composable
private fun CompletionTypeIcon(
    type: CompletionType,
    modifier: Modifier = Modifier
) {
    val icon = when (type) {
        CompletionType.KEYWORD -> Icons.Default.Tag
        CompletionType.FUNCTION -> Icons.Default.Functions
        CompletionType.VARIABLE -> Icons.Default.Variables
        CompletionType.CLASS -> Icons.Default.Class
        CompletionType.INTERFACE -> Icons.Default.Interface
        CompletionType.ENUM -> Icons.Default.Enum
        CompletionType.TYPE -> Icons.Default.TypeSpecimen
        CompletionType.MODULE -> Icons.Default.Apps
        CompletionType.PROPERTY -> Icons.Default.Settings
        CompletionType.PARAMETER -> Icons.Default.Parameters
        CompletionType.TEXT -> Icons.Default.TextFields
        CompletionType.SNIPPET -> Icons.Default.Code
    }
    
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier,
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
}

/**
 * 查找替换对话框
 */
@Composable
fun FindReplaceDialog(
    visible: Boolean,
    findQuery: String,
    replaceQuery: String,
    searchResults: List<SearchResult>,
    currentSearchIndex: Int,
    onFindQueryChanged: (String) -> Unit,
    onReplaceQueryChanged: (String) -> Unit,
    onFindNext: () -> Unit,
    onFindPrevious: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 标题
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Find & Replace",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }
            
            // 查找输入框
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Find:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                OutlinedTextField(
                    value = findQuery,
                    onValueChange = onFindQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter text to find") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (findQuery.isNotEmpty()) {
                            IconButton(onClick = { onFindQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }
            
            // 替换输入框
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Replace:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                
                OutlinedTextField(
                    value = replaceQuery,
                    onValueChange = onReplaceQueryChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter replacement text") },
                    leadingIcon = { Icon(Icons.Default.FindReplace, contentDescription = null) },
                    trailingIcon = {
                        if (replaceQuery.isNotEmpty()) {
                            IconButton(onClick = { onReplaceQueryChanged("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
            }
            
            // 搜索结果信息
            if (searchResults.isNotEmpty()) {
                Text(
                    text = "Found ${searchResults.size} results",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            
            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 查找按钮
                Button(
                    onClick = onFindNext,
                    enabled = findQuery.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Find Next")
                }
                
                Button(
                    onClick = onFindPrevious,
                    enabled = findQuery.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Find Previous")
                }
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 替换按钮
                Button(
                    onClick = onReplace,
                    enabled = findQuery.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FindReplace, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replace")
                }
                
                // 全部替换按钮
                Button(
                    onClick = onReplaceAll,
                    enabled = findQuery.isNotEmpty(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FindReplace, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Replace All")
                }
            }
        }
    }
}

/**
 * 生成所有符号
 */
private fun generateAllSymbols(): List<Symbol> {
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
        Symbol(">=", ">=", "Greater or equal", SymbolCategory.OPERATORS),
        Symbol("<=", "<=", "Less or equal", SymbolCategory.OPERATORS),
        Symbol("&&", "&&", "Logical AND", SymbolCategory.OPERATORS),
        Symbol("||", "||", "Logical OR", SymbolCategory.OPERATORS),
        Symbol("!", "!", "Logical NOT", SymbolCategory.OPERATORS),
        Symbol("&", "&", "Bitwise AND", SymbolCategory.OPERATORS),
        Symbol("|", "|", "Bitwise OR", SymbolCategory.OPERATORS),
        Symbol("^", "^", "Bitwise XOR", SymbolCategory.OPERATORS),
        Symbol("~", "~", "Bitwise NOT", SymbolCategory.OPERATORS),
        Symbol("<<", "<<", "Left shift", SymbolCategory.OPERATORS),
        Symbol(">>", ">>", "Right shift", SymbolCategory.OPERATORS),
        Symbol(">>>", ">>>", "Unsigned right shift", SymbolCategory.OPERATORS),
        Symbol("+=", "+=", "Add and assign", SymbolCategory.OPERATORS),
        Symbol("-=", "-=", "Subtract and assign", SymbolCategory.OPERATORS),
        Symbol("*=", "*=", "Multiply and assign", SymbolCategory.OPERATORS),
        Symbol("/=", "/=", "Divide and assign", SymbolCategory.OPERATORS),
        Symbol("%=", "%=", "Modulo and assign", SymbolCategory.OPERATORS)
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
    
    // 箭头
    symbols.addAll(listOf(
        Symbol("->", "→", "Arrow", SymbolCategory.ARROWS),
        Symbol("=>", "⇒", "Double arrow", SymbolCategory.ARROWS),
        Symbol("::", "::", "Scope resolution", SymbolCategory.ARROWS),
        Symbol("=>", "⇒", "Lambda arrow", SymbolCategory.ARROWS)
    ))
    
    // 特殊符号
    symbols.addAll(listOf(
        Symbol("?", "?", "Question mark", SymbolCategory.SPECIAL),
        Symbol("!", "!", "Exclamation mark", SymbolCategory.SPECIAL),
        Symbol("@", "@", "At symbol", SymbolCategory.SPECIAL),
        Symbol("#", "#", "Hash", SymbolCategory.SPECIAL),
        Symbol("$", "$", "Dollar", SymbolCategory.SPECIAL),
        Symbol("%", "%", "Percent", SymbolCategory.SPECIAL),
        Symbol("^", "^", "Caret", SymbolCategory.SPECIAL),
        Symbol("&", "&", "Ampersand", SymbolCategory.SPECIAL),
        Symbol("*", "*", "Asterisk", SymbolCategory.SPECIAL),
        Symbol("-", "-", "Hyphen", SymbolCategory.SPECIAL),
        Symbol("+", "+", "Plus", SymbolCategory.SPECIAL),
        Symbol("=", "=", "Equals", SymbolCategory.SPECIAL),
        Symbol("|", "|", "Pipe", SymbolCategory.SPECIAL),
        Symbol("\\", "\\", "Backslash", SymbolCategory.SPECIAL),
        Symbol("/", "/", "Forward slash", SymbolCategory.SPECIAL)
    ))
    
    // 标点符号
    symbols.addAll(listOf(
        Symbol(":", ":", "Colon", SymbolCategory.PUNCTUATION),
        Symbol(";", ";", "Semicolon", SymbolCategory.PUNCTUATION),
        Symbol(".", ".", "Dot", SymbolCategory.PUNCTUATION),
        Symbol(",", ",", "Comma", SymbolCategory.PUNCTUATION),
        Symbol("...", "...", "Ellipsis", SymbolCategory.PUNCTUATION)
    ))
    
    // 数学符号
    symbols.addAll(listOf(
        Symbol("∑", "∑", "Summation", SymbolCategory.MATH),
        Symbol("∫", "∫", "Integral", SymbolCategory.MATH),
        Symbol("√", "√", "Square root", SymbolCategory.MATH),
        Symbol("π", "π", "Pi", SymbolCategory.MATH),
        Symbol("∞", "∞", "Infinity", SymbolCategory.MATH),
        Symbol("±", "±", "Plus-minus", SymbolCategory.MATH),
        Symbol("×", "×", "Multiplication", SymbolCategory.MATH),
        Symbol("÷", "÷", "Division", SymbolCategory.MATH)
    ))
    
    // 大箭头
    symbols.addAll(listOf(
        Symbol("←", "←", "Left arrow", SymbolCategory.ARROWS_LARGE),
        Symbol("→", "→", "Right arrow", SymbolCategory.ARROWS_LARGE),
        Symbol("↑", "↑", "Up arrow", SymbolCategory.ARROWS_LARGE),
        Symbol("↓", "↓", "Down arrow", SymbolCategory.ARROWS_LARGE),
        Symbol("↔", "↔", "Left-right arrow", SymbolCategory.ARROWS_LARGE),
        Symbol("↕", "↕", "Up-down arrow", SymbolCategory.ARROWS_LARGE)
    ))
    
    return symbols
}