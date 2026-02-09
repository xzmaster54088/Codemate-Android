package com.codemate.features.github.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.codemate.features.github.data.remote.GitCommandExecutor
import com.codemate.features.github.data.remote.GitHubAPIClient
import com.codemate.features.github.data.repository.*
import com.codemate.features.github.domain.model.*
import com.codemate.features.github.domain.usecase.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * GitHub智能客户端主ViewModel
 * 整合所有功能模块的状态管理
 */
class GitHubSmartClientViewModel : ViewModel() {
    
    // Repository实例
    private val gitCommandExecutor = GitCommandExecutor()
    private val githubAPIClient = GitHubAPIClient()
    
    private val gitRepository = GitRepositoryImpl(gitCommandExecutor)
    private val githubRepository = GitHubRepositoryImpl(githubAPIClient)
    private val classificationRepository = ClassificationRepositoryImpl()
    private val collaborationRepository = CollaborationRepositoryImpl()
    private val qualityRepository = QualityRepositoryImpl()
    private val deploymentRepository = DeploymentRepositoryImpl()
    private val changelogRepository = ChangelogRepositoryImpl()
    
    // UseCase实例
    private val gitOperationsUseCase = GitOperationsUseCase(gitRepository, githubRepository)
    private val commitClassificationUseCase = CommitClassificationUseCase(classificationRepository, gitRepository)
    private val changelogGenerationUseCase = ChangelogGenerationUseCase(
        changelogRepository,
        classificationRepository,
        githubRepository,
        gitRepository
    )
    private val collaborationUseCase = CollaborationUseCase(
        collaborationRepository,
        githubRepository,
        gitRepository
    )
    private val codeQualityUseCase = CodeQualityUseCase(
        qualityRepository,
        githubRepository,
        gitRepository
    )
    
    // UI状态
    private val _uiState = MutableStateFlow(GitHubSmartClientUiState())
    val uiState: StateFlow<GitHubSmartClientUiState> = _uiState.asStateFlow()
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    private val _repositories = MutableStateFlow<List<GitRepository>>(emptyList())
    val repositories: StateFlow<List<GitRepository>> = _repositories.asStateFlow()
    
    private val _issues = MutableStateFlow<List<GitHubIssue>>(emptyList())
    val issues: StateFlow<List<GitHubIssue>> = _issues.asStateFlow()
    
    private val _pullRequests = MutableStateFlow<List<GitHubPR>>(emptyList())
    val pullRequests: StateFlow<List<GitHubPR>> = _pullRequests.asStateFlow()
    
    private val _collaborationSessions = MutableStateFlow<List<CollaborationSession>>(emptyList())
    val collaborationSessions: StateFlow<List<CollaborationSession>> = _collaborationSessions.asStateFlow()
    
    private val _codeQualityAssessment = MutableStateFlow<CodeQualityAssessment?>(null)
    val codeQualityAssessment: StateFlow<CodeQualityAssessment?> = _codeQualityAssessment.asStateFlow()
    
    private val _deploymentExecutions = MutableStateFlow<List<DeploymentExecution>>(emptyList())
    val deploymentExecutions: StateFlow<List<DeploymentExecution>> = _deploymentExecutions.asStateFlow()
    
    private val _changelogEntries = MutableStateFlow<List<ChangelogEntry>>(emptyList())
    val changelogEntries: StateFlow<List<ChangelogEntry>> = _changelogEntries.asStateFlow()
    
    /**
     * 初始化
     */
    fun initialize() {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                // 获取当前用户信息
                loadCurrentUser()
                
                // 获取仓库列表
                loadRepositories()
                
                updateUiState { copy(isLoading = false) }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "Unknown error occurred"
                    )
                }
            }
        }
    }
    
    /**
     * 克隆仓库
     */
    fun cloneRepository(
        repositoryUrl: String,
        localPath: String,
        branch: String? = null,
        config: GitConfig
    ) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                val result = gitOperationsUseCase.cloneAndInitialize(
                    repositoryUrl = repositoryUrl,
                    localPath = localPath,
                    branch = branch,
                    config = config
                )
                
                if (result.isSuccess) {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            successMessage = "Repository cloned successfully"
                        )
                    }
                    // 刷新仓库列表
                    loadRepositories()
                } else {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Clone failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "Clone failed"
                    )
                }
            }
        }
    }
    
    /**
     * 提交并推送更改
     */
    fun commitAndPushChanges(
        localPath: String,
        files: List<String>,
        message: String,
        branch: String? = null
    ) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                val result = gitOperationsUseCase.commitAndPush(
                    localPath = localPath,
                    files = files,
                    message = message,
                    branch = branch
                )
                
                if (result.isSuccess) {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            successMessage = "Changes committed and pushed successfully"
                        )
                    }
                } else {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Commit failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "Commit failed"
                    )
                }
            }
        }
    }
    
    /**
     * 分类提交消息
     */
    fun classifyCommit(message: String) {
        viewModelScope.launch {
            try {
                val result = commitClassificationUseCase.classifyCommitMessage(message)
                
                if (result.isSuccess) {
                    updateUiState { 
                        copy(
                            latestClassification = result.getOrNull(),
                            successMessage = "Commit classified successfully"
                        )
                    }
                } else {
                    updateUiState { 
                        copy(
                            error = result.exceptionOrNull()?.message ?: "Classification failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        error = e.message ?: "Classification failed"
                    )
                }
            }
        }
    }
    
    /**
     * 分析仓库提交历史
     */
    fun analyzeRepositoryCommits(
        repositoryPath: String,
        branch: String? = null
    ) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                val result = commitClassificationUseCase.analyzeRepositoryCommits(
                    repositoryPath = repositoryPath,
                    branch = branch
                )
                
                if (result.isSuccess) {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            commitAnalysis = result.getOrNull(),
                            successMessage = "Repository commits analyzed successfully"
                        )
                    }
                } else {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Analysis failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "Analysis failed"
                    )
                }
            }
        }
    }
    
    /**
     * 生成CHANGELOG
     */
    fun generateChangelog(
        repositoryOwner: String,
        repositoryName: String,
        fromVersion: Version? = null,
        toVersion: Version? = null
    ) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                val result = changelogGenerationUseCase.generateChangelog(
                    repositoryOwner = repositoryOwner,
                    repositoryName = repositoryName,
                    fromVersion = fromVersion,
                    toVersion = toVersion
                )
                
                if (result.isSuccess) {
                    val entry = result.getOrNull()!!
                    val updatedEntries = _changelogEntries.value + entry
                    _changelogEntries.value = updatedEntries
                    
                    updateUiState { 
                        copy(
                            isLoading = false,
                            latestChangelog = entry,
                            successMessage = "CHANGELOG generated successfully"
                        )
                    }
                } else {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "CHANGELOG generation failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "CHANGELOG generation failed"
                    )
                }
            }
        }
    }
    
    /**
     * 创建协作会话
     */
    fun createCollaborationSession(
        name: String,
        description: String?,
        owner: User,
        repository: String,
        branch: String,
        files: List<String>
    ) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                val result = collaborationUseCase.createCollaborationSession(
                    name = name,
                    description = description,
                    owner = owner,
                    repository = repository,
                    branch = branch,
                    files = files
                )
                
                if (result.isSuccess) {
                    val session = result.getOrNull()!!
                    val updatedSessions = _collaborationSessions.value + session
                    _collaborationSessions.value = updatedSessions
                    
                    updateUiState { 
                        copy(
                            isLoading = false,
                            latestCollaborationSession = session,
                            successMessage = "Collaboration session created successfully"
                        )
                    }
                } else {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Session creation failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "Session creation failed"
                    )
                }
            }
        }
    }
    
    /**
     * 创建分享链接
     */
    fun createShareLink(
        repository: String,
        branch: String,
        files: List<String>,
        permissions: SharePermissions
    ) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                val result = collaborationUseCase.createShareLink(
                    repository = repository,
                    branch = branch,
                    files = files,
                    permissions = permissions
                )
                
                if (result.isSuccess) {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            latestShareLink = result.getOrNull(),
                            successMessage = "Share link created successfully"
                        )
                    }
                } else {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Share link creation failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "Share link creation failed"
                    )
                }
            }
        }
    }
    
    /**
     * 分析代码质量
     */
    fun analyzeCodeQuality(
        repositoryOwner: String,
        repositoryName: String,
        branch: String = "main"
    ) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                val result = codeQualityUseCase.analyzeCodeQuality(
                    repositoryOwner = repositoryOwner,
                    repositoryName = repositoryName,
                    branch = branch
                )
                
                if (result.isSuccess) {
                    _codeQualityAssessment.value = result.getOrNull()
                    
                    updateUiState { 
                        copy(
                            isLoading = false,
                            successMessage = "Code quality analyzed successfully"
                        )
                    }
                } else {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Quality analysis failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "Quality analysis failed"
                    )
                }
            }
        }
    }
    
    /**
     * 执行安全扫描
     */
    fun performSecurityScan(
        repositoryOwner: String,
        repositoryName: String,
        branch: String = "main"
    ) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                val result = codeQualityUseCase.performSecurityScan(
                    repositoryOwner = repositoryOwner,
                    repositoryName = repositoryName,
                    branch = branch
                )
                
                if (result.isSuccess) {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            latestSecurityScan = result.getOrNull(),
                            successMessage = "Security scan completed successfully"
                        )
                    }
                } else {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Security scan failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "Security scan failed"
                    )
                }
            }
        }
    }
    
    /**
     * 创建Issue
     */
    fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String?
    ) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                val result = githubRepository.createIssue(
                    owner = owner,
                    repo = repo,
                    title = title,
                    body = body
                )
                
                if (result.isSuccess) {
                    val issue = result.getOrNull()!!
                    val updatedIssues = _issues.value + issue
                    _issues.value = updatedIssues
                    
                    updateUiState { 
                        copy(
                            isLoading = false,
                            successMessage = "Issue created successfully"
                        )
                    }
                } else {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "Issue creation failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "Issue creation failed"
                    )
                }
            }
        }
    }
    
    /**
     * 创建Pull Request
     */
    fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String?
    ) {
        viewModelScope.launch {
            updateUiState { copy(isLoading = true) }
            
            try {
                val result = githubRepository.createPullRequest(
                    owner = owner,
                    repo = repo,
                    title = title,
                    head = head,
                    base = base,
                    body = body
                )
                
                if (result.isSuccess) {
                    val pr = result.getOrNull()!!
                    val updatedPRs = _pullRequests.value + pr
                    _pullRequests.value = updatedPRs
                    
                    updateUiState { 
                        copy(
                            isLoading = false,
                            successMessage = "Pull Request created successfully"
                        )
                    }
                } else {
                    updateUiState { 
                        copy(
                            isLoading = false,
                            error = result.exceptionOrNull()?.message ?: "PR creation failed"
                        )
                    }
                }
            } catch (e: Exception) {
                updateUiState { 
                    copy(
                        isLoading = false,
                        error = e.message ?: "PR creation failed"
                    )
                }
            }
        }
    }
    
    /**
     * 清除错误消息
     */
    fun clearError() {
        updateUiState { copy(error = null) }
    }
    
    /**
     * 清除成功消息
     */
    fun clearSuccessMessage() {
        updateUiState { copy(successMessage = null) }
    }
    
    private suspend fun loadCurrentUser() {
        try {
            val result = githubRepository.getCurrentUser()
            if (result.isSuccess) {
                _currentUser.value = result.getOrNull()
            }
        } catch (e: Exception) {
            // 静默失败，不影响应用启动
        }
    }
    
    private suspend fun loadRepositories() {
        try {
            val result = githubRepository.getUserRepositories()
            if (result.isSuccess) {
                _repositories.value = result.getOrNull()!!.data
            }
        } catch (e: Exception) {
            // 静默失败，不影响应用启动
        }
    }
    
    private fun updateUiState(update: (GitHubSmartClientUiState) -> GitHubSmartClientUiState) {
        _uiState.value = update(_uiState.value)
    }
}

/**
 * GitHub智能客户端UI状态
 */
data class GitHubSmartClientUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    
    // 功能特定状态
    val latestClassification: CommitClassification? = null,
    val commitAnalysis: CommitAnalysisResult? = null,
    val latestChangelog: ChangelogEntry? = null,
    val latestCollaborationSession: CollaborationSession? = null,
    val latestShareLink: ShareLink? = null,
    val latestSecurityScan: SecurityScanResult? = null
)

/**
 * ViewModel工厂
 */
class GitHubSmartClientViewModelFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GitHubSmartClientViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GitHubSmartClientViewModel() as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}