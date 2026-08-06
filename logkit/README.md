# :logkit

## 这是什么 / 不是什么

一个轻量级 Android 日志 SDK:分级(V/D/I/W/E)、多线程写入不阻塞调用方、并发下顺序一致、压缩后加密落盘、总量 5MB 上限滚动淘汰,支持把日志文件分享出来给开发者离线排查问题。仿 `:updatechecker` 的四条铁律(见 `CLAUDE.md` "The five `:logkit` rules"),本期**不对外发布**——见 `docs/adr/0008-logkit-pipeline-vs-apm-detection.md`。

**不是**:不检测崩溃/ANR/卡顿——那是 `:core-telemetry` 的活,它检测到之后通过两条通道把信号写进这里(见同一份 ADR)。不做用户同意门控——本期决定日志无条件落盘,`UserSettings.telemetryEnabled` 没有接进来,若要用于面向消费者的生产 App,这是上线前必须先解决的一项。不做 PII 过滤或脱敏——加密保护的是传输/静态存储,挡不住开发者自己把 token/邮箱写进日志。

## 独立引入

本期没有发布坐标。想在别的项目里用,直接把 `logkit/` 目录整个复制过去,改一下 `namespace`,跑一次 `scripts/logkit-keygen.sh`(见下)。

## 公开 API

```kotlin
object LogKit {
    fun install(context: Context, config: LogKitConfig): Boolean   // 已安装返回 false;永不抛
    fun isInstalled(): Boolean
    fun isHealthy(): Boolean

    fun v(tag: String, message: String)
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable)

    fun fatal(tag: String, message: String, throwable: Throwable?): Boolean   // 写入 + 自动 flush
    fun flushBlocking(timeoutMillis: Long): Boolean

    fun droppedRecordCount(): Long
    fun stats(): LogKitStats
    fun purge(): Int
    fun export(destination: File): Boolean   // 打 zip;~5MB IO,勿在主线程调用
}
```

语义契约(这段文字本身就是 API 的一部分):

- `install()` 已安装过返回 `false`,否则 `true`。**永不抛异常。**
- `install()` 之前调用任何 `v/d/i/w/e` 都是静默 no-op——不崩溃、不分配。
- `flushBlocking(t)` 返回 `true` 当且仅当调用之前被接受的每一条记录都已经落盘并 `fsync`。超时或 SDK 处于降级状态返回 `false`。`install()` 之前调用返回 `true`(空真)。与调用【同时】发生的 enqueue 可能被包含,也可能不被包含。
- `fatal()` = 以 ERROR 级别写入 + 自动 `flushBlocking`;返回 flush 的结果。
- **没有 `shutdown()`**:Android 进程里没有正确的关闭时机,而公开 API 只增不减,一个 `shutdown()` 的设计错误就是永久的。持久性靠 `flushBlocking`,不靠"关闭"。

配置(`LogKitConfig.Builder`):`minLevel`(默认 DEBUG)、`queueCapacity`(默认 4096)、`maxFileBytes`(默认 1MiB)、`totalBudgetBytes`(默认 5MiB)、`maxFileCount`(默认 24)、`mirrorToLogcat`(默认 false)、`maxMessageBytes`(默认 8192)、`frameLingerMillis`(默认 200)、`maxFrameBytes`(默认 32768)、`fatalFlushTimeoutMillis`(默认 500)、`setRecipientPublicKey(keyId, x509Der)`(覆盖内置公钥——**没有这个,没人能读自己 dev build 的日志**,私钥离线)、`putMetadata(key, value)`。

## 归档格式(normative——写方与 `logkit-decrypt` 共用同一份 `io.sanato.logkit.format` 源码)

大端字节序。加密方案:每文件随机 AES-256-GCM 内容密钥,用 ECIES(P-256 + HKDF-SHA256)包裹给内置(或配置覆盖)的收件方公钥;私钥离线,只有拿到私钥的人能解密。

**为什么是 ECIES 而不是 RSA-OAEP**:Android 的 JCE provider 历史上会无视请求的 OAEP **MGF1** 摘要而默认 SHA-1,桌面 JVM 用 SHA-256——设备上加密的日志在开发者机器上会**永久解不开,且几周后才发现**。ECIES 的曲线/KDF/AEAD 全部由本文件里的字节钉死,没有这个含糊参数面。代价是手写 HKDF(RFC 5869,`format/Hkdf.kt`,~50 行),用官方测试向量兜底。

**文件名**:`logkit-<fileSeq:%012d>-<createdAtMillis:%013d>-<processTag>.logkit`。`fileSeq` 零填充 12 位 ⇒ 字典序 == 创建序,读方不解析也能排序。

**文件头**(64 字节固定 + 变长尾,`headerLength` 对齐 16,明文——不含密钥,只含"用哪个密钥包裹了内容密钥",支持无私钥时也能看 `metadata` 分诊):

| Off | Len | 字段 |
|---|---|---|
| 0 | 4 | `magic` = `"LKF1"` |
| 4 | 1 | `formatVersion` = 1 |
| 5 | 1 | `kemId` = 1(ECIES-P256;2 预留给 RSA-OAEP,无需升版本即可回退) |
| 6 | 1 | `aeadId` = 1(AES-256-GCM,12B nonce,16B tag) |
| 7 | 1 | `compressionId` = 1(raw deflate) |
| 8 | 4 | `headerLength` —— 读方必须按此跳过,**不得**按结构体大小 |
| 12 | 4 | `keyId`(支持轮换) |
| 16 | 4 | `nonceSalt`(随机,帧 nonce 的高 4 字节) |
| 20 | 8 | `createdAtWallMillis`(仅供展示) |
| 28 | 8 | `createdAtElapsedNanos`(单调锚点) |
| 36 | 8 | `fileSeq` |
| 44 | 4 | `pid` |
| 48/50/52 | 2/2/2 | `processTagLen`/`wrappedKeyLen`(=125)/`metaLen` |
| 56 | 4 | `headerCrc32`(覆盖 `[0,56)∪[64,headerLength)`) |

`wrappedKey`(125B)= `ephPubPoint(65) ‖ kwNonce(12) ‖ wrappedContentKey‖tag(48)`。⚠️ `metadata` 是**明文**——绝不能塞用户 ID/邮箱之类的 PII。

**帧**(40B 头 + payload,重复至 EOF):`frameMagic="LKFR"(4) | frameFlags(1) | rsv(3) | frameIndex(8) | firstRecordSeq(8) | recordCount(4) | plaintextLen(4) | payloadLen(4) | headerCrc32(4)`。`nonce = nonceSalt(4) ‖ frameIndex(8)`;`aad = 帧头[0,36)`;`payload = AES-GCM(contentKey, nonce, aad).seal(deflate(records))`。

**记录**(帧明文内,长度前缀):`recordLen(4) | seq(8) | wallMillis(8) | elapsedNanos(8) | threadId(4) | level(1) | tagLen(1) | threadNameLen(2) | msgLen(4) | tag‖threadName‖message`。

### 五条不可动摇的格式不变量

1. **每帧独立 deflate + 独立 GCM。** 跨帧共用一个 deflate 流会让截尾摧毁文件其余部分——这是最容易被"优化"掉的一条。
2. **永不追加已有文件。** `install()`/滚动总是新建文件。追加需要从可能残缺的尾部恢复帧计数器,弄错就是同密钥 nonce 重用 = GCM 认证子密钥泄露,静默且灾难性。副产品:启动/滚动时无需读任何旧文件,损坏文件零成本。
3. **未经 CRC 校验的长度字段不可信。** `headerCrc32`/帧的 `headerCrc32` 先过,才敢用长度字段去 seek/分配。
4. **加密失败绝不退化为明文。** 失败即永久 `cryptoUnavailable`,丢弃记录,`isHealthy()=false`。
5. **`seq` 是唯一全序。** 时间戳仅供展示,可能与 `seq` 逆序(两次 `AtomicLong` 与时钟读取之间无锁);解密工具按 `seq` 排序并标出时间倒挂,不掩盖。

### 5MB 预算与淘汰

总预算 5MB,单文件 1MB 滚动。"最旧" = 最小 `fileSeq`,**绝不用 mtime**——用户改设备日期/NTP 校正/1s mtime 粒度/`adb pull` 重写 mtime 都会让 mtime 排序反转;`fileSeq` 是唯一稳定序。淘汰跳过当前打开的文件,并跳过每个 `processTag` 的最大 `fileSeq`(多进程宿主不能删别的进程正在写的最新文件)。

### 导出

`export()` 打 zip(不是拼接——拼接需要再一层长度前缀容器和第二个恢复扫描器,纯属新增 bug 面),含 `manifest.txt`。⚠️ **导出的 zip 不计入 5MB 预算**,峰值约 2× 预算(~10MB)。

## 密钥流程

1. 跑 `./scripts/logkit-keygen.sh --out-dir <仓库外的目录>`,生成一份 P-256 密钥对。
2. 把打印出来的 `PUBLIC_KEY_SPKI_DER` 常量粘进 `BuiltInRecipientKey.kt`——公钥不是秘密,可以提交。
3. **私钥只此一份**,备份到密码管理器,绝不提交、绝不进 CI 日志、绝不发到聊天工具。丢了 = 以后所有日志永久不可读,没有恢复手段。
4. 离线解密:`./gradlew :logkit-decrypt:installDist` 之后,`tools/logkit-decrypt/build/install/logkit-decrypt/bin/logkit-decrypt --private-key <pem> --in <file|dir> [--json] [--verify-seq]`。

本仓库为了让 fork 出去的人第一次跑 debug build 就能读自己的日志,**刻意签入了一份 THROWAWAY 测试密钥对**(`logkit/keys/debug-private-key.pem` + 内置公钥)。**任何真正要发布的 App 必须换掉它**——见 `logkit/keys/README.md` 和 `TEMPLATE.md` 的 fork checklist。

## 已知限制 / 不要做的事

- **不要**在 `:logkit` 里装 `Thread.UncaughtExceptionHandler`、跑 ANR 看门狗、或注册 `ContentProvider`——它是管道不是探测器,见 `docs/adr/0008`。
- **不要**假设它会自动做同意门控——本期没有,`UserSettings.telemetryEnabled` 没接进来。
- **不要**往 `config.metadata` 里塞 PII——那个字段是明文,写文件头时不加密。
- **不要**在 `UpdateDownloadState.InProgress` 之类的高频进度回调里打日志——一个下载进度块记一条,几秒内就能 churn 掉整个 5MB 预算,把想留住的崩溃现场挤出去。
- **不要**指望 Robolectric 测试证明了设备侧的加密行为——它跑在桌面 JVM 的 JCE provider 上,不是 Android 的 `AndroidOpenSSL`/Conscrypt。`EnvelopeRoundTripTest` 只证明格式自洽;真正的设备兼容性靠每文件建档时的对称自检(`Envelope.selfProbeSymmetric`)在运行时兜底,以及一次性的真机 API 24 验证。
- **不要**认为沿用仓库自带的 debug 密钥对发布 App 是安全的——那等于把用户日志加密给了模板作者能解密的密钥。
