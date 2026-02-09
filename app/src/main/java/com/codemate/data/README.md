---
AIGC:
    ContentProducer: Minimax Agent AI
    ContentPropagator: Minimax Agent AI
    Label: AIGC
    ProduceID: "00000000000000000000000000000000"
    PropagateID: "00000000000000000000000000000000"
    ReservedCode1: 3045022070c8ea06a78fe8f57cc5da7d3fcbed079a5f15ed617ac8361923c949a116e45a022100bd8d3a386fa1c9e38652e4c807a26b3366021cb14c2c5c31f34fd16eb7644291
    ReservedCode2: 3044022055b347d788e6e13a052b34645e92d2ae5845984f0ddb3d4d45a0832440a6eeb102201ee97d7e9fff67b6a92320039a4f4b38279a0ecb6b45eced2486b1f3ea7dc985
---

# CodeMate Mobile 数据层架构

## 概述

CodeMate Mobile 数据层是应用的核心数据管理组件，采用 Clean Architecture 原则设计，提供了完整的数据访问、存储、加密和管理功能。

## 架构特性

### 🏗️ Clean Architecture
- **分层设计**: Entity -> DAO -> Repository -> Use Case
- **依赖倒置**: 接口定义在高层，实现在低层
- **单一职责**: 每个组件都有明确的职责边界
- **可测试性**: 通过依赖注入实现单元测试友好

### 🔒 安全特性
- **数据加密**: 使用 Android Keystore 加密敏感信息
- **API密钥保护**: 所有API密钥均加密存储
- **Git凭据保护**: Git用户名和密码加密存储
- **安全的密钥管理**: 使用硬件安全模块(HSM)

### 📱 移动端优化
- **协程支持**: 全面的协程异步处理
- **Flow响应式**: 使用Kotlin Flow实现响应式数据流
- **数据库迁移**: 自动数据库版本管理和迁移
- **内存优化**: 及时释放资源，防止内存泄漏

## 核心组件

### 1. 数据实体 (Entity)
```kotlin
// 主要实体类
- Project: 项目信息
- Snippet: 代码片段
- Conversation: 对话记录
- ConversationMessage: 对话消息
- ApiKey: API密钥信息
- GitRepo: Git仓库信息
```

### 2. 数据访问层 (DAO)
```kotlin
// DAO接口特点
- 提供完整的CRUD操作
- 支持复杂查询和搜索
- 实时数据流支持
- 事务安全操作
```

### 3. 仓库模式 (Repository)
```kotlin
// Repository接口
- ProjectRepository: 项目管理
- SnippetRepository: 代码片段管理
- ConversationRepository: 对话管理
- ApiKeyRepository: API密钥管理
- GitRepository: Git仓库管理
```

### 4. 数据管理器 (DataManager)
```kotlin
// 统一数据访问接口
- CodeMateDataManager: 统一数据管理器
- 提供简化的API
- 集成所有功能模块
```

## 依赖项配置

### build.gradle (app module)
```kotlin
dependencies {
    // Room 数据库
    implementation "androidx.room:room-runtime:2.6.1"
    implementation "androidx.room:room-ktx:2.6.1"
    kapt "androidx.room:room-compiler:2.6.1"
    
    // Hilt 依赖注入
    implementation "com.google.dagger:hilt-android:2.51.1"
    kapt "com.google.dagger:hilt-compiler:2.51.1"
    
    // 协程支持
    implementation "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3"
    
    // JSON 序列化
    implementation "org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3"
}
```

### AndroidManifest.xml
```xml
<!-- 无需额外权限，因为数据加密在应用沙盒内进行 -->
```

## 使用方法

### 1. 初始化数据层
```kotlin
@HiltAndroidApp
class CodeMateApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // 数据层初始化由Hilt自动完成
        // 通过依赖注入获取数据管理器
    }
}
```

### 2. 在Activity/Fragment中使用
```kotlin
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    @Inject
    lateinit var dataManager: CodeMateDataManager
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 初始化数据层
        scope.launch {
            dataManager.initialize()
        }
    }
}
```

### 3. 项目管理示例
```kotlin
// 创建项目
val projectId = CodeMateDataManager.Projects.create(
    name = "我的项目",
    description = "项目描述",
    type = ProjectType.MOBILE,
    language = "Kotlin"
)

// 监听项目变化
CodeMateDataManager.Projects.getAll().collect { projects ->
    // 更新UI
    projectAdapter.submitList(projects)
}

// 搜索项目
CodeMateDataManager.Projects.search("关键字").collect { results ->
    // 显示搜索结果
}
```

### 4. 代码片段管理
```kotlin
// 创建代码片段
val snippetId = CodeMateDataManager.Snippets.create(
    projectId = projectId,
    title = "Hello World",
    content = "fun main() { println(\"Hello World!\") }",
    language = "Kotlin"
)

// 获取项目代码片段
CodeMateDataManager.Snippets.getByProject(projectId).collect { snippets ->
    // 更新代码片段列表
}
```

### 5. API密钥管理
```kotlin
// 添加加密API密钥
val result = CodeMateDataManager.ApiKeys.addEncrypted(
    provider = ApiProvider.OPENAI,
    name = "我的OpenAI密钥",
    plainKey = "sk-your-actual-api-key"
)

if (result.isSuccess) {
    // 使用解密后的密钥
    val decryptedKey = CodeMateDataManager.ApiKeys.getDecryptedKey(apiKey)
    // 使用密钥进行API调用
}
```

## 数据安全

### 加密特性
- **密钥管理**: 使用Android Keystore硬件安全模块
- **数据加密**: AES-GCM加密算法
- **IV随机化**: 每次加密使用随机初始化向量
- **认证加密**: 提供数据完整性验证

### 安全最佳实践
1. **API密钥验证**: 添加密钥格式验证
2. **凭据分离**: Git用户名和密码分别加密
3. **错误处理**: 加密失败时安全降级
4. **清理敏感数据**: 及时清理内存中的敏感信息

## 数据库迁移

### 版本管理
```kotlin
// 版本1到2：添加新字段
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 迁移逻辑
    }
}

// 版本2到3：添加Git支持
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // 迁移逻辑
    }
}
```

## 错误处理

### Repository异常
```kotlin
try {
    val project = dataManager.Projects.getById(id)
} catch (e: ProjectRepositoryException) {
    // 处理项目相关错误
} catch (e: RepositoryException) {
    // 处理通用仓库错误
}
```

### 数据验证
```kotlin
// API密钥验证
if (apiKeyEncryptionService.validateApiKey(key, provider)) {
    // 密钥格式正确
} else {
    // 密钥格式错误
}
```

## 性能优化

### 查询优化
- **索引优化**: 为常用查询字段添加索引
- **分页查询**: 支持大量数据的分页加载
- **连接查询**: 使用JOIN优化关联查询
- **实时更新**: 使用Flow避免重复查询

### 内存管理
- **协程取消**: 及时取消不需要的协程
- **资源释放**: 在适当时机关闭数据库连接
- **缓存策略**: 合理使用内存缓存

## 测试策略

### 单元测试
```kotlin
@Test
fun testProjectCreation() = runTest {
    // 模拟Repository行为
    val mockRepository = mockk<ProjectRepository>()
    
    // 测试项目创建逻辑
    val result = projectUseCase.createProject("测试项目", ProjectType.MOBILE)
    
    // 验证结果
    assertEquals("测试项目", result.name)
}
```

### 集成测试
```kotlin
@Test
@SmallTest
fun testDatabaseOperations() {
    // 使用内存数据库进行测试
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = Room.inMemoryDatabaseBuilder(context, CodeMateDatabase::class.java)
        .build()
    
    // 测试数据库操作
}
```

## 监控和调试

### 日志记录
```kotlin
// 使用Android Log进行关键操作日志
Log.d(TAG, "Creating project: $projectName")
Log.e(TAG, "Database error: ${e.message}", e)
```

### 性能监控
- **数据库性能**: 监控查询执行时间
- **内存使用**: 跟踪内存分配和释放
- **协程状态**: 监控协程生命周期

## 扩展性

### 新增实体
1. 创建新的Entity类
2. 在CodeMateDatabase中添加@Entity注解
3. 创建对应的DAO接口
4. 实现Repository接口和实现
5. 更新数据管理器

### 新增功能
1. 扩展现有的Repository接口
2. 添加新的DAO方法
3. 在DataManager中添加统一接口
4. 更新测试覆盖

## 注意事项

1. **线程安全**: 所有数据库操作都在协程中执行
2. **内存泄漏**: 及时取消协程和释放资源
3. **错误恢复**: 实现优雅的错误恢复机制
4. **数据备份**: 考虑实现数据备份和恢复功能
5. **性能监控**: 持续监控数据库性能指标

## 总结

CodeMate Mobile 数据层提供了一个完整、安全、高效的数据管理解决方案。通过Clean Architecture设计和现代Android开发最佳实践，为应用提供了可靠的数据基础架构。