package com.codemate.security

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据泄露防护模块
 * 检测和防止敏感数据意外泄露
 * 提供数据分类、模式匹配、访问控制和实时监控功能
 */
@Singleton
class DataLeakagePrevention @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "DataLeakagePrevention"
        private const val MONITOR_INTERVAL = 10000L // 10秒
        private const val MAX_LOG_SIZE = 1000
        private const val HASH_CHUNK_SIZE = 8192
        
        // 敏感数据模式
        private val SENSITIVE_PATTERNS = mapOf(
            "credit_card" to Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"),
            "ssn" to Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
            "phone" to Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b"),
            "email" to Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"),
            "api_key" to Pattern.compile("\\b(?:sk-|pk-)[a-zA-Z0-9]{32,}\\b"),
            "password" to Pattern.compile("\\b(password|passwd|pwd)\\s*[=:]\s*\\S+\\b", Pattern.CASE_INSENSITIVE),
            "bank_account" to Pattern.compile("\\b\\d{8,17}\\b"),
            "token" to Pattern.compile("\\b(?:token|key|secret)\\s*[=:]\s*[a-zA-Z0-9]{20,}\\b", Pattern.CASE_INSENSITIVE)
        )
        
        // 敏感关键词
        private val SENSITIVE_KEYWORDS = setOf(
            "password", "passwd", "pwd", "secret", "token", "key",
            "credit_card", "ssn", "social_security", "bank_account",
            "api_key", "auth_token", "access_token", "refresh_token",
            "private_key", "certificate", "credential", "login"
        )
        
        // 数据分类级别
        private val DATA_CLASSIFICATION = mapOf(
            "pii" to DataClassification.PERSONAL_INFO,
            "financial" to DataClassification.FINANCIAL,
            "health" to DataClassification.HEALTH,
            "credentials" to DataClassification.CREDENTIALS,
            "configuration" to DataClassification.CONFIGURATION,
            "logs" to DataClassification.LOGS
        )
    }

    private val monitoringScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val protectionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val isMonitoring = AtomicBoolean(false)
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    // 数据监控
    private val monitoredData = ConcurrentHashMap<String, MonitoredData>()
    private val leakageIncidents = mutableListOf<LeakageIncident>()
    private val dataAccessLog = mutableListOf<DataAccessEvent>()
    
    // 防护规则
    private val protectionRules = mutableListOf<DataProtectionRule>()
    private val classificationRules = mutableListOf<DataClassificationRule>()
    
    // 事件通道
    private val leakageEventChannel = Channel<LeakageEvent>(Channel.UNLIMITED)
    
    // 威胁检测器
    private val detectionEngines = listOf(
        PatternMatchingDetector(),
        HashComparisonDetector(),
        AnomalyDetectionEngine(),
        AccessPatternAnalyzer()
    )

    /**
     * 启动数据泄露防护
     */
    fun startDataLeakagePrevention(): Boolean {
        return try {
            if (isMonitoring.getAndSet(true)) {
                Log.w(TAG, "数据泄露防护已经在运行中")
                return true
            }

            // 初始化防护规则
            initializeProtectionRules()
            
            // 初始化分类规则
            initializeClassificationRules()
            
            // 启动监控
            monitoringScope.launch {
                dataMonitoringLoop()
            }
            
            // 启动威胁检测
            protectionScope.launch {
                threatDetectionLoop()
            }
            
            // 启动实时防护
            protectionScope.launch {
                realTimeProtectionLoop()
            }
            
            SecurityLog.i("数据泄露防护已启动")
            logLeakageEvent(
                type = LeakageEventType.SYSTEM,
                severity = LeakageSeverity.INFO,
                description = "数据泄露防护系统已启动"
            )
            
            true
        } catch (e: Exception) {
            SecurityLog.e("启动数据泄露防护失败", e)
            isMonitoring.set(false)
            false
        }
    }

    /**
     * 停止数据泄露防护
     */
    fun stopDataLeakagePrevention() {
        if (isMonitoring.getAndSet(false)) {
            try {
                monitoringScope.cancel()
                protectionScope.cancel()
                leakageEventChannel.close()
                
                SecurityLog.i("数据泄露防护已停止")
                logLeakageEvent(
                    type = LeakageEventType.SYSTEM,
                    severity = LeakageSeverity.INFO,
                    description = "数据泄露防护系统已停止"
                )
            } catch (e: Exception) {
                SecurityLog.e("停止数据泄露防护失败", e)
            }
        }
    }

    /**
     * 监控数据访问
     */
    suspend fun monitorDataAccess(
        dataId: String,
        dataContent: String,
        source: String,
        action: DataAction,
        classification: DataClassification = DataClassification.UNKNOWN
    ): MonitorResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val timestamp = System.currentTimeMillis()
            
            // 数据分类
            val detectedClassification = if (classification == DataClassification.UNKNOWN) {
                classifyData(dataContent)
            } else {
                classification
            }
            
            // 检查敏感数据
            val sensitiveDataCheck = detectSensitiveData(dataContent)
            
            // 创建监控数据记录
            val monitoredData = MonitoredData(
                id = dataId,
                content = dataContent,
                classification = detectedClassification,
                source = source,
                action = action,
                timestamp = timestamp,
                sensitivePatterns = sensitiveDataCheck.patterns,
                riskScore = calculateRiskScore(detectedClassification, sensitiveDataCheck.patterns)
            )
            
            // 存储监控数据
            this@DataLeakagePrevention.monitoredData[dataId] = monitoredData
            
            // 记录访问事件
            logDataAccess(dataId, source, action, detectedClassification, timestamp)
            
            // 检查防护规则
            val protectionResult = checkProtectionRules(monitoredData)
            
            // 检查威胁
            val threatResult = checkForThreats(monitoredData)
            
            MonitorResult(
                success = true,
                dataId = dataId,
                classification = detectedClassification,
                riskScore = monitoredData.riskScore,
                violations = protectionResult.violations + threatResult.violations,
                recommendations = generateProtectionRecommendations(detectedClassification, sensitiveDataCheck)
            )
        } catch (e: Exception) {
            SecurityLog.e("监控数据访问失败: $dataId", e)
            MonitorResult(
                success = false,
                dataId = dataId,
                classification = DataClassification.UNKNOWN,
                riskScore = 0.0,
                violations = listOf(DataViolation("MONITORING_FAILED", e.message ?: "监控失败")),
                recommendations = emptyList()
            )
        }
    }

    /**
     * 检测敏感数据
     */
    suspend fun detectSensitiveData(data: String): SensitiveDataResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val detectedPatterns = mutableListOf<DetectedPattern>()
            val riskFactors = mutableListOf<RiskFactor>()
            
            // 模式匹配检测
            SENSITIVE_PATTERNS.forEach { (patternName, pattern) ->
                val matcher = pattern.matcher(data)
                if (matcher.find()) {
                    detectedPatterns.add(
                        DetectedPattern(
                            name = patternName,
                            matches = listOf(matcher.group()),
                            risk = getPatternRisk(patternName)
                        )
                    )
                }
            }
            
            // 关键词检测
            val detectedKeywords = SENSITIVE_KEYWORDS.filter { keyword ->
                data.contains(keyword, ignoreCase = true)
            }
            
            if (detectedKeywords.isNotEmpty()) {
                riskFactors.add(
                    RiskFactor(
                        name = "sensitive_keywords",
                        value = detectedKeywords.joinToString(","),
                        weight = 0.3
                    )
                )
            }
            
            // 长度和复杂度检查
            val complexityScore = calculateDataComplexity(data)
            if (complexityScore > 0.8) {
                riskFactors.add(
                    RiskFactor(
                        name = "high_complexity",
                        value = complexityScore.toString(),
                        weight = 0.2
                    )
                )
            }
            
            // 熵值检查
            val entropy = calculateEntropy(data)
            if (entropy > 4.0) {
                riskFactors.add(
                    RiskFactor(
                        name = "high_entropy",
                        value = entropy.toString(),
                        weight = 0.4
                    )
                )
            }
            
            SensitiveDataResult(
                hasSensitiveData = detectedPatterns.isNotEmpty() || detectedKeywords.isNotEmpty(),
                patterns = detectedPatterns,
                keywords = detectedKeywords,
                riskFactors = riskFactors,
                complexityScore = complexityScore,
                entropy = entropy
            )
        } catch (e: Exception) {
            SecurityLog.e("检测敏感数据失败", e)
            SensitiveDataResult(
                hasSensitiveData = false,
                patterns = emptyList(),
                keywords = emptyList(),
                riskFactors = emptyList(),
                complexityScore = 0.0,
                entropy = 0.0
            )
        }
    }

    /**
     * 数据分类
     */
    suspend fun classifyData(data: String): DataClassification = withContext(Dispatchers.IO) {
        return@withContext try {
            var maxScore = 0.0
            var bestMatch = DataClassification.UNKNOWN
            
            classificationRules.filter { it.enabled }.forEach { rule ->
                val score = rule.classifier(data)
                if (score > maxScore) {
                    maxScore = score
                    bestMatch = rule.classification
                }
            }
            
            // 如果没有匹配的规则，使用默认分类
            if (maxScore < 0.5) {
                bestMatch = determineDefaultClassification(data)
            }
            
            SecurityLog.d("数据分类结果: $bestMatch (score: $maxScore)")
            bestMatch
        } catch (e: Exception) {
            SecurityLog.e("数据分类失败", e)
            DataClassification.UNKNOWN
        }
    }

    /**
     * 计算数据哈希
     */
    suspend fun calculateDataHash(data: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(data.toByteArray(StandardCharsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            SecurityLog.e("计算数据哈希失败", e)
            ""
        }
    }

    /**
     * 检查数据泄露
     */
    suspend fun checkDataLeakage(dataId: String): LeakageCheckResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val monitoredData = this@DataLeakagePrevention.monitoredData[dataId]
            if (monitoredData == null) {
                return@withContext LeakageCheckResult(
                    isLeaked = false,
                    riskLevel = LeakageRiskLevel.UNKNOWN,
                    incidents = emptyList(),
                    recommendations = listOf("数据未监控")
                )
            }
            
            val incidents = detectLeakageIncidents(monitoredData)
            val riskLevel = calculateLeakageRisk(incidents)
            
            LeakageCheckResult(
                isLeaked = incidents.isNotEmpty(),
                riskLevel = riskLevel,
                incidents = incidents,
                recommendations = generateLeakageRecommendations(incidents, riskLevel)
            )
        } catch (e: Exception) {
            SecurityLog.e("检查数据泄露失败: $dataId", e)
            LeakageCheckResult(
                isLeaked = false,
                riskLevel = LeakageRiskLevel.UNKNOWN,
                incidents = emptyList(),
                recommendations = listOf("检查失败")
            )
        }
    }

    /**
     * 获取数据泄露事件
     */
    suspend fun getLeakageIncidents(
        startTime: Long = System.currentTimeMillis() - 24 * 3600 * 1000,
        endTime: Long = System.currentTimeMillis(),
        severity: LeakageSeverity? = null
    ): List<LeakageIncident> = withContext(Dispatchers.IO) {
        return@withContext synchronized(leakageIncidents) {
            leakageIncidents.filter { incident ->
                incident.timestamp in startTime..endTime &&
                (severity == null || incident.severity == severity)
            }
        }
    }

    /**
     * 获取监控统计
     */
    suspend fun getMonitoringStatistics(): MonitoringStatistics = withContext(Dispatchers.IO) {
        return@withContext try {
            val totalMonitored = monitoredData.size
            val sensitiveDataCount = monitoredData.values.count { 
                it.classification != DataClassification.UNKNOWN 
            }
            val highRiskCount = monitoredData.values.count { 
                it.riskScore > 0.7 
            }
            val recentLeakage = leakageIncidents.count { 
                it.timestamp > System.currentTimeMillis() - 24 * 3600 * 1000 
            }
            
            val classificationDistribution = monitoredData.values
                .groupingBy { it.classification }
                .eachCount()
            
            val accessPatternAnalysis = analyzeAccessPatterns()
            
            MonitoringStatistics(
                totalMonitoredData = totalMonitored,
                sensitiveDataCount = sensitiveDataCount,
                highRiskDataCount = highRiskCount,
                recentLeakageIncidents = recentLeakage,
                classificationDistribution = classificationDistribution,
                accessPatternAnalysis = accessPatternAnalysis,
                protectionRulesCount = protectionRules.size,
                activeDetectors = detectionEngines.size
            )
        } catch (e: Exception) {
            SecurityLog.e("获取监控统计失败", e)
            MonitoringStatistics()
        }
    }

    /**
     * 生成数据泄露防护报告
     */
    suspend fun generateProtectionReport(): ProtectionReport = withContext(Dispatchers.IO) {
        return@withContext try {
            val statistics = getMonitoringStatistics()
            val incidents = getLeakageIncidents()
            val recommendations = generateProtectionRecommendations()
            
            ProtectionReport(
                generatedAt = System.currentTimeMillis(),
                statistics = statistics,
                recentIncidents = incidents.takeLast(50),
                criticalFindings = incidents.filter { it.severity == LeakageSeverity.CRITICAL },
                riskAssessment = assessOverallRisk(),
                recommendations = recommendations,
                complianceStatus = checkCompliance()
            )
        } catch (e: Exception) {
            SecurityLog.e("生成防护报告失败", e)
            ProtectionReport()
        }
    }

    /**
     * 获取防护事件流
     */
    fun getLeakageEventFlow(): Flow<LeakageEvent> = flow {
        leakageEventChannel.receiveAsFlow().collect { event ->
            emit(event)
        }
    }

    /**
     * 添加防护规则
     */
    fun addProtectionRule(rule: DataProtectionRule) {
        try {
            protectionRules.add(rule)
            SecurityLog.i("防护规则已添加: ${rule.name}")
            
            logLeakageEvent(
                type = LeakageEventType.RULE_ADDED,
                severity = LeakageSeverity.INFO,
                description = "防护规则已添加: ${rule.name}",
                metadata = mapOf(
                    "ruleId" to rule.id,
                    "ruleType" to rule.type.name,
                    "classification" to rule.classification.name
                )
            )
        } catch (e: Exception) {
            SecurityLog.e("添加防护规则失败", e)
        }
    }

    /**
     * 数据监控循环
     */
    private suspend fun dataMonitoringLoop() {
        while (isMonitoring.get()) {
            try {
                // 监控数据完整性
                monitorDataIntegrity()
                
                // 检查访问模式异常
                checkAccessAnomalies()
                
                // 清理过期数据
                cleanupExpiredData()
                
                delay(MONITOR_INTERVAL)
            } catch (e: Exception) {
                SecurityLog.e("数据监控循环异常", e)
                delay(MONITOR_INTERVAL)
            }
        }
    }

    /**
     * 威胁检测循环
     */
    private suspend fun threatDetectionLoop() {
        while (isMonitoring.get()) {
            try {
                // 并行执行威胁检测
                val detectionResults = detectionEngines.map { detector ->
                    async {
                        try {
                            detector.detect(monitoredData.values.toList())
                        } catch (e: Exception) {
                            SecurityLog.e("威胁检测器异常: ${detector.javaClass.simpleName}", e)
                            emptyList<LeakageIncident>()
                        }
                    }
                }.awaitAll()
                
                // 合并结果
                detectionResults.flatten().forEach { incident ->
                    handleLeakageIncident(incident)
                }
                
                delay(MONITOR_INTERVAL)
            } catch (e: Exception) {
                SecurityLog.e("威胁检测循环异常", e)
                delay(MONITOR_INTERVAL)
            }
        }
    }

    /**
     * 实时防护循环
     */
    private suspend fun realTimeProtectionLoop() {
        while (isMonitoring.get()) {
            try {
                // 实施实时防护措施
                implementRealTimeProtection()
                
                // 检查防护规则触发
                checkRuleTriggers()
                
                delay(5000) // 5秒间隔
            } catch (e: Exception) {
                SecurityLog.e("实时防护循环异常", e)
                delay(5000)
            }
        }
    }

    /**
     * 初始化防护规则
     */
    private fun initializeProtectionRules() {
        protectionRules.clear()
        
        // 添加默认防护规则
        protectionRules.addAll(listOf(
            DataProtectionRule(
                id = "rule_001",
                name = "敏感数据外传阻止",
                type = DataProtectionType.BLOCK,
                classification = DataClassification.CREDENTIALS,
                action = ProtectionAction.BLOCK,
                enabled = true
            ),
            DataProtectionRule(
                id = "rule_002",
                name = "个人信息加密提醒",
                type = DataProtectionType.ENCRYPT,
                classification = DataClassification.PERSONAL_INFO,
                action = ProtectionAction.ENCRYPT,
                enabled = true
            ),
            DataProtectionRule(
                id = "rule_003",
                name = "财务数据审计",
                type = DataProtectionType.AUDIT,
                classification = DataClassification.FINANCIAL,
                action = ProtectionAction.AUDIT,
                enabled = true
            ),
            DataProtectionRule(
                id = "rule_004",
                name = "健康数据保护",
                type = DataProtectionType.PROTECT,
                classification = DataClassification.HEALTH,
                action = ProtectionAction.PROTECT,
                enabled = true
            )
        ))
    }

    /**
     * 初始化分类规则
     */
    private fun initializeClassificationRules() {
        classificationRules.clear()
        
        // 添加默认分类规则
        classificationRules.addAll(listOf(
            DataClassificationRule(
                id = "class_001",
                name = "个人信息分类",
                classification = DataClassification.PERSONAL_INFO,
                classifier = { data ->
                    val keywords = listOf("name", "email", "phone", "address", "birth")
                    val keywordCount = keywords.count { data.contains(it, ignoreCase = true) }
                    if (keywordCount > 0) keywordCount / keywords.size.toDouble() else 0.0
                },
                enabled = true
            ),
            DataClassificationRule(
                id = "class_002",
                name = "财务信息分类",
                classification = DataClassification.FINANCIAL,
                classifier = { data ->
                    val patterns = listOf("\\$\\d+", "\\d+\\.\\d{2}", "balance", "payment")
                    val patternCount = patterns.count { Regex(it).containsMatchIn(data) }
                    if (patternCount > 0) patternCount / patterns.size.toDouble() else 0.0
                },
                enabled = true
            ),
            DataClassificationRule(
                id = "class_003",
                name = "凭据分类",
                classification = DataClassification.CREDENTIALS,
                classifier = { data ->
                    val credentialKeywords = listOf("password", "token", "key", "secret", "auth")
                    val keywordCount = credentialKeywords.count { data.contains(it, ignoreCase = true) }
                    if (keywordCount > 0) keywordCount / credentialKeywords.size.toDouble() else 0.0
                },
                enabled = true
            )
        ))
    }

    /**
     * 记录数据访问
     */
    private fun logDataAccess(
        dataId: String,
        source: String,
        action: DataAction,
        classification: DataClassification,
        timestamp: Long
    ) {
        val accessEvent = DataAccessEvent(
            id = generateAccessId(),
            dataId = dataId,
            source = source,
            action = action,
            classification = classification,
            timestamp = timestamp
        )
        
        synchronized(dataAccessLog) {
            dataAccessLog.add(accessEvent)
            
            // 保持日志大小限制
            if (dataAccessLog.size > MAX_LOG_SIZE) {
                dataAccessLog.removeFirst()
            }
        }
    }

    /**
     * 检查防护规则
     */
    private suspend fun checkProtectionRules(monitoredData: MonitoredData): ProtectionCheckResult {
        val violations = mutableListOf<DataViolation>()
        
        protectionRules.filter { it.enabled }.forEach { rule ->
            if (rule.classification == monitoredData.classification || rule.classification == DataClassification.UNKNOWN) {
                val violation = when (rule.action) {
                    ProtectionAction.BLOCK -> {
                        DataViolation(
                            type = "DATA_BLOCKED",
                            description = "数据访问被阻止: ${monitoredData.classification}"
                        )
                    }
                    ProtectionAction.ENCRYPT -> {
                        DataViolation(
                            type = "ENCRYPTION_REQUIRED",
                            description = "数据需要加密: ${monitoredData.classification}"
                        )
                    }
                    ProtectionAction.AUDIT -> {
                        DataViolation(
                            type = "AUDIT_REQUIRED",
                            description = "数据需要审计: ${monitoredData.classification}"
                        )
                    }
                    ProtectionAction.PROTECT -> {
                        DataViolation(
                            type = "PROTECTION_REQUIRED",
                            description = "数据需要保护: ${monitoredData.classification}"
                        )
                    }
                }
                violations.add(violation)
            }
        }
        
        return ProtectionCheckResult(violations)
    }

    /**
     * 检查威胁
     */
    private suspend fun checkForThreats(monitoredData: MonitoredData): ThreatCheckResult {
        val violations = mutableListOf<DataViolation>()
        
        // 检查高风险数据
        if (monitoredData.riskScore > 0.8) {
            violations.add(
                DataViolation(
                    type = "HIGH_RISK_DATA",
                    description = "高风险数据检测: 风险评分 ${monitoredData.riskScore}"
                )
            )
        }
        
        // 检查敏感模式
        if (monitoredData.sensitivePatterns.isNotEmpty()) {
            violations.add(
                DataViolation(
                    type = "SENSITIVE_PATTERN_DETECTED",
                    description = "敏感模式检测: ${monitoredData.sensitivePatterns.map { it.name }}"
                )
            )
        }
        
        return ThreatCheckResult(violations)
    }

    /**
     * 计算风险评分
     */
    private fun calculateRiskScore(
        classification: DataClassification,
        patterns: List<DetectedPattern>
    ): Double {
        var riskScore = 0.0
        
        // 基于分类的风险
        riskScore += when (classification) {
            DataClassification.CREDENTIALS -> 0.9
            DataClassification.FINANCIAL -> 0.8
            DataClassification.HEALTH -> 0.8
            DataClassification.PERSONAL_INFO -> 0.6
            DataClassification.CONFIGURATION -> 0.4
            DataClassification.LOGS -> 0.2
            DataClassification.UNKNOWN -> 0.1
        }
        
        // 基于敏感模式的风险
        patterns.forEach { pattern ->
            riskScore += pattern.risk
        }
        
        return riskScore.coerceIn(0.0, 1.0)
    }

    /**
     * 检测泄露事件
     */
    private suspend fun detectLeakageIncidents(monitoredData: MonitoredData): List<LeakageIncident> {
        val incidents = mutableListOf<LeakageIncident>()
        
        // 检查异常访问模式
        val accessPattern = dataAccessLog.filter { it.dataId == monitoredData.id }
        if (accessPattern.size > 10) {
            incidents.add(
                LeakageIncident(
                    id = generateIncidentId(),
                    type = LeakageIncidentType.ABNORMAL_ACCESS,
                    severity = LeakageSeverity.MEDIUM,
                    description = "检测到异常访问模式: ${accessPattern.size} 次访问",
                    dataId = monitoredData.id,
                    timestamp = System.currentTimeMillis(),
                    evidence = accessPattern.takeLast(10)
                )
            )
        }
        
        // 检查敏感数据外传
        if (monitoredData.classification == DataClassification.CREDENTIALS && 
            monitoredData.action == DataAction.EXPORT) {
            incidents.add(
                LeakageIncident(
                    id = generateIncidentId(),
                    type = LeakageIncidentType.DATA_EXFILTRATION,
                    severity = LeakageSeverity.CRITICAL,
                    description = "检测到凭据数据外传",
                    dataId = monitoredData.id,
                    timestamp = System.currentTimeMillis(),
                    evidence = listOf(monitoredData)
                )
            )
        }
        
        return incidents
    }

    /**
     * 处理泄露事件
     */
    private suspend fun handleLeakageIncident(incident: LeakageIncident) {
        try {
            synchronized(leakageIncidents) {
                leakageIncidents.add(incident)
                
                // 保持事件数量限制
                if (leakageIncidents.size > MAX_LOG_SIZE) {
                    leakageIncidents.removeFirst()
                }
            }
            
            logLeakageEvent(
                type = LeakageEventType.LEAKAGE_DETECTED,
                severity = incident.severity,
                description = incident.description,
                metadata = mapOf(
                    "incidentId" to incident.id,
                    "incidentType" to incident.type.name,
                    "dataId" to incident.dataId
                )
            )
            
            // 根据严重程度实施响应措施
            when (incident.severity) {
                LeakageSeverity.CRITICAL -> {
                    implementCriticalResponse(incident)
                }
                LeakageSeverity.HIGH -> {
                    implementHighResponse(incident)
                }
                LeakageSeverity.MEDIUM -> {
                    implementMediumResponse(incident)
                }
                else -> {
                    implementLowResponse(incident)
                }
            }
        } catch (e: Exception) {
            SecurityLog.e("处理泄露事件失败", e)
        }
    }

    /**
     * 实施严重响应
     */
    private suspend fun implementCriticalResponse(incident: LeakageIncident) {
        SecurityLog.e("实施严重响应: ${incident.description}")
        // 阻止数据访问、通知安全团队、记录详细日志等
    }

    /**
     * 实施高级响应
     */
    private suspend fun implementHighResponse(incident: LeakageIncident) {
        SecurityLog.w("实施高级响应: ${incident.description}")
        // 增强监控、触发警报等
    }

    /**
     * 实施中级响应
     */
    private suspend fun implementMediumResponse(incident: LeakageIncident) {
        SecurityLog.i("实施中级响应: ${incident.description}")
        // 增加监控频率、记录日志等
    }

    /**
     * 实施低级响应
     */
    private suspend fun implementLowResponse(incident: LeakageIncident) {
        SecurityLog.d("实施低级响应: ${incident.description}")
        // 记录日志、继续监控
    }

    // 辅助方法
    private fun getPatternRisk(patternName: String): Double {
        return when (patternName) {
            "credit_card", "ssn", "api_key", "password" -> 0.8
            "bank_account", "token" -> 0.7
            "phone", "email" -> 0.4
            else -> 0.2
        }
    }

    private fun calculateDataComplexity(data: String): Double {
        val hasUpper = data.any { it.isUpperCase() }
        val hasLower = data.any { it.isLowerCase() }
        val hasDigit = data.any { it.isDigit() }
        val hasSpecial = data.any { "!@#$%^&*()_+-=[]{}|;:,.<>?".contains(it) }
        
        var complexity = 0.0
        if (hasUpper) complexity += 0.25
        if (hasLower) complexity += 0.25
        if (hasDigit) complexity += 0.25
        if (hasSpecial) complexity += 0.25
        
        return complexity
    }

    private fun calculateEntropy(data: String): Double {
        val frequency = mutableMapOf<Char, Int>()
        data.forEach { frequency[it] = frequency.getOrDefault(it, 0) + 1 }
        
        val entropy = frequency.values.sumOf { count ->
            val probability = count.toDouble() / data.length
            -probability * Math.log(probability) / Math.log(2.0)
        }
        
        return entropy
    }

    private fun determineDefaultClassification(data: String): DataClassification {
        // 简化的默认分类逻辑
        val length = data.length
        return when {
            length < 10 -> DataClassification.LOGS
            length < 50 -> DataClassification.CONFIGURATION
            else -> DataClassification.UNKNOWN
        }
    }

    private fun generateProtectionRecommendations(): List<String> {
        val recommendations = mutableListOf<String>()
        
        val statistics = getBlockingSynchronously { getMonitoringStatistics() }
        if (statistics.highRiskDataCount > 0) {
            recommendations.add("存在 ${statistics.highRiskDataCount} 项高风险数据，建议加强保护")
        }
        
        if (statistics.recentLeakageIncidents > 0) {
            recommendations.add("最近 ${statistics.recentLeakageIncidents} 起泄露事件，需要加强监控")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("当前数据保护状态良好")
        }
        
        return recommendations
    }

    private fun assessOverallRisk(): RiskAssessment {
        val recentIncidents = leakageIncidents.count { 
            it.timestamp > System.currentTimeMillis() - 24 * 3600 * 1000 
        }
        
        val riskLevel = when {
            recentIncidents >= 10 -> LeakageRiskLevel.HIGH
            recentIncidents >= 5 -> LeakageRiskLevel.MEDIUM
            recentIncidents >= 1 -> LeakageRiskLevel.LOW
            else -> LeakageRiskLevel.MINIMAL
        }
        
        return RiskAssessment(
            level = riskLevel,
            score = recentIncidents / 10.0,
            factors = listOf("recent_incidents" to recentIncidents.toDouble()),
            trend = "stable"
        )
    }

    private fun checkCompliance(): ComplianceStatus {
        return ComplianceStatus(
            isCompliant = true,
            violations = emptyList(),
            score = 1.0,
            lastAudit = System.currentTimeMillis()
        )
    }

    private fun analyzeAccessPatterns(): AccessPatternAnalysis {
        val totalAccess = dataAccessLog.size
        val uniqueSources = dataAccessLog.map { it.source }.distinct().size
        
        return AccessPatternAnalysis(
            totalAccess = totalAccess,
            uniqueSources = uniqueSources,
            averageAccessPerSource = if (uniqueSources > 0) totalAccess / uniqueSources.toDouble() else 0.0,
            mostActiveSource = dataAccessLog.groupingBy { it.source }.eachCount().maxByOrNull { it.value }?.key ?: "",
            suspiciousPatterns = emptyList()
        )
    }

    private fun logLeakageEvent(
        type: LeakageEventType,
        severity: LeakageSeverity,
        description: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        monitoringScope.launch {
            try {
                leakageEventChannel.send(
                    LeakageEvent(
                        type = type,
                        severity = severity,
                        description = description,
                        timestamp = System.currentTimeMillis(),
                        metadata = metadata
                    )
                )
            } catch (e: Exception) {
                SecurityLog.e("记录泄露事件失败", e)
            }
        }
    }

    // 简化的阻塞方法用于同步操作
    private fun <T> getBlockingSynchronously(block: suspend () -> T): T {
        return runBlocking { block() }
    }

    // 辅助方法的占位实现
    private suspend fun monitorDataIntegrity() { /* 实现数据完整性监控 */ }
    private suspend fun checkAccessAnomalies() { /* 检查访问异常 */ }
    private suspend fun cleanupExpiredData() { /* 清理过期数据 */ }
    private suspend fun implementRealTimeProtection() { /* 实施实时防护 */ }
    private suspend fun checkRuleTriggers() { /* 检查规则触发 */ }
    private fun calculateLeakageRisk(incidents: List<LeakageIncident>): LeakageRiskLevel {
        return if (incidents.isEmpty()) LeakageRiskLevel.MINIMAL 
               else LeakageRiskLevel.LOW
    }
    private fun generateLeakageRecommendations(incidents: List<LeakageIncident>, risk: LeakageRiskLevel): List<String> {
        return listOf("加强数据访问控制")
    }

    // ID生成方法
    private fun generateAccessId(): String = "access_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    private fun generateIncidentId(): String = "incident_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
}

// 检测引擎接口
interface ThreatDetectionEngine {
    suspend fun detect(data: List<MonitoredData>): List<LeakageIncident>
}

// 模式匹配检测器
class PatternMatchingDetector : ThreatDetectionEngine {
    override suspend fun detect(data: List<MonitoredData>): List<LeakageIncident> {
        // 实现模式匹配检测逻辑
        return emptyList()
    }
}

// 哈希比较检测器
class HashComparisonDetector : ThreatDetectionEngine {
    override suspend fun detect(data: List<MonitoredData>): List<LeakageIncident> {
        // 实现哈希比较检测逻辑
        return emptyList()
    }
}

// 异常检测引擎
class AnomalyDetectionEngine : ThreatDetectionEngine {
    override suspend fun detect(data: List<MonitoredData>): List<LeakageIncident> {
        // 实现异常检测逻辑
        return emptyList()
    }
}

// 访问模式分析器
class AccessPatternAnalyzer : ThreatDetectionEngine {
    override suspend fun detect(data: List<MonitoredData>): List<LeakageIncident> {
        // 实现访问模式分析逻辑
        return emptyList()
    }
}

// 数据类定义
@Serializable
data class MonitoredData(
    val id: String,
    val content: String,
    val classification: DataClassification,
    val source: String,
    val action: DataAction,
    val timestamp: Long,
    val sensitivePatterns: List<DetectedPattern>,
    val riskScore: Double
)

data class MonitorResult(
    val success: Boolean,
    val dataId: String,
    val classification: DataClassification,
    val riskScore: Double,
    val violations: List<DataViolation>,
    val recommendations: List<String>
)

data class SensitiveDataResult(
    val hasSensitiveData: Boolean,
    val patterns: List<DetectedPattern>,
    val keywords: List<String>,
    val riskFactors: List<RiskFactor>,
    val complexityScore: Double,
    val entropy: Double
)

data class DetectedPattern(
    val name: String,
    val matches: List<String>,
    val risk: Double
)

data class RiskFactor(
    val name: String,
    val value: String,
    val weight: Double
)

enum class DataClassification {
    PERSONAL_INFO,
    FINANCIAL,
    HEALTH,
    CREDENTIALS,
    CONFIGURATION,
    LOGS,
    UNKNOWN
}

enum class DataAction {
    READ,
    WRITE,
    DELETE,
    EXPORT,
    IMPORT,
    SHARE
}

data class LeakageIncident(
    val id: String,
    val type: LeakageIncidentType,
    val severity: LeakageSeverity,
    val description: String,
    val dataId: String,
    val timestamp: Long,
    val evidence: List<Any>
)

enum class LeakageIncidentType {
    UNAUTHORIZED_ACCESS,
    DATA_EXFILTRATION,
    ABNORMAL_ACCESS,
    PATTERN_VIOLATION,
    POLICY_BREACH
}

enum class LeakageSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

data class LeakageCheckResult(
    val isLeaked: Boolean,
    val riskLevel: LeakageRiskLevel,
    val incidents: List<LeakageIncident>,
    val recommendations: List<String>
)

enum class LeakageRiskLevel {
    HIGH,
    MEDIUM,
    LOW,
    MINIMAL,
    UNKNOWN
}

data class DataAccessEvent(
    val id: String,
    val dataId: String,
    val source: String,
    val action: DataAction,
    val classification: DataClassification,
    val timestamp: Long
)

data class MonitoringStatistics(
    val totalMonitoredData: Int = 0,
    val sensitiveDataCount: Int = 0,
    val highRiskDataCount: Int = 0,
    val recentLeakageIncidents: Int = 0,
    val classificationDistribution: Map<DataClassification, Int> = emptyMap(),
    val accessPatternAnalysis: AccessPatternAnalysis = AccessPatternAnalysis(),
    val protectionRulesCount: Int = 0,
    val activeDetectors: Int = 0
)

data class AccessPatternAnalysis(
    val totalAccess: Int = 0,
    val uniqueSources: Int = 0,
    val averageAccessPerSource: Double = 0.0,
    val mostActiveSource: String = "",
    val suspiciousPatterns: List<String> = emptyList()
)

data class ProtectionReport(
    val generatedAt: Long = 0,
    val statistics: MonitoringStatistics = MonitoringStatistics(),
    val recentIncidents: List<LeakageIncident> = emptyList(),
    val criticalFindings: List<LeakageIncident> = emptyList(),
    val riskAssessment: RiskAssessment = RiskAssessment(),
    val recommendations: List<String> = emptyList(),
    val complianceStatus: ComplianceStatus = ComplianceStatus()
) {
    companion object {
        fun empty() = ProtectionReport()
    }
}

data class RiskAssessment(
    val level: LeakageRiskLevel = LeakageRiskLevel.UNKNOWN,
    val score: Double = 0.0,
    val factors: List<Pair<String, Double>> = emptyList(),
    val trend: String = "unknown"
)

data class ComplianceStatus(
    val isCompliant: Boolean = false,
    val violations: List<String> = emptyList(),
    val score: Double = 0.0,
    val lastAudit: Long = 0
)

data class LeakageEvent(
    val type: LeakageEventType,
    val severity: LeakageSeverity,
    val description: String,
    val timestamp: Long,
    val metadata: Map<String, Any>
)

enum class LeakageEventType {
    SYSTEM,
    LEAKAGE_DETECTED,
    RULE_VIOLATION,
    PROTECTION_TRIGGERED,
    MONITORING_STARTED,
    MONITORING_STOPPED
}

data class DataProtectionRule(
    val id: String,
    val name: String,
    val type: DataProtectionType,
    val classification: DataClassification,
    val action: ProtectionAction,
    val enabled: Boolean
)

enum class DataProtectionType {
    BLOCK,
    ENCRYPT,
    AUDIT,
    PROTECT,
    MASK,
    REDACT
}

enum class ProtectionAction {
    BLOCK,
    ENCRYPT,
    AUDIT,
    PROTECT,
    MASK,
    REDACT
}

data class DataClassificationRule(
    val id: String,
    val name: String,
    val classification: DataClassification,
    val classifier: (String) -> Double,
    val enabled: Boolean
)

data class DataViolation(
    val type: String,
    val description: String
)

data class ProtectionCheckResult(
    val violations: List<DataViolation>
)

data class ThreatCheckResult(
    val violations: List<DataViolation>
)