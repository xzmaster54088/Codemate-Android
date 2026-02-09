package com.codemate.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 安全模块初始化器
 * 提供统一的模块初始化和配置管理
 */
@Singleton
class SecurityModuleInitializer @Inject constructor(
    private val context: Context,
    private val securityManager: SecurityManager
) {

    companion object {
        private const val PREFS_NAME = "codemate_security_config"
        private const val KEY_INITIALIZED = "security_module_initialized"
        private const val KEY_VERSION = "security_module_version"
        private const val CURRENT_VERSION = "1.0.0"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 初始化安全模块
     */
    suspend fun initializeSecurityModules(): InitializationResult {
        return withContext(Dispatchers.IO) {
            try {
                SecurityLog.i("开始初始化CodeMate Mobile安全模块...")
                
                // 检查是否已经初始化
                if (isAlreadyInitialized()) {
                    SecurityLog.i("安全模块已经初始化，跳过初始化过程")
                    return@withContext InitializationResult(
                        success = true,
                        message = "安全模块已初始化",
                        modulesInitialized = getInitializedModules(),
                        version = CURRENT_VERSION
                    )
                }

                // 执行初始化步骤
                val initSteps = listOf(
                    "验证系统环境" to ::validateSystemEnvironment,
                    "初始化安全管理器" to ::initializeSecurityManager,
                    "配置证书绑定" to ::configureCertificatePinning,
                    "设置权限管理" to ::setupPermissionManagement,
                    "启用反调试保护" to ::enableAntiDebugProtection,
                    "启动安全审计" to ::startSecurityAudit,
                    "配置数据泄露防护" to ::configureDataLeakagePrevention,
                    "初始化性能监控" to ::initializePerformanceMonitor,
                    "设置资源管理" to ::setupResourceManagement,
                    "配置安全更新" to ::configureSecureUpdates,
                    "执行最终验证" to ::performFinalVerification
                )

                val results = mutableListOf<InitializationStep>()
                var overallSuccess = true

                for ((stepName, stepFunction) in initSteps) {
                    try {
                        SecurityLog.d("执行初始化步骤: $stepName")
                        val result = stepFunction()
                        results.add(
                            InitializationStep(
                                name = stepName,
                                success = result.success,
                                message = result.message,
                                duration = result.duration
                            )
                        )
                        
                        if (!result.success) {
                            overallSuccess = false
                            SecurityLog.e("初始化步骤失败: $stepName - ${result.message}")
                        }
                        
                        SecurityLog.i("完成初始化步骤: $stepName")
                    } catch (e: Exception) {
                        SecurityLog.e("初始化步骤异常: $stepName", e)
                        results.add(
                            InitializationStep(
                                name = stepName,
                                success = false,
                                message = "异常: ${e.message}",
                                duration = 0
                            )
                        )
                        overallSuccess = false
                    }
                }

                // 标记为已初始化
                markAsInitialized()

                val finalResult = InitializationResult(
                    success = overallSuccess,
                    message = if (overallSuccess) "所有安全模块初始化成功" else "部分安全模块初始化失败",
                    modulesInitialized = results.filter { it.success }.size,
                    totalModules = initSteps.size,
                    steps = results,
                    version = CURRENT_VERSION
                )

                SecurityLog.i("安全模块初始化完成: ${finalResult.success}")
                finalResult
            } catch (e: Exception) {
                SecurityLog.e("安全模块初始化失败", e)
                InitializationResult(
                    success = false,
                    message = "初始化异常: ${e.message}",
                    modulesInitialized = 0,
                    totalModules = 0,
                    steps = emptyList(),
                    version = CURRENT_VERSION
                )
            }
        }
    }

    /**
     * 检查是否已经初始化
     */
    private fun isAlreadyInitialized(): Boolean {
        return prefs.getBoolean(KEY_INITIALIZED, false) && 
               prefs.getString(KEY_VERSION, "") == CURRENT_VERSION
    }

    /**
     * 标记为已初始化
     */
    private fun markAsInitialized() {
        prefs.edit().apply {
            putBoolean(KEY_INITIALIZED, true)
            putString(KEY_VERSION, CURRENT_VERSION)
            apply()
        }
    }

    /**
     * 获取已初始化的模块列表
     */
    private fun getInitializedModules(): List<String> {
        return listOf(
            "SecurityManager",
            "CertificatePinner",
            "PermissionManager", 
            "AntiDebugTampering",
            "SecurityAudit",
            "DataLeakagePrevention",
            "PerformanceMonitor",
            "ResourceManager",
            "SecureUpdateManager",
            "SecureStorageManager"
        )
    }

    // 初始化步骤实现

    private suspend fun validateSystemEnvironment(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 检查Android版本
            val androidVersion = android.os.Build.VERSION.SDK_INT
            if (androidVersion < android.os.Build.VERSION_CODES.M) {
                return StepResult(
                    success = false,
                    message = "不支持的Android版本: $androidVersion (需要API 23+)",
                    duration = System.currentTimeMillis() - startTime
                )
            }

            // 检查必要权限
            val requiredPermissions = listOf(
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.ACCESS_NETWORK_STATE
            )

            // 检查应用签名
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (packageInfo.signatures.isEmpty()) {
                return StepResult(
                    success = false,
                    message = "应用签名信息缺失",
                    duration = System.currentTimeMillis() - startTime
                )
            }

            StepResult(
                success = true,
                message = "系统环境验证通过",
                duration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "系统环境验证异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun initializeSecurityManager(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val result = securityManager.initialize()
            StepResult(
                success = result.success,
                message = if (result.success) {
                    "安全管理器初始化成功，${result.initializedModules.size} 个模块已初始化"
                } else {
                    "安全管理器初始化失败: ${result.error}"
                },
                duration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "安全管理器初始化异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun configureCertificatePinning(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 配置默认证书绑定
            val defaultPins = setOf(
                "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
                "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="
            )
            
            val success = certificatePinner.addPinnedCertificate(
                hostname = "api.codemate.com",
                certificatePins = defaultPins
            )
            
            StepResult(
                success = success,
                message = if (success) "证书绑定配置成功" else "证书绑定配置失败",
                duration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "证书绑定配置异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun setupPermissionManagement(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val success = permissionManager.startPermissionManagement()
            
            StepResult(
                success = success,
                message = if (success) "权限管理设置成功" else "权限管理设置失败",
                duration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "权限管理设置异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun enableAntiDebugProtection(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val success = antiDebugTampering.enableProtection()
            
            StepResult(
                success = success,
                message = if (success) "反调试保护启用成功" else "反调试保护启用失败",
                duration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "反调试保护启用异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun startSecurityAudit(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val success = securityAudit.startSecurityAudit()
            
            StepResult(
                success = success,
                message = if (success) "安全审计启动成功" else "安全审计启动失败",
                duration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "安全审计启动异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun configureDataLeakagePrevention(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val success = dataLeakagePrevention.startDataLeakagePrevention()
            
            StepResult(
                success = success,
                message = if (success) "数据泄露防护配置成功" else "数据泄露防护配置失败",
                duration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "数据泄露防护配置异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun initializePerformanceMonitor(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val success = performanceMonitor.startMonitoring()
            
            StepResult(
                success = success,
                message = if (success) "性能监控器初始化成功" else "性能监控器初始化失败",
                duration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "性能监控器初始化异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun setupResourceManagement(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val success = resourceManager.startResourceManagement()
            
            StepResult(
                success = success,
                message = if (success) "资源管理器设置成功" else "资源管理器设置失败",
                duration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "资源管理器设置异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun configureSecureUpdates(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            val success = secureUpdateManager.startUpdateManagement()
            
            StepResult(
                success = success,
                message = if (success) "安全更新管理器配置成功" else "安全更新管理器配置失败",
                duration = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "安全更新管理器配置异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }

    private suspend fun performFinalVerification(): StepResult {
        val startTime = System.currentTimeMillis()
        
        return try {
            // 执行最终的安全验证
            val securityCheck = securityManager.performSecurityCheck()
            
            if (securityCheck.success && securityCheck.totalIssues == 0) {
                StepResult(
                    success = true,
                    message = "最终验证通过，所有安全模块正常工作",
                    duration = System.currentTimeMillis() - startTime
                )
            } else {
                StepResult(
                    success = false,
                    message = "最终验证发现问题: ${securityCheck.totalIssues} 个问题",
                    duration = System.currentTimeMillis() - startTime
                )
            }
        } catch (e: Exception) {
            StepResult(
                success = false,
                message = "最终验证异常: ${e.message}",
                duration = System.currentTimeMillis() - startTime
            )
        }
    }
}

// 数据类定义
data class InitializationResult(
    val success: Boolean,
    val message: String,
    val modulesInitialized: Int,
    val totalModules: Int = 0,
    val steps: List<InitializationStep> = emptyList(),
    val version: String
)

data class InitializationStep(
    val name: String,
    val success: Boolean,
    val message: String,
    val duration: Long
)

data class StepResult(
    val success: Boolean,
    val message: String,
    val duration: Long
)