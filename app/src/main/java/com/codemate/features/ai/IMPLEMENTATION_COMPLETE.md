---
AIGC:
    ContentProducer: Minimax Agent AI
    ContentPropagator: Minimax Agent AI
    Label: AIGC
    ProduceID: "00000000000000000000000000000000"
    PropagateID: "00000000000000000000000000000000"
    ReservedCode1: 3045022100fa0859d5e289bf4e0d9e503346867a802c004e8406ad6b175f2daf1724fb732102203228a1d8769ce1e7d73a1b6f035a01655ab882456a7910c8df17c8dd9aee4f06
    ReservedCode2: 3046022100a469eacb55f58bfd51051890996d5c490c4179530ed5194bb249f095640c01df022100ba37cf569beaee36420722c1e5d0e6a4180507c9a0aa0fa8764fef4dbc60dd65
---

# CodeMate Mobile AI服务管理器完整实现

## 📋 概述

CodeMate Mobile的AI服务管理器是一个完整的企业级AI服务解决方案，支持多种AI提供商和本地模型推理。该实现提供了统一接口、安全过滤、性能监控、错误处理等企业级功能。

## 🏗️ 架构设计

### Clean Architecture层次
- **Domain Layer**: 业务逻辑和实体
- **Data Layer**: 数据存储和获取  
- **Presentation Layer**: UI层和状态管理
- **Utils Layer**: 工具类和辅助功能

### 支持的AI服务
1. **OpenAI GPT**: GPT-4, GPT-3.5-turbo
2. **Anthropic Claude**: Claude-3系列模型
3. **本地LLM**: 通过ONNX Runtime Mobile
4. **自定义API**: 支持用户自定义端点

## 📁 完整文件结构

```
ai/
├── domain/
│   ├── entity/                    # 业务实体
│   │   ├── AIRequestResponse.kt   # 请求响应模型
│   │   ├── AIModel.kt            # AI模型定义
│   │   ├── AIConfig.kt           # 配置管理
│   │   ├── Conversation.kt       # 对话管理
│   │   ├── Safety.kt             # 安全相关
│   │   ├── LocalLLM.kt           # 本地LLM
│   │   └── Health.kt             # 健康检查
│   ├── repository/                # 仓储接口
│   │   ├── AIServiceRepository.kt      # AI服务接口
│   │   ├── AIConfigRepository.kt      # 配置管理接口
│   │   └── AISafetyRepository.kt       # 安全服务接口
│   └── usecase/                  # 用例
│       ├── ChatUseCase.kt              # 聊天用例
│       ├── CodeGenerationUseCase.kt    # 代码生成用例
│       ├── AISafetyUseCase.kt          # 安全检查用例
│       └── LocalLLMUseCase.kt          # 本地LLM用例
├── data/
│   └── repository/               # 仓储实现
│       ├── OpenAIService.kt          # OpenAI服务实现
│       ├── ClaudeService.kt          # Claude服务实现
│       ├── LocalLLMService.kt        # 本地LLM服务实现
│       ├── AIServiceRepositoryImpl.kt # AI服务仓储实现
│       ├── AIConfigManager.kt        # 配置管理器
│       ├── ConversationManager.kt    # 对话管理器
│       ├── CacheManager.kt           # 缓存管理器
│       ├── MemoryManager.kt          # 内存管理器
│       ├── ModelCache.kt             # 模型缓存
│       ├── NetworkClient.kt          # 网络客户端
│       ├── NetworkMonitor.kt         # 网络监控
│       ├── RetryManager.kt           # 重试管理器
│       ├── ServiceMonitor.kt         # 服务监控
│       ├── MetricsDatabase.kt       # 指标数据库
│       ├── LocalDatabase.kt          # 本地数据库
│       ├── EncryptionManager.kt     # 加密管理器
│       └── GsonUtils.kt            # JSON工具
├── presentation/
│   ├── viewmodel/              # ViewModel
│   │   └── AIChatViewModel.kt      # AI聊天视图模型
│   └── ui/                    # UI组件
│       └── AIChatScreen.kt         # AI聊天界面
├── utils/                     # 工具类
│   ├── AIConstants.kt             # 常量定义
│   ├── RetrofitNetworkClient.kt  # Retrofit网络客户端
│   ├── RetryManager.kt           # 重试管理器
│   ├── AISafetyFilter.kt        # 安全过滤器
│   └── AILogger.kt              # 日志记录器
└── di/
    └── AIModule.kt              # 依赖注入模块
```

## 🚀 核心功能实现

### 1. 统一AI服务接口
- `AIServiceRepository`: 统一的AI服务接口
- 支持多种AI提供商的无缝切换
- 统一的数据模型和响应格式

### 2. OpenAI GPT服务适配器
```kotlin
// 支持GPT-4和GPT-3.5-turbo
suspend fun sendChatMessage(request: ChatRequest): TextResponse
fun sendStreamingChatMessage(request: ChatRequest): Flow<StreamingResponse>
suspend fun generateCode(request: CodeGenerationRequest): TextResponse
```

### 3. Anthropic Claude服务适配器
```kotlin
// 支持Claude-3系列模型
suspend fun sendChatMessage(request: ChatRequest): TextResponse
fun sendStreamingChatMessage(request: ChatRequest): Flow<StreamingResponse>
```

### 4. 本地LLM集成(ONNX Runtime)
```kotlin
// 本地模型推理
suspend fun executeLocalLLM(request: LocalLLMRequest): LocalLLMResponse
fun executeStreamingLocalLLM(request: LocalLLMRequest): Flow<StreamingResponse>
```

### 5. 流式响应处理
- 支持Server-Sent Events (SSE)
- 实时内容增量更新
- 流式响应状态管理

### 6. 对话上下文管理
```kotlin
data class Conversation(
    val id: String,
    val title: String,
    val messages: List<AIMessage>,
    val context: ConversationContext,
    val metadata: Map<String, Any>
)
```

### 7. API配置管理
- 加密存储API密钥
- 模型参数配置
- 服务端点管理

### 8. 安全过滤机制
- 内容安全检查
- 敏感信息过滤
- 恶意代码检测

### 9. 网络请求优化
- Retrofit + OkHttp实现
- 连接池和缓存优化
- 智能重试机制

### 10. 错误处理和重试机制
- 指数退避重试
- 错误分类处理
- 降级策略

## 🔧 依赖注入配置

使用Dagger/Hilt进行依赖注入：

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object AIModule {
    @Provides
    @Singleton
    fun provideOpenAIService(
        networkClient: NetworkClient,
        configManager: AIConfigManager,
        retryManager: RetryManager
    ): OpenAIService {
        return OpenAIService(networkClient, configManager, retryManager)
    }
    
    // 更多依赖注入配置...
}
```

## 📊 性能监控

- 实时性能指标收集
- 响应时间和成功率统计
- 内存使用监控
- 缓存命中率分析

## 🔒 安全特性

- API密钥加密存储
- 内容安全过滤
- 敏感信息检测
- 恶意代码防护

## 📱 使用示例

### 基本聊天
```kotlin
class AIChatViewModel @Inject constructor(
    private val chatUseCase: ChatUseCase
) {
    fun sendMessage(message: String) {
        val context = ConversationContext(
            systemPrompt = "你是一个有用的AI助手",
            maxTokens = 2048,
            temperature = 0.7f
        )
        
        viewModelScope.launch {
            val result = chatUseCase.sendMessage(
                conversationId = "123",
                message = message,
                context = context,
                modelType = AIModelType.OPENAI_GPT_4
            )
        }
    }
}
```

### 流式响应
```kotlin
fun sendStreamingMessage(message: String) {
    chatUseCase.sendStreamingMessage(
        conversationId = "123",
        message = message,
        context = context,
        modelType = AIModelType.ANTHROPIC_CLAUDE_3_SONNET
    ).collect { response ->
        // 实时处理流式响应
        updateUI(response.contentDelta)
    }
}
```

### 本地LLM推理
```kotlin
class LocalLLMUseCase @Inject constructor(
    private val aiServiceRepository: AIServiceRepository
) {
    suspend fun runLocalInference(input: String): LocalLLMResponse {
        val request = LocalLLMRequest(
            modelId = "local_model_onnx",
            inputText = input,
            maxTokens = 512,
            temperature = 0.7f
        )
        
        return aiServiceRepository.executeLocalLLM(request)
    }
}
```

## 📈 监控和日志

```kotlin
// 性能监控
metricsCollector.recordMetrics(
    AIMetrics(
        provider = "openai",
        model = "gpt-4",
        requestType = "chat",
        responseTime = 1500L,
        tokensUsed = 250,
        success = true
    )
)

// 日志记录
logger.info(TAG_AI_SERVICE, "AI请求完成", metadata)
```

## 🔄 配置管理

```kotlin
// API配置
val config = AIConfig(
    provider = AIProvider.OPENAI,
    apiKey = "your-api-key",
    model = "gpt-4",
    parameters = mapOf(
        "temperature" to 0.7f,
        "max_tokens" to 2048
    )
)

// 安全过滤
val safetyResult = safetyFilter.checkInputSafety(content)
if (safetyResult.isSafe) {
    // 处理内容
} else {
    // 处理安全问题
}
```

## 📚 完整特性列表

✅ **AI服务统一接口定义** - 完整的仓储模式实现
✅ **OpenAI GPT-4/3.5服务适配器** - 支持流式和同步请求
✅ **Anthropic Claude服务适配器** - 支持Claude-3系列模型
✅ **本地LLM集成(ONNX Runtime)** - 完整的本地推理框架
✅ **流式响应处理** - SSE协议支持
✅ **对话上下文管理** - 持久化对话历史
✅ **API配置管理** - 加密存储和配置管理
✅ **安全过滤机制** - 内容审查和敏感信息过滤
✅ **网络请求优化** - Retrofit + OkHttp实现
✅ **错误处理和重试机制** - 智能重试和降级策略
✅ **Kotlin Coroutines和Flow** - 完整的异步编程支持
✅ **详细注释和文档** - 企业级代码质量

## 🎯 架构优势

1. **可扩展性**: 易于添加新的AI提供商
2. **可维护性**: 清晰的层次结构和职责分离
3. **可靠性**: 完善的错误处理和重试机制
4. **安全性**: 全面的安全过滤和加密存储
5. **性能**: 智能缓存和性能监控
6. **可观测性**: 详细的日志和指标收集

这个实现提供了企业级的AI服务管理解决方案，完全满足了所有需求并提供了额外的企业级功能。