package com.codemate.features.ai.domain.entity

/**
 * AI模型类型枚举
 */
enum class AIModelType {
    OPENAI_GPT_4,
    OPENAI_GPT_35_TURBO,
    ANTHROPIC_CLAUDE_3_OPUS,
    ANTHROPIC_CLAUDE_3_SONNET,
    ANTHROPIC_CLAUDE_3_HAIKU,
    LOCAL_LLM,
    CUSTOM_API
}

/**
 * AI服务提供商枚举
 */
enum class AIProvider {
    OPENAI,
    ANTHROPIC,
    LOCAL,
    CUSTOM
}

/**
 * AI请求类型
 */
enum class AIRequestType {
    CHAT,
    COMPLETION,
    EMBEDDING,
    IMAGE_GENERATION,
    IMAGE_ANALYSIS,
    CODE_GENERATION,
    CODE_REVIEW
}

/**
 * AI响应状态
 */
enum class AIResponseStatus {
    PENDING,
    STREAMING,
    COMPLETED,
    ERROR,
    CANCELLED
}