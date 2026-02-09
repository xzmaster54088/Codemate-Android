package com.codemate.features.compiler.presentation.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codemate.features.compiler.core.bridge.TermuxBridge
import com.codemate.features.compiler.core.manager.*
import com.codemate.features.compiler.core.parser.LogParser
import com.codemate.features.compiler.domain.entities.*
import com.codemate.features.compiler.domain.repositories.CompilerRepository
import com.codemate.features.compiler.domain.usecases.CompileUseCases
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 编译器主ViewModel
 * 管理UI状态和用户交互
 */
class CompilerViewModel(
    application: Application,
    private val repository: CompilerRepository,
    private val compileUseCases: CompileUseCases
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CompilerUiState())
    val uiState: StateFlow<CompilerUiState> = _uiState.asStateFlow()

    init {
        // 初始化时加载基础数据
        loadInitialData()
    }

    /**
     * 执行编译
     */
    fun executeCompile(
        projectPath: String,
        sourceFiles: List<String>,
        language: Language,
        priority: TaskPriority = TaskPriority.NORMAL
    ) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isLoading = true,
                    currentTask = "Starting compilation..."
                )

                val result = compileUseCases.executeCompile(
                    projectPath = projectPath,
                    sourceFiles = sourceFiles,
                    language = language,
                    priority = priority
                )

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    lastResult = result,
                    currentTask = null,
                    error = if (!result.success) result.error else null
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    currentTask = null,
                    error = "Compilation failed: ${e.message}"
                )
            }
        }
    }

    /**
     * 取消编译任务
     */
    fun cancelCompileTask(taskId: String) {
        viewModelScope.launch {
            compileUseCases.cancelCompileTask(taskId)
        }
    }

    /**
     * 加载编译历史
     */
    fun loadCompileHistory(
        projectPath: String? = null,
        language: Language? = null,
        limit: Int = 50
    ) {
        viewModelScope.launch {
            try {
                val history = repository.getCompileHistory(projectPath, language, limit)
                _uiState.value = _uiState.value.copy(
                    compileHistory = history
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load history: ${e.message}"
                )
            }
        }
    }

    /**
     * 加载编译统计
     */
    fun loadCompileStatistics() {
        viewModelScope.launch {
            try {
                val statistics = repository.getCompileStatistics()
                _uiState.value = _uiState.value.copy(
                    statistics = statistics
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load statistics: ${e.message}"
                )
            }
        }
    }

    /**
     * 加载项目统计
     */
    fun loadProjectStatistics(projectPath: String) {
        viewModelScope.launch {
            try {
                val projectStats = repository.getProjectStatistics(projectPath)
                _uiState.value = _uiState.value.copy(
                    projectStatistics = projectStats
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load project statistics: ${e.message}"
                )
            }
        }
    }

    /**
     * 安装工具链
     */
    fun installToolchain(language: Language) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isInstalling = true,
                    installationProgress = 0,
                    installationMessage = "Starting installation..."
                )

                val result = repository.installToolchain(language)

                _uiState.value = _uiState.value.copy(
                    isInstalling = false,
                    installationProgress = 100,
                    installationMessage = result.message,
                    lastInstallationResult = result
                )

                // 重新加载工具链信息
                loadToolchains()

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isInstalling = false,
                    error = "Installation failed: ${e.message}"
                )
            }
        }
    }

    /**
     * 加载工具链信息
     */
    fun loadToolchains() {
        viewModelScope.launch {
            try {
                val toolchains = repository.getAllToolchains()
                _uiState.value = _uiState.value.copy(
                    availableToolchains = toolchains
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load toolchains: ${e.message}"
                )
            }
        }
    }

    /**
     * 清理缓存
     */
    fun clearCache() {
        viewModelScope.launch {
            try {
                repository.clearCache()
                _uiState.value = _uiState.value.copy(
                    message = "Cache cleared successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to clear cache: ${e.message}"
                )
            }
        }
    }

    /**
     * 清理历史
     */
    fun clearHistory() {
        viewModelScope.launch {
            try {
                repository.clearAllHistory()
                _uiState.value = _uiState.value.copy(
                    compileHistory = emptyList(),
                    message = "History cleared successfully"
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to clear history: ${e.message}"
                )
            }
        }
    }

    /**
     * 导出数据
     */
    fun exportData(format: ExportFormat) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(
                    isExporting = true,
                    exportProgress = 0
                )

                val result = repository.exportData(format)

                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    exportProgress = 100,
                    lastExportResult = result
                )

            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isExporting = false,
                    error = "Export failed: ${e.message}"
                )
            }
        }
    }

    /**
     * 获取编译建议
     */
    fun getCompilationRecommendations(projectPath: String) {
        viewModelScope.launch {
            try {
                val recommendations = compileUseCases.getCompilationRecommendations(projectPath)
                _uiState.value = _uiState.value.copy(
                    recommendations = recommendations
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to get recommendations: ${e.message}"
                )
            }
        }
    }

    /**
     * 检查工具链更新
     */
    fun checkToolchainUpdates() {
        viewModelScope.launch {
            try {
                val updates = compileUseCases.checkToolchainUpdates()
                _uiState.value = _uiState.value.copy(
                    availableUpdates = updates
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to check updates: ${e.message}"
                )
            }
        }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * 清除消息
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    /**
     * 清除最后结果
     */
    fun clearLastResult() {
        _uiState.value = _uiState.value.copy(lastResult = null)
    }

    /**
     * 加载初始数据
     */
    private fun loadInitialData() {
        viewModelScope.launch {
            try {
                // 并行加载多个数据源
                launch { loadToolchains() }
                launch { loadCompileHistory() }
                launch { loadCompileStatistics() }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    error = "Failed to load initial data: ${e.message}"
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // 清理资源
        compileUseCases.cleanup()
    }
}

/**
 * 编译器UI状态
 */
data class CompilerUiState(
    // 加载状态
    val isLoading: Boolean = false,
    val isInstalling: Boolean = false,
    val isExporting: Boolean = false,
    val currentTask: String? = null,
    val installationProgress: Int = 0,
    val installationMessage: String? = null,
    val exportProgress: Int = 0,
    
    // 数据
    val compileHistory: List<CompileHistory> = emptyList(),
    val statistics: CompileStatistics? = null,
    val projectStatistics: ProjectStatistics? = null,
    val availableToolchains: List<ToolchainInfo> = emptyList(),
    val availableUpdates: List<ToolchainUpdate> = emptyList(),
    val recommendations: List<CompilationRecommendation> = emptyList(),
    
    // 结果
    val lastResult: CompileExecutionResult? = null,
    val lastInstallationResult: InstallationResult? = null,
    val lastExportResult: ExportResult? = null,
    
    // 错误和消息
    val error: String? = null,
    val message: String? = null
)

/**
 * 编译器UI事件
 */
sealed class CompilerUiEvent {
    data class ShowMessage(val message: String) : CompilerUiEvent()
    data class ShowError(val error: String) : CompilerUiEvent()
    data class ShowProgress(val progress: Int, val message: String) : CompilerUiEvent()
    data class NavigateToDetail(val historyId: String) : CompilerUiEvent()
    data class OpenFilePicker(val callback: (List<String>) -> Unit) : CompilerUiEvent()
    object ShowInstallationDialog : CompilerUiEvent()
    object ShowExportDialog : CompilerUiEvent()
    object ShowClearDataDialog : CompilerUiEvent()
}

/**
 * 编译器Action
 */
sealed class CompilerAction {
    data class ExecuteCompile(
        val projectPath: String,
        val sourceFiles: List<String>,
        val language: Language,
        val priority: TaskPriority = TaskPriority.NORMAL
    ) : CompilerAction()
    
    data class CancelCompile(val taskId: String) : CompilerAction()
    data class LoadHistory(val projectPath: String? = null, val language: Language? = null) : CompilerAction()
    object LoadStatistics : CompilerAction()
    data class LoadProjectStatistics(val projectPath: String) : CompilerAction()
    data class InstallToolchain(val language: Language) : CompilerAction()
    object ClearCache : CompilerAction()
    object ClearHistory : CompilerAction()
    data class ExportData(val format: ExportFormat) : CompilerAction()
    data class GetRecommendations(val projectPath: String) : CompilerAction()
    object CheckUpdates : CompilerAction()
    object ClearError : CompilerAction()
    object ClearMessage : CompilerAction()
    object ClearLastResult : CompilerAction()
}