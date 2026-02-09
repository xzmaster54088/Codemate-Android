package com.codemate.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.*
import java.security.cert.CertificateException
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 安全存储管理器
 * 使用Android KeyStore和AES-GCM算法管理敏感数据的加密存储
 * 支持硬件安全模块，提供企业级数据保护
 */
@Singleton
class SecureStorageManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS_PREFIX = "codemate_secure_storage_"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
        private const val MAX_ENCRYPTED_DATA_SIZE = 1024 * 1024 // 1MB
        private const val PREFS_NAME = "codemate_secure_prefs"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    private val json = Json { 
        ignoreUnknownKeys = true 
        encodeDefaults = true
    }

    /**
     * 初始化密钥管理
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            generateMasterKey()
            initializeEncryptedPreferences()
            true
        } catch (e: Exception) {
            SecurityLog.e("SecureStorageManager初始化失败", e)
            false
        }
    }

    /**
     * 生成主密钥
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun generateMasterKey() {
        val keyAlias = "${KEY_ALIAS_PREFIX}master"
        if (!keyStore.containsAlias(keyAlias)) {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )

            val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(
                    5, // 5分钟后需要重新验证
                    KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                )
                .build()

            keyGenerator.init(keyGenParameterSpec)
            keyGenerator.generateKey()
        }
    }

    /**
     * 初始化加密的共享偏好设置
     */
    private fun initializeEncryptedPreferences(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    /**
     * 存储字符串数据
     */
    suspend fun storeString(key: String, value: String): StorageResult = withContext(Dispatchers.IO) {
        try {
            if (value.length > MAX_ENCRYPTED_DATA_SIZE) {
                return@withContext StorageResult(
                    success = false,
                    error = "数据大小超过限制 (${MAX_ENCRYPTED_DATA_SIZE} bytes)"
                )
            }

            val encryptedData = encryptData(value)
            val prefs = initializeEncryptedPreferences()
            
            with(prefs.edit()) {
                putString("secure_$key", encryptedData)
                apply()
            }

            StorageResult(success = true, error = null)
        } catch (e: Exception) {
            SecurityLog.e("存储字符串数据失败: $key", e)
            StorageResult(success = false, error = e.message)
        }
    }

    /**
     * 读取字符串数据
     */
    suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = initializeEncryptedPreferences()
            val encryptedData = prefs.getString("secure_$key", null) ?: return@withContext null
            decryptData(encryptedData)
        } catch (e: Exception) {
            SecurityLog.e("读取字符串数据失败: $key", e)
            null
        }
    }

    /**
     * 存储JSON对象
     */
    suspend fun <T> storeObject(key: String, data: T, serializer: kotlinx.serialization.KSerializer<T>): StorageResult = withContext(Dispatchers.IO) {
        try {
            val jsonString = json.encodeToString(serializer, data)
            storeString(key, jsonString)
        } catch (e: Exception) {
            SecurityLog.e("存储JSON对象失败: $key", e)
            StorageResult(success = false, error = e.message)
        }
    }

    /**
     * 读取JSON对象
     */
    suspend fun <T> getObject(key: String, deserializer: kotlinx.serialization.KSerializer<T>): T? = withContext(Dispatchers.IO) {
        try {
            val jsonString = getString(key) ?: return@withContext null
            json.decodeFromString(deserializer, jsonString)
        } catch (e: Exception) {
            SecurityLog.e("读取JSON对象失败: $key", e)
            null
        }
    }

    /**
     * 存储二进制数据
     */
    suspend fun storeBinary(key: String, data: ByteArray): StorageResult = withContext(Dispatchers.IO) {
        try {
            if (data.size > MAX_ENCRYPTED_DATA_SIZE) {
                return@withContext StorageResult(
                    success = false,
                    error = "数据大小超过限制 (${MAX_ENCRYPTED_DATA_SIZE} bytes)"
                )
            }

            val base64Data = Base64.encodeToString(data, Base64.DEFAULT)
            storeString(key, base64Data)
        } catch (e: Exception) {
            SecurityLog.e("存储二进制数据失败: $key", e)
            StorageResult(success = false, error = e.message)
        }
    }

    /**
     * 读取二进制数据
     */
    suspend fun getBinary(key: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val base64Data = getString(key) ?: return@withContext null
            Base64.decode(base64Data, Base64.DEFAULT)
        } catch (e: Exception) {
            SecurityLog.e("读取二进制数据失败: $key", e)
            null
        }
    }

    /**
     * 删除数据
     */
    suspend fun remove(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = initializeEncryptedPreferences()
            with(prefs.edit()) {
                remove("secure_$key")
                apply()
            }
            true
        } catch (e: Exception) {
            SecurityLog.e("删除数据失败: $key", e)
            false
        }
    }

    /**
     * 清空所有数据
     */
    suspend fun clear(): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = initializeEncryptedPreferences()
            prefs.edit().clear().apply()
            true
        } catch (e: Exception) {
            SecurityLog.e("清空数据失败", e)
            false
        }
    }

    /**
     * 检查密钥是否存在
     */
    suspend fun hasKey(key: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val prefs = initializeEncryptedPreferences()
            prefs.contains("secure_$key")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 获取所有存储的密钥
     */
    suspend fun getAllKeys(): List<String> = withContext(Dispatchers.IO) {
        try {
            val prefs = initializeEncryptedPreferences()
            prefs.all.keys.filter { it.startsWith("secure_") }
                .map { it.removePrefix("secure_") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 加密数据
     */
    private fun encryptData(data: String): String {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        
        val iv = ByteArray(GCM_IV_LENGTH)
        SecureRandom().nextBytes(iv)
        
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)
        
        val encryptedData = cipher.doFinal(data.toByteArray())
        val encryptedBytes = iv + encryptedData
        
        return Base64.encodeToString(encryptedBytes, Base64.DEFAULT)
    }

    /**
     * 解密数据
     */
    private fun decryptData(encryptedData: String): String {
        val secretKey = getSecretKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        
        val encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
        
        if (encryptedBytes.size < GCM_IV_LENGTH) {
            throw IllegalArgumentException("无效的加密数据格式")
        }
        
        val iv = encryptedBytes.copyOfRange(0, GCM_IV_LENGTH)
        val data = encryptedBytes.copyOfRange(GCM_IV_LENGTH, encryptedBytes.size)
        
        val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
        
        val decryptedBytes = cipher.doFinal(data)
        return String(decryptedBytes)
    }

    /**
     * 获取密钥
     */
    private fun getSecretKey(): SecretKey {
        val keyAlias = "${KEY_ALIAS_PREFIX}master"
        return keyStore.getKey(keyAlias, null) as SecretKey
    }

    /**
     * 验证存储完整性
     */
    suspend fun verifyIntegrity(): IntegrityResult = withContext(Dispatchers.IO) {
        try {
            val allKeys = getAllKeys()
            var validCount = 0
            var invalidCount = 0

            allKeys.forEach { key ->
                try {
                    getString(key)?.let {
                        validCount++
                    } ?: run {
                        invalidCount++
                    }
                } catch (e: Exception) {
                    invalidCount++
                }
            }

            IntegrityResult(
                success = true,
                validItems = validCount,
                invalidItems = invalidCount,
                error = null
            )
        } catch (e: Exception) {
            IntegrityResult(
                success = false,
                validItems = 0,
                invalidItems = 0,
                error = e.message
            )
        }
    }
}

/**
 * 存储结果数据类
 */
data class StorageResult(
    val success: Boolean,
    val error: String?
)

/**
 * 完整性验证结果
 */
data class IntegrityResult(
    val success: Boolean,
    val validItems: Int,
    val invalidItems: Int,
    val error: String?
)