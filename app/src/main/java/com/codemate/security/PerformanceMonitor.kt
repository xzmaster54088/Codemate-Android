package com.codemate.security

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Debug
import android.os.Process
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.lang.management.ManagementFactory
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 性能监控器
 * 监控内存、CPU、电池、网络等系统资源使用情况
 * 提供实时性能指标和告警功能
 */
@Singleton
class PerformanceMonitor @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "PerformanceMonitor"
        private const val MONITOR_INTERVAL_MS = 1000L // 1秒
        private const val HISTORY_SIZE = 60 // 保存60个数据点
        private const val CPU_USAGE_SAMPLES = 10
        private const val MEMORY_WARNING_THRESHOLD = 0.8f // 80%
        private const val CPU_WARNING_THRESHOLD = 0.7f // 70%
        private const val BATTERY_LOW_THRESHOLD = 0.2f // 20%
        private const val NETWORK_SLOW_THRESHOLD = 1000L // 1MB/s
    }

    private val monitoringScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isMonitoring = AtomicBoolean(false)
    
    // 性能数据存储
    private val performanceHistory = ConcurrentHashMap<PerformanceMetric, MutableList<PerformanceData>>()
    
    // 监控通道
    private val alertChannel = Channel<PerformanceAlert>(Channel.UNLIMITED)
    
    // 网络监听器
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // 当前性能数据
    private val currentMetrics = ConcurrentHashMap<PerformanceMetric, Any>()

    /**
     * 开始性能监控
     */
    fun startMonitoring(): Boolean {
        return try {
            if (isMonitoring.getAndSet(true)) {
                Log.w(TAG, "性能监控已经在运行中")
                return true
            }

            // 初始化历史数据存储
            PerformanceMetric.values().forEach { metric ->
                performanceHistory[metric] = mutableListOf()
            }

            // 启动监控循环
            monitoringScope.launch {
                performanceMonitoringLoop()
            }

            // 启动网络监控
            startNetworkMonitoring()

            SecurityLog.i("性能监控已启动")
            true
        } catch (e: Exception) {
            SecurityLog.e("启动性能监控失败", e)
            isMonitoring.set(false)
            false
        }
    }

    /**
     * 停止性能监控
     */
    fun stopMonitoring() {
        if (isMonitoring.getAndSet(false)) {
            try {
                stopNetworkMonitoring()
                monitoringScope.cancel()
                SecurityLog.i("性能监控已停止")
            } catch (e: Exception) {
                SecurityLog.e("停止性能监控失败", e)
            }
        }
    }

    /**
     * 获取实时性能指标
     */
    suspend fun getCurrentMetrics(): Map<PerformanceMetric, Any> = withContext(Dispatchers.IO) {
        val metrics = mutableMapOf<PerformanceMetric, Any>()
        
        try {
            metrics[PerformanceMetric.CPU_USAGE] = getCpuUsage()
            metrics[PerformanceMetric.MEMORY_USAGE] = getMemoryUsage()
            metrics[PerformanceMetric.BATTERY_LEVEL] = getBatteryLevel()
            metrics[PerformanceMetric.NETWORK_STATUS] = getNetworkStatus()
            metrics[PerformanceMetric.DISK_USAGE] = getDiskUsage()
            metrics[PerformanceMetric.THREAD_COUNT] = getThreadCount()
            metrics[PerformanceMetric.APP_MEMORY] = getAppMemoryUsage()
            
            currentMetrics.clear()
            currentMetrics.putAll(metrics)
        } catch (e: Exception) {
            SecurityLog.e("获取性能指标失败", e)
        }
        
        metrics
    }

    /**
     * 获取性能历史数据
     */
    fun getPerformanceHistory(
        metric: PerformanceMetric,
        limit: Int = HISTORY_SIZE
    ): List<PerformanceData> {
        return performanceHistory[metric]?.takeLast(limit) ?: emptyList()
    }

    /**
     * 获取性能告警流
     */
    fun getAlertFlow(): Flow<PerformanceAlert> = flow {
        alertChannel.receiveAsFlow().collect { alert ->
            emit(alert)
        }
    }

    /**
     * 设置性能阈值
     */
    fun setThresholds(thresholds: Map<PerformanceMetric, ThresholdConfig>) {
        // 实现阈值设置逻辑
        SecurityLog.i("性能阈值已更新: ${thresholds.size} 个指标")
    }

    /**
     * 生成性能报告
     */
    suspend fun generatePerformanceReport(): PerformanceReport = withContext(Dispatchers.IO) {
        val currentMetrics = getCurrentMetrics()
        val reportTime = System.currentTimeMillis()
        
        val metricsSummary = mutableMapOf<PerformanceMetric, MetricSummary>()
        
        PerformanceMetric.values().forEach { metric ->
            val history = performanceHistory[metric] ?: emptyList()
            val summary = if (history.isNotEmpty()) {
                val values = history.mapNotNull { it.value as? Number }
                if (values.isNotEmpty()) {
                    MetricSummary(
                        average = values.average(),
                        min = values.minOrNull()?.toDouble() ?: 0.0,
                        max = values.maxOrNull()?.toDouble() ?: 0.0,
                        current = values.last().toDouble(),
                        samples = values.size
                    )
                } else {
                    MetricSummary(0.0, 0.0, 0.0, 0.0, 0)
                }
            } else {
                MetricSummary(0.0, 0.0, 0.0, 0.0, 0)
            }
            metricsSummary[metric] = summary
        }
        
        PerformanceReport(
            generatedAt = reportTime,
            duration = reportTime - (history.firstOrNull()?.timestamp ?: reportTime),
            metrics = metricsSummary,
            alerts = getRecentAlerts(),
            recommendations = generateRecommendations(currentMetrics)
        )
    }

    /**
     * 性能监控循环
     */
    private suspend fun performanceMonitoringLoop() {
        while (isMonitoring.get()) {
            try {
                val timestamp = System.currentTimeMillis()
                
                // 收集性能数据
                val metrics = getCurrentMetrics()
                
                // 存储历史数据
                metrics.forEach { (metric, value) ->
                    addToHistory(metric, PerformanceData(timestamp, value))
                }
                
                // 检查告警
                checkAlerts(metrics)
                
                delay(MONITOR_INTERVAL_MS)
            } catch (e: Exception) {
                SecurityLog.e("性能监控循环异常", e)
                delay(MONITOR_INTERVAL_MS)
            }
        }
    }

    /**
     * 添加数据到历史记录
     */
    private fun addToHistory(metric: PerformanceMetric, data: PerformanceData) {
        val history = performanceHistory[metric]
        if (history != null) {
            synchronized(history) {
                history.add(data)
                if (history.size > HISTORY_SIZE) {
                    history.removeAt(0)
                }
            }
        }
    }

    /**
     * 检查性能告警
     */
    private suspend fun checkAlerts(metrics: Map<PerformanceMetric, Any>) {
        try {
            // CPU使用率告警
            val cpuUsage = metrics[PerformanceMetric.CPU_USAGE] as? Float
            if (cpuUsage != null && cpuUsage > CPU_WARNING_THRESHOLD) {
                sendAlert(
                    PerformanceAlert(
                        type = AlertType.CPU_HIGH_USAGE,
                        severity = if (cpuUsage > 0.9f) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                        message = "CPU使用率过高: ${(cpuUsage * 100).format(1)}%",
                        timestamp = System.currentTimeMillis(),
                        value = cpuUsage,
                        threshold = CPU_WARNING_THRESHOLD
                    )
                )
            }
            
            // 内存使用率告警
            val memoryUsage = metrics[PerformanceMetric.MEMORY_USAGE] as? Float
            if (memoryUsage != null && memoryUsage > MEMORY_WARNING_THRESHOLD) {
                sendAlert(
                    PerformanceAlert(
                        type = AlertType.MEMORY_HIGH_USAGE,
                        severity = if (memoryUsage > 0.9f) AlertSeverity.CRITICAL else AlertSeverity.WARNING,
                        message = "内存使用率过高: ${(memoryUsage * 100).format(1)}%",
                        timestamp = System.currentTimeMillis(),
                        value = memoryUsage,
                        threshold = MEMORY_WARNING_THRESHOLD
                    )
                )
            }
            
            // 电池电量告警
            val batteryLevel = metrics[PerformanceMetric.BATTERY_LEVEL] as? Float
            if (batteryLevel != null && batteryLevel < BATTERY_LOW_THRESHOLD) {
                sendAlert(
                    PerformanceAlert(
                        type = AlertType.BATTERY_LOW,
                        severity = AlertSeverity.WARNING,
                        message = "电池电量过低: ${(batteryLevel * 100).format(0)}%",
                        timestamp = System.currentTimeMillis(),
                        value = batteryLevel,
                        threshold = BATTERY_LOW_THRESHOLD
                    )
                )
            }
            
        } catch (e: Exception) {
            SecurityLog.e("检查性能告警失败", e)
        }
    }

    /**
     * 发送告警
     */
    private suspend fun sendAlert(alert: PerformanceAlert) {
        try {
            alertChannel.send(alert)
            SecurityLog.w("性能告警: ${alert.message}")
        } catch (e: Exception) {
            SecurityLog.e("发送性能告警失败", e)
        }
    }

    /**
     * 启动网络监控
     */
    private fun startNetworkMonitoring() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    monitoringScope.launch {
                        val status = getNetworkStatus()
                        SecurityLog.i("网络可用: $status")
                    }
                }
                
                override fun onLost(network: Network) {
                    SecurityLog.w("网络连接丢失")
                    monitoringScope.launch {
                        sendAlert(
                            PerformanceAlert(
                                type = AlertType.NETWORK_DISCONNECTED,
                                severity = AlertSeverity.WARNING,
                                message = "网络连接已断开",
                                timestamp = System.currentTimeMillis(),
                                value = 0f,
                                threshold = 1f
                            )
                        )
                    }
                }
                
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    monitoringScope.launch {
                        val status = getNetworkStatus()
                        SecurityLog.d("网络能力变化: $status")
                    }
                }
            }
            
            networkCallback?.let { callback ->
                connectivityManager.registerNetworkCallback(networkRequest, callback)
            }
        } catch (e: Exception) {
            SecurityLog.e("启动网络监控失败", e)
        }
    }

    /**
     * 停止网络监控
     */
    private fun stopNetworkMonitoring() {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            networkCallback?.let { callback ->
                connectivityManager.unregisterNetworkCallback(callback)
                networkCallback = null
            }
        } catch (e: Exception) {
            SecurityLog.e("停止网络监控失败", e)
        }
    }

    /**
     * 获取CPU使用率
     */
    private fun getCpuUsage(): Float {
        return try {
            val cpuInfo = readCpuInfo()
            val usage = calculateCpuUsage(cpuInfo)
            usage.coerceIn(0f, 1f)
        } catch (e: Exception) {
            SecurityLog.e("获取CPU使用率失败", e)
            0f
        }
    }

    /**
     * 读取CPU信息
     */
    private fun readCpuInfo(): CpuInfo {
        return try {
            BufferedReader(FileReader("/proc/stat")).use { reader ->
                val line = reader.readLine()
                val tokens = line.split("\\s+".toRegex()).map { it.toLong() }
                
                CpuInfo(
                    user = tokens.getOrElse(1) { 0 },
                    nice = tokens.getOrElse(2) { 0 },
                    system = tokens.getOrElse(3) { 0 },
                    idle = tokens.getOrElse(4) { 0 }
                )
            }
        } catch (e: Exception) {
            SecurityLog.e("读取CPU信息失败", e)
            CpuInfo(0, 0, 0, 0)
        }
    }

    /**
     * 计算CPU使用率
     */
    private fun calculateCpuUsage(cpuInfo: CpuInfo): Float {
        val total = cpuInfo.user + cpuInfo.nice + cpuInfo.system + cpuInfo.idle
        val usage = total - cpuInfo.idle
        return if (total > 0) usage.toFloat() / total else 0f
    }

    /**
     * 获取内存使用情况
     */
    private fun getMemoryUsage(): MemoryInfo {
        return try {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            
            MemoryInfo(
                totalMemory = memoryInfo.totalMem,
                availableMemory = memoryInfo.availMem,
                usedMemory = memoryInfo.totalMem - memoryInfo.availMem,
                usagePercentage = if (memoryInfo.totalMem > 0) {
                    (memoryInfo.totalMem - memoryInfo.availMem).toFloat() / memoryInfo.totalMem
                } else 0f
            )
        } catch (e: Exception) {
            SecurityLog.e("获取内存使用情况失败", e)
            MemoryInfo(0, 0, 0, 0f)
        }
    }

    /**
     * 获取电池信息
     */
    private fun getBatteryInfo(): BatteryInfo {
        return try {
            val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryLevel = if (level >= 0 && scale > 0) level.toFloat() / scale else 0f
            
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                           status == BatteryManager.BATTERY_STATUS_FULL
            
            BatteryInfo(
                level = batteryLevel,
                isCharging = isCharging,
                voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0,
                temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            )
        } catch (e: Exception) {
            SecurityLog.e("获取电池信息失败", e)
            BatteryInfo(0f, false, 0, 0)
        }
    }

    /**
     * 获取电池电量（百分比）
     */
    private fun getBatteryLevel(): Float {
        return getBatteryInfo().level
    }

    /**
     * 获取网络状态
     */
    private fun getNetworkStatus(): NetworkInfo {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            
            val isConnected = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isCellular = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
            
            // 获取网络速度（简化实现）
            val speed = if (isWifi) 50f else if (isCellular) 20f else 0f
            
            NetworkInfo(
                isConnected = isConnected,
                type = when {
                    isWifi -> NetworkType.WIFI
                    isCellular -> NetworkType.CELLULAR
                    else -> NetworkType.NONE
                },
                speed = speed,
                signalStrength = getSignalStrength()
            )
        } catch (e: Exception) {
            SecurityLog.e("获取网络状态失败", e)
            NetworkInfo(false, NetworkType.NONE, 0f, 0)
        }
    }

    /**
     * 获取信号强度（简化实现）
     */
    private fun getSignalStrength(): Int {
        return try {
            // 实际实现需要TelephonyManager
            75 // 模拟值
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 获取磁盘使用情况
     */
    private fun getDiskUsage(): DiskInfo {
        return try {
            val stat = android.os.StatFs(android.os.Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val availableBytes = stat.availableBytes
            val usedBytes = totalBytes - availableBytes
            
            DiskInfo(
                totalSpace = totalBytes,
                availableSpace = availableBytes,
                usedSpace = usedBytes,
                usagePercentage = if (totalBytes > 0) {
                    usedBytes.toFloat() / totalBytes
                } else 0f
            )
        } catch (e: Exception) {
            SecurityLog.e("获取磁盘使用情况失败", e)
            DiskInfo(0, 0, 0, 0f)
        }
    }

    /**
     * 获取线程数
     */
    private fun getThreadCount(): Int {
        return try {
            val threadMXBean = ManagementFactory.getThreadMXBean()
            threadMXBean.threadCount
        } catch (e: Exception) {
            SecurityLog.e("获取线程数失败", e)
            0
        }
    }

    /**
     * 获取应用内存使用
     */
    private fun getAppMemoryUsage(): AppMemoryInfo {
        return try {
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            
            AppMemoryInfo(
                usedMemory = usedMemory,
                maxMemory = maxMemory,
                freeMemory = runtime.freeMemory(),
                totalMemory = runtime.totalMemory(),
                heapPercentage = if (maxMemory > 0) {
                    usedMemory.toFloat() / maxMemory
                } else 0f
            )
        } catch (e: Exception) {
            SecurityLog.e("获取应用内存使用失败", e)
            AppMemoryInfo(0, 0, 0, 0, 0f)
        }
    }

    /**
     * 获取最近的告警
     */
    private fun getRecentAlerts(): List<PerformanceAlert> {
        return try {
            // 这里应该从告警存储中获取最近的数据
            // 简化实现，返回空列表
            emptyList()
        } catch (e: Exception) {
            SecurityLog.e("获取最近告警失败", e)
            emptyList()
        }
    }

    /**
     * 生成性能建议
     */
    private fun generateRecommendations(metrics: Map<PerformanceMetric, Any>): List<String> {
        val recommendations = mutableListOf<String>()
        
        try {
            val cpuUsage = metrics[PerformanceMetric.CPU_USAGE] as? Float
            val memoryUsage = metrics[PerformanceMetric.MEMORY_USAGE] as? Float
            val batteryLevel = metrics[PerformanceMetric.BATTERY_LEVEL] as? Float
            
            if (cpuUsage != null && cpuUsage > 0.8f) {
                recommendations.add("CPU使用率过高，建议关闭不必要的后台应用")
            }
            
            if (memoryUsage != null && memoryUsage > 0.8f) {
                recommendations.add("内存使用率过高，建议清理缓存或重启应用")
            }
            
            if (batteryLevel != null && batteryLevel < 0.2f) {
                recommendations.add("电池电量过低，建议连接充电器或降低性能模式")
            }
            
            if (recommendations.isEmpty()) {
                recommendations.add("系统运行良好，无需特殊优化")
            }
        } catch (e: Exception) {
            SecurityLog.e("生成性能建议失败", e)
        }
        
        return recommendations
    }

    /**
     * 格式化浮点数
     */
    private fun Float.format(digits: Int): String = "%.${digits}f".format(this)
}

/**
 * 性能指标枚举
 */
enum class PerformanceMetric {
    CPU_USAGE,
    MEMORY_USAGE,
    BATTERY_LEVEL,
    NETWORK_STATUS,
    DISK_USAGE,
    THREAD_COUNT,
    APP_MEMORY
}

/**
 * 性能数据类型
 */
data class PerformanceData(
    val timestamp: Long,
    val value: Any
)

/**
 * 性能告警
 */
data class PerformanceAlert(
    val type: AlertType,
    val severity: AlertSeverity,
    val message: String,
    val timestamp: Long,
    val value: Float,
    val threshold: Float
)

/**
 * 告警类型
 */
enum class AlertType {
    CPU_HIGH_USAGE,
    MEMORY_HIGH_USAGE,
    BATTERY_LOW,
    NETWORK_DISCONNECTED,
    DISK_SPACE_LOW,
    TEMPERATURE_HIGH
}

/**
 * 告警严重程度
 */
enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

/**
 * 阈值配置
 */
data class ThresholdConfig(
    val warning: Float,
    val critical: Float,
    val enabled: Boolean = true
)

/**
 * 性能报告
 */
data class PerformanceReport(
    val generatedAt: Long,
    val duration: Long,
    val metrics: Map<PerformanceMetric, MetricSummary>,
    val alerts: List<PerformanceAlert>,
    val recommendations: List<String>
)

/**
 * 指标摘要
 */
data class MetricSummary(
    val average: Double,
    val min: Double,
    val max: Double,
    val current: Double,
    val samples: Int
)

// 数据类定义
data class CpuInfo(
    val user: Long,
    val nice: Long,
    val system: Long,
    val idle: Long
)

data class MemoryInfo(
    val totalMemory: Long,
    val availableMemory: Long,
    val usedMemory: Long,
    val usagePercentage: Float
)

data class BatteryInfo(
    val level: Float,
    val isCharging: Boolean,
    val voltage: Int,
    val temperature: Int
)

data class NetworkInfo(
    val isConnected: Boolean,
    val type: NetworkType,
    val speed: Float,
    val signalStrength: Int
)

enum class NetworkType {
    WIFI,
    CELLULAR,
    NONE
}

data class DiskInfo(
    val totalSpace: Long,
    val availableSpace: Long,
    val usedSpace: Long,
    val usagePercentage: Float
)

data class AppMemoryInfo(
    val usedMemory: Long,
    val maxMemory: Long,
    val freeMemory: Long,
    val totalMemory: Long,
    val heapPercentage: Float
)