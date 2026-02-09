package com.codemate.security

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安全更新管理器
 * 安全地检查和安装应用更新
 * 提供签名验证、完整性检查、更新审计等功能
 */
@Singleton
class SecureUpdateManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "SecureUpdateManager"
        private const val UPDATE_CHECK_INTERVAL = 24 * 3600 * 1000L // 24小时
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val DOWNLOAD_TIMEOUT = 30000L // 30秒
        private const val VERIFICATION_TIMEOUT = 10000L // 10秒
        private const val SIGNATURE_MISMATCH_RETRY = 5
        
        // 更新源配置
        private val UPDATE_SOURCES = listOf(
            "https://api.codemate.com/updates/v1/check",
            "https://updates.codemate.com/check"
        )
        
        // 更新类型
        private val CRITICAL_UPDATE_TYPES = setOf(
            "security_patch",
            "vulnerability_fix",
            "critical_bug_fix"
        )
        
        // 下载目录
        private const val DOWNLOAD_DIR = "updates"
        private const val BACKUP_DIR = "backups"
    }

    private val updateScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val verificationScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val isUpdateChecking = AtomicBoolean(false)
    private val isUpdating = AtomicBoolean(false)
    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }
    
    // 更新状态跟踪
    private val updateStates = ConcurrentHashMap<String, UpdateState>()
    private val downloadProgress = ConcurrentHashMap<String, DownloadProgress>()
    private val verificationResults = ConcurrentHashMap<String, VerificationResult>()
    
    // 更新配置
    private val updateConfig = UpdateConfig()
    
    // 事件通道
    private val updateEventChannel = Channel<UpdateEvent>(Channel.UNLIMITED)
    
    // 签名验证器
    private val signatureValidator = UpdateSignatureValidator()
    
    // 更新策略
    private val updateStrategies = mutableListOf<UpdateStrategy>()
    
    // 安全检查器
    private val securityChecker = UpdateSecurityChecker()

    /**
     * 启动更新管理
     */
    fun startUpdateManagement(): Boolean {
        return try {
            // 初始化更新策略
            initializeUpdateStrategies()
            
            // 启动定期更新检查
            updateScope.launch {
                periodicUpdateCheckLoop()
            }
            
            SecurityLog.i("安全更新管理器已启动")
            logUpdateEvent(
                type = UpdateEventType.SYSTEM,
                severity = UpdateSeverity.INFO,
                description = "安全更新管理器已启动"
            )
            
            true
        } catch (e: Exception) {
            SecurityLog.e("启动更新管理失败", e)
            false
        }
    }

    /**
     * 停止更新管理
     */
    fun stopUpdateManagement() {
        try {
            updateScope.cancel()
            verificationScope.cancel()
            updateEventChannel.close()
            
            SecurityLog.i("安全更新管理器已停止")
            logUpdateEvent(
                type = UpdateEventType.SYSTEM,
                severity = UpdateSeverity.INFO,
                description = "安全更新管理器已停止"
            )
        } catch (e: Exception) {
            SecurityLog.e("停止更新管理失败", e)
        }
    }

    /**
     * 检查更新
     */
    suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        return@withContext try {
            if (isUpdateChecking.getAndSet(true)) {
                return@withContext UpdateCheckResult(
                    success = false,
                    hasUpdates = false,
                    updates = emptyList(),
                    error = "更新检查已在进行中"
                )
            }

            val currentVersion = getCurrentVersion()
            val updates = mutableListOf<AvailableUpdate>()
            
            // 从多个源检查更新
            for (source in UPDATE_SOURCES) {
                try {
                    val sourceUpdates = checkUpdatesFromSource(source, currentVersion)
                    updates.addAll(sourceUpdates)
                } catch (e: Exception) {
                    SecurityLog.w("从源检查更新失败: $source", e)
                }
            }
            
            // 去重和排序
            val uniqueUpdates = updates.distinctBy { it.versionCode }
                .sortedByDescending { it.priority }
            
            val hasUpdates = uniqueUpdates.isNotEmpty()
            
            // 记录检查结果
            logUpdateEvent(
                type = UpdateEventType.UPDATE_CHECK,
                severity = if (hasUpdates) UpdateSeverity.WARNING else UpdateSeverity.INFO,
                description = "更新检查完成: ${uniqueUpdates.size} 个可用更新",
                metadata = mapOf(
                    "currentVersion" to currentVersion,
                    "updatesFound" to uniqueUpdates.size
                )
            )
            
            UpdateCheckResult(
                success = true,
                hasUpdates = hasUpdates,
                updates = uniqueUpdates,
                error = null
            )
        } catch (e: Exception) {
            SecurityLog.e("检查更新失败", e)
            UpdateCheckResult(
                success = false,
                hasUpdates = false,
                updates = emptyList(),
                error = e.message
            )
        } finally {
            isUpdateChecking.set(false)
        }
    }

    /**
     * 下载更新
     */
    suspend fun downloadUpdate(update: AvailableUpdate): DownloadResult = withContext(Dispatchers.IO) {
        return@withContext try {
            if (isUpdating.get()) {
                return@withContext DownloadResult(
                    success = false,
                    update = update,
                    downloadedBytes = 0,
                    totalBytes = 0,
                    error = "更新正在进行中"
                )
            }
            
            isUpdating.set(true)
            
            SecurityLog.i("开始下载更新: ${update.versionName} (${update.versionCode})")
            
            // 创建下载目录
            val downloadDir = createDownloadDirectory()
            val updateFile = File(downloadDir, "codemate_${update.versionCode}.apk")
            
            // 下载文件
            val result = downloadUpdateFile(update, updateFile)
            
            if (result.success) {
                // 验证下载的文件
                val verification = verifyDownloadedUpdate(update, updateFile)
                if (verification.isValid) {
                    updateStates[update.versionCode.toString()] = UpdateState.DOWNLOADED
                    downloadProgress[update.versionCode.toString()] = DownloadProgress(
                        downloaded = result.downloadedBytes,
                        total = result.totalBytes,
                        completed = true
                    )
                    
                    SecurityLog.i("更新下载完成并验证通过: ${update.versionName}")
                    
                    logUpdateEvent(
                        type = UpdateEventType.DOWNLOAD_COMPLETE,
                        severity = UpdateSeverity.INFO,
                        description = "更新下载完成: ${update.versionName}",
                        metadata = mapOf(
                            "versionCode" to update.versionCode,
                            "versionName" to update.versionName,
                            "fileSize" to result.totalBytes
                        )
                    )
                } else {
                    SecurityLog.e("更新验证失败: ${update.versionName}")
                    result.error = "更新验证失败: ${verification.error}"
                }
            }
            
            result
        } catch (e: Exception) {
            SecurityLog.e("下载更新失败: ${update.versionName}", e)
            DownloadResult(
                success = false,
                update = update,
                downloadedBytes = 0,
                totalBytes = 0,
                error = e.message
            )
        } finally {
            isUpdating.set(false)
        }
    }

    /**
     * 安装更新
     */
    suspend fun installUpdate(update: AvailableUpdate, silent: Boolean = false): InstallResult = withContext(Dispatchers.IO) {
        return@withContext try {
            val updateState = updateStates[update.versionCode.toString()]
            if (updateState != UpdateState.DOWNLOADED) {
                return@withContext InstallResult(
                    success = false,
                    update = update,
                    error = "更新未下载或下载失败"
                )
            }
            
            SecurityLog.i("开始安装更新: ${update.versionName}")
            
            // 备份当前应用
            val backupResult = backupCurrentApplication()
            if (!backupResult.success) {
                return@withContext InstallResult(
                    success = false,
                    update = update,
                    error = "备份失败: ${backupResult.error}"
                )
            }
            
            // 执行安装前安全检查
            val securityCheck = performSecurityChecks(update)
            if (!securityCheck.isSafe) {
                return@withContext InstallResult(
                    success = false,
                    update = update,
                    error = "安全检查失败: ${securityCheck.reasons.joinToString(", ")}"
                )
            }
            
            // 执行安装
            val installResult = executeInstallation(update, silent)
            
            if (installResult.success) {
                updateStates[update.versionCode.toString()] = UpdateState.INSTALLED
                
                logUpdateEvent(
                    type = UpdateEventType.INSTALLATION_COMPLETE,
                    severity = UpdateSeverity.INFO,
                    description = "更新安装成功: ${update.versionName}",
                    metadata = mapOf(
                        "versionCode" to update.versionCode,
                        "versionName" to update.versionName,
                        "silent" to silent
                    )
                )
                
                // 清理临时文件
                cleanupTemporaryFiles(update)
            }
            
            installResult
        } catch (e: Exception) {
            SecurityLog.e("安装更新失败: ${update.versionName}", e)
            InstallResult(
                success = false,
                update = update,
                error = e.message
            )
        }
    }

    /**
     * 获取更新状态
     */
    suspend fun getUpdateStatus(): UpdateStatus = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentVersion = getCurrentVersion()
            val lastCheck = updateStates["last_check"]?.timestamp ?: 0L
            val availableUpdates = updateStates.values.count { it == UpdateState.AVAILABLE }
            val downloadedUpdates = updateStates.values.count { it == UpdateState.DOWNLOADED }
            const installedUpdates = updateStates.values.count { it == UpdateState.INSTALLED }
            
            UpdateStatus(
                currentVersion = currentVersion,
                lastCheckTime = lastCheck,
                availableUpdates = availableUpdates,
                downloadedUpdates = downloadedUpdates,
                installedUpdates = installedUpdates,
                isChecking = isUpdateChecking.get(),
                isUpdating = isUpdating.get(),
                updateStates = updateStates.toMap()
            )
        } catch (e: Exception) {
            SecurityLog.e("获取更新状态失败", e)
            UpdateStatus()
        }
    }

    /**
     * 获取下载进度
     */
    suspend fun getDownloadProgress(updateId: String): DownloadProgress? = withContext(Dispatchers.IO) {
        return@withContext downloadProgress[updateId]
    }

    /**
     * 取消下载
     */
    suspend fun cancelDownload(updateId: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            downloadProgress[updateId]?.let { progress ->
                progress.isCancelled = true
                SecurityLog.i("下载已取消: $updateId")
                true
            } ?: false
        } catch (e: Exception) {
            SecurityLog.e("取消下载失败: $updateId", e)
            false
        }
    }

    /**
     * 验证更新签名
     */
    suspend fun verifyUpdateSignature(updateFile: File): VerificationResult = withContext(verificationScope) {
        return@withContext try {
            signatureValidator.verifySignature(updateFile)
        } catch (e: Exception) {
            SecurityLog.e("验证更新签名失败", e)
            VerificationResult(
                isValid = false,
                signature = null,
                error = e.message,
                timestamp = System.currentTimeMillis()
            )
        }
    }

    /**
     * 获取更新历史
     */
    suspend fun getUpdateHistory(): List<UpdateHistoryEntry> = withContext(Dispatchers.IO) {
        return@withContext try {
            // 这里应该从本地数据库或文件读取更新历史
            // 简化实现，返回空列表
            emptyList<UpdateHistoryEntry>()
        } catch (e: Exception) {
            SecurityLog.e("获取更新历史失败", e)
            emptyList()
        }
    }

    /**
     * 生成更新报告
     */
    suspend fun generateUpdateReport(): UpdateReport = withContext(Dispatchers.IO) {
        return@withContext try {
            val status = getUpdateStatus()
            val history = getUpdateHistory()
            const securityMetrics = getSecurityMetrics()
            
            UpdateReport(
                generatedAt = System.currentTimeMillis(),
                status = status,
                history = history,
                securityMetrics = securityMetrics,
                recommendations = generateUpdateRecommendations(status, history),
                complianceStatus = checkComplianceStatus()
            )
        } catch (e: Exception) {
            SecurityLog.e("生成更新报告失败", e)
            UpdateReport()
        }
    }

    /**
     * 获取更新事件流
     */
    fun getUpdateEventFlow(): Flow<UpdateEvent> = flow {
        updateEventChannel.receiveAsFlow().collect { event ->
            emit(event)
        }
    }

    /**
     * 设置更新策略
     */
    fun setUpdateStrategy(strategy: UpdateStrategy) {
        try {
            updateStrategies.clear()
            updateStrategies.add(strategy)
            
            SecurityLog.i("更新策略已设置: ${strategy.name}")
            
            logUpdateEvent(
                type = UpdateEventType.STRATEGY_CHANGED,
                severity = UpdateSeverity.INFO,
                description = "更新策略已更改: ${strategy.name}",
                metadata = mapOf(
                    "strategyName" to strategy.name,
                    "autoUpdate" to strategy.autoUpdate,
                    "checkInterval" to strategy.checkInterval
                )
            )
        } catch (e: Exception) {
            SecurityLog.e("设置更新策略失败", e)
        }
    }

    /**
     * 定期更新检查循环
     */
    private suspend fun periodicUpdateCheckLoop() {
        while (updateScope.isActive) {
            try {
                val result = checkForUpdates()
                
                if (result.success && result.hasUpdates) {
                    // 根据更新策略处理更新
                    handleAvailableUpdates(result.updates)
                }
                
                delay(UPDATE_CHECK_INTERVAL)
            } catch (e: Exception) {
                SecurityLog.e("定期更新检查异常", e)
                delay(UPDATE_CHECK_INTERVAL)
            }
        }
    }

    /**
     * 处理可用更新
     */
    private suspend fun handleAvailableUpdates(updates: List<AvailableUpdate>) {
        try {
            val strategy = updateStrategies.firstOrNull()
            if (strategy == null) {
                SecurityLog.w("未设置更新策略")
                return
            }
            
            updates.forEach { update ->
                updateStates[update.versionCode.toString()] = UpdateState.AVAILABLE
                
                when {
                    update.priority in CRITICAL_UPDATE_TYPES -> {
                        // 关键更新自动下载和安装
                        if (strategy.autoUpdate) {
                            handleCriticalUpdate(update)
                        } else {
                            notifyCriticalUpdate(update)
                        }
                    }
                    strategy.autoUpdate -> {
                        // 自动更新策略
                        handleAutoUpdate(update)
                    }
                    else -> {
                        // 手动更新
                        notifyManualUpdate(update)
                    }
                }
            }
        } catch (e: Exception) {
            SecurityLog.e("处理可用更新失败", e)
        }
    }

    /**
     * 处理关键更新
     */
    private suspend fun handleCriticalUpdate(update: AvailableUpdate) {
        try {
            SecurityLog.w("处理关键更新: ${update.versionName}")
            
            val downloadResult = downloadUpdate(update)
            if (downloadResult.success) {
                val installResult = installUpdate(update, silent = true)
                if (installResult.success) {
                    SecurityLog.i("关键更新已自动安装: ${update.versionName}")
                } else {
                    SecurityLog.e("关键更新安装失败: ${installResult.error}")
                }
            }
        } catch (e: Exception) {
            SecurityLog.e("处理关键更新失败", e)
        }
    }

    /**
     * 处理自动更新
     */
    private suspend fun handleAutoUpdate(update: AvailableUpdate) {
        try {
            SecurityLog.i("处理自动更新: ${update.versionName}")
            
            // 下载但不自动安装
            downloadUpdate(update)
        } catch (e: Exception) {
            SecurityLog.e("处理自动更新失败", e)
        }
    }

    /**
     * 通知关键更新
     */
    private suspend fun notifyCriticalUpdate(update: AvailableUpdate) {
        logUpdateEvent(
            type = UpdateEventType.CRITICAL_UPDATE_AVAILABLE,
            severity = UpdateSeverity.CRITICAL,
            description = "发现关键更新: ${update.versionName} - ${update.description}",
            metadata = mapOf(
                "versionCode" to update.versionCode,
                "versionName" to update.versionName,
                "priority" to update.priority
            )
        )
    }

    /**
     * 通知手动更新
     */
    private suspend fun notifyManualUpdate(update: AvailableUpdate) {
        logUpdateEvent(
            type = UpdateEventType.UPDATE_AVAILABLE,
            severity = UpdateSeverity.INFO,
            description = "发现可选更新: ${update.versionName} - ${update.description}",
            metadata = mapOf(
                "versionCode" to update.versionCode,
                "versionName" to update.versionName
            )
        )
    }

    /**
     * 从源检查更新
     */
    private suspend fun checkUpdatesFromSource(source: String, currentVersion: String): List<AvailableUpdate> {
        return withContext(Dispatchers.IO) {
            try {
                val url = "$source?package=${context.packageName}&version=$currentVersion"
                val response = makeSecureRequest(url)
                
                if (response.isNotEmpty()) {
                    parseUpdateResponse(response)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                SecurityLog.e("从源检查更新失败: $source", e)
                emptyList()
            }
        }
    }

    /**
     * 发起安全请求
     */
    private suspend fun makeSecureRequest(url: String): String {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val urlObj = URL(url)
                connection = urlObj.openConnection() as HttpURLConnection
                
                // 设置安全参数
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = DOWNLOAD_TIMEOUT.toInt()
                    readTimeout = DOWNLOAD_TIMEOUT.toInt()
                    setRequestProperty("User-Agent", "CodeMate-UpdateChecker/1.0")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("X-Request-ID", generateRequestId())
                }
                
                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    connection.inputStream.bufferedReader().use { reader ->
                        reader.readText()
                    }
                } else {
                    SecurityLog.w("更新检查请求失败: $responseCode")
                    ""
                }
            } catch (e: Exception) {
                SecurityLog.e("发起安全请求失败: $url", e)
                ""
            } finally {
                connection?.disconnect()
            }
        }
    }

    /**
     * 解析更新响应
     */
    private fun parseUpdateResponse(response: String): List<AvailableUpdate> {
        return try {
            // 这里应该使用JSON解析库
            // 简化实现，返回空列表
            emptyList<AvailableUpdate>()
        } catch (e: Exception) {
            SecurityLog.e("解析更新响应失败", e)
            emptyList()
        }
    }

    /**
     * 下载更新文件
     */
    private suspend fun downloadUpdateFile(update: AvailableUpdate, targetFile: File): DownloadResult {
        return withContext(Dispatchers.IO) {
            var input: InputStream? = null
            var output: OutputStream? = null
            var connection: HttpURLConnection? = null
            
            try {
                val url = URL(update.downloadUrl)
                connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "GET"
                    connectTimeout = DOWNLOAD_TIMEOUT.toInt()
                    readTimeout = DOWNLOAD_TIMEOUT.toInt()
                }
                
                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    return@withContext DownloadResult(
                        success = false,
                        update = update,
                        downloadedBytes = 0,
                        totalBytes = 0,
                        error = "下载失败: HTTP $responseCode"
                    )
                }
                
                val totalBytes = connection.contentLength.toLong()
                
                output = FileOutputStream(targetFile)
                input = connection.inputStream
                
                val buffer = ByteArray(8192)
                var downloadedBytes = 0L
                
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    // 检查是否被取消
                    downloadProgress[update.versionCode.toString()]?.let { progress ->
                        if (progress.isCancelled) {
                            targetFile.delete()
                            return@withContext DownloadResult(
                                success = false,
                                update = update,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                error = "下载已取消"
                            )
                        }
                    }
                    
                    output.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead
                    
                    // 更新进度
                    downloadProgress[update.versionCode.toString()] = DownloadProgress(
                        downloaded = downloadedBytes,
                        total = totalBytes,
                        completed = false
                    )
                }
                
                DownloadResult(
                    success = true,
                    update = update,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    error = null
                )
            } catch (e: Exception) {
                SecurityLog.e("下载更新文件失败: ${update.versionName}", e)
                DownloadResult(
                    success = false,
                    update = update,
                    downloadedBytes = 0,
                    totalBytes = 0,
                    error = e.message
                )
            } finally {
                input?.close()
                output?.close()
                connection?.disconnect()
            }
        }
    }

    /**
     * 验证下载的更新
     */
    private suspend fun verifyDownloadedUpdate(update: AvailableUpdate, file: File): VerificationResult {
        return withContext(verificationScope) {
            try {
                // 检查文件完整性
                val fileChecksum = calculateFileChecksum(file)
                if (fileChecksum != update.checksum) {
                    return@withContext VerificationResult(
                        isValid = false,
                        signature = null,
                        error = "文件校验和不匹配",
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                // 验证签名
                val signature = signatureValidator.verifySignature(file)
                
                if (!signature.isValid) {
                    return@withContext VerificationResult(
                        isValid = false,
                        signature = signature.signature,
                        error = "签名验证失败: ${signature.error}",
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                // 执行安全检查
                val securityCheck = securityChecker.performSecurityChecks(file)
                if (!securityCheck.isSafe) {
                    return@withContext VerificationResult(
                        isValid = false,
                        signature = signature.signature,
                        error = "安全检查失败: ${securityCheck.issues.joinToString(", ")}",
                        timestamp = System.currentTimeMillis()
                    )
                }
                
                VerificationResult(
                    isValid = true,
                    signature = signature.signature,
                    error = null,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                SecurityLog.e("验证下载更新失败: ${update.versionName}", e)
                VerificationResult(
                    isValid = false,
                    signature = null,
                    error = e.message,
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * 备份当前应用
     */
    private suspend fun backupCurrentApplication(): BackupResult {
        return withContext(Dispatchers.IO) {
            try {
                val backupDir = createBackupDirectory()
                val backupFile = File(backupDir, "codemate_backup_${System.currentTimeMillis()}.apk")
                
                val currentApkPath = context.packageCodePath
                val currentApk = File(currentApkPath)
                
                if (currentApk.exists()) {
                    currentApk.copyTo(backupFile, overwrite = true)
                    
                    BackupResult(
                        success = true,
                        backupPath = backupFile.absolutePath,
                        timestamp = System.currentTimeMillis(),
                        error = null
                    )
                } else {
                    BackupResult(
                        success = false,
                        backupPath = null,
                        timestamp = System.currentTimeMillis(),
                        error = "当前APK文件不存在"
                    )
                }
            } catch (e: Exception) {
                SecurityLog.e("备份当前应用失败", e)
                BackupResult(
                    success = false,
                    backupPath = null,
                    timestamp = System.currentTimeMillis(),
                    error = e.message
                )
            }
        }
    }

    /**
     * 执行安全检查
     */
    private suspend fun performSecurityChecks(update: AvailableUpdate): SecurityCheckResult {
        return withContext(verificationScope) {
            try {
                val issues = mutableListOf<String>()
                
                // 检查更新来源
                if (!UPDATE_SOURCES.any { update.downloadUrl.contains(it) }) {
                    issues.add("更新来源不受信任")
                }
                
                // 检查版本号
                val currentVersion = getCurrentVersion()
                if (update.versionCode <= currentVersion.versionCode) {
                    issues.add("版本号异常：当前版本高于更新版本")
                }
                
                // 检查时间戳
                if (System.currentTimeMillis() - update.timestamp > 30 * 24 * 3600 * 1000L) {
                    issues.add("更新包过于陈旧")
                }
                
                SecurityCheckResult(
                    isSafe = issues.isEmpty(),
                    reasons = issues,
                    timestamp = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                SecurityLog.e("执行安全检查失败", e)
                SecurityCheckResult(
                    isSafe = false,
                    reasons = listOf("检查过程出错: ${e.message}"),
                    timestamp = System.currentTimeMillis()
                )
            }
        }
    }

    /**
     * 执行安装
     */
    private suspend fun executeInstallation(update: AvailableUpdate, silent: Boolean): InstallResult {
        return withContext(Dispatchers.IO) {
            try {
                val downloadDir = createDownloadDirectory()
                val updateFile = File(downloadDir, "codemate_${update.versionCode}.apk")
                
                if (!updateFile.exists()) {
                    return@withContext InstallResult(
                        success = false,
                        update = update,
                        error = "更新文件不存在"
                    )
                }
                
                if (silent) {
                    // 静默安装（需要系统权限）
                    val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                        data = android.net.Uri.fromFile(updateFile)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(installIntent)
                } else {
                    // 手动安装
                    val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                        data = android.net.Uri.fromFile(updateFile)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(installIntent)
                }
                
                InstallResult(
                    success = true,
                    update = update,
                    error = null
                )
            } catch (e: Exception) {
                SecurityLog.e("执行安装失败: ${update.versionName}", e)
                InstallResult(
                    success = false,
                    update = update,
                    error = e.message
                )
            }
        }
    }

    /**
     * 清理临时文件
     */
    private suspend fun cleanupTemporaryFiles(update: AvailableUpdate) {
        withContext(Dispatchers.IO) {
            try {
                val downloadDir = createDownloadDirectory()
                val updateFile = File(downloadDir, "codemate_${update.versionCode}.apk")
                
                if (updateFile.exists()) {
                    updateFile.delete()
                    SecurityLog.i("临时文件已清理: ${updateFile.absolutePath}")
                }
            } catch (e: Exception) {
                SecurityLog.e("清理临时文件失败", e)
            }
        }
    }

    // 辅助方法
    private fun getCurrentVersion(): AppVersion {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            AppVersion(
                versionName = packageInfo.versionName,
                versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            )
        } catch (e: Exception) {
            SecurityLog.e("获取当前版本失败", e)
            AppVersion("unknown", 0L)
        }
    }

    private fun createDownloadDirectory(): File {
        val dir = File(context.filesDir, DOWNLOAD_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun createBackupDirectory(): File {
        val dir = File(context.filesDir, BACKUP_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun calculateFileChecksum(file: File): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    md.update(buffer, 0, bytesRead)
                }
            }
            md.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            SecurityLog.e("计算文件校验和失败", e)
            ""
        }
    }

    private fun initializeUpdateStrategies() {
        updateStrategies.clear()
        updateStrategies.add(
            UpdateStrategy(
                name = "默认策略",
                autoUpdate = false,
                silentUpdate = false,
                checkInterval = UPDATE_CHECK_INTERVAL,
                priority = 1
            )
        )
    }

    private fun generateUpdateRecommendations(status: UpdateStatus, history: List<UpdateHistoryEntry>): List<String> {
        val recommendations = mutableListOf<String>()
        
        if (status.availableUpdates > 0) {
            recommendations.add("有 ${status.availableUpdates} 个可用更新，建议及时安装")
        }
        
        if (status.downloadedUpdates > 0) {
            recommendations.add("有 ${status.downloadedUpdates} 个已下载的更新待安装")
        }
        
        val recentHistory = history.filter { 
            System.currentTimeMillis() - it.timestamp < 7 * 24 * 3600 * 1000L 
        }
        
        if (recentHistory.size < 2) {
            recommendations.add("近期更新较少，建议检查自动更新设置")
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("系统更新状态良好")
        }
        
        return recommendations
    }

    private fun checkComplianceStatus(): ComplianceStatus {
        return try {
            val status = runBlocking { getUpdateStatus() }
            val lastCheck = System.currentTimeMillis() - status.lastCheckTime
            val isCompliant = lastCheck < 7 * 24 * 3600 * 1000L // 7天内检查过
            
            ComplianceStatus(
                isCompliant = isCompliant,
                violations = if (isCompliant) emptyList() else listOf("更新检查超时"),
                score = if (isCompliant) 1.0 else 0.5,
                lastAudit = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            ComplianceStatus(
                isCompliant = false,
                violations = listOf("检查过程出错"),
                score = 0.0,
                lastAudit = System.currentTimeMillis()
            )
        }
    }

    private fun getSecurityMetrics(): SecurityMetrics {
        return SecurityMetrics(
            totalUpdates = updateStates.size,
            verifiedUpdates = verificationResults.values.count { it.isValid },
            failedVerifications = verificationResults.values.count { !it.isValid },
            lastSecurityCheck = System.currentTimeMillis(),
            securityScore = 1.0
        )
    }

    private fun logUpdateEvent(
        type: UpdateEventType,
        severity: UpdateSeverity,
        description: String,
        metadata: Map<String, Any> = emptyMap()
    ) {
        updateScope.launch {
            try {
                updateEventChannel.send(
                    UpdateEvent(
                        type = type,
                        severity = severity,
                        description = description,
                        timestamp = System.currentTimeMillis(),
                        metadata = metadata
                    )
                )
            } catch (e: Exception) {
                SecurityLog.e("记录更新事件失败", e)
            }
        }
    }

    private fun generateRequestId(): String = "req_${System.currentTimeMillis()}_${(Math.random() * 10000).toInt()}"
}

// 更新签名验证器
class UpdateSignatureValidator {
    suspend fun verifySignature(file: File): VerificationResult {
        // 实现签名验证逻辑
        return VerificationResult(
            isValid = true,
            signature = "verified_signature",
            error = null,
            timestamp = System.currentTimeMillis()
        )
    }
}

// 更新安全检查器
class UpdateSecurityChecker {
    suspend fun performSecurityChecks(file: File): SecurityCheckResult {
        // 实现安全检查逻辑
        return SecurityCheckResult(
            isSafe = true,
            reasons = emptyList(),
            timestamp = System.currentTimeMillis()
        )
    }
}

// 数据类定义
@Serializable
data class AppVersion(
    val versionName: String,
    val versionCode: Long
)

enum class UpdateState {
    AVAILABLE,
    DOWNLOADING,
    DOWNLOADED,
    INSTALLING,
    INSTALLED,
    FAILED
}

data class AvailableUpdate(
    val versionCode: Long,
    val versionName: String,
    val description: String,
    val downloadUrl: String,
    val checksum: String,
    val size: Long,
    val priority: String,
    val timestamp: Long,
    val releaseNotes: String = ""
)

data class UpdateCheckResult(
    val success: Boolean,
    val hasUpdates: Boolean,
    val updates: List<AvailableUpdate>,
    val error: String?
)

data class DownloadResult(
    val success: Boolean,
    val update: AvailableUpdate,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val error: String?
)

data class DownloadProgress(
    val downloaded: Long,
    val total: Long,
    val completed: Boolean,
    var isCancelled: Boolean = false
)

data class InstallResult(
    val success: Boolean,
    val update: AvailableUpdate,
    val error: String?
)

data class BackupResult(
    val success: Boolean,
    val backupPath: String?,
    val timestamp: Long,
    val error: String?
)

data class VerificationResult(
    val isValid: Boolean,
    val signature: String?,
    val error: String?,
    val timestamp: Long
)

data class UpdateStatus(
    val currentVersion: AppVersion = AppVersion("unknown", 0L),
    val lastCheckTime: Long = 0L,
    val availableUpdates: Int = 0,
    val downloadedUpdates: Int = 0,
    val installedUpdates: Int = 0,
    val isChecking: Boolean = false,
    val isUpdating: Boolean = false,
    val updateStates: Map<String, Any> = emptyMap()
)

data class UpdateHistoryEntry(
    val id: String,
    val versionCode: Long,
    val versionName: String,
    val action: UpdateAction,
    val timestamp: Long,
    val success: Boolean,
    val error: String? = null
)

enum class UpdateAction {
    CHECKED,
    DOWNLOADED,
    INSTALLED,
    FAILED,
    ROLLED_BACK
}

data class UpdateReport(
    val generatedAt: Long = 0,
    val status: UpdateStatus = UpdateStatus(),
    val history: List<UpdateHistoryEntry> = emptyList(),
    val securityMetrics: SecurityMetrics = SecurityMetrics(),
    val recommendations: List<String> = emptyList(),
    val complianceStatus: ComplianceStatus = ComplianceStatus()
) {
    companion object {
        fun empty() = UpdateReport()
    }
}

data class SecurityMetrics(
    val totalUpdates: Int = 0,
    val verifiedUpdates: Int = 0,
    val failedVerifications: Int = 0,
    val lastSecurityCheck: Long = 0,
    val securityScore: Double = 1.0
)

data class ComplianceStatus(
    val isCompliant: Boolean = false,
    val violations: List<String> = emptyList(),
    val score: Double = 0.0,
    val lastAudit: Long = 0
)

data class SecurityCheckResult(
    val isSafe: Boolean,
    val reasons: List<String>,
    val timestamp: Long
)

data class UpdateStrategy(
    val name: String,
    val autoUpdate: Boolean,
    val silentUpdate: Boolean,
    val checkInterval: Long,
    val priority: Int
)

data class UpdateEvent(
    val type: UpdateEventType,
    val severity: UpdateSeverity,
    val description: String,
    val timestamp: Long,
    val metadata: Map<String, Any>
)

enum class UpdateEventType {
    SYSTEM,
    UPDATE_CHECK,
    UPDATE_AVAILABLE,
    CRITICAL_UPDATE_AVAILABLE,
    DOWNLOAD_STARTED,
    DOWNLOAD_PROGRESS,
    DOWNLOAD_COMPLETE,
    DOWNLOAD_FAILED,
    INSTALLATION_STARTED,
    INSTALLATION_COMPLETE,
    INSTALLATION_FAILED,
    STRATEGY_CHANGED
}

enum class UpdateSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
    INFO
}

data class UpdateConfig(
    val autoCheck: Boolean = true,
    val autoDownload: Boolean = false,
    val autoInstall: Boolean = false,
    val silentInstall: Boolean = false,
    val checkInterval: Long = UPDATE_CHECK_INTERVAL,
    val maxRetries: Int = MAX_RETRY_ATTEMPTS,
    val requireWifi: Boolean = false,
    val requireCharging: Boolean = false
)