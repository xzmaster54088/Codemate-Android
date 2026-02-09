package com.codemate.features.compiler.presentation.ui

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.codemate.features.compiler.domain.entities.*

/**
 * 编译器主界面
 */
@Composable
fun CompilerScreen(
    viewModel: CompilerViewModel,
    onNavigateToDetail: (String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 监听UI状态变化
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // 标题
        Text(
            text = "CodeMate Mobile 编译器",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // 工具链状态
        ToolchainStatusSection(
            toolchains = uiState.availableToolchains,
            onInstallToolchain = { language ->
                viewModel.installToolchain(language)
            },
            onRefresh = {
                viewModel.loadToolchains()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 快速编译区域
        QuickCompileSection(
            isLoading = uiState.isLoading,
            onExecuteCompile = { projectPath, sourceFiles, language ->
                viewModel.executeCompile(projectPath, sourceFiles, language)
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 结果显示区域
        if (uiState.lastResult != null) {
            CompileResultSection(
                result = uiState.lastResult,
                onClear = {
                    viewModel.clearLastResult()
                }
            )
        }

        // 历史记录区域
        HistorySection(
            history = uiState.compileHistory,
            onHistoryItemClick = { history ->
                onNavigateToDetail(history.id)
            },
            onRefresh = {
                viewModel.loadCompileHistory()
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 统计信息区域
        if (uiState.statistics != null) {
            StatisticsSection(
                statistics = uiState.statistics!!,
                onRefresh = {
                    viewModel.loadCompileStatistics()
                }
            )
        }
    }
}

/**
 * 工具链状态区域
 */
@Composable
fun ToolchainStatusSection(
    toolchains: List<ToolchainInfo>,
    onInstallToolchain: (Language) -> Unit,
    onRefresh: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "工具链状态",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onRefresh) {
                    Text("刷新")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.height(200.dp)
            ) {
                items(toolchains) { toolchain ->
                    ToolchainItem(
                        toolchain = toolchain,
                        onInstall = { onInstallToolchain(toolchain.supportedLanguages.first()) }
                    )
                }
            }
        }
    }
}

/**
 * 工具链项
 */
@Composable
fun ToolchainItem(
    toolchain: ToolchainInfo,
    onInstall: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = toolchain.name,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (toolchain.isInstalled) {
                    "已安装: ${toolchain.version}"
                } else {
                    "未安装"
                },
                fontSize = 12.sp,
                color = if (toolchain.isInstalled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
            )
        }

        if (!toolchain.isInstalled) {
            Button(
                onClick = onInstall,
                modifier = Modifier.height(32.dp)
            ) {
                Text("安装", fontSize = 12.sp)
            }
        } else {
            Text(
                text = "✓",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * 快速编译区域
 */
@Composable
fun QuickCompileSection(
    isLoading: Boolean,
    onExecuteCompile: (String, List<String>, Language) -> Unit
) {
    var projectPath by remember { mutableStateOf("") }
    var sourceFiles by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf(Language.JAVA) }

    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "快速编译",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 项目路径输入
            OutlinedTextField(
                value = projectPath,
                onValueChange = { projectPath = it },
                label = { Text("项目路径") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 源文件输入
            OutlinedTextField(
                value = sourceFiles,
                onValueChange = { sourceFiles = it },
                label = { Text("源文件（逗号分隔）") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 语言选择
            LanguageSelector(
                selectedLanguage = selectedLanguage,
                onLanguageSelected = { selectedLanguage = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val files = sourceFiles.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    if (projectPath.isNotEmpty() && files.isNotEmpty()) {
                        onExecuteCompile(projectPath, files, selectedLanguage)
                    }
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("开始编译")
            }
        }
    }
}

/**
 * 语言选择器
 */
@Composable
fun LanguageSelector(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit
) {
    val languages = Language.values()
    
    Column {
        Text(
            text = "编程语言:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        languages.forEach { language ->
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedLanguage == language,
                    onClick = { onLanguageSelected(language) }
                )
                Text(
                    text = language.displayName,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

/**
 * 编译结果区域
 */
@Composable
fun CompileResultSection(
    result: CompileExecutionResult,
    onClear: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "编译结果",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (result.success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
                TextButton(onClick = onClear) {
                    Text("清除")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (result.success) {
                Text(
                    text = "✓ 编译成功",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                
                result.result?.let { compileResult ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "执行时间: ${compileResult.executionTime}ms",
                        fontSize = 12.sp
                    )
                    Text(
                        text = "缓存命中: ${if (result.fromCache) "是" else "否"}",
                        fontSize = 12.sp
                    )
                }
            } else {
                Text(
                    text = "✗ 编译失败",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
                
                result.error?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "错误: $error",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * 历史记录区域
 */
@Composable
fun HistorySection(
    history: List<CompileHistory>,
    onHistoryItemClick: (CompileHistory) -> Unit,
    onRefresh: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "编译历史",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onRefresh) {
                    Text("刷新")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (history.isEmpty()) {
                Text(
                    text = "暂无编译历史",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(200.dp)
                ) {
                    items(history.take(10)) { historyItem ->
                        HistoryItem(
                            history = historyItem,
                            onClick = { onHistoryItemClick(historyItem) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 历史记录项
 */
@Composable
fun HistoryItem(
    history: CompileHistory,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = history.task.targetLanguage.displayName,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (history.result.success) "✓" else "✗",
                    color = if (history.result.success) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                )
            }
            
            Text(
                text = history.task.projectPath,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Text(
                text = "${history.timestamp}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 统计信息区域
 */
@Composable
fun StatisticsSection(
    statistics: CompileStatistics,
    onRefresh: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "统计信息",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onRefresh) {
                    Text("刷新")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            StatItem("总任务数", statistics.totalTasks.toString())
            StatItem("成功任务", statistics.successfulTasks.toString())
            StatItem("失败任务", statistics.failedTasks.toString())
            StatItem("平均执行时间", "${statistics.averageExecutionTime.toInt()}ms")
            StatItem("缓存命中率", "${(statistics.hitRate * 100).toInt()}%")
        }
    }
}

/**
 * 统计项
 */
@Composable
fun StatItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 14.sp
        )
        Text(
            text = value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
    }
}