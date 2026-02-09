package com.codemate.features.ai.utils

import com.codemate.features.ai.utils.AIConstants.*
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit网络客户端
 * 使用Retrofit库进行HTTP请求，提供更好的性能和功能
 */
@Singleton
class RetrofitNetworkClient @Inject constructor(
    private val gson: Gson,
    private val cacheManager: com.codemate.features.ai.data.repository.CacheManager,
    private val networkMonitor: com.codemate.features.ai.data.repository.NetworkMonitor
) {
    
    private val okHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val request = originalRequest.newBuilder()
                    .header("User-Agent", "CodeMate/1.0")
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
            .build()
    }
    
    private val openAIRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(OPENAI_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    private val anthropicRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl(ANTHROPIC_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    /**
     * 获取OpenAI API服务
     */
    fun getOpenAIService(): OpenAIRetrofitService {
        return openAIRetrofit.create(OpenAIRetrofitService::class.java)
    }
    
    /**
     * 获取Anthropic API服务
     */
    fun getAnthropicService(): AnthropicRetrofitService {
        return anthropicRetrofit.create(AnthropicRetrofitService::class.java)
    }
    
    /**
     * 执行通用HTTP请求
     */
    suspend fun makeRequest(
        endpoint: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: Any? = null,
        timeout: Long = DEFAULT_TIMEOUT_MS
    ): NetworkResponse = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val request = buildRequest(endpoint, method, headers, body)
            okHttpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                val responseTime = System.currentTimeMillis() - startTime
                
                // 更新网络监控
                networkMonitor.recordRequest(
                    endpoint = endpoint,
                    method = method,
                    responseCode = response.code,
                    responseTime = responseTime
                )
                
                // 缓存GET请求
                if (method == "GET" && response.code == 200) {
                    cacheManager.cacheResponse(endpoint, responseBody, responseTime)
                }
                
                NetworkResponse(
                    isSuccess = response.isSuccessful,
                    responseCode = response.code,
                    responseMessage = response.message,
                    data = responseBody,
                    responseTime = responseTime
                )
            }
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            
            // 更新网络监控
            networkMonitor.recordError(endpoint, method, e.message, responseTime)
            
            NetworkResponse(
                isSuccess = false,
                responseCode = -1,
                responseMessage = e.message ?: "网络请求失败",
                data = "",
                responseTime = responseTime
            )
        }
    }
    
    /**
     * 执行流式HTTP请求
     */
    suspend fun makeStreamingRequest(
        endpoint: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: Any? = null,
        timeout: Long = STREAMING_TIMEOUT_MS,
        onChunk: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest(endpoint, method, headers, body)
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    onChunk("错误: HTTP ${response.code}")
                    return@withContext
                }
                
                response.body?.source()?.let { source ->
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isNotEmpty()) {
                            when {
                                line.startsWith("data: ") -> {
                                    val data = line.substring(6)
                                    if (data != "[DONE]") {
                                        onChunk(data)
                                    }
                                }
                                line.startsWith("{") -> {
                                    // JSON格式的流式响应
                                    onChunk(line)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            onChunk("错误: ${e.message}")
        }
    }
    
    /**
     * 构建HTTP请求
     */
    private fun buildRequest(
        endpoint: String,
        method: String,
        headers: Map<String, String>,
        body: Any?
    ): Request {
        val builder = Request.Builder()
            .url(endpoint)
        
        // 设置方法
        when (method.uppercase()) {
            "GET" -> builder.get()
            "POST" -> {
                val requestBody = body?.let {
                    gson.toJson(it).toRequestBody("application/json".toMediaType())
                }
                builder.post(requestBody ?: "".toRequestBody())
            }
            "PUT" -> {
                val requestBody = body?.let {
                    gson.toJson(it).toRequestBody("application/json".toMediaType())
                }
                builder.put(requestBody ?: "".toRequestBody())
            }
            "DELETE" -> builder.delete()
            else -> builder.get()
        }
        
        // 设置请求头
        headers.forEach { (key, value) ->
            builder.addHeader(key, value)
        }
        
        return builder.build()
    }
}

/**
 * OpenAI Retrofit服务接口
 */
interface OpenAIRetrofitService {
    
    @retrofit2.http.POST("chat/completions")
    suspend fun createChatCompletion(
        @retrofit2.http.Body request: Map<String, Any>,
        @retrofit2.http.Header("Authorization") authorization: String
    ): OpenAIChatResponse
    
    @retrofit2.http.GET("models")
    suspend fun listModels(
        @retrofit2.http.Header("Authorization") authorization: String
    ): OpenAIModelsResponse
}

/**
 * Anthropic Retrofit服务接口
 */
interface AnthropicRetrofitService {
    
    @retrofit2.http.POST("messages")
    suspend fun createMessage(
        @retrofit2.http.Body request: Map<String, Any>,
        @retrofit2.http.Header("x-api-key") apiKey: String,
        @retrofit2.http.Header("anthropic-version") version: String = "2023-06-01"
    ): AnthropicMessageResponse
    
    @retrofit2.http.GET("messages")
    suspend fun listMessages(
        @retrofit2.http.Header("x-api-key") apiKey: String,
        @retrofit2.http.Header("anthropic-version") version: String = "2023-06-01"
    ): AnthropicMessagesResponse
}

/**
 * OpenAI响应数据类
 */
data class OpenAIChatResponse(
    val id: String,
    val `object`: String,
    val created: Long,
    val model: String,
    val choices: List<OpenAIChoice>,
    val usage: OpenAIUsage?
)

data class OpenAIChoice(
    val index: Int,
    val message: OpenAIMessage,
    val finish_reason: String
)

data class OpenAIMessage(
    val role: String,
    val content: String
)

data class OpenAIUsage(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int
)

data class OpenAIModelsResponse(
    val `object`: String,
    val data: List<OpenAIModel>
)

data class OpenAIModel(
    val id: String,
    val `object`: String,
    val created: Long,
    val owned_by: String
)

/**
 * Anthropic响应数据类
 */
data class AnthropicMessageResponse(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicContent>,
    val model: String,
    val stop_reason: String?,
    val stop_sequence: String?,
    val usage: AnthropicUsage
)

data class AnthropicContent(
    val type: String,
    val text: String
)

data class AnthropicUsage(
    val input_tokens: Int,
    val output_tokens: Int
)

data class AnthropicMessagesResponse(
    val data: List<AnthropicMessage>,
    val has_more: Boolean,
    val first_id: String?,
    val last_id: String?
)

data class AnthropicMessage(
    val id: String,
    val type: String,
    val role: String,
    val content: List<AnthropicContent>,
    val model: String,
    val stop_reason: String?,
    val stop_sequence: String?,
    val usage: AnthropicUsage
)