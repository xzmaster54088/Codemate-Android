---
AIGC:
    ContentProducer: Minimax Agent AI
    ContentPropagator: Minimax Agent AI
    Label: AIGC
    ProduceID: "00000000000000000000000000000000"
    PropagateID: "00000000000000000000000000000000"
    ReservedCode1: 30460221009ea51b668f43e8b64b3710ea629d4b81bee4359cf984c21fb4475fbc10c86ebe0221009afc7d0576b45a640df7e29d313871c843ecf0fa623f7e01f6729853366a132e
    ReservedCode2: 3046022100c4577cfb09e77cb690c6fc7b262ccc5352e717d5a13843f77b02e5badee0bfb00221008ad072865be12c2ccfb1c1f9b3613c6b90d750945413ca20715cf774768dbc81
---

# CodeMate AI服务管理器

## 概述
为CodeMate Mobile实现的完整AI服务管理器，支持多种AI提供商和本地模型推理。

## 架构设计

### Clean Architecture层次
- **Domain Layer**: 业务逻辑和实体
- **Data Layer**: 数据存储和获取
- **Presentation Layer**: UI层和状态管理

### 支持的AI服务
1. **OpenAI GPT**: GPT-4, GPT-3.5-turbo
2. **Anthropic Claude**: Claude-3系列模型
3. **本地LLM**: 通过ONNX Runtime Mobile
4. **自定义API**: 支持用户自定义端点

### 主要功能
- 流式响应处理
- 对话上下文管理
- AI安全过滤
- 服务健康检查
- 错误重试机制
- 缓存优化
- 内存管理

## 模块结构
```
ai/
├── domain/
│   ├── entity/          # 业务实体
│   ├── usecase/         # 用例
│   └── repository/      # 仓储接口
├── data/
│   ├── repository/      # 仓储实现
│   ├── local/           # 本地存储
│   ├── remote/          # 远程服务
│   └── model/           # 数据模型
└── presentation/
    ├── ui/              # UI组件
    ├── viewmodel/       # 状态管理
    └── utils/           # 工具类
```