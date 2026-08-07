# :backupkit

## 这是什么 / 不是什么

一个纯 JVM/Android、**零第三方依赖**（`kotlinx-coroutines-android` 除外）的备份/恢复引擎：自描述版本号 + 算法标识的加密容器格式（SBK1）、zip 打包/解包、`BackupOrchestrator` 编排层（增量条目 / 快照 / 整包三种传输单元的上传-校验-清理流程）、以及一个零 GMS 依赖的 `SafBackupStore`（Android SAF 目录）。发布坐标 `com.github.sanatowhite.sdk:backupkit:<version>`。

内置一个**只读**的 legacy 解码器（`LegacySdbCodec`），认识三种旧格式：SDB1（AES-GCM，一次性缓冲）、SDB2（AES-CTR+HMAC，流式）、裸 zip。这不是"兼容历史版本"承诺——`:backupkit` 自己只写 SBK1，legacy 解码器存在纯粹是因为很多消费方（包括本仓库的 `:app`）云端已经有大量旧格式存量数据，SDK 必须能读，但不会再写这些格式，也不接受把新格式改造成兼容旧格式的请求。

**不是**：不管密钥从哪来——`PassphraseProvider` 是唯一入口，SDK 内部没有任何 KDF salt 之外的密钥材料，也不知道"账号""口令强度"这些概念，全部留给宿主。不做业务去重——`BackupRestoreTarget.accept()` 返回 `true`/`false` 决定是否落库，去重逻辑（按 id 还是按内容 hash）完全是宿主的事。不含任何具体云盘实现——Google Drive 在 `:backupkit-drive`，别的云盘（iCloud/OneDrive/自建 S3）需要消费方自己实现 `RemoteBackupStore`（4 个 suspend 方法：`list/uploadIfAbsent/upload/download/delete`）。不做 UI、不做通知、不做前台服务/WakeLock——这些留给宿主（本仓库消费方在 `JournalBackupService` 里做）。

## 独立引入

```groovy
// settings.gradle 加 JitPack 仓库
dependencyResolutionManagement {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

```groovy
dependencies {
    implementation 'com.github.sanatowhite.sdk:backupkit:1.0.0'
}
```

想一次引入 `:backupkit` + `:backupkit-drive` 并保持版本对齐，用 `:sdk-bom`（见根 README）：`implementation(platform('com.github.sanatowhite.sdk:sdk-bom:1.0.0'))` 之后两个坐标都不用再写版本号。

## AI 接入指南（可直接执行）

**要不要用这个模块**：想要"给自己 app 的某种业务数据加一套加密备份/恢复能力"，且不想自己设计容器格式、不想自己写 zip 打包/解包安全规则时用。只需要本地导出导入/SAF 目录同步，不需要 Google Drive，只引 `:backupkit` 即可，不必带上 `:backupkit-drive`。

**接入步骤**：

1. 实现 4 个宿主接口（全部在 `io.sanato.appkit.backup.core` 包）：
   - `PassphraseProvider`：`suspend fun passphrase(): ByteArray`，返回加密口令的原始字节。**这是全链路唯一装密钥的地方**——SDK 拿到后立即用于 KDF，用完调用 `KeySchedule.wipe()` 清零派生出的中间密钥，但不会去清 `ByteArray` 本身（`SecretKeySpec` 内部会拷贝一份，Java 密码学 API 没有能把这份拷贝一起清零的公开办法），宿主自己用完也应 `fill(0)`。
   - `BackupDataSource`：导出侧，`listRecordSummaries()`/`loadRecord(id)`/`resolveLocalMedia(mediaName)` 三个 suspend 方法 + `payloadSchema`/`payloadSchemaVersion` 两个属性（写进 manifest，恢复时用来拒绝"导入了别的 app 的备份"）。
   - `BackupRestoreTarget`：恢复侧，`mediaDirectory()` 返回媒体落盘目录，`accept(record, mediaFiles)` 决定是否写入（去重逻辑自己写），`onRestoreStart`/`onRestoreFinish` 是可选的生命周期钩子（默认空实现）。
   - 如果要用 Google Drive 或自建云盘，还需要一个 `RemoteBackupStore` 实现（Drive 见 `:backupkit-drive`；SAF 目录直接用内置的 `SafBackupStore(contentResolver, treeUri)`，不用自己写）。
2. 把 `BackupRecord.body` 定义成宿主自己的 JSON 字符串——SDK 只透传这个字段，不解析、不校验其内部结构，业务字段（正文/标签/心情……）全部自己决定 schema。
3. 组装 `BackupOrchestrator(store, dataSource, restoreTarget, sealPassphrase, unsealPassphrases, workDir, codec, logger, sealedSuffix, ioDispatcher)`（后四个都有默认值，通常只需要传前六个）：
   - `unsealPassphrases` 是一个列表，用于"曾经改过口令派生算法"的场景——恢复时按顺序逐个尝试，直到某个能通过 MAC 校验；新装机场景传空列表即可。
   - `codec` 默认已经是 `Sbk1BackupCodec()`（目前唯一实现，`seal()` 只写 SBK1，`unseal()` 按 magic 自动分派到 SBK1 或三种 legacy 格式），一般不需要覆盖。
   - `sealedSuffix` 默认 `.sdb`，不要改（见下方"已知限制"）。
   - `ioDispatcher` 默认 `Dispatchers.IO`，一般不需要覆盖。
4. 调用：`backupEntryDelta(entryId)` 单条增量、`writeSnapshot(sinceMillis, progressListener)` 快照、`writeArchiveBundle(sinceMillis)` 整包、`restore(progressListener)` 恢复（内部已经把远端媒体目录 list 一次并缓存，不会对每个媒体文件单独发一次 list 请求）。

**验证**：`./gradlew :backupkit:test` 通过；用 `Sbk1BackupCodec().seal()`+`unseal()` 跑一轮往返，再用一份已知的旧格式文件跑 `LegacySdbCodec().detect()`+`decodeSdb1/decodeSdb2`，确认三种 legacy 格式都能正确探测。

**不要做的事**：不要往 `BackupRecord`/`RecordSummary`/`ManifestHeader` 这类返回给消费方的类型上加字段后改成 `data class`——它们本来就是普通 `class`（见下面"已知限制"），改成 `data class` 会把 `copy()`/`componentN()` 冻进 ABI；不要在 SDK 内部写死任何"诱饵空间""隐私空间"之类的业务概念，这些属于宿主自己的分区逻辑（用不同的 `RemoteBackupStore`/`BackupDataSource` 实例区分即可）。

## 公开 API

### `core` 包——宿主要实现/使用的接口与数据类型

- `BackupRecord(id, createdAtMillis, modifiedAtMillis, body, mediaNames)` — 一条业务记录，`body` 是宿主自定义 JSON 字符串，SDK 不解释。
- `RecordSummary(id, createdAtMillis, modifiedAtMillis)` — 增量比对用的轻量视图。
- `BackupDataSource` — 导出侧接口：`payloadSchema`/`payloadSchemaVersion` + `listRecordSummaries()`/`loadRecord(id)`/`resolveLocalMedia(mediaName)`。
- `BackupRestoreTarget` — 恢复侧接口：`mediaDirectory()`/`accept(record, mediaFiles)`/`onRestoreStart(header)`/`onRestoreFinish(count)`。
- `ManifestHeader(manifestVersion, payloadSchema, payloadSchemaVersion, recordCount, createdAtMillis)` — `onRestoreStart` 收到的 manifest 元信息视图。
- `PassphraseProvider` — `suspend fun passphrase(): ByteArray`。
- `BackupProgress(phase, completed, total)` / `TransferPhase`（`UPLOADING`/`DOWNLOADING`/`COMPRESSING`）/ `ProgressListener`。
- `BackupLogger`（`info`/`warn`，`BackupLogger.None` 是默认空实现）——SDK 内部不直接调 `android.util.Log`，保持纯 JVM 可测。

### `format` 包——容器格式

- `BackupAlgorithms` — 全部算法/尺寸常量（见下方"SBK1 格式规范"表）。
- `BackupCodec` 接口 + `Sbk1BackupCodec` 实现 — `inspect(file): BackupContainerInfo`（未认证头部视图）、`suspend fun seal(plain, sealed, passphraseProvider, options)`、`suspend fun unseal(sealed, plain, passphraseProviders)`。
- `SealOptions(contentType, payloadProfile, kdfIterations, producer)` — 默认构造已经填好 `CONTENT_ARCHIVE_ZIP`/`PROFILE_FULL`/`DEFAULT_PBKDF2_ITERATIONS`。
- `BackupContainerInfo` — `inspect()` 的返回值，**未认证**（读头部即可得到，不代表通过了 MAC 校验），只能用于 UI 展示/诊断，不能用于任何安全判定。
- `BackupFormatException` 六个子类：`UnsupportedFormatVersion`、`UnsupportedAlgorithm`、`UnsupportedManifestVersion`、`HeaderCorrupted`、`Truncated`、`AuthenticationFailed`、`NotABackupFile`、`SchemaMismatch`——每类都携带具体的字段名/数值，禁止吞掉细节改成一句"backup file invalid"。
- `LegacySdbCodec`（internal 之外唯一公开的 legacy 入口）— `detect(file): LegacyFormat?`（`SDB1_GCM`/`SDB2_CTR`/`PLAIN_ZIP`，探测不出返回 null）、`decodeSdb1(sealed, plain, passphraseBytes, ...)`、`decodeSdb2(...)`。**只读**，没有对应的 encode 方法。

### `archive` 包——zip 打包/解包

- `ArchiveBuilder` — `MANIFEST_ENTRY`/`MEDIA_DIR` 两个常量 + 构建结果里的 `getMediaFiles()`。
- `ArchiveReader` — `peekManifest(zipFile, expectedSchema): ParsedManifest`（**只读 manifest，不解压媒体**，用于恢复前预览/校验）；完整解包结果暴露 `getManifest()`/`getExtractedMedia(): Map<String, File>`。
- `ManifestCodec` — `encode(...)`/`decode(...)`，负责 legacy manifest（`{"version":3,...}`）到 `manifestVersion=0` 哨兵信封的适配。
- `ParsedManifest(header, mediaNames, records)`。

### `remote` 包——编排与云存储抽象

- `RemoteBackupStore` 接口 — `list(folder)`/`uploadIfAbsent(folder,name,file)`/`upload(...)`/`download(fileId,dest)`/`delete(fileId)`，全部 suspend；`Companion` 上有 `FOLDER_MEDIA`/`FOLDER_SNAPSHOTS`/`FOLDER_ENTRIES`/`FOLDER_BUNDLES` 四个约定目录名常量。
- `RemoteFile(id, name, size)`。
- `BackupOrchestrator` — 构造参数见上方"接入步骤"第 3 点；公开可变属性 `snapshotRetention`/`bundleRetention`/`maxMediaUploadRetry`（默认值分别是 30/2/3，灰度期可以调大甚至设 `Int.MAX_VALUE` 临时禁用清理）；四个核心 suspend 方法：`backupEntryDelta(id): Int`、`writeSnapshot(sinceMillis, listener): Int`、`writeArchiveBundle(sinceMillis): Int`、`restore(listener): Int`（均返回处理的记录数）。

### `saf` 包

- `SafBackupStore(contentResolver, treeUri)` — `RemoteBackupStore` 的 SAF 实现，用 `DocumentsContract` 而非 `DocumentFile`（性能原因：`DocumentFile` 每次子目录/子文件查询都是一次跨进程 IPC，`DocumentsContract` 可以批量查询）。

## SBK1 格式规范（normative）

这是 `:backupkit` 唯一会**写**的格式；SBK1 之外的一切都只走 `LegacySdbCodec` 只读路径。大端字节序。整体结构：

```
Header(变长, 按 16 字节对齐) || Ciphertext || MAC trailer(32 字节)
```

**MAC 覆盖 `header || ciphertext` 全部字节**（legacy 只盖 `salt||iv||ciphertext`，magic/算法字段完全不受保护——这是相对 legacy 的关键安全提升：篡改 header 里的算法标识本身也会被 MAC 校验挡住，不会出现"用错误的算法解出乱码却不报错"）。

### 固定头（48 字节）

| Off | Len | 字段 | 说明 |
|---|---|---|---|
| 0 | 4 | `magic` | `"SBK1"`（ASCII） |
| 4 | 1 | `formatVersion` | 当前 =1；未知版本抛 `UnsupportedFormatVersion(found, supportedRange)` |
| 5 | 1 | `cipherId` | 见下方算法注册表 |
| 6 | 1 | `macId` | 同上 |
| 7 | 1 | `kdfId` | 同上；**任一算法字段未知都抛 `UnsupportedAlgorithm(field, value)`，即使 `formatVersion` 已知**——新增算法编号不需要 bump `formatVersion` |
| 8 | 2 | `headerLength` | 头部总长度（含变长尾），读方必须按此字段跳转到密文起始位置，不得按结构体大小自行推算 |
| 10 | 1 | `contentType` | `CONTENT_ARCHIVE_ZIP` / `CONTENT_OPAQUE_BLOB` |
| 11 | 1 | `payloadProfile` | `PROFILE_FULL` / `PROFILE_MANIFEST_ONLY` / `PROFILE_MEDIA` |
| 12 | 4 | `kdfIterations` | PBKDF2 轮数 |
| 16 | 1 | `saltLength` | v1 = 16 |
| 17 | 1 | `ivLength` | v1 = 16 |
| 18 | 1 | `macLength` | v1 = 32 |
| 19 | 1 | `keyLength` | v1 = 32 |
| 20 | 8 | `createdAtWallMillis` | 仅展示用途，不参与任何解密/校验判定 |
| 28 | 2 | `producerLength` | |
| 30 | 2 | `metadataLength` | 当前实现固定写 0（字段保留给未来扩展，解析时按长度跳过即可，不假设内容） |
| 32 | 4 | `headerCrc32` | 覆盖 `[0,32) ∪ [36,headerLength)` 字节；**未认证，只挡运输过程中的意外损坏（如网盘同步半截），不挡故意篡改**——真正的防篡改靠 MAC trailer |
| 36 | 4 | `reserved0` | 写 0；读方遇到非零值忽略，不报错 |
| 40 | 8 | `plaintextLength` | 明文长度；未知场景写 -1 |

### 变长尾

```
salt(saltLength 字节) ‖ iv(ivLength 字节) ‖ producer(UTF-8, producerLength 字节) ‖ 零填充到 headerLength
```

`producer` 是**明文**（不加密、只受 CRC32 保护），只允许写"产品/客户端标识"这类无隐私信息（如 `"sanato-diary/1.1.1"`），**绝不能塞用户 ID、邮箱、设备标识**等 PII——header 在磁盘/云盘上以明文形式存在，任何能拿到备份文件的人都能读到这个字段。

### 算法注册表

| 字段 | 值 | 名称 | 说明 |
|---|---|---|---|
| `cipherId` | 1 | AES-256-CTR | `ivLength=16`，完整 128 位计数器块，真流式加解密（大文件常量内存）。刻意不用 AES-GCM：Conscrypt 的 GCM 实现无法真正流式处理，超过可用内存的大文件必然 OOM；编号 2 保留给未来可能的流式 AEAD 方案 |
| `macId` | 1 | HMAC-SHA256 | |
| `kdfId` | 1 | PBKDF2-HMAC-SHA256 | 默认 `DEFAULT_PBKDF2_ITERATIONS` 轮 |
| `kdfId` | 2 | HKDF-Expand-SHA256（单次，无拉伸） | 仅用于口令本身已是高熵密钥（如另一层已做过 KDF）的场景；当前实现未启用任何调用路径，保留编号供未来场景使用 |

### 密钥派生

```
pwChars = hex(SHA-256(passphraseBytes))        // 先做一次哈希再转十六进制 ASCII 字符串，
                                                 // 避免 Java PBEKeySpec 对 char[] 的 latin-1
                                                 // 式字节映射在跨语言（如与 Flutter 互通）场景
                                                 // 下产生字节膨胀不一致的坑
master  = PBKDF2(pwChars, salt, kdfIterations, keyLength)   // KeySchedule.deriveMasterKey
encKey  = HKDF-Expand-SHA256(master, info="SBK1/enc/v1")    // KeySchedule.deriveEncKey
macKey  = HKDF-Expand-SHA256(master, info="SBK1/mac/v1")    // KeySchedule.deriveMacKey
```

`encKey`/`macKey` 相互独立派生（legacy 是从原始口令直接算出 MAC key，独立性弱于此两阶段派生）。`KeySchedule.wipe(bytes)` 用于清零 `master` 这类中间产物；`SecretKeySpec` 本身持有内部拷贝，Java 密码学 API 没有公开方法能清零那份拷贝，这是 JVM 密码学 API 的已知限制，不是这里能修的。

### manifest.json 契约

**必须是 zip 的物理第一个 entry**（`ArchiveReader` 用 `ZipInputStream` 顺序读取校验这一点，不依赖 central directory——这是防路径穿越的前提条件之一，manifest 必须先于任何媒体 entry 被读到并校验通过）。

```json
{
  "manifestVersion": 1,
  "producer": "...",
  "payloadSchema": "io.sanato.diary.journal",
  "payloadSchemaVersion": 4,
  "createdAt": 0,
  "recordCount": 0,
  "mediaEmbedded": false,
  "mediaNames": ["..."],
  "records": [
    {
      "id": "...",
      "createdAt": 0,
      "modifiedAt": 0,
      "media": ["..."],
      "body": "<宿主自定义 JSON 字符串，SDK 不解释>"
    }
  ]
}
```

`manifestVersion` 归 SDK 管——未知值抛 `UnsupportedManifestVersion(found, supportedRange)`。`payloadSchema`/`payloadSchemaVersion`/`body` 归宿主，SDK 只做 `payloadSchema` 与 `BackupDataSource.payloadSchema` 的字符串比对（不匹配抛 `SchemaMismatch(expected, found)`，防止把别的 app 的备份导进来）。legacy 格式的 `{"version":3,"notes":[...]}` 由 `ManifestCodec.decode()` 识别并适配成 `manifestVersion=0`（哨兵值，代表"legacy 信封，无 SDK 层版本概念"）。

### 解包安全规则（修复了历史实现里的 zip 路径穿越漏洞）

1. manifest 先读取并校验通过（schema 匹配、`manifestVersion` 已知）之后，才开始处理任何媒体 entry。
2. 媒体 entry 名必须精确命中 manifest 里 `mediaNames` 白名单（zip 里出现白名单之外的文件名直接跳过，不解压）——legacy manifest 没有 `mediaNames` 字段时跳过这条白名单检查（否则任何 legacy 包都读不出媒体）。
3. entry 名不含 `/`、`\`、`..`，也不是绝对路径或 Windows 盘符路径（`C:\...`）。
4. 解析后的目标路径做 canonical path 检查，必须仍然位于媒体目录 canonical path 之下——即便前三条都通过，符号链接等边界情况仍可能逃逸，这一步是最后一道防线。
5. entry 数量上限 100,000，解压后总字节数上限默认 8GiB（可配置）——挡 zip bomb。

## 已知限制 / 不要做的事

- **记录/结果类一律是普通 `class`，不是 `data class`**——`BackupRecord`/`RecordSummary`/`ManifestHeader`/`RemoteFile`/`BackupContainerInfo`/`ContainerHeader` 等全部如此。`data class` 一旦发布，`copy()`/`componentN()` 就冻进 ABI，之后加字段会破坏源码兼容或产生一堆 deprecated 的 copy 重载；这是刻意的选择，不要"优化"成 `data class`。
- **`inspect()`/`BackupContainerInfo` 的结果未认证**——只是读了头部字节，没有做 MAC 校验，不能用来判断"这个文件是否是真实、未被篡改的备份"，只能用于 UI 展示（比如"这个文件大概是什么时候创建的、多大"）或者诊断日志。真正的完整性判定只有 `unseal()` 成功返回这一种。
- **`legacyPassphraseProviders` 只是"多试几个口令"，不是"多试几种格式"**——格式分派（SBK1 vs SDB1 vs SDB2 vs 裸 zip）完全由 magic 字节自动决定，跟传了几个 passphrase provider 无关。
- **不会新增任何"写 legacy 格式"的能力**——`LegacySdbCodec` 是且只会是只读的；如果某天需要支持第三种旧格式，加一个新的只读解码分支即可，不要给 SDB1/SDB2 补 encode 方法。
- **suspend fun interface 对纯 Java 消费方不友好**——`PassphraseProvider`/`RemoteBackupStore` 等接口的 suspend 方法在 Java 里需要手写 `Continuation`，这个模块目前不提供 Java 友好的桥接层（本仓库所有消费方都是 Kotlin）。
- **PBKDF2 120k 轮的性能成本是按调用次数算的，不是按数据量算的**——每次 `seal()`/`unseal()` 只跑一次 KDF（不管文件多大），但如果宿主对"每个媒体文件"都单独 `seal()` 一次（而不是把媒体和正文打进同一个 zip 再整体 `seal()`），会产生 N 次 KDF 开销；`kdfId=2`（HKDF 单次无拉伸）是为这种场景保留的选项，首发默认不启用，需要的话自己在 `SealOptions` 里显式指定。
- **远端文件名后缀必须保持 `.sdb`**——格式身份只认 magic 字节，不认后缀；但 `BackupOrchestrator` 内部的 `list()`/清理逻辑目前按 `.sdb` 后缀过滤远端文件，改后缀会导致这些文件在远端清理/去重逻辑里被当成"不认识的文件"而永久保留，不会被自动清理也不会报错。
