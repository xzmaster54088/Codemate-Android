package com.codemate.features.github.presentation.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.codemate.MainActivity
import com.codemate.features.github.presentation.state.GitHubState
import com.codemate.features.github.domain.model.GitHubRepository
import com.codemate.features.github.domain.model.GitHubUser
import com.codemate.features.github.domain.model.GitHubCommit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * GitHub功能UI测试
 * 测试GitHub集成的主要功能，包括登录、仓库管理、代码同步等
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalCoroutinesApi::class)
class GitHubUITest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var device: UiDevice

    @Before
    fun setup() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @Test
    fun `githubLoginScreen when loaded should display login components`() {
        // Given - Setup GitHub login screen

        // When - Launch GitHub login screen
        composeTestRule.setContent {
            GitHubLoginScreen(
                onLoginSuccess = { },
                onLoginFailure = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify UI components are displayed
        composeTestRule.onNodeWithTag("GitHubLoginContainer")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("GitHub登录")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("GitHubLoginButton")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("GitHubTokenInput")
            .assertIsDisplayed()
    }

    @Test
    fun `githubLogin should authenticate user and navigate to repositories`() {
        // Given - Setup login form
        var loginCalled = false
        var loginToken = ""

        composeTestRule.setContent {
            GitHubLoginScreen(
                onLoginSuccess = { token ->
                    loginCalled = true
                    loginToken = token
                },
                onLoginFailure = { },
                onNavigateBack = { }
            )
        }

        // When - Enter GitHub token and click login
        val tokenInput = composeTestRule.onNodeWithTag("GitHubTokenInput")
        tokenInput.performTextInput("ghp_test_token_123456789")

        composeTestRule.onNodeWithTag("GitHubLoginButton")
            .performClick()

        // Then - Login should be called with token
        assert(loginCalled)
        assert(loginToken == "ghp_test_token_123456789")

        // Verify success state
        composeTestRule.onNodeWithTag("LoginSuccessMessage")
            .assertIsDisplayed()
    }

    @Test
    fun `githubLogin failure should show error message`() {
        // Given - Setup failed login
        var loginFailureCalled = false
        var errorMessage = ""

        composeTestRule.setContent {
            GitHubLoginScreen(
                onLoginSuccess = { },
                onLoginFailure = { error ->
                    loginFailureCalled = true
                    errorMessage = error
                },
                onNavigateBack = { }
            )
        }

        // When - Enter invalid token and attempt login
        val tokenInput = composeTestRule.onNodeWithTag("GitHubTokenInput")
        tokenInput.performTextInput("invalid_token")

        composeTestRule.onNodeWithTag("GitHubLoginButton")
            .performClick()

        // Then - Error should be displayed
        assert(loginFailureCalled)
        assert(errorMessage.isNotEmpty())

        composeTestRule.onNodeWithText("登录失败")
            .assertIsDisplayed()
    }

    @Test
    fun `githubRepositoryList should display repositories`() {
        // Given - Setup repository list with sample data
        val repositories = listOf(
            GitHubRepository(
                id = 1L,
                name = "kotlin-projects",
                description = "A collection of Kotlin projects",
                language = "Kotlin",
                stars = 150,
                forks = 25,
                isPrivate = false,
                updatedAt = "2024-01-15T10:30:00Z"
            ),
            GitHubRepository(
                id = 2L,
                name = "android-apps",
                description = "Android applications",
                language = "Java",
                stars = 89,
                forks = 12,
                isPrivate = false,
                updatedAt = "2024-01-14T15:45:00Z"
            )
        )

        composeTestRule.setContent {
            GitHubRepositoryListScreen(
                repositories = repositories,
                onRepositorySelected = { },
                onRefresh = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify repositories are displayed
        composeTestRule.onNodeWithText("kotlin-projects")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("A collection of Kotlin projects")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("android-apps")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Android applications")
            .assertIsDisplayed()

        // Verify language indicators
        composeTestRule.onNodeWithText("Kotlin")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Java")
            .assertIsDisplayed()
    }

    @Test
    fun `repositorySearch should filter repositories`() {
        // Given - Setup repository list
        val repositories = listOf(
            GitHubRepository(
                id = 1L,
                name = "kotlin-projects",
                description = "Kotlin development projects",
                language = "Kotlin",
                stars = 150,
                forks = 25,
                isPrivate = false,
                updatedAt = "2024-01-15T10:30:00Z"
            ),
            GitHubRepository(
                id = 2L,
                name = "android-apps",
                description = "Java Android applications",
                language = "Java",
                stars = 89,
                forks = 12,
                isPrivate = false,
                updatedAt = "2024-01-14T15:45:00Z"
            )
        )

        composeTestRule.setContent {
            GitHubRepositoryListScreen(
                repositories = repositories,
                onRepositorySelected = { },
                onRefresh = { },
                onNavigateBack = { }
            )
        }

        // When - Search for "kotlin"
        val searchField = composeTestRule.onNodeWithTag("RepositorySearchField")
        searchField.performTextInput("kotlin")

        // Then - Only Kotlin repository should be visible
        composeTestRule.onNodeWithText("kotlin-projects")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("android-apps")
            .assertDoesNotExist()
    }

    @Test
    fun `commitHistory should display commit list`() {
        // Given - Setup commit history with sample data
        val commits = listOf(
            GitHubCommit(
                sha = "abc123",
                message = "Add new feature: code completion",
                author = GitHubUser(
                    login = "developer1",
                    avatarUrl = "https://github.com/developer1.png"
                ),
                date = "2024-01-15T10:30:00Z",
                url = "https://github.com/user/repo/commit/abc123"
            ),
            GitHubCommit(
                sha = "def456",
                message = "Fix syntax highlighting bug",
                author = GitHubUser(
                    login = "developer2",
                    avatarUrl = "https://github.com/developer2.png"
                ),
                date = "2024-01-14T15:45:00Z",
                url = "https://github.com/user/repo/commit/def456"
            )
        )

        composeTestRule.setContent {
            GitHubCommitHistoryScreen(
                commits = commits,
                repositoryName = "test-repo",
                onCommitSelected = { },
                onRefresh = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify commits are displayed
        composeTestRule.onNodeWithText("Add new feature: code completion")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("Fix syntax highlighting bug")
            .assertIsDisplayed()

        // Verify author information
        composeTestRule.onNodeWithText("developer1")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("developer2")
            .assertIsDisplayed()

        // Verify commit dates
        composeTestRule.onNodeWithText("2024-01-15")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("2024-01-14")
            .assertIsDisplayed()
    }

    @Test
    fun `pushChanges should show progress and success feedback`() {
        // Given - Setup file changes
        var pushCalled = false
        var pushedFiles = listOf<String>()

        composeTestRule.setContent {
            GitHubPushChangesScreen(
                changedFiles = listOf("Main.kt", "Helper.java"),
                commitMessage = "Update code files",
                onPushChanges = { files ->
                    pushCalled = true
                    pushedFiles = files
                },
                onNavigateBack = { }
            )
        }

        // When - Click push button
        composeTestRule.onNodeWithTag("PushButton")
            .performClick()

        // Then - Push operation should be called
        assert(pushCalled)
        assert(pushedFiles.contains("Main.kt"))
        assert(pushedFiles.contains("Helper.java"))

        // Verify progress indicator
        composeTestRule.onNodeWithTag("PushProgressIndicator")
            .assertIsDisplayed()

        // Verify success feedback
        composeTestRule.onNodeWithText("推送成功")
            .assertIsDisplayed()
    }

    @Test
    fun `pullChanges should fetch and display latest commits`() {
        // Given - Setup pull operation
        var pullCalled = false
        var pullBranch = ""

        composeTestRule.setContent {
            GitHubPullChangesScreen(
                currentBranch = "main",
                latestCommit = GitHubCommit(
                    sha = "xyz789",
                    message = "Latest commit from remote",
                    author = GitHubUser(
                        login = "remote-dev",
                        avatarUrl = "https://github.com/remote-dev.png"
                    ),
                    date = "2024-01-15T12:00:00Z",
                    url = "https://github.com/user/repo/commit/xyz789"
                ),
                onPullChanges = { branch ->
                    pullCalled = true
                    pullBranch = branch
                },
                onNavigateBack = { }
            )
        }

        // When - Click pull button
        composeTestRule.onNodeWithTag("PullButton")
            .performClick()

        // Then - Pull operation should be called
        assert(pullCalled)
        assert(pullBranch == "main")

        // Verify latest commit is displayed
        composeTestRule.onNodeWithText("Latest commit from remote")
            .assertIsDisplayed()
    }

    @Test
    fun `branchManagement should allow branch switching`() {
        // Given - Setup branch list
        val branches = listOf(
            "main",
            "develop",
            "feature/code-completion",
            "bugfix/syntax-highlighting"
        )

        composeTestRule.setContent {
            GitHubBranchManagementScreen(
                currentBranch = "main",
                branches = branches,
                onBranchSelected = { },
                onCreateBranch = { },
                onDeleteBranch = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify branches are displayed
        branches.forEach { branch ->
            composeTestRule.onNodeWithText(branch)
                .assertIsDisplayed()
        }

        // When - Select a different branch
        composeTestRule.onNodeWithText("develop")
            .performClick()

        // Then - Branch selection should be triggered
        // (This would require mocking the callback)
    }

    @Test
    fun `mergeConflictResolution should display conflict information`() {
        // Given - Setup merge conflict scenario
        val conflicts = listOf(
            GitHubConflict(
                filePath = "src/Main.kt",
                conflicts = listOf(
                    "<<<<<<< HEAD",
                    "fun oldFunction() { }",
                    "=======",
                    "fun newFunction() { }",
                    ">>>>>>> feature-branch"
                ),
                baseContent = "fun oldFunction() { }",
                currentContent = "fun oldFunction() { }",
                incomingContent = "fun newFunction() { }"
            )
        )

        composeTestRule.setContent {
            GitHubMergeConflictScreen(
                conflicts = conflicts,
                onResolveConflict = { file, resolution ->
                    // Handle conflict resolution
                },
                onAbortMerge = { },
                onNavigateBack = { }
            )
        }

        // Then - Verify conflict information is displayed
        composeTestRule.onNodeWithText("src/Main.kt")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("<<<<<<< HEAD")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("=======")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText(">>>>>>> feature-branch")
            .assertIsDisplayed()

        // Verify resolution options
        composeTestRule.onNodeWithTag("AcceptCurrentButton")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("AcceptIncomingButton")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("AcceptBothButton")
            .assertIsDisplayed()
    }

    @Test
    fun `githubSettings should allow configuration`() {
        // Given - Setup GitHub settings
        var settingsUpdated = false

        composeTestRule.setContent {
            GitHubSettingsScreen(
                currentSettings = GitHubSettings(
                    defaultBranch = "main",
                    autoSync = true,
                    showLineNumbers = true,
                    enableSyntaxHighlighting = true
                ),
                onSettingsUpdated = {
                    settingsUpdated = true
                },
                onNavigateBack = { }
            )
        }

        // When - Toggle auto sync setting
        composeTestRule.onNodeWithTag("AutoSyncToggle")
            .performClick()

        // Then - Settings should be updated
        assert(settingsUpdated)

        // Verify current state is displayed
        composeTestRule.onNodeWithText("主分支: main")
            .assertIsDisplayed()
    }

    @Test
    fun `githubAuthentication should handle token refresh`() {
        // Given - Setup authentication screen
        var refreshTokenCalled = false
        var newToken = ""

        composeTestRule.setContent {
            GitHubAuthenticationScreen(
                isTokenExpired = true,
                onRefreshToken = { token ->
                    refreshTokenCalled = true
                    newToken = token
                },
                onLogout = { },
                onNavigateBack = { }
            )
        }

        // Then - Token expiration warning should be shown
        composeTestRule.onNodeWithText("GitHub令牌已过期")
            .assertIsDisplayed()

        composeTestRule.onNodeWithTag("RefreshTokenButton")
            .assertIsDisplayed()

        // When - Refresh token
        composeTestRule.onNodeWithTag("RefreshTokenButton")
            .performClick()

        // Then - Token refresh should be called
        assert(refreshTokenCalled)
    }

    @Test
    fun `githubSyncStatus should show synchronization state`() {
        // Given - Setup sync status screen
        val syncStatus = GitHubSyncStatus(
            isOnline = true,
            lastSyncTime = System.currentTimeMillis(),
            pendingChanges = 3,
            conflictedFiles = listOf("Main.kt", "Helper.java"),
            syncProgress = 0.75f
        )

        composeTestRule.setContent {
            GitHubSyncStatusScreen(
                syncStatus = syncStatus,
                onSyncNow = { },
                onViewConflicts = { },
                onNavigateBack = { }
            )
        }

        // Then - Sync status should be displayed
        composeTestRule.onNodeWithText("在线同步")
            .assertIsDisplayed()

        composeTestRule.onNodeWithText("待同步更改: 3")
            .assertIsDisplayed()

        // Verify sync progress
        composeTestRule.onNodeWithTag("SyncProgressBar")
            .assertIsDisplayed()

        // Verify conflict information if any
        if (syncStatus.conflictedFiles.isNotEmpty()) {
            composeTestRule.onNodeWithText("冲突文件")
                .assertIsDisplayed()
        }
    }
}
