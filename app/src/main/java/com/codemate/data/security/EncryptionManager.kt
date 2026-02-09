package com.codemate.data.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.*
import java.security.cert.CertificateException
import javax.crypto.*
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Base64

/**
 * 数据加密管理器
 * 负责敏感数据的加密和解密，如API密钥、Git凭据等
 */
@Singleton
class EncryptionManager @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "codemate_encryption_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
        load(null)
    }

    /**
     * 初始化加密密钥
     */
    suspend fun initializeKey(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                generateKey()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 生成密钥
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun generateKey() {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )

        val keyGenParameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()

        keyGenerator.init(keyGenParameterSpec)
        keyGenerator.generateKey()
    }

    /**
     * 加密数据
     */
    suspend fun encrypt(data: String): EncryptionResult = withContext(Dispatchers.IO) {
        try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            
            val iv = ByteArray(GCM_IV_LENGTH)
            SecureRandom().nextBytes(iv)
            
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmParameterSpec)
            
            val encryptedData = cipher.doFinal(data.toByteArray())
            val encryptedBytes = iv + encryptedData
            
            EncryptionResult(
                success = true,
                encryptedData = Base64.encodeToString(encryptedBytes, Base64.DEFAULT),
                error = null
            )
        } catch (e: Exception) {
            EncryptionResult(
                success = false,
                encryptedData = null,
                error = "Encryption failed: ${e.message}"
            )
        }
    }

    /**
     * 解密数据
     */
    suspend fun decrypt(encryptedData: String): DecryptionResult = withContext(Dispatchers.IO) {
        try {
            val secretKey = getSecretKey()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            
            val encryptedBytes = Base64.decode(encryptedData, Base64.DEFAULT)
            
            if (encryptedBytes.size < GCM_IV_LENGTH) {
                return@withContext DecryptionResult(
                    success = false,
                    decryptedData = null,
                    error = "Invalid encrypted data format"
                )
            }
            
            val iv = encryptedBytes.copyOfRange(0, GCM_IV_LENGTH)
            val data = encryptedBytes.copyOfRange(GCM_IV_LENGTH, encryptedBytes.size)
            
            val gcmParameterSpec = GCMParameterSpec(GCM_TAG_LENGTH * 8, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmParameterSpec)
            
            val decryptedBytes = cipher.doFinal(data)
            val decryptedData = String(decryptedBytes)
            
            DecryptionResult(
                success = true,
                decryptedData = decryptedData,
                error = null
            )
        } catch (e: Exception) {
            DecryptionResult(
                success = false,
                decryptedData = null,
                error = "Decryption failed: ${e.message}"
            )
        }
    }

    /**
     * 获取密钥
     */
    private fun getSecretKey(): SecretKey {
        return keyStore.getKey(KEY_ALIAS, null) as SecretKey
    }

    /**
     * 检查密钥是否存在
     */
    suspend fun hasKey(): Boolean = withContext(Dispatchers.IO) {
        try {
            keyStore.containsAlias(KEY_ALIAS)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 删除密钥
     */
    suspend fun deleteKey(): Boolean = withContext(Dispatchers.IO) {
        try {
            keyStore.deleteEntry(KEY_ALIAS)
            true
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * 加密结果数据类
 */
data class EncryptionResult(
    val success: Boolean,
    val encryptedData: String?,
    val error: String?
)

/**
 * 解密结果数据类
 */
data class DecryptionResult(
    val success: Boolean,
    val decryptedData: String?,
    val error: String?
)

/**
 * API密钥加密服务
 * 专门用于API密钥的加密和解密
 */
@Singleton
class ApiKeyEncryptionService @Inject constructor(
    private val encryptionManager: EncryptionManager
) {
    
    /**
     * 加密API密钥
     */
    suspend fun encryptApiKey(apiKey: String): String? {
        val result = encryptionManager.encrypt(apiKey)
        return if (result.success) result.encryptedData else null
    }
    
    /**
     * 解密API密钥
     */
    suspend fun decryptApiKey(encryptedApiKey: String): String? {
        val result = encryptionManager.decrypt(encryptedApiKey)
        return if (result.success) result.decryptedData else null
    }
    
    /**
     * 验证API密钥格式
     */
    fun validateApiKey(apiKey: String, provider: ApiProvider): Boolean {
        return when (provider) {
            ApiProvider.OPENAI -> apiKey.startsWith("sk-") && apiKey.length > 20
            ApiProvider.ANTHROPIC -> apiKey.startsWith("sk-ant-") && apiKey.length > 20
            ApiProvider.GOOGLE -> apiKey.length > 20
            ApiProvider.AZURE -> apiKey.length > 20
            ApiProvider.LOCAL -> true // 本地服务无需验证
            ApiProvider.CUSTOM -> true // 自定义服务无需验证
        }
    }
}

/**
 * Git凭据加密服务
 * 专门用于Git凭据的加密和解密
 */
@Singleton
class GitCredentialEncryptionService @Inject constructor(
    private val encryptionManager: EncryptionManager
) {
    
    /**
     * 加密Git凭据
     */
    suspend fun encryptGitCredentials(username: String, password: String): String? {
        val credentials = mapOf(
            "username" to username,
            "password" to password
        )
        val json = kotlinx.serialization.json.Json.encodeToString(credentials)
        val result = encryptionManager.encrypt(json)
        return if (result.success) result.encryptedData else null
    }
    
    /**
     * 解密Git凭据
     */
    suspend fun decryptGitCredentials(encryptedCredentials: String): GitCredentials? {
        val result = encryptionManager.decrypt(encryptedCredentials)
        return if (result.success) {
            try {
                val credentials = kotlinx.serialization.json.Json.decodeFromString<Map<String, String>>(result.decryptedData!!)
                GitCredentials(
                    username = credentials["username"] ?: "",
                    password = credentials["password"] ?: ""
                )
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }
}

/**
 * Git凭据数据类
 */
data class GitCredentials(
    val username: String,
    val password: String
)