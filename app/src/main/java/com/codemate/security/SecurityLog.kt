package com.codemate.security

import android.util.Log

/**
 * 安全日志工具类
 * 统一的安全日志记录，支持日志级别控制和安全事件记录
 */
object SecurityLog {
    
    private const val TAG = "CodeMateSecurity"
    
    /**
     * 信息级别日志
     */
    fun i(message: String, throwable: Throwable? = null) {
        Log.i(TAG, message, throwable)
    }
    
    /**
     * 调试级别日志
     */
    fun d(message: String, throwable: Throwable? = null) {
        if (isDebugEnabled()) {
            Log.d(TAG, message, throwable)
        }
    }
    
    /**
     * 警告级别日志
     */
    fun w(message: String, throwable: Throwable? = null) {
        Log.w(TAG, message, throwable)
    }
    
    /**
     * 错误级别日志
     */
    fun e(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
    
    /**
     * 安全事件记录
     */
    fun securityEvent(event: String, details: String = "") {
        val logMessage = "SECURITY_EVENT: $event${if (details.isNotEmpty()) " - $details" else ""}"
        Log.i(TAG, logMessage)
    }
    
    /**
     * 权限事件记录
     */
    fun permissionEvent(action: String, permission: String, result: String) {
        val logMessage = "PERMISSION_EVENT: $action - $permission - $result"
        Log.i(TAG, logMessage)
    }
    
    /**
     * 网络安全事件记录
     */
    fun networkSecurityEvent(hostname: String, action: String, status: String) {
        val logMessage = "NETWORK_SECURITY: $hostname - $action - $status"
        Log.i(TAG, logMessage)
    }
    
    /**
     * 数据安全事件记录
     */
    fun dataSecurityEvent(dataType: String, action: String, classification: String) {
        val logMessage = "DATA_SECURITY: $dataType - $action - $classification"
        Log.i(TAG, logMessage)
    }
    
    /**
     * 更新安全事件记录
     */
    fun updateSecurityEvent(version: String, action: String, status: String) {
        val logMessage = "UPDATE_SECURITY: $version - $action - $status"
        Log.i(TAG, logMessage)
    }
    
    /**
     * 性能监控事件记录
     */
    fun performanceEvent(metric: String, value: Double, threshold: Double? = null) {
        val logMessage = "PERFORMANCE: $metric = $value${if (threshold != null) " (threshold: $threshold)" else ""}"
        if (isPerformanceLoggingEnabled()) {
            Log.d(TAG, logMessage)
        }
    }
    
    /**
     * 资源监控事件记录
     */
    fun resourceEvent(resourceType: String, action: String, size: Long) {
        val logMessage = "RESOURCE: $resourceType - $action - ${formatBytes(size)}"
        if (isResourceLoggingEnabled()) {
            Log.d(TAG, logMessage)
        }
    }
    
    /**
     * 威胁检测事件记录
     */
    fun threatDetected(threatType: String, severity: String, description: String) {
        val logMessage = "THREAT_DETECTED: $threatType - $severity - $description"
        Log.w(TAG, logMessage)
    }
    
    /**
     * 审计事件记录
     */
    fun auditEvent(eventType: String, user: String, action: String, result: String) {
        val logMessage = "AUDIT: $eventType - User: $user - Action: $action - Result: $result"
        Log.i(TAG, logMessage)
    }
    
    /**
     * 安全违规记录
     */
    fun securityViolation(violationType: String, details: String, severity: String) {
        val logMessage = "SECURITY_VIOLATION: $violationType - $details - Severity: $severity"
        Log.e(TAG, logMessage)
    }
    
    /**
     * 调试检测事件记录
     */
    fun debugDetectionEvent(detectionType: String, result: String, evidence: String = "") {
        val logMessage = "DEBUG_DETECTION: $detectionType - $result${if (evidence.isNotEmpty()) " - Evidence: $evidence" else ""}"
        Log.w(TAG, logMessage)
    }
    
    /**
     * 完整性检查事件记录
     */
    fun integrityCheckEvent(checkType: String, result: String, details: String = "") {
        val logMessage = "INTEGRITY_CHECK: $checkType - $result${if (details.isNotEmpty()) " - Details: $details" else ""}"
        Log.i(TAG, logMessage)
    }
    
    /**
     * 沙盒执行事件记录
     */
    fun sandboxExecutionEvent(sandboxId: String, language: String, executionTime: Long, result: String) {
        val logMessage = "SANDBOX_EXECUTION: $sandboxId - Language: $language - Time: ${executionTime}ms - Result: $result"
        if (isSandboxLoggingEnabled()) {
            Log.d(TAG, logMessage)
        }
    }
    
    /**
     * 加密操作事件记录
     */
    fun encryptionEvent(operation: String, algorithm: String, keyId: String? = null, result: String) {
        val logMessage = "ENCRYPTION: $operation - Algorithm: $algorithm${if (keyId != null) " - Key: $keyId" else ""} - Result: $result"
        Log.i(TAG, logMessage)
    }
    
    /**
     * 证书绑定事件记录
     */
    fun certificatePinningEvent(hostname: String, action: String, status: String) {
        val logMessage = "CERTIFICATE_PINNING: $hostname - $action - Status: $status"
        Log.i(TAG, logMessage)
    }
    
    /**
     * 格式化的字节数显示
     */
    private fun formatBytes(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0
        
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        
        return String.format("%.2f %s", size, units[unitIndex])
    }
    
    /**
     * 检查是否启用调试日志
     */
    private fun isDebugEnabled(): Boolean {
        return BuildConfig.DEBUG || isSecurityDebugEnabled()
    }
    
    /**
     * 检查是否启用性能日志
     */
    private fun isPerformanceLoggingEnabled(): Boolean {
        return BuildConfig.DEBUG || isSecurityDebugEnabled()
    }
    
    /**
     * 检查是否启用资源日志
     */
    private fun isResourceLoggingEnabled(): Boolean {
        return BuildConfig.DEBUG || isSecurityDebugEnabled()
    }
    
    /**
     * 检查是否启用沙盒日志
     */
    private fun isSandboxLoggingEnabled(): Boolean {
        return BuildConfig.DEBUG || isSecurityDebugEnabled()
    }
    
    /**
     * 检查是否启用安全调试
     */
    private fun isSecurityDebugEnabled(): Boolean {
        return try {
            // 这里可以检查系统属性或配置
            false // 简化实现
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * 构建配置占位类
 * 实际项目中应该使用真实的构建配置
 */
object BuildConfig {
    const val DEBUG = true
    const val BUILD_TYPE = "debug"
}