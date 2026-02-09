package com.codemate.features.ai.data.repository

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 网络监控器
 * 监控网络状态和连接信息
 */
@Singleton
class NetworkMonitor @Inject constructor(
    private val context: Context
) {
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val _networkStatus = Channel<NetworkStatus>(Channel.UNLIMITED)
    
    private var currentNetwork: Network? = null
    private var currentCapabilities: NetworkCapabilities? = null
    
    init {
        setupNetworkCallback()
    }
    
    /**
     * 获取网络状态流
     */
    fun getNetworkStatusFlow(): Flow<NetworkStatus> = _networkStatus.receiveAsFlow()
    
    /**
     * 获取当前网络状态
     */
    fun getCurrentNetworkStatus(): NetworkStatus {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        
        return NetworkStatus(
            isConnected = capabilities != null,
            networkType = getNetworkType(capabilities),
            isWifi = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_WIFI) == true,
            isMobile = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_MOBILE) == true,
            isEthernet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_ETHERNET) == true,
            signalStrength = getSignalStrength()
        )
    }
    
    /**
     * 记录网络请求
     */
    fun recordRequest(
        endpoint: String,
        method: String,
        responseCode: Int,
        responseTime: Long
    ) {
        // 这里可以记录到数据库或日志
        println("网络请求: $method $endpoint - 状态码: $responseCode - 耗时: ${responseTime}ms")
    }
    
    /**
     * 记录网络错误
     */
    fun recordError(
        endpoint: String,
        method: String,
        error: String?,
        responseTime: Long
    ) {
        // 这里可以记录到数据库或日志
        println("网络错误: $method $endpoint - 错误: $error - 耗时: ${responseTime}ms")
    }
    
    /**
     * 检查网络是否可用
     */
    fun isNetworkAvailable(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        return capabilities != null && 
               (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
    }
    
    /**
     * 检查是否是移动网络
     */
    fun isMobileNetwork(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_MOBILE) == true
    }
    
    /**
     * 检查是否是WiFi网络
     */
    fun isWifiNetwork(): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_WIFI) == true
    }
    
    /**
     * 获取网络延迟
     */
    suspend fun getNetworkLatency(): Long {
        // 简化的延迟检测，ping一个已知的主机
        return try {
            val startTime = System.currentTimeMillis()
            
            // 这里应该执行真正的ping操作
            // 为了演示，返回模拟值
            delay(100)
            
            System.currentTimeMillis() - startTime
        } catch (e: Exception) {
            -1L // 表示无法测量
        }
    }
    
    /**
     * 获取网络类型
     */
    private fun getNetworkType(capabilities: NetworkCapabilities?): NetworkType {
        return when {
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_WIFI) == true -> NetworkType.WIFI
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_MOBILE) == true -> NetworkType.MOBILE
            capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_ETHERNET) == true -> NetworkType.ETHERNET
            else -> NetworkType.UNKNOWN
        }
    }
    
    /**
     * 获取信号强度（简化实现）
     */
    private fun getSignalStrength(): SignalStrength {
        return SignalStrength.MEDIUM
    }
    
    /**
     * 设置网络回调
     */
    private fun setupNetworkCallback() {
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                currentNetwork = network
                currentCapabilities = connectivityManager.getNetworkCapabilities(network)
                
                val status = getCurrentNetworkStatus()
                _networkStatus.trySend(status)
            }
            
            override fun onLost(network: Network) {
                currentNetwork = null
                currentCapabilities = null
                
                _networkStatus.trySend(
                    NetworkStatus(
                        isConnected = false,
                        networkType = NetworkType.NONE,
                        isWifi = false,
                        isMobile = false,
                        isEthernet = false,
                        signalStrength = SignalStrength.NONE
                    )
                )
            }
            
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                currentNetwork = network
                currentCapabilities = capabilities
                
                val status = getCurrentNetworkStatus()
                _networkStatus.trySend(status)
            }
        }
        
        connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        // 这里应该注销网络回调
        // connectivityManager.unregisterNetworkCallback(networkCallback)
    }
}

/**
 * 网络状态
 */
data class NetworkStatus(
    val isConnected: Boolean,
    val networkType: NetworkType,
    val isWifi: Boolean,
    val isMobile: Boolean,
    val isEthernet: Boolean,
    val signalStrength: SignalStrength
)

/**
 * 网络类型
 */
enum class NetworkType {
    WIFI,
    MOBILE,
    ETHERNET,
    NONE,
    UNKNOWN
}

/**
 * 信号强度
 */
enum class SignalStrength {
    NONE,
    WEAK,
    MEDIUM,
    STRONG,
    EXCELLENT
}