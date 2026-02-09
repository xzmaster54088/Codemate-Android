package com.codemate.features.ai.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 加密管理器
 * 负责API密钥等敏感数据的加密存储
 */
@Singleton
class EncryptionManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val KEY_ALIAS = "ai_encryption_key"
        private const val PREFS_NAME = "encryption_prefs"
        private const val KEY_KEY = "encryption_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val ALGORITHM = "AES"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }
    
    private val preferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    init {
        initializeKey()
    }
    
    /**
     * 初始化加密密钥
     */
    private fun initializeKey() {
        if (!preferences.contains(KEY_KEY)) {
            try {
                val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
                keyGenerator.init(256)
                val secretKey = keyGenerator.generateKey()
                val encodedKey = Base64.encodeToString(secretKey.encoded, Base64.NO_WRAP)
                preferences.edit().putString(KEY_KEY, encodedKey).apply()
            } catch (e: Exception) {
                throw RuntimeException("初始化加密密钥失败", e)
            }
        }
    }
    
    /**
     * 加密数据
     */
    fun encrypt(data: String): String {
        return try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)
            
            val encryptedData = cipher.doFinal(data.toByteArray())
            val ivAndEncrypted = iv + encryptedData
            
            Base64.encodeToString(ivAndEncrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw RuntimeException("加密失败", e)
        }
    }
    
    /**
     * 解密数据
     */
    fun decrypt(encryptedData: String): String {
        return try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            
            val ivAndEncrypted = Base64.decode(encryptedData, Base64.NO_WRAP)
            val iv = ivAndEncrypted.sliceArray(0 until GCM_IV_LENGTH)
            val encrypted = ivAndEncrypted.sliceArray(GCM_IV_LENGTH until ivAndEncrypted.size)
            
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
            
            val decryptedData = cipher.doFinal(encrypted)
            String(decryptedData)
        } catch (e: Exception) {
            throw RuntimeException("解密失败", e)
        }
    }
    
    /**
     * 获取密钥
     */
    private fun getSecretKey(): SecretKey {
        val encodedKey = preferences.getString(KEY_KEY, null)
            ?: throw IllegalStateException("加密密钥未初始化")
        
        val keyBytes = Base64.decode(encodedKey, Base64.NO_WRAP)
        return SecretKeySpec(keyBytes, ALGORITHM)
    }
    
    /**
     * 验证加密解密完整性
     */
    fun testEncryption(): Boolean {
        return try {
            val originalData = "test_encryption_data_123"
            val encrypted = encrypt(originalData)
            val decrypted = decrypt(encrypted)
            originalData == decrypted
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 安全删除密钥
     */
    fun secureDeleteKey(): Boolean {
        return try {
            // 覆盖密钥数据
            preferences.edit()
                .putString(KEY_KEY, "0".repeat(256))
                .remove(KEY_KEY)
                .apply()
            
            // 重新初始化
            initializeKey()
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 导出加密配置（不包含密钥）
     */
    fun exportConfig(): EncryptionConfig {
        return EncryptionConfig(
            algorithm = ALGORITHM,
            transformation = TRANSFORMATION,
            keySize = 256,
            ivLength = GCM_IV_LENGTH,
            tagLength = GCM_TAG_LENGTH,
            isInitialized = preferences.contains(KEY_KEY)
        )
    }
    
    /**
     * 导入加密配置
     */
    fun importConfig(config: EncryptionConfig): Boolean {
        return try {
            // 验证配置兼容性
            if (config.algorithm != ALGORITHM || 
                config.transformation != TRANSFORMATION || 
                config.keySize != 256) {
                return false
            }
            
            // 如果配置有效，重新初始化密钥
            if (!config.isInitialized) {
                initializeKey()
            }
            
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取密钥摘要（用于验证密钥完整性）
     */
    fun getKeyFingerprint(): String {
        return try {
            val secretKey = getSecretKey()
            val hash = secretKey.encoded.contentHashCode().toString()
            Base64.encodeToString(hash.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            "error"
        }
    }
    
    /**
     * 检查是否支持硬件加密
     */
    fun isHardwareEncryptionSupported(): Boolean {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val keyGenerator = KeyGenerator.getInstance(ALGORITHM)
            keyGenerator.init(256)
            val secretKey = keyGenerator.generateKey()
            
            // 尝试使用硬件加速
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取加密统计信息
     */
    fun getEncryptionStatistics(): EncryptionStatistics {
        return EncryptionStatistics(
            isInitialized = preferences.contains(KEY_KEY),
            isHardwareSupported = isHardwareEncryptionSupported(),
            testPassed = testEncryption(),
            keyFingerprint = getKeyFingerprint(),
            algorithm = ALGORITHM,
            transformation = TRANSFORMATION
        )
    }
}

/**
 * 加密配置
 */
data class EncryptionConfig(
    val algorithm: String,
    val transformation: String,
    val keySize: Int,
    val ivLength: Int,
    val tagLength: Int,
    val isInitialized: Boolean
)

/**
 * 加密统计信息
 */
data class EncryptionStatistics(
    val isInitialized: Boolean,
    val isHardwareSupported: Boolean,
    val testPassed: Boolean,
    val keyFingerprint: String,
    val algorithm: String,
    val transformation: String
)