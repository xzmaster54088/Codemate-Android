package com.codemate.features.github.di

import com.codemate.features.github.data.remote.GitCommandExecutor
import com.codemate.features.github.data.remote.GitHubAPIClient
import com.codemate.features.github.data.repository.*
import com.codemate.features.github.domain.repository.*
import com.codemate.features.github.domain.usecase.*
import com.codemate.features.github.presentation.viewmodel.GitHubSmartClientViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * GitHub智能客户端依赖注入模块
 * 统一管理所有组件的创建和依赖关系
 */
object GitHubSmartClientModule {
    
    /**
     * 创建Koin模块
     */
    fun createModule() = module {
        
        // 远程数据源
        single<GitHubAPIClient> {
            GitHubAPIClient(
                baseUrl = "https://api.github.com",
                timeout = 30_000,
                headers = mapOf(
                    "Accept" to "application/vnd.github.v3+json",
                    "User-Agent" to "CodeMate-Mobile-GitHub-Client/1.0"
                )
            )
        }
        
        single<GitCommandExecutor> {
            GitCommandExecutor()
        }
        
        // Repository接口实现
        single<GitRepository> {
            GitRepositoryImpl(get())
        }
        
        single<GitHubRepository> {
            GitHubRepositoryImpl(get())
        }
        
        single<ClassificationRepository> {
            ClassificationRepositoryImpl()
        }
        
        single<CollaborationRepository> {
            CollaborationRepositoryImpl()
        }
        
        single<QualityRepository> {
            QualityRepositoryImpl()
        }
        
        single<DeploymentRepository> {
            DeploymentRepositoryImpl()
        }
        
        single<ChangelogRepository> {
            ChangelogRepositoryImpl()
        }
        
        // UseCase类
        single<GitOperationsUseCase> {
            GitOperationsUseCase(
                gitRepository = get(),
                githubRepository = get()
            )
        }
        
        single<CommitClassificationUseCase> {
            CommitClassificationUseCase(
                classificationRepository = get(),
                gitRepository = get()
            )
        }
        
        single<ChangelogGenerationUseCase> {
            ChangelogGenerationUseCase(
                changelogRepository = get(),
                classificationRepository = get(),
                githubRepository = get(),
                gitRepository = get()
            )
        }
        
        single<CollaborationUseCase> {
            CollaborationUseCase(
                collaborationRepository = get(),
                githubRepository = get(),
                gitRepository = get()
            )
        }
        
        single<CodeQualityUseCase> {
            CodeQualityUseCase(
                qualityRepository = get(),
                githubRepository = get(),
                gitRepository = get()
            )
        }
        
        // ViewModel
        viewModel<GitHubSmartClientViewModel> {
            GitHubSmartClientViewModel()
        }
    }
}

/**
 * 手动依赖注入辅助类
 * 当不使用Koin时可以使用此类进行依赖管理
 */
object GitHubSmartClientDI {
    
    @Volatile
    private var INSTANCE: GitHubSmartClientDI? = null
    
    private val gitCommandExecutor: GitCommandExecutor by lazy { GitCommandExecutor() }
    private val githubAPIClient: GitHubAPIClient by lazy { GitHubAPIClient() }
    
    // Repository实例
    private val gitRepository: GitRepository by lazy { GitRepositoryImpl(gitCommandExecutor) }
    private val githubRepository: GitHubRepository by lazy { GitHubRepositoryImpl(githubAPIClient) }
    private val classificationRepository: ClassificationRepository by lazy { ClassificationRepositoryImpl() }
    private val collaborationRepository: CollaborationRepository by lazy { CollaborationRepositoryImpl() }
    private val qualityRepository: QualityRepository by lazy { QualityRepositoryImpl() }
    private val deploymentRepository: DeploymentRepository by lazy { DeploymentRepositoryImpl() }
    private val changelogRepository: ChangelogRepository by lazy { ChangelogRepositoryImpl() }
    
    // UseCase实例
    private val gitOperationsUseCase: GitOperationsUseCase by lazy {
        GitOperationsUseCase(gitRepository, githubRepository)
    }
    
    private val commitClassificationUseCase: CommitClassificationUseCase by lazy {
        CommitClassificationUseCase(classificationRepository, gitRepository)
    }
    
    private val changelogGenerationUseCase: ChangelogGenerationUseCase by lazy {
        ChangelogGenerationUseCase(changelogRepository, classificationRepository, githubRepository, gitRepository)
    }
    
    private val collaborationUseCase: CollaborationUseCase by lazy {
        CollaborationUseCase(collaborationRepository, githubRepository, gitRepository)
    }
    
    private val codeQualityUseCase: CodeQualityUseCase by lazy {
        CodeQualityUseCase(qualityRepository, githubRepository, gitRepository)
    }
    
    fun getInstance(): GitHubSmartClientDI {
        return INSTANCE ?: synchronized(this) {
            INSTANCE ?: GitHubSmartClientDI.also { INSTANCE = it }
        }
    }
    
    // Repository获取方法
    fun getGitRepository(): GitRepository = gitRepository
    fun getGitHubRepository(): GitHubRepository = githubRepository
    fun getClassificationRepository(): ClassificationRepository = classificationRepository
    fun getCollaborationRepository(): CollaborationRepository = collaborationRepository
    fun getQualityRepository(): QualityRepository = qualityRepository
    fun getDeploymentRepository(): DeploymentRepository = deploymentRepository
    fun getChangelogRepository(): ChangelogRepository = changelogRepository
    
    // UseCase获取方法
    fun getGitOperationsUseCase(): GitOperationsUseCase = gitOperationsUseCase
    fun getCommitClassificationUseCase(): CommitClassificationUseCase = commitClassificationUseCase
    fun getChangelogGenerationUseCase(): ChangelogGenerationUseCase = changelogGenerationUseCase
    fun getCollaborationUseCase(): CollaborationUseCase = collaborationUseCase
    fun getCodeQualityUseCase(): CodeQualityUseCase = codeQualityUseCase
    
    // 创建ViewModel的工厂方法
    fun createViewModel(): GitHubSmartClientViewModel {
        return GitHubSmartClientViewModel()
    }
    
    // 清理资源
    fun cleanup() {
        INSTANCE = null
        // 这里可以添加其他清理逻辑
    }
}

/**
 * 依赖注入配置类
 */
object GitHubSmartClientConfig {
    
    // API配置
    const val BASE_URL = "https://api.github.com"
    const val TIMEOUT = 30000L
    
    // 默认配置
    const val DEFAULT_BRANCH = "main"
    const val MAX_PARTICIPANTS = 10
    const val SESSION_TIMEOUT_HOURS = 24
    
    // 质量阈值
    const val MIN_TEST_COVERAGE = 70.0f
    const val MAX_CYCLOMATIC_COMPLEXITY = 10.0f
    const val MIN_CODE_QUALITY_SCORE = 60.0f
    
    // 缓存配置
    const val CACHE_SIZE_MB = 50
    const val CACHE_EXPIRY_HOURS = 24
    
    // 分页配置
    const val DEFAULT_PAGE_SIZE = 30
    const val MAX_PAGE_SIZE = 100
    
    // 重试配置
    const val MAX_RETRIES = 3
    const val RETRY_DELAY_MS = 1000L
}

/**
 * 组件配置
 */
data class GitHubSmartClientComponentConfig(
    val enableCache: Boolean = true,
    val enableOffline: Boolean = true,
    val enableAutoSync: Boolean = true,
    val enableNotifications: Boolean = true,
    val logLevel: LogLevel = LogLevel.INFO
)

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}

/**
 * 组件管理器
 */
object GitHubSmartClientComponentManager {
    
    @Volatile
    private var initialized = false
    
    fun initialize(config: GitHubSmartClientComponentConfig = GitHubSmartClientComponentConfig()) {
        if (initialized) return
        
        // 初始化各种组件
        initializeRepositories()
        initializeUseCases()
        initializeViewModels()
        
        initialized = true
    }
    
    private fun initializeRepositories() {
        // 初始化Repository组件
        // 这里可以添加具体的初始化逻辑
    }
    
    private fun initializeUseCases() {
        // 初始化UseCase组件
        // 这里可以添加具体的初始化逻辑
    }
    
    private fun initializeViewModels() {
        // 初始化ViewModel组件
        // 这里可以添加具体的初始化逻辑
    }
    
    fun isInitialized(): Boolean = initialized
    
    fun reset() {
        initialized = false
        GitHubSmartClientDI.cleanup()
    }
}

/**
 * 测试用的依赖注入辅助类
 */
object TestGitHubSmartClientDI {
    
    fun createMockGitRepository(): GitRepository {
        return object : GitRepository {
            override suspend fun initializeRepository(path: String, config: GitConfig): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Mock repository initialized", null, 0, "mock command"))
            }
            
            override suspend fun cloneRepository(url: String, path: String, branch: String?): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Mock repository cloned", null, 0, "mock command"))
            }
            
            // 其他方法返回模拟数据...
            override suspend fun addFiles(path: String, files: List<String>): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Files added", null, 0, "mock command"))
            }
            
            // 实现所有接口方法...
            override suspend fun commitChanges(path: String, message: String, author: String?): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Changes committed", null, 0, "mock command"))
            }
            
            override suspend fun pushChanges(path: String, branch: String?, force: Boolean): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Changes pushed", null, 0, "mock command"))
            }
            
            override suspend fun pullChanges(path: String, branch: String?, rebase: Boolean): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Changes pulled", null, 0, "mock command"))
            }
            
            override suspend fun getStatus(path: String): Result<GitStatus> {
                return Result.success(GitStatus("main", 0, 0, emptyList(), emptyList(), emptyList(), emptyList(), true))
            }
            
            // 其他方法省略...
            override suspend fun createBranch(path: String, branchName: String, checkout: Boolean): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Branch created", null, 0, "mock command"))
            }
            
            override suspend fun checkoutBranch(path: String, branchName: String, create: Boolean): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Branch checked out", null, 0, "mock command"))
            }
            
            override suspend fun mergeBranch(path: String, sourceBranch: String, targetBranch: String?): Result<MergeResult> {
                return Result.success(MergeResult(true, "mock_sha", null, "Merge completed"))
            }
            
            override suspend fun getBranches(path: String): Result<List<GitBranch>> {
                return Result.success(emptyList())
            }
            
            override suspend fun getCommitHistory(path: String, branch: String?, since: Date?, until: Date?, maxCount: Int): Result<List<GitCommit>> {
                return Result.success(emptyList())
            }
            
            override suspend fun getCommit(path: String, sha: String): Result<GitCommit> {
                return Result.failure(Exception("Mock commit not found"))
            }
            
            override suspend fun getDiff(path: String, from: String?, to: String?, file: String?): Result<GitDiff> {
                return Result.success(GitDiff(0, 0, emptyList()))
            }
            
            override suspend fun resetChanges(path: String, mode: ResetMode, commit: String?): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Changes reset", null, 0, "mock command"))
            }
            
            override suspend fun stashChanges(path: String, message: String?): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Changes stashed", null, 0, "mock command"))
            }
            
            override suspend fun applyStash(path: String, stashRef: String?): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Stash applied", null, 0, "mock command"))
            }
            
            override suspend fun createTag(path: String, tagName: String, message: String?, commit: String?): Result<GitCommandResult> {
                return Result.success(GitCommandResult(true, "Tag created", null, 0, "mock command"))
            }
            
            override suspend fun getTags(path: String): Result<List<GitTag>> {
                return Result.success(emptyList())
            }
        }
    }
    
    fun createMockClassificationRepository(): ClassificationRepository {
        return object : ClassificationRepository {
            override suspend fun classifyCommit(message: String): Result<CommitClassification> {
                return Result.success(
                    CommitClassification(
                        type = CommitType.FEATURE,
                        scope = null,
                        description = message,
                        breaking = false,
                        confidence = 0.9f,
                        features = mapOf("mock" to 1.0f)
                    )
                )
            }
            
            override suspend fun classifyCommits(messages: List<String>): Result<List<CommitClassification>> {
                val classifications = messages.map { message ->
                    CommitClassification(
                        type = CommitType.FEATURE,
                        scope = null,
                        description = message,
                        breaking = false,
                        confidence = 0.9f,
                        features = mapOf("mock" to 1.0f)
                    )
                }
                return Result.success(classifications)
            }
            
            // 其他方法返回默认实现...
            override suspend fun trainModel(trainingData: List<TrainingData>): Result<ModelMetrics> {
                return Result.success(
                    ModelMetrics(0.9f, 0.9f, 0.9f, 0.9f, emptyMap())
                )
            }
            
            override suspend fun evaluateModel(testData: List<TrainingData>): Result<ModelMetrics> {
                return Result.success(
                    ModelMetrics(0.9f, 0.9f, 0.9f, 0.9f, emptyMap())
                )
            }
            
            override suspend fun getModelInfo(): Result<ModelInfo> {
                return Result.success(
                    ModelInfo("Mock Model", "1.0.0", Date(), Date(), 0.9f, 1000, 10000)
                )
            }
            
            override suspend fun updateModelConfig(config: ModelConfig): Result<Unit> {
                return Result.success(Unit)
            }
        }
    }
}