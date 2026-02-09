package com.codemate.features.ai.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络客户端
 * 负责处理HTTP请求和响应
 */
@Singleton
class NetworkClient @Inject constructor(
    private val cacheManager: CacheManager,
    private val networkMonitor: NetworkMonitor
) {
    
    /**
     * 执行HTTP请求
     */
    suspend fun makeRequest(
        endpoint: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: Any? = null,
        timeout: Int = 30000
    ): NetworkResponse {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                
                // 设置请求属性
                connection.requestMethod = method
                connection.connectTimeout = timeout
                connection.readTimeout = timeout
                connection.doInput = true
                connection.doOutput = body != null
                
                // 设置请求头
                headers.forEach { (key, value) ->
                    connection.setRequestProperty(key, value)
                }
                
                // 添加请求体
                body?.let { requestBody ->
                    val outputStream = connection.outputStream
                    val writer = OutputStreamWriter(outputStream, "UTF-8")
                    writer.write(gson.toJson(requestBody))
                    writer.flush()
                    writer.close()
                }
                
                // 检查响应状态
                val responseCode = connection.responseCode
                val responseMessage = connection.responseMessage
                
                // 读取响应
                val inputStream = if (responseCode in 200..299) {
                    connection.inputStream
                } else {
                    connection.errorStream
                }
                
                val responseBody = inputStream?.let { input ->
                    BufferedReader(InputStreamReader(input, "UTF-8")).use { reader ->
                        val response = StringBuilder()
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            response.append(line)
                            response.append("\n")
                        }
                        response.toString()
                    }
                } ?: ""
                
                val responseTime = System.currentTimeMillis() - startTime
                
                // 更新网络监控
                networkMonitor.recordRequest(
                    endpoint = endpoint,
                    method = method,
                    responseCode = responseCode,
                    responseTime = responseTime
                )
                
                // 缓存响应（如果是GET请求且成功）
                if (method == "GET" && responseCode == 200) {
                    cacheManager.cacheResponse(endpoint, responseBody, responseTime)
                }
                
                NetworkResponse(
                    isSuccess = responseCode in 200..299,
                    responseCode = responseCode,
                    responseMessage = responseMessage,
                    data = responseBody,
                    responseTime = responseTime
                )
                
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
    }
    
    /**
     * 执行流式HTTP请求
     */
    suspend fun makeStreamingRequest(
        endpoint: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: Any? = null,
        timeout: Int = 60000,
        onChunk: (String) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                
                // 设置请求属性
                connection.requestMethod = method
                connection.connectTimeout = timeout
                connection.readTimeout = timeout
                connection.doInput = true
                connection.doOutput = body != null
                connection.setRequestProperty("Accept", "text/event-stream")
                connection.setRequestProperty("Cache-Control", "no-cache")
                
                // 设置请求头
                headers.forEach { (key, value) ->
                    connection.setRequestProperty(key, value)
                }
                
                // 添加请求体
                body?.let { requestBody ->
                    val outputStream = connection.outputStream
                    val writer = OutputStreamWriter(outputStream, "UTF-8")
                    writer.write(gson.toJson(requestBody))
                    writer.flush()
                    writer.close()
                }
                
                // 检查响应状态
                val responseCode = connection.responseCode
                if (responseCode !in 200..299) {
                    throw IllegalStateException("HTTP错误: $responseCode")
                }
                
                // 读取流式响应
                val inputStream = connection.inputStream
                val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
                
                var line: String?
                val buffer = StringBuilder()
                
                while (reader.readLine().also { line = it } != null) {
                    line?.let { currentLine ->
                        if (currentLine.isNotEmpty()) {
                            if (currentLine.startsWith("data: ")) {
                                val data = currentLine.substring(6)
                                if (data != "[DONE]") {
                                    onChunk(data)
                                }
                            } else if (currentLine.startsWith("{")) {
                                // JSON格式的流式响应
                                onChunk(currentLine)
                            }
                        }
                    }
                }
                
                reader.close()
                
            } catch (e: Exception) {
                onChunk("错误: ${e.message}")
            }
        }
    }
    
    /**
     * 从缓存获取响应
     */
    suspend fun getCachedResponse(endpoint: String): NetworkResponse? {
        return withContext(Dispatchers.IO) {
            cacheManager.getCachedResponse(endpoint)
        }
    }
    
    /**
     * 清除缓存
     */
    suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            cacheManager.clearCache()
        }
    }
}

/**
 * 网络响应数据类
 */
data class NetworkResponse(
    val isSuccess: Boolean,
    val responseCode: Int,
    val responseMessage: String,
    val data: String,
    val responseTime: Long
)