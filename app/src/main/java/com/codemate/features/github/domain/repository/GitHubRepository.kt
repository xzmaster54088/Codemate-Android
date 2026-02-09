package com.codemate.features.github.domain.repository

import com.codemate.features.github.domain.model.*
import kotlinx.coroutines.flow.Flow

/**
 * Git操作Repository接口
 * 定义Git命令执行的核心业务逻辑
 */
interface GitRepository {
    
    /**
     * 初始化Git仓库
     */
    suspend fun initializeRepository(path: String, config: GitConfig): Result<GitCommandResult>
    
    /**
     * 克隆仓库
     */
    suspend fun cloneRepository(url: String, path: String, branch: String? = null): Result<GitCommandResult>
    
    /**
     * 添加文件到暂存区
     */
    suspend fun addFiles(path: String, files: List<String>): Result<GitCommandResult>
    
    /**
     * 提交更改
     */
    suspend fun commitChanges(path: String, message: String, author: String? = null): Result<GitCommandResult>
    
    /**
     * 推送更改
     */
    suspend fun pushChanges(path: String, branch: String? = null, force: Boolean = false): Result<GitCommandResult>
    
    /**
     * 拉取更改
     */
    suspend fun pullChanges(path: String, branch: String? = null, rebase: Boolean = false): Result<GitCommandResult>
    
    /**
     * 获取仓库状态
     */
    suspend fun getStatus(path: String): Result<GitStatus>
    
    /**
     * 创建分支
     */
    suspend fun createBranch(path: String, branchName: String, checkout: Boolean = true): Result<GitCommandResult>
    
    /**
     * 切换分支
     */
    suspend fun checkoutBranch(path: String, branchName: String, create: Boolean = false): Result<GitCommandResult>
    
    /**
     * 合并分支
     */
    suspend fun mergeBranch(path: String, sourceBranch: String, targetBranch: String? = null): Result<MergeResult>
    
    /**
     * 获取分支列表
     */
    suspend fun getBranches(path: String): Result<List<GitBranch>>
    
    /**
     * 获取提交历史
     */
    suspend fun getCommitHistory(
        path: String,
        branch: String? = null,
        since: Date? = null,
        until: Date? = null,
        maxCount: Int = 50
    ): Result<List<GitCommit>>
    
    /**
     * 获取特定提交
     */
    suspend fun getCommit(path: String, sha: String): Result<GitCommit>
    
    /**
     * 获取差异
     */
    suspend fun getDiff(
        path: String,
        from: String? = null,
        to: String? = null,
        file: String? = null
    ): Result<GitDiff>
    
    /**
     * 重置更改
     */
    suspend fun resetChanges(path: String, mode: ResetMode, commit: String? = null): Result<GitCommandResult>
    
    /**
     * 储藏更改
     */
    suspend fun stashChanges(path: String, message: String? = null): Result<GitCommandResult>
    
    /**
     * 应用储藏
     */
    suspend fun applyStash(path: String, stashRef: String? = null): Result<GitCommandResult>
    
    /**
     * 创建标签
     */
    suspend fun createTag(path: String, tagName: String, message: String? = null, commit: String? = null): Result<GitCommandResult>
    
    /**
     * 获取标签列表
     */
    suspend fun getTags(path: String): Result<List<GitTag>>
}

/**
 * GitHub API Repository接口
 * 定义GitHub REST API交互的核心业务逻辑
 */
interface GitHubRepository {
    
    /**
     * 获取用户仓库列表
     */
    suspend fun getUserRepositories(
        username: String? = null,
        type: RepositoryType = RepositoryType.OWNER,
        sort: RepositorySort = RepositorySort.UPDATED,
        direction: SortDirection = SortDirection.DESC,
        page: Int = 1,
        perPage: Int = 30
    ): Result<PaginatedResponse<GitRepository>>
    
    /**
     * 获取仓库详情
     */
    suspend fun getRepository(owner: String, repo: String): Result<GitRepository>
    
    /**
     * 创建仓库
     */
    suspend fun createRepository(
        name: String,
        description: String? = null,
        private: Boolean = false,
        hasIssues: Boolean = true,
        hasProjects: Boolean = true,
        hasWiki: Boolean = true
    ): Result<GitRepository>
    
    /**
     * 更新仓库
     */
    suspend fun updateRepository(
        owner: String,
        repo: String,
        name: String? = null,
        description: String? = null,
        private: Boolean? = null
    ): Result<GitRepository>
    
    /**
     * 删除仓库
     */
    suspend fun deleteRepository(owner: String, repo: String): Result<Unit>
    
    /**
     * Fork仓库
     */
    suspend fun forkRepository(owner: String, repo: String, organization: String? = null): Result<GitRepository>
    
    /**
     * 获取Issues列表
     */
    suspend fun getIssues(
        owner: String,
        repo: String,
        state: IssueState = IssueState.OPEN,
        labels: List<String>? = null,
        sort: IssueSort = IssueSort.CREATED,
        direction: SortDirection = SortDirection.DESC,
        since: Date? = null,
        page: Int = 1,
        perPage: Int = 30
    ): Result<PaginatedResponse<GitHubIssue>>
    
    /**
     * 创建Issue
     */
    suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String? = null,
        assignees: List<String>? = null,
        milestone: Int? = null,
        labels: List<String>? = null
    ): Result<GitHubIssue>
    
    /**
     * 更新Issue
     */
    suspend fun updateIssue(
        owner: String,
        repo: String,
        number: Int,
        title: String? = null,
        body: String? = null,
        state: IssueState? = null,
        labels: List<String>? = null
    ): Result<GitHubIssue>
    
    /**
     * 获取Pull Requests列表
     */
    suspend fun getPullRequests(
        owner: String,
        repo: String,
        state: PRState = PRState.OPEN,
        head: String? = null,
        base: String? = null,
        sort: PRSort = PRSort.CREATED,
        direction: SortDirection = SortDirection.DESC,
        page: Int = 1,
        perPage: Int = 30
    ): Result<PaginatedResponse<GitHubPR>>
    
    /**
     * 创建Pull Request
     */
    suspend fun createPullRequest(
        owner: String,
        repo: String,
        title: String,
        head: String,
        base: String,
        body: String? = null,
        draft: Boolean = false
    ): Result<GitHubPR>
    
    /**
     * 合并Pull Request
     */
    suspend fun mergePullRequest(
        owner: String,
        repo: String,
        number: Int,
        commitTitle: String? = null,
        commitMessage: String? = null,
        mergeMethod: MergeMethod = MergeMethod.MERGE
    ): Result<MergeResult>
    
    /**
     * 获取PR审查列表
     */
    suspend fun getPRReviews(
        owner: String,
        repo: String,
        number: Int
    ): Result<List<PRReview>>
    
    /**
     * 创建PR审查
     */
    suspend fun createPRReview(
        owner: String,
        repo: String,
        number: Int,
        body: String,
        event: ReviewEvent
    ): Result<PRReview>
    
    /**
     * 搜索仓库
     */
    suspend fun searchRepositories(
        query: String,
        sort: SearchSort? = null,
        order: SearchOrder = SearchOrder.DESC,
        page: Int = 1,
        perPage: 30
    ): Result<SearchResponse<GitRepository>>
    
    /**
     * 获取用户信息
     */
    suspend fun getUser(username: String): Result<User>
    
    /**
     * 获取当前用户信息
     */
    suspend fun getCurrentUser(): Result<User>
    
    /**
     * 获取速率限制信息
     */
    suspend fun getRateLimit(): Result<RateLimit>
    
    /**
     * 创建Webhook
     */
    suspend fun createWebhook(
        owner: String,
        repo: String,
        config: WebhookConfig
    ): Result<Webhook>
    
    /**
     * 获取Webhook列表
     */
    suspend fun getWebhooks(owner: String, repo: String): Result<List<Webhook>>
    
    /**
     * 删除Webhook
     */
    suspend fun deleteWebhook(owner: String, repo: String, hookId: Long): Result<Unit>
    
    /**
     * 创建发布
     */
    suspend fun createRelease(
        owner: String,
        repo: String,
        tagName: String,
        targetCommitish: String? = null,
        name: String? = null,
        body: String? = null,
        draft: Boolean = false,
        prerelease: Boolean = false
    ): Result<ReleaseInfo>
    
    /**
     * 获取发布列表
     */
    suspend fun getReleases(
        owner: String,
        repo: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<PaginatedResponse<ReleaseInfo>>
    
    /**
     * 获取Actions工作流列表
     */
    suspend fun getWorkflows(
        owner: String,
        repo: String,
        page: Int = 1,
        perPage: Int = 30
    ): Result<PaginatedResponse<WorkflowInfo>>
    
    /**
     * 触发工作流
     */
    suspend fun triggerWorkflow(
        owner: String,
        repo: String,
        workflowId: String,
        ref: String,
        inputs: Map<String, String> = emptyMap()
    ): Result<WorkflowRun>
    
    /**
     * 获取工作流运行列表
     */
    suspend fun getWorkflowRuns(
        owner: String,
        repo: String,
        workflowId: String? = null,
        branch: String? = null,
        event: WorkflowEvent? = null,
        status: WorkflowRunStatus? = null,
        page: Int = 1,
        perPage: Int = 30
    ): Result<PaginatedResponse<WorkflowRun>>
}

/**
 * 智能分类Repository接口
 * 定义机器学习算法相关的业务逻辑
 */
interface ClassificationRepository {
    
    /**
     * 分类提交消息
     */
    suspend fun classifyCommit(message: String): Result<CommitClassification>
    
    /**
     * 批量分类提交消息
     */
    suspend fun classifyCommits(messages: List<String>): Result<List<CommitClassification>>
    
    /**
     * 训练分类模型
     */
    suspend fun trainModel(trainingData: List<TrainingData>): Result<ModelMetrics>
    
    /**
     * 评估模型性能
     */
    suspend fun evaluateModel(testData: List<TrainingData>): Result<ModelMetrics>
    
    /**
     * 获取模型信息
     */
    suspend fun getModelInfo(): Result<ModelInfo>
    
    /**
     * 更新模型配置
     */
    suspend fun updateModelConfig(config: ModelConfig): Result<Unit>
}

/**
 * 协作Repository接口
 * 定义实时协作和代码分享相关的业务逻辑
 */
interface CollaborationRepository {
    
    /**
     * 创建协作会话
     */
    suspend fun createSession(session: CollaborationSession): Result<CollaborationSession>
    
    /**
     * 加入协作会话
     */
    suspend fun joinSession(sessionId: String, user: User): Result<CollaborationSession>
    
    /**
     * 离开协作会话
     */
    suspend fun leaveSession(sessionId: String, userId: String): Result<Unit>
    
    /**
     * 更新会话状态
     */
    suspend fun updateSession(sessionId: String, updates: SessionUpdate): Result<CollaborationSession>
    
    /**
     * 获取活跃会话列表
     */
    suspend fun getActiveSessions(userId: String): Result<List<CollaborationSession>>
    
    /**
     * 创建分享链接
     */
    suspend fun createShareLink(
        repository: String,
        branch: String,
        files: List<String>,
        permissions: SharePermissions,
        expiresAt: Date
    ): Result<ShareLink>
    
    /**
     * 通过分享链接访问代码
     */
    suspend fun accessShareLink(token: String): Result<ShareLinkAccess>
    
    /**
     * 记录活动
     */
    suspend fun recordActivity(activity: SessionActivity): Result<Unit>
    
    /**
     * 获取活动历史
     */
    suspend fun getActivityHistory(sessionId: String, limit: Int = 50): Result<List<SessionActivity>>
    
    /**
     * 实时活动流
     */
    fun getActivityStream(sessionId: String): Flow<SessionActivity>
}

/**
 * 质量检查Repository接口
 * 定义代码质量分析和审查相关的业务逻辑
 */
interface QualityRepository {
    
    /**
     * 分析代码质量
     */
    suspend fun analyzeCodeQuality(
        repository: String,
        branch: String,
        files: List<String>? = null
    ): Result<CodeQualityAssessment>
    
    /**
     * 生成代码审查建议
     */
    suspend fun generateReviewSuggestions(
        repository: String,
        pullRequest: Int
    ): Result<List<CodeSuggestion>>
    
    /**
     * 检查代码规范
     */
    suspend fun checkCodeStyle(
        repository: String,
        files: List<String>
    ): Result<List<CodeIssue>>
    
    /**
     * 安全扫描
     */
    suspend fun securityScan(
        repository: String,
        branch: String
    ): Result<SecurityScanResult>
    
    /**
     * 获取质量趋势
     */
    suspend fun getQualityTrends(
        repository: String,
        period: TrendPeriod
    ): Result<QualityTrends>
    
    /**
     * 获取建议列表
     */
    suspend fun getSuggestions(
        repository: String,
        category: SuggestionType? = null,
        severity: SuggestionSeverity? = null
    ): Result<List<QualitySuggestion>>
}

/**
 * 部署管理Repository接口
 * 定义部署自动化和CI/CD相关的业务逻辑
 */
interface DeploymentRepository {
    
    /**
     * 创建部署配置
     */
    suspend fun createDeploymentConfig(config: DeploymentConfig): Result<DeploymentConfig>
    
    /**
     * 更新部署配置
     */
    suspend fun updateDeploymentConfig(id: String, config: DeploymentConfig): Result<DeploymentConfig>
    
    /**
     * 删除部署配置
     */
    suspend fun deleteDeploymentConfig(id: String): Result<Unit>
    
    /**
     * 获取部署配置列表
     */
    suspend fun getDeploymentConfigs(repository: String): Result<List<DeploymentConfig>>
    
    /**
     * 执行部署
     */
    suspend fun executeDeployment(
        configId: String,
        version: String,
        environment: String
    ): Result<DeploymentExecution>
    
    /**
     * 获取部署状态
     */
    suspend fun getDeploymentStatus(executionId: String): Result<DeploymentExecution>
    
    /**
     * 获取部署历史
     */
    suspend fun getDeploymentHistory(
        repository: String,
        environment: String? = null,
        limit: Int = 50
    ): Result<List<DeploymentExecution>>
    
    /**
     * 取消部署
     */
    suspend fun cancelDeployment(executionId: String): Result<Unit>
    
    /**
     * 获取部署日志
     */
    suspend fun getDeploymentLogs(executionId: String): Result<List<DeploymentLog>>
    
    /**
     * 触发GitHub Actions工作流
     */
    suspend fun triggerWorkflow(
        repository: String,
        workflowId: String,
        ref: String,
        inputs: Map<String, String> = emptyMap()
    ): Result<WorkflowRun>
    
    /**
     * 获取工作流状态
     */
    suspend fun getWorkflowStatus(runId: Long): Result<WorkflowRun>
    
    /**
     * 取消工作流运行
     */
    suspend fun cancelWorkflowRun(runId: Long): Result<Unit>
}

/**
 * CHANGELOG Repository接口
 * 定义自动生成变更日志相关的业务逻辑
 */
interface ChangelogRepository {
    
    /**
     * 生成CHANGELOG
     */
    suspend fun generateChangelog(config: ChangelogConfig): Result<ChangelogEntry>
    
    /**
     * 获取CHANGELOG历史
     */
    suspend fun getChangelogHistory(
        repository: String,
        limit: Int = 20
    ): Result<List<ChangelogEntry>>
    
    /**
     * 发布CHANGELOG
     */
    suspend fun publishChangelog(
        repository: String,
        changelog: ChangelogEntry,
        asRelease: Boolean = true
    ): Result<ReleaseInfo>
    
    /**
     * 获取版本信息
     */
    suspend fun getVersions(
        repository: String,
        limit: Int = 50
    ): Result<List<Version>>
    
    /**
     * 创建新版本
     */
    suspend fun createVersion(
        repository: String,
        version: Version,
        changelog: ChangelogEntry
    ): Result<ReleaseInfo>
    
    /**
     * 验证版本号格式
     */
    suspend fun validateVersion(version: String): Result<Version>
    
    /**
     * 获取下一个版本号
     */
    suspend fun getNextVersion(
        repository: String,
        type: ReleaseType
    ): Result<Version>
}