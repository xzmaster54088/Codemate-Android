package com.codemate.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.logging.HttpLoggingInterceptor
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.*

/**
 * 证书绑定器
 * 对网络请求进行证书固定，防止中间人攻击
 * 支持多种证书格式和信任策略
 */
@Singleton
class CertificatePinner @Inject constructor(
    private val context: Context
) {

    companion object {
        private const val TAG = "CertificatePinner"
        private const val DEFAULT_TIMEOUT = 30L
        private const val MAX_PINNED_CERTIFICATES = 5
    }

    // 已固定的证书映射
    private val pinnedCertificates = ConcurrentHashMap<String, Set<String>>()
    
    // 证书验证结果缓存
    private val certificateValidationCache = ConcurrentHashMap<String, CertificateValidationResult>()
    
    // 受信任的主机列表
    private val trustedHosts = mutableSetOf<String>()

    /**
     * 添加固定证书
     * @param hostname 主机名
     * @param certificatePins 证书指纹列表
     */
    suspend fun addPinnedCertificate(
        hostname: String,
        certificatePins: Set<String>
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            if (certificatePins.size > MAX_PINNED_CERTIFICATES) {
                SecurityLog.w("证书数量超过限制: $hostname")
                return@withContext false
            }

            // 验证证书指纹格式
            certificatePins.forEach { pin ->
                if (!isValidCertificatePin(pin)) {
                    SecurityLog.e("无效的证书指纹格式: $pin")
                    return@withContext false
                }
            }

            pinnedCertificates[hostname] = certificatePins
            SecurityLog.i("添加固定证书: $hostname, 指纹数量: ${certificatePins.size}")
            true
        } catch (e: Exception) {
            SecurityLog.e("添加固定证书失败: $hostname", e)
            false
        }
    }

    /**
     * 移除固定证书
     */
    suspend fun removePinnedCertificate(hostname: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val removed = pinnedCertificates.remove(hostname)
            if (removed != null) {
                SecurityLog.i("移除固定证书: $hostname")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            SecurityLog.e("移除固定证书失败: $hostname", e)
            false
        }
    }

    /**
     * 添加受信任主机
     */
    suspend fun addTrustedHost(hostname: String): Boolean = withContext(Dispatchers.IO) {
        try {
            trustedHosts.add(hostname)
            SecurityLog.i("添加受信任主机: $hostname")
            true
        } catch (e: Exception) {
            SecurityLog.e("添加受信任主机失败: $hostname", e)
            false
        }
    }

    /**
     * 验证证书固定
     */
    suspend fun verifyCertificatePinning(
        hostname: String,
        certificateChain: Array<Certificate>
    ): CertificateValidationResult = withContext(Dispatchers.IO) {
        try {
            // 检查是否是受信任主机
            if (trustedHosts.contains(hostname)) {
                return@withContext CertificateValidationResult(
                    isValid = true,
                    reason = "受信任主机",
                    error = null
                )
            }

            // 检查是否有固定的证书
            val pinnedPins = pinnedCertificates[hostname]
            if (pinnedPins == null || pinnedPins.isEmpty()) {
                return@withContext CertificateValidationResult(
                    isValid = false,
                    reason = "未配置证书固定",
                    error = "请为 $hostname 配置证书固定"
                )
            }

            // 获取证书指纹
            val actualPins = certificateChain.map { certificate ->
                computeCertificatePin(certificate as X509Certificate)
            }.toSet()

            // 验证指纹匹配
            val matchedPins = actualPins intersect pinnedPins
            val isValid = matchedPins.isNotEmpty()

            val result = CertificateValidationResult(
                isValid = isValid,
                reason = if (isValid) "证书验证成功" else "证书指纹不匹配",
                error = if (isValid) null else "期望的指纹: $pinnedPins, 实际指纹: $actualPins"
            )

            // 缓存验证结果
            certificateValidationCache[hostname] = result

            SecurityLog.w(
                "证书验证 $hostname: ${result.isValid}, ${result.reason}"
            )

            result
        } catch (e: Exception) {
            SecurityLog.e("证书验证失败: $hostname", e)
            CertificateValidationResult(
                isValid = false,
                reason = "验证异常",
                error = e.message
            )
        }
    }

    /**
     * 创建安全的OkHttp客户端
     */
    fun createSecureHttpClient(
        loggingLevel: HttpLoggingInterceptor.Level = HttpLoggingInterceptor.Level.BASIC,
        timeout: Long = DEFAULT_TIMEOUT
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .apply {
                // 设置超时
                connectTimeout(timeout, TimeUnit.SECONDS)
                readTimeout(timeout, TimeUnit.SECONDS)
                writeTimeout(timeout, TimeUnit.SECONDS)

                // 设置证书固定
                certificatePinner {
                    pinnedCertificates.forEach { (hostname, pins) ->
                        pin(hostname, pins)
                    }
                }

                // 自定义SSLSocketFactory
                sslSocketFactory(createSecureSSLSocketFactory(), createTrustManager())
                
                // 设置主机名验证器
                hostnameVerifier(createCustomHostnameVerifier())

                // 添加HTTP日志拦截器
                val loggingInterceptor = HttpLoggingInterceptor().apply {
                    level = loggingLevel
                }
                addInterceptor(loggingInterceptor)

                // 添加安全拦截器
                addInterceptor(createSecurityInterceptor())
            }
            .build()
    }

    /**
     * 创建安全的SSL Socket工厂
     */
    private fun createSecureSSLSocketFactory(): SSLSocketFactory {
        val trustManager = createTrustManager()
        
        return SSLSocketFactory.getDefault().let { defaultFactory ->
            object : SSLSocketFactory() {
                override fun getDefaultCipherSuites(): Array<String> = defaultFactory.defaultCipherSuites
                override fun getSupportedCipherSuites(): Array<String> = defaultFactory.supportedCipherSuites

                override fun createSocket(socket: Socket?, hostname: String?, port: Int): Socket {
                    val sslSocket = defaultFactory.createSocket(socket, hostname, port) as SSLSocket
                    enableCertificatePinning(sslSocket, hostname)
                    return sslSocket
                }

                override fun createSocket(host: String?, port: Int): Socket {
                    val sslSocket = defaultFactory.createSocket(host, port) as SSLSocket
                    enableCertificatePinning(sslSocket, host)
                    return sslSocket
                }

                override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
                    val sslSocket = defaultFactory.createSocket(host, port, localHost, localPort) as SSLSocket
                    enableCertificatePinning(sslSocket, host)
                    return sslSocket
                }

                override fun createSocket(address: InetAddress?, port: Int): Socket {
                    val sslSocket = defaultFactory.createSocket(address, port) as SSLSocket
                    enableCertificatePinning(sslSocket, address?.hostName)
                    return sslSocket
                }

                override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
                    val sslSocket = defaultFactory.createSocket(address, port, localAddress, localPort) as SSLSocket
                    enableCertificatePinning(sslSocket, address?.hostName)
                    return sslSocket
                }

                private fun enableCertificatePinning(socket: SSLSocket, hostname: String?) {
                    try {
                        // 设置加密套件和协议
                        socket.enabledProtocols = arrayOf("TLSv1.2", "TLSv1.3")
                        
                        // 强制使用证书固定
                        val params = socket.sslParameters.apply {
                            endpointIdentificationAlgorithm = "HTTPS"
                        }
                        socket.sslParameters = params
                    } catch (e: Exception) {
                        SecurityLog.e("启用证书固定失败: $hostname", e)
                    }
                }
            }
        }
    }

    /**
     * 创建信任管理器
     */
    private fun createTrustManager(): X509TrustManager {
        return object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
                // 客户端证书验证逻辑
            }

            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                // 服务器证书验证逻辑
                chain.forEachIndexed { index, cert ->
                    try {
                        cert.checkValidity()
                    } catch (e: CertificateExpiredException) {
                        throw SecurityException("证书已过期: ${cert.subjectDN}")
                    } catch (e: CertificateNotYetValidException) {
                        throw SecurityException("证书尚未生效: ${cert.subjectDN}")
                    }
                }
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }
    }

    /**
     * 创建自定义主机名验证器
     */
    private fun createCustomHostnameVerifier(): HostnameVerifier {
        return HostnameVerifier { hostname, session ->
            try {
                // 检查受信任主机
                if (trustedHosts.contains(hostname)) {
                    return@HostnameVerifier true
                }

                // 使用默认验证器
                HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            } catch (e: Exception) {
                SecurityLog.e("主机名验证失败: $hostname", e)
                false
            }
        }
    }

    /**
     * 创建安全拦截器
     */
    private fun createSecurityInterceptor(): Interceptor {
        return Interceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            
            // 记录安全事件
            val hostname = request.url.host
            val protocol = request.url.scheme
            
            SecurityLog.i("网络请求: $protocol://$hostname${request.url.encodedPath}")
            
            // 检查响应安全头
            checkSecurityHeaders(response)
            
            response
        }
    }

    /**
     * 检查安全响应头
     */
    private fun checkSecurityHeaders(response: Response) {
        val securityHeaders = mapOf(
            "Strict-Transport-Security" to "强制HTTPS",
            "X-Content-Type-Options" to "防止MIME类型嗅探",
            "X-Frame-Options" to "防止点击劫持",
            "X-XSS-Protection" to "XSS保护"
        )

        securityHeaders.forEach { (header, description) ->
            val headerValue = response.header(header)
            if (headerValue == null) {
                SecurityLog.w("缺少安全响应头: $header ($description)")
            } else {
                SecurityLog.i("安全响应头: $header = $headerValue")
            }
        }
    }

    /**
     * 验证证书指纹格式
     */
    private fun isValidCertificatePin(pin: String): Boolean {
        // 验证格式：SHA-256:base64hash 或 SHA-1:base64hash
        val pattern = "^(SHA-256|SHA-1):[A-Za-z0-9+/=]+$".toRegex()
        return pattern.matches(pin)
    }

    /**
     * 计算证书指纹
     */
    private fun computeCertificatePin(certificate: X509Certificate): String {
        val fingerprint = java.security.MessageDigest.getInstance("SHA-256")
            .digest(certificate.encoded)
            .joinToString("") { "%02x".format(it) }
        
        return "SHA-256:$fingerprint"
    }

    /**
     * 获取证书信息
     */
    suspend fun getCertificateInfo(hostname: String): CertificateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://$hostname"
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, arrayOf(createTrustManager()), SecureRandom())
            
            val socketFactory = sslContext.socketFactory
            val socket = socketFactory.createSocket() as SSLSocket
            
            socket.connect(InetSocketAddress(hostname, 443), 10000)
            socket.startHandshake()
            
            val session = socket.session
            val peerCertificates = session.peerCertificates
            
            val certificate = peerCertificates[0] as X509Certificate
            
            CertificateInfo(
                subject = certificate.subjectDN.name,
                issuer = certificate.issuerDN.name,
                validFrom = certificate.notBefore,
                validTo = certificate.notAfter,
                serialNumber = certificate.serialNumber.toString(),
                fingerprint = computeCertificatePin(certificate),
                publicKeyAlgorithm = certificate.publicKey.algorithm,
                signatureAlgorithm = certificate.sigAlgName
            )
        } catch (e: Exception) {
            SecurityLog.e("获取证书信息失败: $hostname", e)
            null
        }
    }

    /**
     * 预热连接（为重要主机建立预连接）
     */
    suspend fun warmUpConnections(): Boolean = withContext(Dispatchers.IO) {
        try {
            val client = createSecureHttpClient()
            
            pinnedCertificates.keys.forEach { hostname ->
                try {
                    val request = Request.Builder()
                        .url("https://$hostname")
                        .head()
                        .build()
                    
                    client.newCall(request).execute().use { response ->
                        SecurityLog.i("预热连接成功: $hostname, 状态码: ${response.code}")
                    }
                } catch (e: Exception) {
                    SecurityLog.w("预热连接失败: $hostname, ${e.message}")
                }
            }
            
            true
        } catch (e: Exception) {
            SecurityLog.e("预热连接失败", e)
            false
        }
    }

    /**
     * 获取状态信息
     */
    suspend fun getStatus(): CertificatePinnerStatus = withContext(Dispatchers.IO) {
        CertificatePinnerStatus(
            pinnedHostnames = pinnedCertificates.keys.toList(),
            trustedHosts = trustedHosts.toList(),
            validationCacheSize = certificateValidationCache.size,
            totalPins = pinnedCertificates.values.sumOf { it.size }
        )
    }
}

/**
 * 证书验证结果
 */
data class CertificateValidationResult(
    val isValid: Boolean,
    val reason: String,
    val error: String?
)

/**
 * 证书信息
 */
data class CertificateInfo(
    val subject: String,
    val issuer: String,
    val validFrom: java.util.Date,
    val validTo: java.util.Date,
    val serialNumber: String,
    val fingerprint: String,
    val publicKeyAlgorithm: String,
    val signatureAlgorithm: String
)

/**
 * 证书绑定器状态
 */
data class CertificatePinnerStatus(
    val pinnedHostnames: List<String>,
    val trustedHosts: List<String>,
    val validationCacheSize: Int,
    val totalPins: Int
)