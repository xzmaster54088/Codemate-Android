package com.codemate.features.ai.utils

/**
 * AI服务管理器常量
 * 定义所有相关的常量和配置
 */
object AIConstants {
    
    // ===== API 端点 =====
    const val OPENAI_BASE_URL = "https://api.openai.com/v1/"
    const val ANTHROPIC_BASE_URL = "https://api.anthropic.com/v1/"
    const val DEFAULT_TIMEOUT_MS = 30000L
    const val STREAMING_TIMEOUT_MS = 60000L
    
    // ===== 模型配置 =====
    const val DEFAULT_MAX_TOKENS = 2048
    const val DEFAULT_TEMPERATURE = 0.7f
    const val DEFAULT_TOP_P = 1.0f
    const val DEFAULT_TOP_K = 50
    
    // ===== 缓存配置 =====
    const val CACHE_SIZE_MB = 50L
    const val CACHE_EXPIRY_HOURS = 24L
    const val MODEL_CACHE_SIZE_MB = 100L
    
    // ===== 内存管理 =====
    const val MAX_MEMORY_THRESHOLD = 0.8f // 80%
    const val MIN_FREE_MEMORY_MB = 100L
    
    // ===== 重试配置 =====
    const val MAX_RETRY_ATTEMPTS = 3
    const val RETRY_DELAY_MS = 1000L
    const val RETRY_BACKOFF_MULTIPLIER = 2.0f
    
    // ===== 安全配置 =====
    const val MAX_INPUT_LENGTH = 10000
    const val MAX_OUTPUT_LENGTH = 4000
    val BANNED_PATTERNS = listOf(
        "password",
        "secret",
        "key",
        "token",
        "credential"
    )
    
    // ===== 本地模型配置 =====
    const val LOCAL_MODEL_PATH = "/models/"
    const val DEFAULT_QUANTIZATION = "int8"
    const val MAX_CONTEXT_LENGTH = 2048
    
    // ===== 流式配置 =====
    const val STREAM_CHUNK_SIZE = 1024
    const val STREAM_BUFFER_SIZE = 8192
    
    // ===== 网络配置 =====
    const val CONNECT_TIMEOUT = 30
    const val READ_TIMEOUT = 30
    const val WRITE_TIMEOUT = 30
    
    // ===== 错误代码 =====
    const val ERROR_NETWORK = "NETWORK_ERROR"
    const val ERROR_TIMEOUT = "TIMEOUT_ERROR"
    const val ERROR_RATE_LIMIT = "RATE_LIMIT_ERROR"
    const val ERROR_AUTHENTICATION = "AUTH_ERROR"
    const val ERROR_INVALID_REQUEST = "INVALID_REQUEST"
    const val ERROR_SERVER_ERROR = "SERVER_ERROR"
    const val ERROR_QUOTA_EXCEEDED = "QUOTA_EXCEEDED"
    const val ERROR_CONTENT_FILTERED = "CONTENT_FILTERED"
    
    // ===== 日志标签 =====
    const val TAG_AI_SERVICE = "CodeMate_AI_Service"
    const val TAG_NETWORK = "CodeMate_Network"
    const val TAG_CACHE = "CodeMate_Cache"
    const val TAG_MEMORY = "CodeMate_Memory"
    const val TAG_SAFETY = "CodeMate_Safety"
}