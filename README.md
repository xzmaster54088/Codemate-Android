
# 🎉 CodeMate Mobile - Android AI编程助手

[![Build Status](https://github.com/xzmaster54088/Codemate-Android/workflows/Build/badge.svg)](https://github.com/xzmaster54088/Codemate-Android/actions)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/kotlin-1.9.10-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/android-34%2B-green.svg)](https://developer.android.com/)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=codemate-mobile&metric=alert_status)](https://sonarcloud.io/dashboard?id=codemate-mobile)

> **Android AI编程助手应用** - 功能完整、技术先进的移动端编程解决方案

## 📱 项目概述

CodeMate Mobile是一个专为移动设备设计的AI编程助手应用，集成了代码编辑、AI辅助、编译执行、版本控制等完整开发功能。采用Clean Architecture + MVVM架构模式，使用最新的Android开发技术栈，提供企业级的代码质量、安全性和性能优化。

### 🎯 核心特性

- **🖥️ 专业代码编辑器** - 14种语言语法高亮，移动端触摸优化
- **🤖 AI智能助手** - 多AI提供商集成 (OpenAI GPT、Claude、本地LLM)
- **⚡ 本地编译引擎** - 多语言编译支持，智能错误解析
- **🐙 GitHub集成** - 智能版本控制，自动CHANGELOG生成
- **🛡️ 企业级安全** - 多层安全防护，数据加密存储
- **🚀 高性能优化** - 虚拟滚动，智能缓存，内存优化

## 🏗️ 技术架构

### 架构模式
- **Clean Architecture** - 清晰的业务逻辑分离
- **MVVM** - 响应式UI架构
- **Dependency Injection** - Hilt统一依赖管理

### 核心技术栈
- **开发语言**: Kotlin 1.9.10
- **UI框架**: Jetpack Compose + Material Design 3
- **架构组件**: ViewModel, StateFlow, Navigation
- **依赖注入**: Hilt
- **本地数据库**: Room
- **网络框架**: Retrofit + OkHttp
- **异步处理**: Kotlin Coroutines + Flow
- **代码编辑器**: CodeMirror Android
- **AI集成**: OpenAI GPT, Claude, ONNX Runtime

## 📁 项目结构

```
code/codemate_mobile/
├── app/                          # Android应用模块
│   ├── src/main/java/com/codemate/
│   │   ├── data/                # 数据层
│   │   ├── domain/              # 业务逻辑层
│   │   ├── features/            # 功能模块
│   │   │   ├── editor/         # 代码编辑器
│   │   │   ├── ai/             # AI服务管理
│   │   │   ├── compiler/       # 编译引擎
│   │   │   └── github/         # GitHub集成
│   │   ├── security/           # 安全模块
│   │   └── ui/                 # UI组件
│   ├── src/test/               # 单元测试
│   ├── src/androidTest/        # 集成测试
│   └── build.gradle            # 构建配置
├── .github/workflows/           # CI/CD配置
├── docs/                       # 技术文档
├── scripts/                    # 构建脚本
└── README.md                   # 项目说明
```

## 🚀 快速开始

### 环境要求

- **JDK**: 17+
- **Android SDK**: 34+
- **Gradle**: 8.2+
- **最低Android版本**: 6.0 (API 23)

### 本地构建

```bash
# 1. 克隆项目
git clone <your-repo-url>
cd codemate-mobile

# 2. 设置环境变量
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk

# 3. 快速构建
./quick-build.sh

# 4. 构建特定版本
./quick-build.sh debug      # Debug APK
./quick-build.sh release   # Release APK
./quick-build.sh test      # 运行测试

# 5. 完整构建选项
./build-local.sh --help     # 查看所有选项
```

### GitHub Actions自动构建

项目配置了完整的CI/CD流水线，支持：

- **自动触发**: 推送代码到main/develop分支
- **代码质量检查**: Detekt静态分析、ktlint代码风格
- **单元测试**: 完整的测试覆盖率报告
- **APK构建**: Debug和Release版本
- **集成测试**: Android模拟器自动化测试
- **安全扫描**: MobSF静态安全分析
- **自动部署**: Firebase App Distribution

## 📊 功能模块详情

### 1. 🖥️ 代码编辑器
- **多语言支持**: Kotlin、Java、Python、JavaScript、TypeScript、C++、C#、Go、Rust、Swift、XML、JSON、YAML、Markdown
- **触摸优化**: 双击选词、三击选行、长按菜单、拖拽选择
- **智能补全**: 关键词、代码片段、AI预测补全
- **主题切换**: 6种精美主题 (Light、Dark、Solarized、Monokai、GitHub)
- **性能优化**: 虚拟滚动、智能缓存、大文件处理

### 2. 🤖 AI服务管理
- **多AI提供商**: OpenAI GPT-4/3.5、Anthropic Claude、本地LLM
- **流式响应**: Server-Sent Events实时内容增量处理
- **对话管理**: 上下文维护、历史记录、状态持久化
- **安全过滤**: 敏感信息检测、恶意代码防护
- **性能监控**: 响应时间、成功率、错误统计

### 3. ⚡ 编译引擎
- **多语言支持**: Java/Kotlin、JavaScript、Python、C/C++、Rust、Go
- **Termux集成**: 本地编译环境、实时输出捕获
- **智能错误解析**: 多语言错误模式识别、自动修复建议
- **后台编译**: WorkManager后台任务、系统通知
- **性能分析**: 编译时间、内存使用、依赖分析

### 4. 🐙 GitHub集成
- **Git操作封装**: 完整的Git命令支持 (init、clone、commit、push等)
- **API集成**: GitHub REST API v3，仓库、Issue、PR、Actions管理
- **智能分类**: 机器学习自动分类commit消息
- **自动化**: CHANGELOG生成、代码质量分析、部署自动化
- **协作功能**: 实时代码分享、协同编辑、代码评审

### 5. 🛡️ 安全保护
- **数据加密**: AES-GCM + Android KeyStore硬件安全模块
- **网络安全**: 证书绑定、证书固定
- **代码安全**: 代码沙盒执行、反调试保护、反篡改
- **权限管理**: 精细化权限控制、最小权限原则
- **审计监控**: 安全事件记录、威胁检测、报告生成

## 🧪 测试覆盖

### 测试类型
- **单元测试**: ViewModel、Repository、UseCase (JUnit + Mockito)
- **集成测试**: API调用、数据库操作、文件操作 (MockWebServer + Room)
- **UI测试**: 用户界面和交互流程 (Espresso)
- **性能测试**: 内存、CPU、电池、网络资源监控
- **安全测试**: 代码质量、安全漏洞扫描

### 测试覆盖统计
- **代码覆盖率**: > 80%
- **测试用例数**: 200+
- **静态分析**: 0错误 (Detekt)
- **代码风格**: 100%通过 (ktlint)

## 📚 文档导航

| 文档 | 描述 |
|------|------|
| [BUILD_GUIDE.md](docs/BUILD_GUIDE.md) | 详细构建指南 |
| [DEPLOYMENT_GUIDE.md](docs/DEPLOYMENT_GUIDE.md) | 部署配置指南 |
| [PROJECT_COMPLETION_SUMMARY.md](PROJECT_COMPLETION_SUMMARY.md) | 项目完成总结 |
| [TEST_AND_DEPLOYMENT_CONFIG.md](docs/TEST_AND_DEPLOYMENT_CONFIG.md) | 测试和部署配置 |

## 🔧 配置说明

### API密钥配置
```bash
# 在 app/src/main/assets/config.properties 中配置
openai.api.key=your_openai_api_key
claude.api.key=your_claude_api_key
github.token=your_github_token
```

### 签名配置
```bash
# 创建 app/signing/signing.properties
storeFile=release.jks
storePassword=your_store_password
keyAlias=codemate-release
keyPassword=your_key_password
```

### 环境变量
```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/tools
```

## 📱 应用截图

*这里可以添加应用截图和功能演示*

## 🤝 贡献指南

我们欢迎所有形式的贡献！请查看 [CONTRIBUTING.md](CONTRIBUTING.md) 了解详细信息。

### 开发流程
1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🆘 获取帮助

- **问题报告**: [GitHub Issues](https://github.com/your-org/codemate-mobile/issues)
- **功能请求**: [GitHub Discussions](https://github.com/your-org/codemate-mobile/discussions)
- **技术文档**: [项目Wiki](https://github.com/your-org/codemate-mobile/wiki)
- **构建帮助**: [构建指南](docs/BUILD_GUIDE.md)

## 🎉 致谢

感谢所有为这个项目做出贡献的开发者和开源项目：

- [Jetpack Compose](https://developer.android.com/jetpack/compose) - 现代UI框架
- [Hilt](https://dagger.dev/hilt/) - 依赖注入
- [Room](https://developer.android.com/training/data-storage/room) - 本地数据库
- [Retrofit](https://square.github.io/retrofit/) - 网络框架
- [CodeMirror](https://codemirror.net/) - 代码编辑器

---

<div align="center">

**🚀 立即开始您的AI编程之旅！**

[开始构建](docs/BUILD_GUIDE.md) • [查看演示](#) • [报告问题](https://github.com/xzmaster54088/Codemate-Android)

---


</div>
