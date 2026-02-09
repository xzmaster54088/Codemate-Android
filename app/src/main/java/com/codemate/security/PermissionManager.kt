package com.codemate.security

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 权限管理器
 * 精细化权限控制，支持运行时权限检查和最小权限原则
 * 提供权限审计、使用情况监控和风险评估功能
 */
@Singleton
class PermissionManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "PermissionManager"
        private const val PERMISSION_AUDIT_INTERVAL = 300000L // 5分钟
        private const val MAX_FAILED_REQUESTS = 3
        private const val PERMISSION_REQUEST_TIMEOUT = 30000L // 30秒
        
        // 敏感权限分类
        private val DANGEROUS_PERMISSIONS = setOf(
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.READ_CALENDAR,
            Manifest.permission.WRITE_CALENDAR,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        
        private val SIGNATURE_PERMISSIONS = setOf(
            Manifest.permission.INSTALL_PACKAGES,
            Manifest.permission.DELETE_PACKAGES,
            Manifest.permission.CLEAR_APP_CACHE,
            Manifest.permission.CLEAR_APP_USER_DATA,
            Manifest.permission.GET_TASKS,
            Manifest.permission.KILL_BACKGROUND_PROCESSES
        )
        
        private val SYSTEM_PERMISSIONS = setOf(
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.BIND_ACCESSIBILITY_SERVICE,
            Manifest.permission.BIND_DEVICE_ADMIN,
            Manifest.permission.BIND_APPWIDGET
        )
    }

    private val permissionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val auditScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val isMonitoring = AtomicBoolean(false)
    
    // 权限状态跟踪
    private val permissionStates = ConcurrentHashMap<String, PermissionState>()
    private val permissionUsage = ConcurrentHashMap<String, PermissionUsage>()
    private val permissionAuditLog = mutableListOf<PermissionAuditEvent>()
    
    // 权限请求监听器
    private val permissionListeners = mutableSetOf<PermissionListener>()
    
    // 事件通道
    private val permissionEventChannel = Channel<PermissionEvent>(Channel.UNLIMITED)
    
    // 权限规则
    private val permissionRules = mutableListOf<PermissionRule>()
    
    // 权限风险评估器
    private val riskAssessor = PermissionRiskAssessor()

    /**
     * 启动权限管理
     */
    fun startPermissionManagement(): Boolean {
        return try {
            if (isMonitoring.getAndSet(true)) {
                Log.w(TAG, "权限管理已经在运行中")
                return true
            }

            // 初始化权限规则
            initializePermissionRules()
            
            // 启动权限审计
            auditScope.launch {
                permissionAuditLoop()
            }
            
            // 收集当前权限状态
            collectCurrentPermissionStates()
            
            SecurityLog.i("权限管理已启动")
            logPermissionEvent(
                type = PermissionEventType.SYSTEM,
                severity = PermissionSeverity.INFO,
                description = "权限管理系统已启动"
            )
            
            true
        } catch (e: Exception) {
            SecurityLog.e("启动权限管理失败", e)
            isMonitoring.set(false)
            false
        }
    }

    /**
     * 停止权限管理
     */
    fun stopPermissionManagement() {
        if (isMonitoring.getAndSet(false)) {
            try {
                auditScope.cancel()
                permissionEventChannel.close()
                
                SecurityLog.i("权限管理已停止")
                logPermissionEvent(
                    type = PermissionEventType.SYSTEM,
                    severity = PermissionSeverity.INFO,
                    description = "权限管理系统已停止"
                )
            } catch (e: Exception) {
                SecurityLog.e("停止权限管理失败", e)
            }
        }
    }

    /**
     * 请求权限
     */
    suspend fun requestPermission(
        permission: String,
        rationale: String? = null,
        timeout: Long = PERMISSION_REQUEST_TIMEOUT
    ): PermissionRequestResult = withContext(Dispatchers.Main) {
        return@withContext try {
            // 检查权限是否已经授予
            val currentState = getPermissionState(permission)
            if (currentState == PermissionState.GRANTED) {
                return@withContext PermissionRequestResult(
                    success = true,
                    granted = true,
                    permission = permission,
                    state = PermissionState.GRANTED,
                    error = null
                )
            }
            
            // 检查是否应该显示理由
            val shouldShowRationale = shouldShowPermissionRationale(permission)
            if (shouldShowRationale && rationale != null) {
                // 这里应该显示理由对话框，但需要UI上下文
                SecurityLog.w("权限请求需要显示理由: $permission")
            }
            
            // 创建权限请求
            val request = createPermissionRequest(permission, rationale)
            
            // 模拟权限请求（在实际实现中，这里需要与Activity/Fragment集成）
            val result = simulatePermissionRequest(request)
            
            // 更新权限状态
            updatePermissionState(permission, result.state)
            
            // 记录权限使用
            logPermissionUsage(permission, PermissionAction.REQUESTED, result.state)
            
            result
        } catch (e: Exception) {
            SecurityLog.e("请求权限失败: $permission", e)
            PermissionRequestResult(
                success = false,
                granted = false,
                permission = permission,
                state = PermissionState.DENIED,
                error = e.message
            )
        }
    }

    /**
     * 批量请求权限
     */
    suspend fun requestPermissions(
        permissions: List<String>,
        rationale: String? = null
    ): List<PermissionRequestResult> = withContext(Dispatchers.IO) {
        return@withContext permissions.map { permission ->
            requestPermission(permission, rationale)
        }
    }

    /**
     * 检查权限状态
     */
    fun getPermissionState(permission: String): PermissionState {
        return try {
            val checkResult = ContextCompat.checkSelfPermission(context, permission)
            when (checkResult) {
                PackageManager.PERMISSION_GRANTED -> PermissionState.GRANTED
                PackageManager.PERMISSION_DENIED -> PermissionState.DENIED
                else -> PermissionState.UNKNOWN
            }
        } catch (e: Exception) {
            SecurityLog.e("检查权限状态失败: $permission", e)
            PermissionState.UNKNOWN
        }
    }

    /**
     * 检查多个权限状态
     */
    fun getPermissionsState(permissions: List<String>): Map<String, PermissionState> {
        return permissions.associateWith { getPermissionState(it) }
    }

    /**
     * 撤销权限
     */
    suspend fun revokePermission(permission: String): PermissionResult = withContext(Dispatchers.IO) {
        return@withContext try {
            // 注意：Android应用无法直接撤销权限，需要用户手动在设置中操作
            logPermissionEvent(
                type = PermissionEventType.REVOCATION_REQUESTED,
                severity = PermissionSeverity.WARNING,
                description = "权限撤销请求: $permission",
                metadata = mapOf("permission" to permission)
            )
            
            // 引导用户到应用设置页面
            openAppSettings()
            
            PermissionResult(
                success = true,
                permission = permission,
                action = PermissionAction.REVOKED,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            SecurityLog.e("撤销权限失败: $permission", e)
            PermissionResult(
                success = false,
                permission = permission,
                action = PermissionAction.FAILED,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * 获取权限风险评估
     */
    suspend fun getPermissionRiskAssessment(permission: String): PermissionRiskAssessment = withContext(Dispatchers.IO) {
        return@withContext try {
            val state = getPermissionState(permission)
            val usage = permissionUsage[permission]
            val assessment = riskAssessor.assessRisk(permission, state, usage)
            
            logPermissionEvent(
                type = PermissionEventType.RISK_ASSESSMENT,
                severity = PermissionSeverity.INFO,
                description = "权限风险评估: $permission",
                metadata = mapOf(
                    "permission" to permission,
                    "riskLevel" to assessment.riskLevel.name,
                    "score" to assessment.riskScore
                )
            )
            
            assessment
        } catch (e: Exception) {
            SecurityLog.e("获取权限风险评估失败: $permission", e)
            PermissionRiskAssessment(
                permission = permission,
                riskLevel = PermissionRiskLevel.UNKNOWN,
                riskScore = 0.0,
                factors = emptyList(),
                recommendations = emptyList()
            )
        }
    }

    /**
     * 获取所有权限状态
     */
    suspend fun getAllPermissionsStatus(): Map<String, PermissionStatus> = withContext(Dispatchers.IO) {
        return@withContext try {
            val allPermissions = getAllPossiblePermissions()
            allPermissions.associateWith { permission ->
                val state = getPermissionState(permission)
                val riskAssessment = getPermissionRiskAssessment(permission)
                val usage = permissionUsage[permission]
                
                PermissionStatus(
                    permission = permission,
                    state = state,
                    category = categorizePermission(permission),
                    riskLevel = riskAssessment.riskLevel,
                    lastUsed = usage?.lastUsed,
                    requestCount = usage?.requestCount ?: 0,
                    grantedCount = usage?.grantedCount ?: 0,
                    deniedCount = usage?.deniedCount ?: 0
                )
            }
        } catch (e: Exception) {
            SecurityLog.e("获取所有权限状态失败", e)
            emptyMap()
        }
    }

    /**
     * 获取权限使用统计
     */
    suspend fun getPermissionUsageStatistics(): PermissionUsageStatistics = withContext(Dispatchers.IO) {
        return@withContext try {
            val totalPermissions = permissionUsage.size
            val grantedPermissions = permissionUsage.values.count { it.lastState == PermissionState.GRANTED }
            val dangerousPermissions = permissionUsage.keys.count { DANGEROUS_PERMISSIONS.contains(it) }
            
            PermissionUsageStatistics(
                totalPermissions = totalPermissions,
                grantedPermissions = grantedPermissions,
                dangerousPermissions = dangerousPermissions,
                permissionUsage = permissionUsage.toMap(),
                riskDistribution = calculateRiskDistribution()
            )
        } catch (e: Exception) {
            SecurityLog.e("获取权限使用统计失败", e)
            PermissionUsageStatistics()
        }
    }

    /**
     * 生成权限审计报告
     */
    suspend fun generatePermissionAuditReport(): PermissionAuditReport = withContext(Dispatchers.IO) {
        return@withContext try {
            val allPermissionsStatus = getAllPermissionsStatus()
            val usageStatistics = getPermissionUsageStatistics()
            val auditEvents = permissionAuditLog.toList()
            
            val report = PermissionAuditReport(
                generatedAt = System.currentTimeMillis(),
                totalPermissions = allPermissionsStatus.size,
                grantedPermissions = allPermissionsStatus.values.count { it.state == PermissionState.GRANTED },
                deniedPermissions = allPermissionsStatus.values.count { it.state == PermissionState.DENIED },
                highRiskPermissions = allPermissionsStatus.values.count { it.riskLevel == PermissionRiskLevel.HIGH },
                permissions = allPermissionsStatus,
                usageStatistics = usageStatistics,
                auditEvents = auditEvents,
                recommendations = generatePermissionRecommendations(allPermissionsStatus)
            )
            
            SecurityLog.i("权限审计报告已生成")
            report
        } catch (e: Exception) {
            SecurityLog.e("生成权限审计报告失败", e)
            PermissionAuditReport()
        }
    }

    /**
     * 添加权限监听器
     */
    fun addPermissionListener(listener: PermissionListener) {
        permissionListeners.add(listener)
        SecurityLog.d("权限监听器已添加: ${listener.javaClass.simpleName}")
    }

    /**
     * 移除权限监听器
     */
    fun removePermissionListener(listener: PermissionListener) {
        permissionListeners.remove(listener)
        SecurityLog.d("权限监听器已移除: ${listener.javaClass.simpleName}")
    }

    /**
     * 获取权限事件流
     */
    fun getPermissionEventFlow(): Flow<PermissionEvent> = flow {
        permissionEventChannel.receiveAsFlow().collect { event ->
            emit(event)
        }
    }

    /**
     * 检查最小权限原则
     */
    suspend fun checkLeastPrivilegePrinciple(): LeastPrivilegeCheckResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val allPermissions = getAllPossiblePermissions()
            val activePermissions = allPermissions.filter { 
                getPermissionState(it) == PermissionState.GRANTED 
            }
            
            val violations = mutableListOf<PrivilegeViolation>()
            
            // 检查是否有不必要的危险权限
            activePermissions.forEach { permission ->
                if (DANGEROUS_PERMISSIONS.contains(permission)) {
                    val usage = permissionUsage[permission]
                    if (usage == null || usage.requestCount == 0) {
                        violations.add(
                            PrivilegeViolation(
                                type = PrivilegeViolationType.UNUSED_DANGEROUS_PERMISSION,
                                permission = permission,
                                severity = ViolationSeverity.HIGH,
                                description = "未使用的危险权限: $permission"
                            )
                        )
                    }
                }
            }
            
            // 检查权限使用模式
            activePermissions.forEach { permission ->
                val usage = permissionUsage[permission]
                if (usage != null && usage.deniedCount > MAX_FAILED_REQUESTS) {
                    violations.add(
                        PrivilegeViolation(
                            type = PrivilegeViolationType.FREQUENTLY_DENIED,
                            permission = permission,
                            severity = ViolationSeverity.MEDIUM,
                            description = "频繁被拒绝的权限: $permission (拒绝次数: ${usage.deniedCount})"
                        )
                    )
                }
            }
            
            LeastPrivilegeCheckResult(
                isCompliant = violations.isEmpty(),
                violations = violations,
                complianceScore = calculateComplianceScore(violations),
                recommendations = generateLeastPrivilegeRecommendations(violations)
            )
        } catch (e: Exception) {
            SecurityLog.e("检查最小权限原则失败", e)
            LeastPrivilegeCheckResult(
                isCompliant = false,
                violations = emptyList(),
                complianceScore = 0.0,
                recommendations = listOf("权限检查失败，请重新检查")
            )
        }
    }

    /**
     * 权限审计循环
     */
    private suspend fun permissionAuditLoop() {
        while (isMonitoring.get()) {
            try {
                // 执行权限审计
                performPermissionAudit()
                
                // 检查权限变更
                checkPermissionChanges()
                
                // 生成定期报告
                if (System.currentTimeMillis() % PERMISSION_AUDIT_INTERVAL < 1000) {
                    generatePermissionAuditReport()
                }
                
                delay(60000) // 1分钟间隔
            } catch (e: Exception) {
                SecurityLog.e("权限审计循环异常", e)
                delay(60000)
            }
        }
    }

    /**
     * 收集当前权限状态
     */
    private fun collectCurrentPermissionStates() {
        try {
            val allPermissions = getAllPossiblePermissions()
            allPermissions.forEach { permission ->
                val state = getPermissionState(permission)
                permissionStates[permission] = state
                
                // 初始化使用统计
                if (!permissionUsage.containsKey(permission)) {
                    permissionUsage[permission] = PermissionUsage(
                        permission = permission,
                        lastUsed = System.currentTimeMillis(),
                        requestCount = 0,
                        grantedCount = 0,
                        deniedCount = 0,
                        lastState = state
                    )
                }
            }
            
            SecurityLog.i("权限状态收集完成: ${allPermissions.size} 个权限")
        } catch (e: Exception) {
            SecurityLog.e("收集权限状态失败", e)
        }
    }

    /**
     * 初始化权限规则
     */
    private fun initializePermissionRules() {
        permissionRules.clear()
        
        // 添加默认规则
        permissionRules.addAll(listOf(
            PermissionRule(
                id = "rule_001",
                name = "危险权限监控",
                type = PermissionRuleType.MONITORING,
                condition = { permission, state ->
                    DANGEROUS_PERMISSIONS.contains(permission) && state == PermissionState.GRANTED
                },
                action = PermissionRuleAction.LOG,
                enabled = true
            ),
            PermissionRule(
                id = "rule_002",
                name = "频繁拒绝警告",
                type = PermissionRuleType.WARNING,
                condition = { permission, state ->
                    val usage = permissionUsage[permission]
                    usage != null && usage.deniedCount > MAX_FAILED_REQUESTS
                },
                action = PermissionRuleAction.ALERT,
                enabled = true
            ),
            PermissionRule(
                id = "rule_003",
                name = "未使用权限检测",
                type = PermissionRuleType.OPTIMIZATION,
                condition = { permission, state ->
                    DANGEROUS_PERMISSIONS.contains(permission) && 
                    state == PermissionState.GRANTED && 
                    (permissionUsage[permission]?.requestCount ?: 0) == 0
                },
                action = PermissionRuleAction.RECOMMEND_REVOCATION,
                enabled = true
            )
        ))
    }

    /**
     * 执行权限审计
     */
    private suspend fun performPermissionAudit() {
        try {
            // 检查权限规则
            permissionRules.filter { it.enabled }.forEach { rule ->
                permissionStates.forEach { (permission, state) ->
                    if (rule.condition(permission, state)) {
                        handlePermissionRuleViolation(rule, permission, state)
                    }
                }
            }
        } catch (e: Exception) {
            SecurityLog.e("执行权限审计失败", e)
        }
    }

    /**
     * 检查权限变更
     */
    private suspend fun checkPermissionChanges() {
        try {
            val allPermissions = getAllPossiblePermissions()
            allPermissions.forEach { permission ->
                val currentState = getPermissionState(permission)
                val previousState = permissionStates[permission]
                
                if (currentState != previousState) {
                    handlePermissionStateChange(permission, previousState, currentState)
                    permissionStates[permission] = currentState
                }
            }
        } catch (e: Exception) {
            SecurityLog.e("检查权限变更失败", e)
        }
    }

    /**
     * 处理权限状态变更
     */
    private suspend fun handlePermissionStateChange(
        permission: String,
        previousState: PermissionState?,
        currentState: PermissionState
    ) {
        try {
            logPermissionEvent(
                type = PermissionEventType.STATE_CHANGED,
                severity = PermissionSeverity.INFO,
                description = "权限状态变更: $permission (${previousState?.name} -> ${currentState.name})",
                metadata = mapOf(
                    "permission" to permission,
                    "previousState" to previousState?.name,
                    "currentState" to currentState.name
                )
            )
            
            // 更新使用统计
            val usage = permissionUsage[permission]?.copy(
                lastUsed = System.currentTimeMillis(),
                lastState = currentState,
                requestCount = (permissionUsage[permission]?.requestCount ?: 0) + 1,
                grantedCount = if (currentState == PermissionState.GRANTED) {
                    (permissionUsage[permission]?.grantedCount ?: 0) + 1
                } else {
                    permissionUsage[permission]?.grantedCount ?: 0
                },
                deniedCount = if (currentState == PermissionState.DENIED) {
                    (permissionUsage[permission]?.deniedCount ?: 0) + 1
                } else {
                    permissionUsage[permission]?.deniedCount ?: 0
                }
            ) ?: PermissionUsage(
                permission = permission,
                lastUsed = System.currentTimeMillis(),
                requestCount = 1,
                grantedCount = if (currentState == PermissionState.GRANTED) 1 else 0,
                deniedCount = if (currentState == PermissionState.DENIED) 1 else 0,
                lastState = currentState
            )
            
            permissionUsage[permission] = usage
            
            // 通知监听器
            permissionListeners.forEach { listener ->
                listener.onPermissionStateChanged(permission, previousState, currentState)
            }
        } catch (e: Exception) {
            SecurityLog.e("处理权限状态变更失败: $permission", e)
        }
    }

    /**
     * 处理权限规则违规
     */
    private suspend fun handlePermissionRuleViolation(
        rule: PermissionRule,
        permission: String,
        state: PermissionState
    ) {
        try {
            when (rule.action) {
                PermissionRuleAction.LOG -> {
                    logPermissionEvent(
                        type = PermissionEventType.RULE_VIOLATION,
                        severity = PermissionSeverity.INFO,
                        description = "权限规则违规: ${rule.name}",
                        metadata = mapOf(
                            "ruleId" to rule.id,
                            "permission" to permission,
                            "state" to state.name
                        )
                    )
                }
                PermissionRuleAction.ALERT -> {
                    logPermissionEvent(
                        type = PermissionEventType.RULE_VIOLATION,
                        severity = PermissionSeverity.WARNING,
                        description = "权限规则警告: ${rule.name}",
                        metadata = mapOf(
                            "ruleId" to rule.id,
                            "permission" to permission,
                            "state" to state.name
                        )
                    )
                }
                PermissionRuleAction.RECOMMEND_REVOCATION -> {
                    logPermissionEvent(
                        type = PermissionEventType.RULE_VIOLATION,
                        severity = PermissionSeverity.RECOMMENDATION,
                        description = "建议撤销权限: ${rule.name}",
                        metadata = mapOf(
                            "ruleId" to rule.id,
                            "permission" to permission,
                            "state" to state.name
                        )
                    )
                }
            }
        } catch (e: Exception) {
            SecurityLog.e("处理权限规则违规失败", e)
        }
    }

    // 辅助方法
    private fun shouldShowPermissionRationale(permission: String): Boolean {
        return try {
            // 检查是否应该显示权限请求理由
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // 这里需要Activity上下文来判断
                // ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
                false // 简化实现
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun createPermissionRequest(permission: String, rationale: String?): PermissionRequest {
        return PermissionRequest(
            id = generateRequestId(),
            permissions = listOf(permission),
            rationale = rationale,
            timestamp = System.currentTimeMillis()
        )
    }

    private suspend fun simulatePermissionRequest(request: PermissionRequest): PermissionRequestResult {
        // 模拟权限请求结果
        val permission = request.permissions.first()
        val state = getPermissionState(permission)
        
        return PermissionRequestResult(
            success = true,
            granted = state == PermissionState.GRANTED,
            permission = permission,
            state = state,
            error = null
        )
    }

    private fun updatePermissionState(permission: String, state: PermissionState) {
        permissionStates[permission] = state
    }

    private fun logPermissionUsage(permission: String, action: PermissionAction, state: PermissionState) {
        val usage = permissionUsage[permission]?.copy(
            lastUsed = System.currentTimeMillis(),
            lastState = state,
            requestCount = (permissionUsage[permission]?.requestCount ?: 0) + 1
        ) ?: PermissionUsage(
            permission = permission,
            lastUsed = System.currentTimeMillis(),
            requestCount = 1,
            grantedCount = if (state == PermissionState.GRANTED) 1 else 0,
            deniedCount = if (state == PermissionState.DENIED) 1 else 0,
            lastState = state
        )
        
        permissionUsage[permission] = usage
    }

    private fun getAllPossiblePermissions(): List<String> {
        return DANGEROUS_PERMISSIONS + SIGNATURE_PERMISSIONS + SYSTEM_PERMISSIONS
    }

    private fun categorizePermission(permission: String): PermissionCategory {
        return when {
            DANGEROUS_PERMISSIONS.contains(permission) -> PermissionCategory.DANGEROUS
            SIGNATURE_PERMISSIONS.contains(permission) -> PermissionCategory.SIGNATURE
            SYSTEM_PERMISSIONS.contains(permission) -> PermissionCategory.SYSTEM
            else -> PermissionCategory.NORMAL
        }
    }

    private fun calculateRiskDistribution(): Map<PermissionRiskLevel, Int> {
        return permissionUsage.keys.map { getPermissionRiskAssessment(it).riskLevel }
            .groupingBy { it }.eachCount()
    }

    private fun generatePermissionRecommendations(status: Map<String, PermissionStatus>): List<String> {
        val recommendations = mutableListOf<String>()
        
        status.values.forEach { status ->
            if (status.riskLevel == PermissionRiskLevel.HIGH && status.state == PermissionState.GRANTED) {
                recommendations.add("高风险权限 ${status.permission} 需要谨慎使用")
            }
            
            if (status.requestCount == 0 && status.state == PermissionState.GRANTED) {
                recommendations.add("未使用的权限 ${status.permission} 可以考虑撤销")
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("当前权限配置合理")
        }
        
        return recommendations
    }

    private fun calculateComplianceScore(violations: List<PrivilegeViolation>): Double {
        if (violations.isEmpty()) return 1.0
        
        val totalWeight = violations.sumOf { it.severity.weight }
        val maxWeight = violations.size * ViolationSeverity.HIGH.weight
        return (1.0 - (totalWeight.toDouble() / maxWeight)).coerceIn(0.0, 1.0)
    }

    private fun generateLeastPrivilegeRecommendations(violations: List<PrivilegeViolation>): List<String> {
        val recommendations = mutableListOf<String>()
        
        violations.forEach { violation ->
            when (violation.type) {
                PrivilegeViolationType.UNUSED_DANGEROUS_PERMISSION -> {
                    recommendations.add("撤销未使用的危险权限: ${violation.permission}")
                }
                PrivilegeViolationType.FREQUENTLY_DENIED -> {
                    recommendations.add("检查频繁被拒绝的权限: ${violation.permission}")
                }
            }
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("符合最小权限原则")
        }
        
        return recommendations
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            SecurityLog.e("打开应用设置失败", e)
        }
    }

    private fun logPermissionEvent(
        type: PermissionEventType,
        severity: PermissionSeverity,
        description: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        permissionScope.launch {
            try {
                permissionEventChannel.send(
                    PermissionEvent(
                        type = type,
                        severity = severity,
                        description = description,
                        timestamp = System.currentTimeMillis(),
                        metadata = metadata
                    )
                )
                
                // 记录到审计日志
                synchronized(permissionAuditLog) {
                    permissionAuditLog.add(
                        PermissionAuditEvent(
                            id = generateAuditId(),
                            type = type,
                            severity = severity,
                            description = description,
                            timestamp = System.currentTimeMillis(),
                            metadata = metadata
                        )
                    )
                    
                    // 保持日志大小限制
                    if (permissionAuditLog.size > 1000) {
                        permissionAuditLog.removeFirst()
                    }
                }
            } catch (e: Exception) {
                SecurityLog.e("记录权限事件失败", e)
            }
        }
    }

    private fun generateRequestId(): String = "req_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
    private fun generateAuditId(): String = "audit_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
}

// 权限风险评估器
class PermissionRiskAssessor {
    fun assessRisk(
        permission: String,
        state: PermissionState,
        usage: PermissionUsage?
    ): PermissionRiskAssessment {
        var riskScore = 0.0
        
        // 基础风险评分
        riskScore += when {
            permission.contains("CAMERA") || permission.contains("RECORD_AUDIO") -> 0.8
            permission.contains("LOCATION") -> 0.7
            permission.contains("CONTACT") || permission.contains("CALENDAR") -> 0.6
            permission.contains("STORAGE") -> 0.5
            permission.contains("PHONE") -> 0.7
            else -> 0.3
        }
        
        // 状态调整
        if (state == PermissionState.GRANTED) {
            riskScore += 0.2
        }
        
        // 使用模式调整
        usage?.let { usageData ->
            if (usageData.requestCount > 10) {
                riskScore += 0.1
            }
            if (usageData.deniedCount > usageData.requestCount * 0.5) {
                riskScore -= 0.2
            }
        }
        
        riskScore = riskScore.coerceIn(0.0, 1.0)
        
        val riskLevel = when {
            riskScore >= 0.8 -> PermissionRiskLevel.HIGH
            riskScore >= 0.6 -> PermissionRiskLevel.MEDIUM
            riskScore >= 0.3 -> PermissionRiskLevel.LOW
            else -> PermissionRiskLevel.MINIMAL
        }
        
        return PermissionRiskAssessment(
            permission = permission,
            riskLevel = riskLevel,
            riskScore = riskScore,
            factors = generateRiskFactors(permission, state, usage),
            recommendations = generateRiskRecommendations(permission, riskLevel)
        )
    }
    
    private fun generateRiskFactors(
        permission: String,
        state: PermissionState,
        usage: PermissionUsage?
    ): List<RiskFactor> {
        val factors = mutableListOf<RiskFactor>()
        
        if (state == PermissionState.GRANTED) {
            factors.add(RiskFactor("权限已授予", 0.2))
        }
        
        if (permission.contains("CAMERA") || permission.contains("RECORD_AUDIO")) {
            factors.add(RiskFactor("涉及媒体访问", 0.4))
        }
        
        if (permission.contains("LOCATION")) {
            factors.add(RiskFactor("涉及位置信息", 0.3))
        }
        
        usage?.let { usageData ->
            if (usageData.requestCount > 5) {
                factors.add(RiskFactor("频繁请求", 0.1))
            }
        }
        
        return factors
    }
    
    private fun generateRiskRecommendations(
        permission: String,
        riskLevel: PermissionRiskLevel
    ): List<String> {
        return when (riskLevel) {
            PermissionRiskLevel.HIGH -> listOf("谨慎使用此权限", "定期检查权限使用情况")
            PermissionRiskLevel.MEDIUM -> listOf("注意权限使用范围")
            PermissionRiskLevel.LOW -> listOf("权限使用相对安全")
            PermissionRiskLevel.MINIMAL -> listOf("低风险权限")
            PermissionRiskLevel.UNKNOWN -> listOf("需要进一步评估")
        }
    }
}

// 权限监听器接口
interface PermissionListener {
    fun onPermissionStateChanged(permission: String, previousState: PermissionState?, currentState: PermissionState)
    fun onPermissionRequest(request: PermissionRequest)
    fun onPermissionViolation(violation: PrivilegeViolation)
}

// 数据类定义
enum class PermissionState {
    GRANTED,
    DENIED,
    UNKNOWN
}

enum class PermissionAction {
    REQUESTED,
    GRANTED,
    DENIED,
    REVOKED,
    FAILED
}

enum class PermissionEventType {
    SYSTEM,
    REQUESTED,
    GRANTED,
    DENIED,
    REVOKED,
    STATE_CHANGED,
    RULE_VIOLATION,
    RISK_ASSESSMENT,
    AUDIT_REPORT
}

enum class PermissionSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO,
    RECOMMENDATION
}

data class PermissionRequest(
    val id: String,
    val permissions: List<String>,
    val rationale: String?,
    val timestamp: Long
)

data class PermissionRequestResult(
    val success: Boolean,
    val granted: Boolean,
    val permission: String,
    val state: PermissionState,
    val error: String?
)

data class PermissionResult(
    val success: Boolean,
    val permission: String,
    val action: PermissionAction,
    val timestamp: Long
)

data class PermissionUsage(
    val permission: String,
    val lastUsed: Long,
    val requestCount: Int,
    val grantedCount: Int,
    val deniedCount: Int,
    val lastState: PermissionState
)

data class PermissionStatus(
    val permission: String,
    val state: PermissionState,
    val category: PermissionCategory,
    val riskLevel: PermissionRiskLevel,
    val lastUsed: Long?,
    val requestCount: Int,
    val grantedCount: Int,
    val deniedCount: Int
)

enum class PermissionCategory {
    NORMAL,
    DANGEROUS,
    SIGNATURE,
    SYSTEM
}

enum class PermissionRiskLevel {
    MINIMAL,
    LOW,
    MEDIUM,
    HIGH,
    UNKNOWN
}

data class PermissionRiskAssessment(
    val permission: String,
    val riskLevel: PermissionRiskLevel,
    val riskScore: Double,
    val factors: List<RiskFactor>,
    val recommendations: List<String>
)

data class RiskFactor(
    val name: String,
    val weight: Double
)

data class PermissionUsageStatistics(
    val totalPermissions: Int = 0,
    val grantedPermissions: Int = 0,
    val dangerousPermissions: Int = 0,
    val permissionUsage: Map<String, PermissionUsage> = emptyMap(),
    val riskDistribution: Map<PermissionRiskLevel, Int> = emptyMap()
)

data class PermissionEvent(
    val type: PermissionEventType,
    val severity: PermissionSeverity,
    val description: String,
    val timestamp: Long,
    val metadata: Map<String, Any>
)

data class PermissionAuditEvent(
    val id: String,
    val type: PermissionEventType,
    val severity: PermissionSeverity,
    val description: String,
    val timestamp: Long,
    val metadata: Map<String, Any>
)

data class PermissionAuditReport(
    val generatedAt: Long,
    val totalPermissions: Int,
    val grantedPermissions: Int,
    val deniedPermissions: Int,
    val highRiskPermissions: Int,
    val permissions: Map<String, PermissionStatus>,
    val usageStatistics: PermissionUsageStatistics,
    val auditEvents: List<PermissionAuditEvent>,
    val recommendations: List<String>
) {
    companion object {
        fun empty() = PermissionAuditReport(
            generatedAt = 0,
            totalPermissions = 0,
            grantedPermissions = 0,
            deniedPermissions = 0,
            highRiskPermissions = 0,
            permissions = emptyMap(),
            usageStatistics = PermissionUsageStatistics(),
            auditEvents = emptyList(),
            recommendations = emptyList()
        )
    }
}

data class PermissionRule(
    val id: String,
    val name: String,
    val type: PermissionRuleType,
    val condition: (String, PermissionState) -> Boolean,
    val action: PermissionRuleAction,
    val enabled: Boolean
)

enum class PermissionRuleType {
    MONITORING,
    WARNING,
    OPTIMIZATION,
    COMPLIANCE
}

enum class PermissionRuleAction {
    LOG,
    ALERT,
    RECOMMEND_REVOCATION,
    BLOCK
}

data class PrivilegeViolation(
    val type: PrivilegeViolationType,
    val permission: String,
    val severity: ViolationSeverity,
    val description: String
)

enum class PrivilegeViolationType {
    UNUSED_DANGEROUS_PERMISSION,
    FREQUENTLY_DENIED,
    UNNECESSARY_PERMISSION,
    DANGEROUS_COMBINATION
}

enum class ViolationSeverity {
    HIGH(2),
    MEDIUM(1),
    LOW(0);
    
    abstract val weight: Int
}

data class LeastPrivilegeCheckResult(
    val isCompliant: Boolean,
    val violations: List<PrivilegeViolation>,
    val complianceScore: Double,
    val recommendations: List<String>
)