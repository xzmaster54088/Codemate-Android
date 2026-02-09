package com.codemate.features.ai.domain.entity

import java.util.*

/**
 * AI消息实体
 * @param id 消息唯一标识
 * @param role 消息角色（用户、助手、系统）
 * @param content 消息内容
 * @param timestamp 时间戳
 * @param metadata 额外元数据
 */
data class AIMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap(),
    val tokens: Int = 0
)

/**
 * 消息角色枚举
 */
enum class MessageRole {
    USER,
    ASSISTANT,
    SYSTEM
}

/**
 * 对话会话实体
 * @param id 对话唯一标识
 * @param title 对话标题
 * @param messages 消息列表
 * @param contextTokens 上下文令牌数
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新对话",
    val messages: MutableList<AIMessage> = mutableListOf(),
    var contextTokens: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * 对话上下文
 * @param systemPrompt 系统提示词
 * @param conversationHistory 对话历史
 * @param maxTokens 最大令牌数
 * @param temperature 创造性参数
 */
data class ConversationContext(
    val systemPrompt: String = "",
    val conversationHistory: List<AIMessage> = emptyList(),
    val maxTokens: Int = 4096,
    val temperature: Float = 0.7f,
    val topP: Float = 1.0f,
    val frequencyPenalty: Float = 0.0f,
    val presencePenalty: Float = 0.0f
)