package com.codemate.features.compiler.core.manager

import android.util.Log
import com.codemate.features.compiler.core.bridge.TermuxBridge
import com.codemate.features.compiler.domain.entities.*
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 编译工具链管理器
 * 负责管理不同编程语言的编译工具链和环境配置
 * 包括工具安装、版本管理、环境变量配置等功能
 */
class ToolchainManager(private val termuxBridge: TermuxBridge) {
    companion object {
        private const val TAG = "ToolchainManager"
        
        // 各语言的工具链配置
        private val TOOLCHAIN_CONFIGS = mapOf(
            Language.JAVA to listOf(
                "openjdk-17",
                "openjdk-11",
                "openjdk-8"
            ),
            Language.JAVASCRIPT to listOf(
                "nodejs",
                "nodejs-current"
            ),
            Language.PYTHON to listOf(
                "python",
                "python3"
            ),
            Language.CPP to listOf(
                "clang",
                "g++",
                "cmake",
                "ninja"
            ),
            Language.C to listOf(
                "clang",
                "gcc",
                "cmake",
                "ninja"
            ),
            Language.RUST to listOf(
                "rust",
                "cargo"
            ),
            Language.GO to listOf(
                "go"
            )
        )
        
        // 工具包管理器命令
        private val PACKAGE_MANAGERS = mapOf(
            "termux" to "pkg",
            "apt" to "apt"
        )
        
        // 工具版本检查命令
        private val VERSION_COMMANDS = mapOf(
            "javac" to listOf("--version", "-version"),
            "node" to listOf("--version", "-v"),
            "python3" to listOf("--version"),
            "gcc" to listOf("--version"),
            "g++" to listOf("--version"),
            "clang" to listOf("--version"),
            "rustc" to listOf("--version"),
            "cargo" to listOf("--version"),
            "go" to listOf("version")
        )
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val toolchainCache = ConcurrentHashMap<String, ToolchainInfo>()
    private val installationStatus = ConcurrentHashMap<String, InstallationStatus>()

    /**
     * 获取指定语言的工具链信息
     */
    suspend fun getToolchainInfo(language: Language): ToolchainInfo = withContext(Dispatchers.IO) {
        val cacheKey = "${language.name}_default"
        
        toolchainCache[cacheKey]?.let { cached ->
            if (isCacheValid(cached)) {
                return@withContext cached
            }
        }
        
        try {
            val config = TOOLCHAIN_CONFIGS[language]?.firstOrNull()
            if (config == null) {
                return@withContext createUnavailableToolchain(language)
            }
            
            val version = getToolVersion(config)
            val isInstalled = version != null
            
            val toolchain = ToolchainInfo(
                name = config,
                version = version ?: "Not installed",
                path = getToolPath(config),
                isInstalled = isInstalled,
                supportedLanguages = listOf(language),
                capabilities = getToolCapabilities(config),
                installationDate = if (isInstalled) System.currentTimeMillis() else null,
                lastUsed = null
            )
            
            toolchainCache[cacheKey] = toolchain
            return@withContext toolchain
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get toolchain info for $language", e)
            return@withContext createUnavailableToolchain(language)
        }
    }

    /**
     * 获取所有支持的工具链
     */
    suspend fun getAllToolchains(): List<ToolchainInfo> = withContext(Dispatchers.IO) {
        Language.values().map { language ->
            getToolchainInfo(language)
        }
    }

    /**
     * 安装指定语言的工具链
     */
    suspend fun installToolchain(language: Language): InstallationResult = withContext(Dispatchers.IO) {
        val cacheKey = "${language.name}_default"
        val statusKey = language.name
        
        try {
            // 检查安装状态
            installationStatus[statusKey]?.let { status ->
                if (status.state == InstallationState.INSTALLING) {
                    return@withContext InstallationResult(
                        success = false,
                        message = "Installation already in progress",
                        state = status
                    )
                }
            }
            
            // 开始安装
            val installStatus = InstallationStatus(
                language = language,
                state = InstallationState.CHECKING,
                progress = 0,
                message = "Checking system requirements..."
            )
            installationStatus[statusKey] = installStatus
            
            // 更新状态
            updateInstallationStatus(statusKey, 10, "Updating package lists...")
            
            // 更新包列表
            val updateResult = termuxBridge.executeCommand("pkg update")
            if (!updateResult.success) {
                Log.w(TAG, "Failed to update package lists, continuing...")
            }
            
            updateInstallationStatus(statusKey, 20, "Installing packages...")
            
            // 获取要安装的包
            val packages = TOOLCHAIN_CONFIGS[language] ?: return@withContext InstallationResult(
                success = false,
                message = "No packages configured for $language",
                state = installStatus
            )
            
            // 安装包
            for ((index, package) in packages.withIndex()) {
                updateInstallationStatus(
                    statusKey, 
                    20 + (index * 60 / packages.size), 
                    "Installing $package..."
                )
                
                val installResult = termuxBridge.executeCommand("pkg install -y $package")
                if (!installResult.success) {
                    return@withContext InstallationResult(
                        success = false,
                        message = "Failed to install $package: ${installResult.errorOutput}",
                        state = installationStatus[statusKey] ?: installStatus
                    )
                }
            }
            
            updateInstallationStatus(statusKey, 90, "Verifying installation...")
            
            // 验证安装
            val toolchainInfo = getToolchainInfo(language)
            if (!toolchainInfo.isInstalled) {
                return@withContext InstallationResult(
                    success = false,
                    message = "Installation completed but toolchain is not available",
                    state = installationStatus[statusKey] ?: installStatus
                )
            }
            
            updateInstallationStatus(statusKey, 100, "Installation completed successfully!")
            
            val result = InstallationResult(
                success = true,
                message = "Successfully installed ${toolchainInfo.name} ${toolchainInfo.version}",
                state = installationStatus[statusKey] ?: installStatus,
                installedToolchain = toolchainInfo
            )
            
            // 清除缓存以强制重新加载
            toolchainCache.remove(cacheKey)
            
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install toolchain for $language", e)
            
            val status = installationStatus[statusKey]
            val errorStatus = status?.copy(
                state = InstallationState.FAILED,
                message = "Installation failed: ${e.message}"
            ) ?: InstallationStatus(
                language = language,
                state = InstallationState.FAILED,
                progress = 0,
                message = "Installation failed: ${e.message}"
            )
            installationStatus[statusKey] = errorStatus
            
            InstallationResult(
                success = false,
                message = "Installation failed: ${e.message}",
                state = errorStatus
            )
        }
    }

    /**
     * 卸载指定语言的工具链
     */
    suspend fun uninstallToolchain(language: Language): Boolean = withContext(Dispatchers.IO) {
        try {
            val packages = TOOLCHAIN_CONFIGS[language] ?: return@withContext false
            
            packages.forEach { package ->
                val result = termuxBridge.executeCommand("pkg uninstall -y $package")
                if (!result.success) {
                    Log.w(TAG, "Failed to uninstall $package")
                }
            }
            
            // 清除缓存
            val cacheKey = "${language.name}_default"
            toolchainCache.remove(cacheKey)
            
            Log.i(TAG, "Successfully uninstalled toolchain for $language")
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to uninstall toolchain for $language", e)
            false
        }
    }

    /**
     * 检查工具链是否已安装
     */
    suspend fun isToolchainInstalled(language: Language): Boolean = withContext(Dispatchers.IO) {
        val toolchainInfo = getToolchainInfo(language)
        toolchainInfo.isInstalled
    }

    /**
     * 获取工具链版本
     */
    suspend fun getToolchainVersion(language: Language): String? = withContext(Dispatchers.IO) {
        val toolchainInfo = getToolchainInfo(language)
        if (toolchainInfo.isInstalled) {
            toolchainInfo.version
        } else {
            null
        }
    }

    /**
     * 检查工具链更新
     */
    suspend fun checkForUpdates(): List<ToolchainUpdate> = withContext(Dispatchers.IO) {
        val updates = mutableListOf<ToolchainUpdate>()
        
        try {
            // 更新包列表
            termuxBridge.executeCommand("pkg update").let { result ->
                if (!result.success) {
                    Log.w(TAG, "Failed to update package lists")
                }
            }
            
            // 检查每个已安装的工具链
            Language.values().forEach { language ->
                val currentInfo = getToolchainInfo(language)
                if (currentInfo.isInstalled) {
                    val packages = TOOLCHAIN_CONFIGS[language] ?: return@forEach
                    
                    packages.forEach { package ->
                        val updateCheck = checkPackageUpdate(package)
                        if (updateCheck.hasUpdate) {
                            updates.add(ToolchainUpdate(
                                packageName = package,
                                currentVersion = updateCheck.currentVersion,
                                availableVersion = updateCheck.availableVersion,
                                language = language
                            ))
                        }
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check for updates", e)
        }
        
        updates
    }

    /**
     * 更新工具链
     */
    suspend fun updateToolchain(language: Language): InstallationResult = withContext(Dispatchers.IO) {
        try {
            val packages = TOOLCHAIN_CONFIGS[language] ?: return@withContext InstallationResult(
                success = false,
                message = "No packages configured for $language"
            )
            
            packages.forEach { package ->
                val result = termuxBridge.executeCommand("pkg install -y --only-upgrade $package")
                if (!result.success) {
                    return@withContext InstallationResult(
                        success = false,
                        message = "Failed to update $package"
                    )
                }
            }
            
            // 清除缓存
            toolchainCache.remove("${language.name}_default")
            
            InstallationResult(
                success = true,
                message = "Successfully updated ${packages.joinToString(", ")}",
                installedToolchain = getToolchainInfo(language)
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update toolchain for $language", e)
            InstallationResult(
                success = false,
                message = "Update failed: ${e.message}"
            )
        }
    }

    /**
     * 获取安装状态
     */
    suspend fun getInstallationStatus(language: Language): InstallationStatus? = 
        installationStatus[language.name]

    /**
     * 获取所有安装状态
     */
    suspend fun getAllInstallationStatuses(): Map<Language, InstallationStatus> = 
        installationStatus.toMap()

    /**
     * 清理管理器资源
     */
    fun cleanup() {
        scope.cancel()
        toolchainCache.clear()
        installationStatus.clear()
    }

    // 私有方法
    private suspend fun getToolVersion(toolName: String): String? = withContext(Dispatchers.IO) {
        val commands = VERSION_COMMANDS[toolName] ?: listOf("--version")
        
        commands.forEach { command ->
            try {
                val result = termuxBridge.executeCommand("$toolName $command")
                if (result.success) {
                    // 解析版本信息
                    return@withContext parseVersionFromOutput(result.output, toolName)
                }
            } catch (e: Exception) {
                // 继续尝试下一个命令
            }
        }
        
        null
    }

    private fun parseVersionFromOutput(output: String, toolName: String): String {
        val lines = output.lines()
        
        // 查找包含版本号的行
        lines.forEach { line ->
            val versionRegex = when (toolName) {
                "javac" -> Regex("javac (\\d+\\.\\d+\\.\\d+)")
                "node" -> Regex("v?(\\d+\\.\\d+\\.\\d+)")
                "python3" -> Regex("Python (\\d+\\.\\d+\\.\\d+)")
                "gcc", "g++", "clang" -> Regex(".*? (\\d+\\.\\d+\\.\\d+).*?")
                "rustc" -> Regex("rustc (\\d+\\.\\d+\\.\\d+)")
                "go" -> Regex("go version (\\d+\\.\\d+\\.\\d+)")
                else -> Regex("(\\d+\\.\\d+\\.\\d+)")
            }
            
            versionRegex.find(line)?.let { match ->
                return match.groupValues[1]
            }
        }
        
        // 如果没有找到版本号，返回输出的一部分
        return lines.firstOrNull()?.take(50) ?: "Unknown"
    }

    private suspend fun getToolPath(toolName: String): String = withContext(Dispatchers.IO) {
        try {
            val result = termuxBridge.executeCommand("which $toolName")
            if (result.success) {
                result.output.lines().firstOrNull()?.trim() ?: ""
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun getToolCapabilities(toolName: String): Set<ToolchainCapability> {
        return when (toolName) {
            "javac" -> setOf(
                ToolchainCapability.COMPILATION,
                ToolchainCapability.LINKING,
                ToolchainCapability.DEBUGGING,
                ToolchainCapability.UNIT_TESTING
            )
            "node" -> setOf(
                ToolchainCapability.COMPILATION,
                ToolchainCapability.DEBUGGING,
                ToolchainCapability.STATIC_ANALYSIS
            )
            "python3" -> setOf(
                ToolchainCapability.COMPILATION,
                ToolchainCapability.DEBUGGING,
                ToolchainCapability.UNIT_TESTING
            )
            "gcc", "g++", "clang" -> setOf(
                ToolchainCapability.COMPILATION,
                ToolchainCapability.LINKING,
                ToolchainCapability.DEBUGGING,
                ToolchainCapability.OPTIMIZATION,
                ToolchainCapability.PROFILING
            )
            "rustc", "cargo" -> setOf(
                ToolchainCapability.COMPILATION,
                ToolchainCapability.LINKING,
                ToolchainCapability.DEBUGGING,
                ToolchainCapability.UNIT_TESTING,
                ToolchainCapability.STATIC_ANALYSIS
            )
            "go" -> setOf(
                ToolchainCapability.COMPILATION,
                ToolchainCapability.LINKING,
                ToolchainCapability.DEBUGGING,
                ToolchainCapability.UNIT_TESTING
            )
            else -> setOf(ToolchainCapability.COMPILATION)
        }
    }

    private fun createUnavailableToolchain(language: Language): ToolchainInfo {
        return ToolchainInfo(
            name = "Not Available",
            version = "Not installed",
            path = "",
            isInstalled = false,
            supportedLanguages = listOf(language),
            capabilities = emptySet()
        )
    }

    private fun isCacheValid(toolchain: ToolchainInfo): Boolean {
        // 简单的缓存验证逻辑
        val cacheTimeout = 5 * 60 * 1000L // 5分钟
        val now = System.currentTimeMillis()
        val lastUsed = toolchain.lastUsed ?: 0L
        
        return (now - lastUsed) < cacheTimeout
    }

    private suspend fun updateInstallationStatus(
        statusKey: String, 
        progress: Int, 
        message: String
    ) {
        val currentStatus = installationStatus[statusKey]
        if (currentStatus != null) {
            installationStatus[statusKey] = currentStatus.copy(
                progress = progress,
                message = message
            )
        }
    }

    private suspend fun checkPackageUpdate(packageName: String): UpdateCheckResult {
        try {
            val versionCommand = when (packageName) {
                "openjdk-17" -> "pkg list-installed | grep openjdk-17"
                "nodejs" -> "node --version"
                "python" -> "python --version"
                else -> "$packageName --version"
            }
            
            val currentResult = termuxBridge.executeCommand(versionCommand)
            val currentVersion = if (currentResult.success) {
                parseVersionFromOutput(currentResult.output, packageName)
            } else {
                null
            }
            
            // 这里简化处理，实际应该检查可用的更新版本
            return UpdateCheckResult(
                hasUpdate = false,
                currentVersion = currentVersion,
                availableVersion = currentVersion
            )
            
        } catch (e: Exception) {
            return UpdateCheckResult(
                hasUpdate = false,
                currentVersion = null,
                availableVersion = null
            )
        }
    }
}

/**
 * 安装状态
 */
data class InstallationStatus(
    val language: Language,
    val state: InstallationState,
    val progress: Int,
    val message: String,
    val startTime: Long = System.currentTimeMillis()
)

/**
 * 安装状态枚举
 */
enum class InstallationState {
    IDLE,
    CHECKING,
    INSTALLING,
    SUCCESS,
    FAILED,
    CANCELLED
}

/**
 * 安装结果
 */
data class InstallationResult(
    val success: Boolean,
    val message: String,
    val state: InstallationStatus? = null,
    val installedToolchain: ToolchainInfo? = null
)

/**
 * 工具链更新信息
 */
data class ToolchainUpdate(
    val packageName: String,
    val currentVersion: String?,
    val availableVersion: String?,
    val language: Language
)

/**
 * 更新检查结果
 */
data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val currentVersion: String?,
    val availableVersion: String?
)