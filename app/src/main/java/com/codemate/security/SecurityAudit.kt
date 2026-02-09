package com.codemate.security

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.os.Process
import android.provider.Settings
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安全审计模块
 * 记录安全事件、检测威胁、生成安全报告
 * 提供全面的安全监控和分析功能
 */
@Singleton
class SecurityAudit @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "SecurityAudit"
        private const val MAX_AUDIT_LOGS = 10000
        private const val AUDIT_RETENTION_DAYS = 30
        private const val THREAT_SCAN_INTERVAL = 60000L // 1分钟
        private const val REPORT_GENERATION_INTERVAL = 3600000L // 1小时
        
        // 安全威胁阈值
        private const val FAILED_LOGIN_THRESHOLD = 5
        private const val SUSPICIOUS_NETWORK_THRESHOLD = 10
        private const val ANOMALY_SCORE_THRESHOLD = 0.7f
    }

    private val auditScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val scanScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val isAuditing = AtomicBoolean(false)
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    // 审计日志存储
    private val auditLogs = mutableListOf<SecurityEvent>()
    private val threatDetections = ConcurrentHashMap<String, ThreatDetection>()
    private val securityMetrics = SecurityMetrics()
    
    // 事件通道
    private val eventChannel = Channel<SecurityEvent>(Channel.UNLIMITED)
    
    // 威胁检测器
    private val threatDetectors = listOf(
        NetworkThreatDetector(),
        SystemThreatDetector(),
        ApplicationThreatDetector(),
        DataThreatDetector()
    )
    
    // 安全规则
    private val securityRules = mutableListOf<SecurityRule>()

    /**
     * 启动安全审计
     */
    fun startSecurityAudit(): Boolean {
        return try {
            if (isAuditing.getAndSet(true)) {
                Log.w(TAG, "安全审计已经在运行中")
                return true
            }

            // 初始化安全规则
            initializeSecurityRules()
            
            // 启动审计循环
            auditScope.launch {
                auditLoop()
            }
            
            // 启动威胁扫描
            scanScope.launch {
                threatScanLoop()
            }
            
            // 启动自动报告生成
            scanScope.launch {
                reportGenerationLoop()
            }

            SecurityLog.i("安全审计已启动")
            logSecurityEvent(
                eventType = SecurityEventType.SYSTEM,
                severity = SecuritySeverity.INFO,
                description = "安全审计系统已启动",
                metadata = mapOf("version" to "1.0.0")
            )
            
            true
        } catch (e: Exception) {
            SecurityLog.e("启动安全审计失败", e)
            isAuditing.set(false)
            false
        }
    }

    /**
     * 停止安全审计
     */
    fun stopSecurityAudit() {
        if (isAuditing.getAndSet(false)) {
            try {
                auditScope.cancel()
                scanScope.cancel()
                eventChannel.close()
                
                SecurityLog.i("安全审计已停止")
                logSecurityEvent(
                    eventType = SecurityEventType.SYSTEM,
                    severity = SecuritySeverity.INFO,
                    description = "安全审计系统已停止"
                )
            } catch (e: Exception) {
                SecurityLog.e("停止安全审计失败", e)
            }
        }
    }

    /**
     * 记录安全事件
     */
    fun logSecurityEvent(
        eventType: SecurityEventType,
        severity: SecuritySeverity,
        description: String,
        source: String = "System",
        metadata: Map<String, Any> = emptyMap(),
        timestamp: Long = System.currentTimeMillis()
    ) {
        try {
            val event = SecurityEvent(
                id = generateEventId(),
                timestamp = timestamp,
                eventType = eventType,
                severity = severity,
                description = description,
                source = source,
                metadata = metadata,
                riskScore = calculateRiskScore(eventType, severity, metadata)
            )
            
            synchronized(auditLogs) {
                auditLogs.add(event)
                
                // 保持日志数量限制
                if (auditLogs.size > MAX_AUDIT_LOGS) {
                    auditLogs.removeFirst()
                }
            }
            
            // 发送到事件通道
            auditScope.launch {
                try {
                    eventChannel.send(event)
                } catch (e: Exception) {
                    SecurityLog.e("发送安全事件失败", e)
                }
            }
            
            // 检查是否需要触发威胁检测
            if (severity == SecuritySeverity.HIGH || severity == SecuritySeverity.CRITICAL) {
                auditScope.launch {
                    triggerThreatDetection(event)
                }
            }
            
            SecurityLog.d("安全事件已记录: ${eventType.name} - $description")
        } catch (e: Exception) {
            SecurityLog.e("记录安全事件失败", e)
        }
    }

    /**
     * 获取安全事件
     */
    suspend fun getSecurityEvents(
        startTime: Long = System.currentTimeMillis() - 24 * 3600 * 1000, // 默认24小时
        endTime: Long = System.currentTimeMillis(),
        severity: SecuritySeverity? = null,
        eventType: SecurityEventType? = null,
        limit: Int = 1000
    ): List<SecurityEvent> = withContext(Dispatchers.IO) {
        return@withContext synchronized(auditLogs) {
            auditLogs.filter { event ->
                event.timestamp in startTime..endTime &&
                (severity == null || event.severity == severity) &&
                (eventType == null || event.eventType == eventType)
            }.takeLast(limit)
        }
    }

    /**
     * 获取威胁检测结果
     */
    suspend fun getThreatDetections(): List<ThreatDetection> = withContext(Dispatchers.IO) {
        return@withContext threatDetections.values.toList()
    }

    /**
     * 生成安全报告
     */
    suspend fun generateSecurityReport(
        startTime: Long = System.currentTimeMillis() - 24 * 3600 * 1000,
        endTime: Long = System.currentTimeMillis()
    ): SecurityReport = withContext(Dispatchers.IO) {
        return@withContext try {
            val events = getSecurityEvents(startTime, endTime)
            val threats = threatDetections.values.filter { 
                it.timestamp in startTime..endTime 
            }
            
            val report = SecurityReport(
                id = generateReportId(),
                generatedAt = System.currentTimeMillis(),
                period = ReportPeriod(startTime, endTime),
                summary = SecurityReportSummary(
                    totalEvents = events.size,
                    highSeverityEvents = events.count { it.severity == SecuritySeverity.HIGH },
                    criticalEvents = events.count { it.severity == SecuritySeverity.CRITICAL },
                    threatsDetected = threats.size,
                    activeThreats = threats.count { it.status == ThreatStatus.ACTIVE },
                    resolvedThreats = threats.count { it.status == ThreatStatus.RESOLVED },
                    averageRiskScore = if (events.isNotEmpty()) {
                        events.map { it.riskScore }.average()
                    } else 0.0
                ),
                events = events,
                threats = threats,
                recommendations = generateSecurityRecommendations(events, threats),
                metrics = securityMetrics.getMetricsSnapshot()
            )
            
            SecurityLog.i("安全报告已生成: ${report.id}")
            report
        } catch (e: Exception) {
            SecurityLog.e("生成安全报告失败", e)
            SecurityReport.empty()
        }
    }

    /**
     * 执行威胁扫描
     */
    suspend fun performThreatScan(): ThreatScanResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val scanStartTime = System.currentTimeMillis()
            val detectedThreats = mutableListOf<ThreatDetection>()
            
            // 并行执行威胁检测
            val scanResults = threatDetectors.map { detector ->
                async {
                    try {
                        detector.detectThreats(context)
                    } catch (e: Exception) {
                        SecurityLog.e("威胁检测器执行失败: ${detector.javaClass.simpleName}", e)
                        emptyList<ThreatDetection>()
                    }
                }
            }.awaitAll()
            
            // 合并结果
            scanResults.forEach { results ->
                detectedThreats.addAll(results)
            }
            
            // 更新威胁检测结果
            detectedThreats.forEach { threat ->
                threatDetections[threat.id] = threat
                
                // 记录威胁事件
                logSecurityEvent(
                    eventType = SecurityEventType.THREAT_DETECTED,
                    severity = threat.severity,
                    description = "威胁检测: ${threat.description}",
                    metadata = mapOf(
                        "threatId" to threat.id,
                        "threatType" to threat.type.name,
                        "riskScore" to threat.riskScore
                    )
                )
            }
            
            val scanEndTime = System.currentTimeMillis()
            
            ThreatScanResult(
                success = true,
                scanId = generateScanId(),
                startTime = scanStartTime,
                endTime = scanEndTime,
                duration = scanEndTime - scanStartTime,
                threatsDetected = detectedThreats.size,
                threats = detectedThreats,
                error = null
            )
        } catch (e: Exception) {
            SecurityLog.e("执行威胁扫描失败", e)
            ThreatScanResult(
                success = false,
                scanId = "",
                startTime = System.currentTimeMillis(),
                endTime = System.currentTimeMillis(),
                duration = 0,
                threatsDetected = 0,
                threats = emptyList(),
                error = e.message
            )
        }
    }

    /**
     * 解决威胁
     */
    suspend fun resolveThreat(threatId: String, action: ThreatResolutionAction): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val threat = threatDetections[threatId] ?: return@withContext false
            
            val resolvedThreat = threat.copy(
                status = ThreatStatus.RESOLVED,
                resolution = ThreatResolution(
                    action = action,
                    resolvedAt = System.currentTimeMillis(),
                    resolvedBy = "System",
                    notes = action.description
                )
            )
            
            threatDetections[threatId] = resolvedThreat
            
            // 记录解决事件
            logSecurityEvent(
                eventType = SecurityEventType.THREAT_RESOLVED,
                severity = SecuritySeverity.INFO,
                description = "威胁已解决: ${threat.description}",
                metadata = mapOf(
                    "threatId" to threatId,
                    "action" to action.name
                )
            )
            
            SecurityLog.i("威胁已解决: $threatId")
            true
        } catch (e: Exception) {
            SecurityLog.e("解决威胁失败: $threatId", e)
            false
        }
    }

    /**
     * 添加安全规则
     */
    fun addSecurityRule(rule: SecurityRule) {
        try {
            securityRules.add(rule)
            SecurityLog.i("安全规则已添加: ${rule.name}")
            
            logSecurityEvent(
                eventType = SecurityEventType.SYSTEM,
                severity = SecuritySeverity.INFO,
                description = "安全规则已添加: ${rule.name}",
                metadata = mapOf(
                    "ruleId" to rule.id,
                    "ruleType" to rule.type.name
                )
            )
        } catch (e: Exception) {
            SecurityLog.e("添加安全规则失败", e)
        }
    }

    /**
     * 获取安全事件流
     */
    fun getSecurityEventFlow(): Flow<SecurityEvent> = flow {
        eventChannel.receiveAsFlow().collect { event ->
            emit(event)
        }
    }

    /**
     * 导出审计日志
     */
    suspend fun exportAuditLog(
        format: ExportFormat = ExportFormat.JSON,
        startTime: Long = System.currentTimeMillis() - 7 * 24 * 3600 * 1000, // 默认7天
        endTime: Long = System.currentTimeMillis()
    ): ExportResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val events = getSecurityEvents(startTime, endTime)
            val content = when (format) {
                ExportFormat.JSON -> json.encodeToString(events)
                ExportFormat.CSV -> convertToCsv(events)
                ExportFormat.PDF -> generatePdfReport(events) // 需要PDF库支持
            }
            
            ExportResult(
                success = true,
                content = content,
                format = format,
                recordCount = events.size,
                error = null
            )
        } catch (e: Exception) {
            SecurityLog.e("导出审计日志失败", e)
            ExportResult(
                success = false,
                content = "",
                format = format,
                recordCount = 0,
                error = e.message
            )
        }
    }

    /**
     * 审计循环
     */
    private suspend fun auditLoop() {
        while (isAuditing.get()) {
            try {
                // 执行安全规则检查
                executeSecurityRules()
                
                // 清理过期日志
                cleanupExpiredLogs()
                
                delay(5000) // 5秒间隔
            } catch (e: Exception) {
                SecurityLog.e("审计循环异常", e)
                delay(5000)
            }
        }
    }

    /**
     * 威胁扫描循环
     */
    private suspend fun threatScanLoop() {
        while (isAuditing.get()) {
            try {
                performThreatScan()
                delay(THREAT_SCAN_INTERVAL)
            } catch (e: Exception) {
                SecurityLog.e("威胁扫描循环异常", e)
                delay(THREAT_SCAN_INTERVAL)
            }
        }
    }

    /**
     * 报告生成循环
     */
    private suspend fun reportGenerationLoop() {
        while (isAuditing.get()) {
            try {
                val lastReport = securityMetrics.lastReportTime
                val now = System.currentTimeMillis()
                
                if (now - lastReport >= REPORT_GENERATION_INTERVAL) {
                    generateSecurityReport()
                    securityMetrics.lastReportTime = now
                }
                
                delay(60000) // 1分钟检查一次
            } catch (e: Exception) {
                SecurityLog.e("报告生成循环异常", e)
                delay(60000)
            }
        }
    }

    /**
     * 初始化安全规则
     */
    private fun initializeSecurityRules() {
        // 添加默认安全规则
        securityRules.addAll(listOf(
            SecurityRule(
                id = "rule_001",
                name = "重复登录失败检测",
                type = SecurityRuleType.AUTHENTICATION,
                condition = { event ->
                    event.eventType == SecurityEventType.LOGIN_FAILED &&
                    event.source == "AuthenticationSystem"
                },
                action = ThreatAction.REPORT,
                enabled = true
            ),
            SecurityRule(
                id = "rule_002", 
                name = "异常网络活动检测",
                type = SecurityRuleType.NETWORK,
                condition = { event ->
                    event.eventType == SecurityEventType.NETWORK_ANOMALY
                },
                action = ThreatAction.BLOCK,
                enabled = true
            ),
            SecurityRule(
                id = "rule_003",
                name = "高风险权限申请检测",
                type = SecurityRuleType.PERMISSION,
                condition = { event ->
                    event.metadata["permission_type"] == "dangerous" &&
                    event.riskScore > 0.8
                },
                action = ThreatAction.ALERT,
                enabled = true
            )
        ))
    }

    /**
     * 执行安全规则
     */
    private suspend fun executeSecurityRules() {
        try {
            val recentEvents = getSecurityEvents(
                startTime = System.currentTimeMillis() - 60000, // 最近1分钟
                endTime = System.currentTimeMillis(),
                limit = 100
            )
            
            securityRules.filter { it.enabled }.forEach { rule ->
                recentEvents.filter { rule.condition(it) }.forEach { event ->
                    when (rule.action) {
                        ThreatAction.BLOCK -> {
                            logSecurityEvent(
                                eventType = SecurityEventType.SECURITY_VIOLATION,
                                severity = SecuritySeverity.HIGH,
                                description = "安全规则触发: ${rule.name}",
                                metadata = mapOf(
                                    "ruleId" to rule.id,
                                    "triggeredEvent" to event.id
                                )
                            )
                        }
                        ThreatAction.ALERT -> {
                            logSecurityEvent(
                                eventType = SecurityEventType.SECURITY_ALERT,
                                severity = SecuritySeverity.MEDIUM,
                                description = "安全规则告警: ${rule.name}",
                                metadata = mapOf(
                                    "ruleId" to rule.id,
                                    "triggeredEvent" to event.id
                                )
                            )
                        }
                        ThreatAction.REPORT -> {
                            logSecurityEvent(
                                eventType = SecurityEventType.SECURITY_REPORT,
                                severity = SecuritySeverity.INFO,
                                description = "安全规则报告: ${rule.name}",
                                metadata = mapOf(
                                    "ruleId" to rule.id,
                                    "triggeredEvent" to event.id
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            SecurityLog.e("执行安全规则失败", e)
        }
    }

    /**
     * 清理过期日志
     */
    private suspend fun cleanupExpiredLogs() {
        try {
            val cutoffTime = System.currentTimeMillis() - (AUDIT_RETENTION_DAYS * 24 * 3600 * 1000)
            
            synchronized(auditLogs) {
                val removedCount = auditLogs.removeAll { it.timestamp < cutoffTime }
                if (removedCount > 0) {
                    SecurityLog.i("清理过期审计日志: $removedCount 条")
                }
            }
        } catch (e: Exception) {
            SecurityLog.e("清理过期日志失败", e)
        }
    }

    /**
     * 触发威胁检测
     */
    private suspend fun triggerThreatDetection(event: SecurityEvent) {
        try {
            val threat = ThreatDetection(
                id = generateThreatId(),
                timestamp = event.timestamp,
                type = when (event.eventType) {
                    SecurityEventType.LOGIN_FAILED -> ThreatType.AUTHENTICATION_BREACH
                    SecurityEventType.NETWORK_ANOMALY -> ThreatType.NETWORK_ATTACK
                    SecurityEventType.DATA_BREACH -> ThreatType.DATA_THEFT
                    SecurityEventType.PERMISSION_ABUSE -> ThreatType.PRIVILEGE_ESCALATION
                    else -> ThreatType.GENERAL_THREAT
                },
                severity = event.severity,
                description = "基于事件检测的威胁: ${event.description}",
                source = event.source,
                riskScore = event.riskScore,
                status = ThreatStatus.ACTIVE,
                evidence = listOf(event)
            )
            
            threatDetections[threat.id] = threat
        } catch (e: Exception) {
            SecurityLog.e("触发威胁检测失败", e)
        }
    }

    /**
     * 计算风险评分
     */
    private fun calculateRiskScore(
        eventType: SecurityEventType,
        severity: SecuritySeverity,
        metadata: Map<String, Any>
    ): Float {
        var score = 0.0f
        
        // 基于事件类型的风险评分
        score += when (eventType) {
            SecurityEventType.DATA_BREACH -> 0.9f
            SecurityEventType.THREAT_DETECTED -> 0.8f
            SecurityEventType.LOGIN_FAILED -> 0.3f
            SecurityEventType.NETWORK_ANOMALY -> 0.6f
            SecurityEventType.PERMISSION_ABUSE -> 0.7f
            SecurityEventType.SYSTEM_COMPROMISE -> 0.95f
            else -> 0.1f
        }
        
        // 基于严重程度的调整
        score += when (severity) {
            SecuritySeverity.CRITICAL -> 0.2f
            SecuritySeverity.HIGH -> 0.15f
            SecuritySeverity.MEDIUM -> 0.1f
            SecuritySeverity.LOW -> 0.05f
            SecuritySeverity.INFO -> 0.0f
        }
        
        // 基于元数据的调整
        metadata["frequency"]?.let { freq ->
            if (freq is Int && freq > 3) {
                score += 0.2f
            }
        }
        
        return score.coerceIn(0.0f, 1.0f)
    }

    /**
     * 生成安全建议
     */
    private fun generateSecurityRecommendations(
        events: List<SecurityEvent>,
        threats: List<ThreatDetection>
    ): List<String> {
        val recommendations = mutableListOf<String>()
        
        try {
            // 基于威胁的建议
            threats.filter { it.status == ThreatStatus.ACTIVE }.forEach { threat ->
                when (threat.type) {
                    ThreatType.AUTHENTICATION_BREACH -> {
                        recommendations.add("加强身份验证机制，考虑启用双因素认证")
                        recommendations.add("实施账户锁定策略以防止暴力破解")
                    }
                    ThreatType.NETWORK_ATTACK -> {
                        recommendations.add("检查网络配置，加强防火墙规则")
                        recommendations.add("监控异常网络流量，考虑实施DDoS防护")
                    }
                    ThreatType.DATA_THEFT -> {
                        recommendations.add("审查数据访问权限，确保最小权限原则")
                        recommendations.add("加强数据加密和访问控制")
                    }
                    ThreatType.PRIVILEGE_ESCALATION -> {
                        recommendations.add("审查权限分配，确保遵循最小权限原则")
                        recommendations.add("定期审计用户权限和访问模式")
                    }
                    else -> {
                        recommendations.add("保持系统更新，修补已知安全漏洞")
                    }
                }
            }
            
            // 基于事件频率的建议
            val loginFailures = events.count { it.eventType == SecurityEventType.LOGIN_FAILED }
            if (loginFailures > 10) {
                recommendations.add("检测到大量登录失败，建议检查密码策略")
            }
            
            val networkAnomalies = events.count { it.eventType == SecurityEventType.NETWORK_ANOMALY }
            if (networkAnomalies > 5) {
                recommendations.add("网络异常频繁，建议进行网络安全评估")
            }
            
            if (recommendations.isEmpty()) {
                recommendations.add("当前安全状态良好，建议继续保持定期监控")
            }
        } catch (e: Exception) {
            SecurityLog.e("生成安全建议失败", e)
            recommendations.add("建议执行全面的安全评估")
        }
        
        return recommendations.distinct()
    }

    /**
     * 转换为CSV格式
     */
    private fun convertToCsv(events: List<SecurityEvent>): String {
        val header = "ID,Timestamp,EventType,Severity,Description,Source,RiskScore\n"
        val rows = events.joinToString("") { event ->
            "${event.id},${event.timestamp},${event.eventType.name},${event.severity.name}," +
            "\"${event.description}\",${event.source},${event.riskScore}\n"
        }
        return header + rows
    }

    /**
     * 生成PDF报告（简化实现）
     */
    private fun generatePdfReport(events: List<SecurityEvent>): String {
        // 这里需要PDF生成库，如iText或Android PDFWriter
        // 简化实现，返回报告摘要
        return "安全审计报告\n" +
                "生成时间: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())}\n" +
                "事件总数: ${events.size}\n" +
                "高危事件: ${events.count { it.severity == SecuritySeverity.HIGH }}\n" +
                "严重事件: ${events.count { it.severity == SecuritySeverity.CRITICAL }}"
    }

    // ID生成方法
    private fun generateEventId(): String = "evt_${System.currentTimeMillis()}_${UUID.randomUUID()}"
    private fun generateReportId(): String = "rpt_${System.currentTimeMillis()}_${UUID.randomUUID()}"
    private fun generateScanId(): String = "scan_${System.currentTimeMillis()}_${UUID.randomUUID()}"
    private fun generateThreatId(): String = "thrt_${System.currentTimeMillis()}_${UUID.randomUUID()}"
}

/**
 * 网络威胁检测器
 */
class NetworkThreatDetector {
    suspend fun detectThreats(context: Context): List<ThreatDetection> {
        // 实现网络威胁检测逻辑
        return emptyList()
    }
}

/**
 * 系统威胁检测器
 */
class SystemThreatDetector {
    suspend fun detectThreats(context: Context): List<ThreatDetection> {
        // 实现系统威胁检测逻辑
        return emptyList()
    }
}

/**
 * 应用威胁检测器
 */
class ApplicationThreatDetector {
    suspend fun detectThreats(context: Context): List<ThreatDetection> {
        // 实现应用威胁检测逻辑
        return emptyList()
    }
}

/**
 * 数据威胁检测器
 */
class DataThreatDetector {
    suspend fun detectThreats(context: Context): List<ThreatDetection> {
        // 实现数据威胁检测逻辑
        return emptyList()
    }
}

// 数据类定义
@Serializable
data class SecurityEvent(
    val id: String,
    val timestamp: Long,
    val eventType: SecurityEventType,
    val severity: SecuritySeverity,
    val description: String,
    val source: String,
    val metadata: Map<String, Any>,
    val riskScore: Float
)

enum class SecurityEventType {
    SYSTEM,
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    DATA_ACCESS,
    DATA_MODIFICATION,
    DATA_DELETION,
    NETWORK_ANOMALY,
    PERMISSION_REQUEST,
    PERMISSION_GRANTED,
    PERMISSION_DENIED,
    PERMISSION_ABUSE,
    THREAT_DETECTED,
    THREAT_RESOLVED,
    SECURITY_VIOLATION,
    SECURITY_ALERT,
    SECURITY_REPORT,
    SYSTEM_COMPROMISE,
    DATA_BREACH
}

enum class SecuritySeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

@Serializable
data class ThreatDetection(
    val id: String,
    val timestamp: Long,
    val type: ThreatType,
    val severity: SecuritySeverity,
    val description: String,
    val source: String,
    val riskScore: Float,
    val status: ThreatStatus,
    val evidence: List<SecurityEvent>,
    val resolution: ThreatResolution? = null
)

enum class ThreatType {
    MALWARE,
    VIRUS,
    NETWORK_ATTACK,
    DATA_THEFT,
    PRIVILEGE_ESCALATION,
    AUTHENTICATION_BREACH,
    SYSTEM_COMPROMISE,
    GENERAL_THREAT
}

enum class ThreatStatus {
    ACTIVE,
    INVESTIGATING,
    RESOLVED,
    FALSE_POSITIVE
}

data class ThreatResolution(
    val action: ThreatResolutionAction,
    val resolvedAt: Long,
    val resolvedBy: String,
    val notes: String
)

enum class ThreatResolutionAction {
    BLOCKED,
    QUARANTINED,
    DELETED,
    REPORTED,
    IGNORED
}

@Serializable
data class SecurityReport(
    val id: String,
    val generatedAt: Long,
    val period: ReportPeriod,
    val summary: SecurityReportSummary,
    val events: List<SecurityEvent>,
    val threats: List<ThreatDetection>,
    val recommendations: List<String>,
    val metrics: Map<String, Any>
) {
    companion object {
        fun empty() = SecurityReport(
            id = "",
            generatedAt = 0,
            period = ReportPeriod(0, 0),
            summary = SecurityReportSummary(),
            events = emptyList(),
            threats = emptyList(),
            recommendations = emptyList(),
            metrics = emptyMap()
        )
    }
}

data class ReportPeriod(
    val startTime: Long,
    val endTime: Long
)

data class SecurityReportSummary(
    val totalEvents: Int = 0,
    val highSeverityEvents: Int = 0,
    val criticalEvents: Int = 0,
    val threatsDetected: Int = 0,
    val activeThreats: Int = 0,
    val resolvedThreats: Int = 0,
    val averageRiskScore: Double = 0.0
)

data class SecurityRule(
    val id: String,
    val name: String,
    val type: SecurityRuleType,
    val condition: (SecurityEvent) -> Boolean,
    val action: ThreatAction,
    val enabled: Boolean
)

enum class SecurityRuleType {
    AUTHENTICATION,
    AUTHORIZATION,
    NETWORK,
    DATA,
    SYSTEM,
    PERMISSION
}

enum class ThreatAction {
    BLOCK,
    ALERT,
    REPORT
}

data class ThreatScanResult(
    val success: Boolean,
    val scanId: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val threatsDetected: Int,
    val threats: List<ThreatDetection>,
    val error: String?
)

data class ExportResult(
    val success: Boolean,
    val content: String,
    val format: ExportFormat,
    val recordCount: Int,
    val error: String?
)

enum class ExportFormat {
    JSON,
    CSV,
    PDF
}

data class SecurityMetrics(
    var lastReportTime: Long = System.currentTimeMillis()
) {
    fun getMetricsSnapshot(): Map<String, Any> {
        return mapOf(
            "lastReportTime" to lastReportTime,
            "generatedAt" to System.currentTimeMillis()
        )
    }
}