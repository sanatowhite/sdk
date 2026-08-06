package io.sanato.logkit

/**
 * 不可变配置。刻意不是 `data class`——golden API dump 里 `data class` 会带出
 * `component1..N`/`copy`/`copy$default`,加一个字段就全变;公开 API 只增不减
 * 这条铁律下,普通 `class` + `Builder` 是唯一安全的形态。构造函数是 `private`,
 * 只能通过 [Builder] 得到实例。
 */
public class LogKitConfig private constructor(
    public val minLevel: LogLevel,
    public val queueCapacity: Int,
    public val maxFileBytes: Long,
    public val totalBudgetBytes: Long,
    public val maxFileCount: Int,
    public val mirrorToLogcat: Boolean,
    public val maxMessageBytes: Int,
    public val frameLingerMillis: Long,
    public val maxFrameBytes: Int,
    public val fatalFlushTimeoutMillis: Long,
    internal val recipientKeyId: Int,
    internal val recipientPublicKeyDer: ByteArray,
    public val metadata: Map<String, String>,
) {
    public class Builder {
        private var minLevel: LogLevel = LogLevel.DEBUG
        private var queueCapacity: Int = 4096
        private var maxFileBytes: Long = 1L * 1024 * 1024
        private var totalBudgetBytes: Long = 5L * 1024 * 1024
        private var maxFileCount: Int = 24
        private var mirrorToLogcat: Boolean = false
        private var maxMessageBytes: Int = 8192
        private var frameLingerMillis: Long = 200
        private var maxFrameBytes: Int = 32768
        private var fatalFlushTimeoutMillis: Long = 500
        private var recipientKeyId: Int = BuiltInRecipientKey.KEY_ID
        private var recipientPublicKeyDer: ByteArray = BuiltInRecipientKey.PUBLIC_KEY_SPKI_DER
        private val metadata: MutableMap<String, String> = LinkedHashMap()

        public fun setMinLevel(level: LogLevel): Builder = apply { minLevel = level }

        public fun setQueueCapacity(records: Int): Builder = apply { queueCapacity = records }

        public fun setMaxFileBytes(bytes: Long): Builder = apply { maxFileBytes = bytes }

        public fun setTotalBudgetBytes(bytes: Long): Builder = apply { totalBudgetBytes = bytes }

        public fun setMaxFileCount(count: Int): Builder = apply { maxFileCount = count }

        public fun setMirrorToLogcat(enabled: Boolean): Builder = apply { mirrorToLogcat = enabled }

        public fun setMaxMessageBytes(bytes: Int): Builder = apply { maxMessageBytes = bytes }

        public fun setFrameLingerMillis(millis: Long): Builder = apply { frameLingerMillis = millis }

        public fun setMaxFrameBytes(bytes: Int): Builder = apply { maxFrameBytes = bytes }

        public fun setFatalFlushTimeoutMillis(millis: Long): Builder = apply { fatalFlushTimeoutMillis = millis }

        /**
         * 覆盖内置公钥——没有这个,没人能读自己 dev build 的日志(私钥离线)。
         * [x509Der] 必须是 P-256 SubjectPublicKeyInfo 的 DER 编码;[keyId] 建议
         * 用 SHA-256(DER) 的前 4 字节,便于解密工具按 keyId 分辨"密钥不对"和
         * "格式不对"。
         */
        public fun setRecipientPublicKey(
            keyId: Int,
            x509Der: ByteArray,
        ): Builder =
            apply {
                recipientKeyId = keyId
                recipientPublicKeyDer = x509Der
            }

        public fun putMetadata(
            key: String,
            value: String,
        ): Builder = apply { metadata[key] = value }

        public fun build(): LogKitConfig {
            require(queueCapacity > 0) { "queueCapacity must be positive" }
            require(maxFileBytes > 0) { "maxFileBytes must be positive" }
            require(totalBudgetBytes >= maxFileBytes) { "totalBudgetBytes must be >= maxFileBytes" }
            require(maxFileCount > 0) { "maxFileCount must be positive" }
            require(maxMessageBytes > 0) { "maxMessageBytes must be positive" }
            require(maxFrameBytes > maxMessageBytes) { "maxFrameBytes must be > maxMessageBytes" }
            require(frameLingerMillis >= 0) { "frameLingerMillis must not be negative" }
            require(fatalFlushTimeoutMillis > 0) { "fatalFlushTimeoutMillis must be positive" }
            return LogKitConfig(
                minLevel,
                queueCapacity,
                maxFileBytes,
                totalBudgetBytes,
                maxFileCount,
                mirrorToLogcat,
                maxMessageBytes,
                frameLingerMillis,
                maxFrameBytes,
                fatalFlushTimeoutMillis,
                recipientKeyId,
                recipientPublicKeyDer,
                LinkedHashMap(metadata),
            )
        }
    }
}
