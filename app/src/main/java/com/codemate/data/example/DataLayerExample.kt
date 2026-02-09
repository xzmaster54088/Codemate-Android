package com.codemate.data.example

import com.codemate.data.entity.*
import com.codemate.data.manager.CodeMateDataManager
import com.codemate.data.repository.ProjectRepositoryException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * CodeMate数据层使用示例
 * 展示如何使用数据层的各种功能
 */
@Singleton
class DataLayerExample @Inject constructor(
    private val dataManager: CodeMateDataManager
) {

    /**
     * 示例1：创建和管理项目
     */
    suspend fun projectManagementExample() {
        println("=== 项目管理示例 ===")
        
        try {
            // 创建新项目
            val projectId = CodeMateDataManager.Projects.create(
                name = "我的第一个项目",
                description = "这是一个示例项目",
                type = ProjectType.MOBILE,
                language = "Kotlin",
                framework = "Jetpack Compose"
            )
            println("创建项目成功，ID: $projectId")
            
            // 获取项目列表
            val projects = CodeMateDataManager.Projects.getAllOnce()
            println("当前项目数量: ${projects.size}")
            
            // 更新项目最后访问时间
            CodeMateDataManager.Projects.updateLastAccessed(projectId)
            println("更新项目访问时间")
            
            // 切换收藏状态
            CodeMateDataManager.Projects.toggleFavorite(projectId)
            println("切换项目收藏状态")
            
            // 获取项目统计信息
            val stats = CodeMateDataManager.Projects.getStatistics()
            println("项目统计信息:")
            println("  总项目数: ${stats.totalProjects}")
            println("  收藏项目数: ${stats.favoriteProjects}")
            println("  项目类型分布: ${stats.projectsByType}")
            println("  编程语言分布: ${stats.projectsByLanguage}")
            
        } catch (e: ProjectRepositoryException) {
            println("项目管理错误: ${e.message}")
        }
    }

    /**
     * 示例2：代码片段管理
     */
    suspend fun snippetManagementExample() {
        println("\n=== 代码片段管理示例 ===")
        
        try {
            // 获取第一个项目ID
            val project = CodeMateDataManager.Projects.getAllOnce().firstOrNull()
            if (project == null) {
                println("没有找到项目，无法创建代码片段")
                return
            }
            
            // 创建代码片段
            val snippetId = CodeMateDataManager.Snippets.create(
                projectId = project.id,
                title = "Hello World函数",
                content = """
                    fun helloWorld() {
                        println("Hello, World!")
                    }
                """.trimIndent(),
                language = "Kotlin",
                description = "一个简单的Hello World函数"
            )
            println("创建代码片段成功，ID: $snippetId")
            
            // 获取项目的代码片段
            CodeMateDataManager.Snippets.getByProject(project.id).collectLatest { snippets ->
                println("项目 '${project.name}' 的代码片段:")
                snippets.forEach { snippet ->
                    println("  - ${snippet.title} (${snippet.language})")
                }
            }
            
            // 搜索代码片段
            val searchResults = CodeMateDataManager.Snippets.search("Hello")
            searchResults.collectLatest { results ->
                println("搜索 'Hello' 的结果:")
                results.forEach { snippet ->
                    println("  - ${snippet.title}")
                }
            }
            
        } catch (e: Exception) {
            println("代码片段管理错误: ${e.message}")
        }
    }

    /**
     * 示例3：对话管理
     */
    suspend fun conversationManagementExample() {
        println("\n=== 对话管理示例 ===")
        
        try {
            // 创建新对话
            val conversationId = CodeMateDataManager.Conversations.create(
                title = "Kotlin最佳实践讨论",
                context = "讨论Kotlin编程语言的最佳实践"
            )
            println("创建对话成功，ID: $conversationId")
            
            // 发送消息
            val userMessageId = CodeMateDataManager.Messages.send(
                conversationId = conversationId,
                role = MessageRole.USER,
                content = "请介绍一下Kotlin的数据类最佳实践？",
                tokensUsed = 15
            )
            println("发送用户消息成功，ID: $userMessageId")
            
            val assistantMessageId = CodeMateDataManager.Messages.send(
                conversationId = conversationId,
                role = MessageRole.ASSISTANT,
                content = "Kotlin数据类的最佳实践包括：\n1. 只在数据类中存储数据\n2. 使用val而不是var\n3. 正确实现equals()和hashCode()方法\n4. 避免在数据类中添加副作用",
                tokensUsed = 45
            )
            println("发送助手回复成功，ID: $assistantMessageId")
            
            // 获取对话消息
            CodeMateDataManager.Messages.getByConversation(conversationId).collectLatest { messages ->
                println("对话消息:")
                messages.forEach { message ->
                    val roleText = when (message.role) {
                        MessageRole.USER -> "用户"
                        MessageRole.ASSISTANT -> "助手"
                        MessageRole.SYSTEM -> "系统"
                    }
                    println("  $roleText: ${message.content.take(50)}...")
                }
            }
            
            // 获取消息统计
            val messageStats = CodeMateDataManager.Messages.getStatistics(conversationId)
            println("消息统计:")
            println("  总消息数: ${messageStats.totalMessages}")
            println("  用户消息数: ${messageStats.userMessages}")
            println("  助手消息数: ${messageStats.assistantMessages}")
            println("  总使用令牌数: ${messageStats.totalTokensUsed}")
            
        } catch (e: Exception) {
            println("对话管理错误: ${e.message}")
        }
    }

    /**
     * 示例4：API密钥管理
     */
    suspend fun apiKeyManagementExample() {
        println("\n=== API密钥管理示例 ===")
        
        try {
            // 添加API密钥（加密存储）
            val addResult = CodeMateDataManager.ApiKeys.addEncrypted(
                provider = ApiProvider.OPENAI,
                name = "我的OpenAI密钥",
                plainKey = "sk-example1234567890abcdef"
            )
            
            when (addResult) {
                is Result.Success -> {
                    val apiKeyId = addResult.getOrThrow()
                    println("添加API密钥成功，ID: $apiKeyId")
                    
                    // 获取并解密API密钥
                    val apiKeys = CodeMateDataManager.ApiKeys.getByProvider(ApiProvider.OPENAI)
                    val firstApiKey = apiKeys.firstOrNull()
                    firstApiKey?.let { key ->
                        val decryptedKey = CodeMateDataManager.ApiKeys.getDecryptedKey(key)
                        println("解密后的API密钥: $decryptedKey")
                    }
                }
                is Result.Failure -> {
                    println("添加API密钥失败: ${addResult.exceptionOrNull()?.message}")
                }
            }
            
            // 获取所有活跃的API密钥
            val activeKeys = CodeMateDataManager.ApiKeys.getActive().first()
            println("活跃的API密钥数量: ${activeKeys.size}")
            
            // 获取即将过期的API密钥
            val expiringKeys = CodeMateDataManager.ApiKeys.getExpiring()
            println("即将过期的API密钥数量: ${expiringKeys.size}")
            
        } catch (e: Exception) {
            println("API密钥管理错误: ${e.message}")
        }
    }

    /**
     * 示例5：Git仓库管理
     */
    suspend fun gitRepositoryManagementExample() {
        println("\n=== Git仓库管理示例 ===")
        
        try {
            // 获取第一个项目
            val project = CodeMateDataManager.Projects.getAllOnce().firstOrNull()
            if (project == null) {
                println("没有找到项目，无法创建Git仓库")
                return
            }
            
            // 添加Git仓库
            val repoId = CodeMateDataManager.GitRepos.add(
                projectId = project.id,
                remoteUrl = "https://github.com/example/repo.git",
                localPath = "/data/data/com.codemate/projects/${project.name}",
                branch = "main",
                username = "codemate",
                password = "secure_password"
            )
            println("添加Git仓库成功，ID: $repoId")
            
            // 获取项目的Git仓库
            val repo = CodeMateDataManager.GitRepos.getByProject(project.id)
            repo?.let { gitRepo ->
                println("项目仓库信息:")
                println("  远程URL: ${gitRepo.remoteUrl}")
                println("  本地路径: ${gitRepo.localPath}")
                println("  分支: ${gitRepo.branch}")
                println("  同步状态: ${if (gitRepo.isSyncEnabled) "启用" else "禁用"}")
                
                // 获取并解密Git凭据
                val credentials = CodeMateDataManager.GitRepos.getDecryptedCredentials(gitRepo)
                credentials?.let { creds ->
                    println("Git凭据:")
                    println("  用户名: ${creds.username}")
                    println("  密码: ${"•".repeat(creds.password.length)}")
                }
            }
            
            // 获取所有启用了同步的仓库
            val syncRepos = CodeMateDataManager.GitRepos.getSyncEnabled().first()
            println("启用了同步的仓库数量: ${syncRepos.size}")
            
        } catch (e: Exception) {
            println("Git仓库管理错误: ${e.message}")
        }
    }

    /**
     * 示例6：数据库维护
     */
    suspend fun databaseMaintenanceExample() {
        println("\n=== 数据库维护示例 ===")
        
        try {
            // 获取数据库统计信息
            val dbStats = CodeMateDataManager.Maintenance.getDatabaseStatistics()
            println("数据库统计信息:")
            println("  总项目数: ${dbStats.totalProjects}")
            println("  总代码片段数: ${dbStats.totalSnippets}")
            println("  活跃对话数: ${dbStats.activeConversations}")
            println("  活跃API密钥数: ${dbStats.activeApiKeys}")
            println("  数据库大小: ${dbStats.databaseSize} 字节")
            
            // 清理数据库
            val cleaned = CodeMateDataManager.Maintenance.cleanDatabase()
            println("数据库清理结果: ${if (cleaned) "成功" else "失败"}")
            
        } catch (e: Exception) {
            println("数据库维护错误: ${e.message}")
        }
    }

    /**
     * 运行所有示例
     */
    suspend fun runAllExamples() {
        println("开始运行CodeMate数据层示例...\n")
        
        projectManagementExample()
        snippetManagementExample()
        conversationManagementExample()
        apiKeyManagementExample()
        gitRepositoryManagementExample()
        databaseMaintenanceExample()
        
        println("\n所有示例运行完成！")
    }
}

/**
 * 在Activity或Fragment中的使用示例
 */
class MainActivityExample {
    
    private lateinit var dataManager: CodeMateDataManager
    
    // 在Activity的onCreate中初始化
    suspend fun initializeDataLayer() {
        // 初始化数据管理器
        val initialized = dataManager.initialize()
        if (initialized) {
            println("数据层初始化成功")
            
            // 创建示例实例
            val example = DataLayerExample(dataManager)
            
            // 运行示例
            example.runAllExamples()
        } else {
            println("数据层初始化失败")
        }
    }
    
    // 示例：在协程中使用数据层
    suspend fun exampleUsageInCoroutine() {
        try {
            // 创建一个移动项目
            val projectId = CodeMateDataManager.Projects.create(
                name = "移动应用开发",
                description = "使用Kotlin开发Android应用",
                type = ProjectType.MOBILE,
                language = "Kotlin",
                framework = "Jetpack Compose"
            )
            
            // 监听项目变化
            CodeMateDataManager.Projects.getAll().collectLatest { projects ->
                println("当前项目数量: ${projects.size}")
                projects.forEach { project ->
                    println("- ${project.name} (${project.type})")
                }
            }
            
        } catch (e: Exception) {
            println("协程中使用数据层时发生错误: ${e.message}")
        }
    }
}