# :backupkit-drive

## 这是什么 / 不是什么

`:backupkit` 的 `RemoteBackupStore` 接口在 Google Drive 上的实现，加上一层基于 GMS Authorization API 的 OAuth 授权封装。发布坐标 `com.github.sanatowhite.sdk:backupkit-drive:<version>`，单独发布、单独引入——只想要本地/SAF 备份、不想要 Google Drive 依赖的消费方，完全不用引这个模块（也不会因为它的存在而多带任何 GMS 相关的 class）。

裸 `HttpURLConnection` + `org.json` 实现 Drive REST v3，**不引入 OkHttp/Retrofit**——这个模块已经因为 GMS 认证带了一份不小的依赖（`play-services-auth`），没必要再叠一个 HTTP 客户端库。

**不是**：不管理"账号是谁""要不要弹账号选择器"这类产品层面的状态——`GmsDriveAuthorizer` 只负责换取/缓存/刷新 access token，账号邮箱展示、"记住上次登录账号"之类的偏好存储留给宿主自己做（本仓库消费方在 app 侧的 `DriveAuthCoordinator`/`DriveBackupSettings` 里做）。不做 Drive 之外的任何云盘——OneDrive/iCloud/自建 S3 需要消费方自己实现 `RemoteBackupStore`，这个模块跟它们无关。不做增量同步之外的 Drive 高级功能（协作、评论、版本历史、共享链接）——只用到 `files.list`/`files.create`/`resumable upload`/`files.get?alt=media`/`files.delete` 这几个端点，够备份/恢复用就行。

## 为什么这个模块可以带三方依赖

本仓库大多数 `:core-*`/`:feature-*` 模块的硬约束是"发布出去的模块要么零三方依赖，要么依赖只出现在 `implementation`、绝不泄漏进公开 API"。`:backupkit-drive` 走的是仓库里已有的先例——`:telemetry-firebase`：**当一个能力在现实中就是必须绑定某个具体厂商 SDK 才能实现时（Google Drive 认证只能用 Google 自己的 Authorization API，没有厂商中立的替代品），允许整个模块专门为这一个厂商依赖而存在，前提是三条边界不能破：**

1. **公开签名里不出现任何厂商类型**——`DriveTokenProvider`/`DriveAuthResult`/`DriveBackupStore`/`GmsDriveAuthorizer` 的公开方法签名里没有一个 `com.google.android.gms.*` 类型出现（`GmsDriveAuthorizer.authorize()` 返回的是这个模块自己定义的 `DriveAuthResult`，不是 GMS 的 `AuthorizationResult`）。消费方永远不需要在自己代码里写出 GMS 类型名，即使他们直接用这个模块。
2. **厂商依赖只能是 `implementation`，绝不能是 `api`**——`play-services-auth` 在 `build.gradle.kts` 里是 `implementation`；`:backupkit` 那份 `api` 是因为 `DriveBackupStore implements RemoteBackupStore`，消费方需要能声明这个类型的变量，这跟"泄漏 GMS 类型"是两回事，不要混淆。
3. **不依赖这个模块的消费方，编译产物里完全不会出现这个厂商的任何东西**——`:backupkit` 单独引入时，`RemoteBackupStore` 只有 `SafBackupStore` 一个内置实现，不会因为"以后可能要用 Drive"就预先带上 GMS 依赖。

不满足这三条的"为了方便"式加依赖（比如给 `:core-common` 加 OkHttp 只因为某个模块想用），走的是常规的 `verifyModuleGraph`/ADR 0009 判定，不适用这条例外规则。这条规则本身被记录在 `docs/adr/0011-vendor-backed-backend-modules.md`，供以后新增类似模块（比如"Dropbox 备份"）时对照判断能否照此先例处理。

## 独立引入

```groovy
dependencies {
    implementation 'com.github.sanatowhite.sdk:backupkit-drive:1.0.0' // 会自动带上 :backupkit
}
```

需要在自己 app 的 `AndroidManifest.xml`/Google Cloud Console 里注册好 OAuth client（`GmsDriveAuthorizer` 内部按调用方传入的 `serverClientId` 请求 `DRIVE_FILE` scope 的 Authorization），这是 Google 侧配置，与本模块代码无关——一个常见坑是只在 release 签名对应的 SHA-1 上注册了 OAuth client，忘了给 debug 签名也注册一份，导致 debug 包登录 Google 授权时静默失败或报 `DEVELOPER_ERROR`。

## AI 接入指南（可直接执行）

**要不要用这个模块**：已经在用 `:backupkit`，且想要把备份存到 Google Drive 时加。

**接入步骤**：

1. 加坐标（见上方"独立引入"）。
2. 实现授权流程：
   ```kotlin
   val authorizer = GmsDriveAuthorizer(context, serverClientId)
   val result = authorizer.authorize()
   when {
       result.needsConsent -> // 用 result.consentIntentSender 发起 IntentSenderRequest，
                               // onActivityResult 里拿到 Intent 后调 authorizer.handleConsentResult(intent)
       result.isSuccess    -> // result.accessToken 可用，但通常不需要直接用它——见下一步
       else                -> // result.errorMessage
   }
   ```
3. 把 `authorizer.asTokenProvider()` 直接传给 `DriveBackupStore` 的构造参数，不需要自己实现 `DriveTokenProvider`：
   ```kotlin
   val store = DriveBackupStore(
       tokenProvider = authorizer.asTokenProvider(),
       rootFolderName = "MyApp",       // 必须传自己 app 的专属根目录名，见下方"已知限制"第一条
       subPath = null,                 // 多空间隔离用（如隐私空间备份），单空间传 null
   )
   ```
4. 把 `store` 传给 `BackupOrchestrator` 的 `remoteStore` 参数，其余用法与 `:backupkit` README 一致。
5. 401 恢复：调用 `authorizer.clearCachedToken()` 后重新走一遍 `authorize()`。

**验证**：`./gradlew :backupkit-drive:test` 通过（`DriveBackupStoreTest` 用 JDK 内置 `HttpServer` 起一个假 Drive API 服务器验证 resumable upload/list 分页/PATCH 覆盖逻辑，不需要真实网络）；真机验证需要一个真实 Google 账号，跑一次 `writeSnapshot`+`restore`，在 Drive 网页版确认对应目录下出现了文件。

**不要做的事**：不要在这个模块的公开 API 上直接暴露 `GoogleSignInAccount`/`AuthorizationResult`等 GMS 类型；不要给 `rootFolderName` 设默认值（多个 app 共用同一个 Google 账号时根目录名撞车会互相覆盖备份，必须强制调用方显式决定）。

## 公开 API

- `DriveTokenProvider` — `suspend fun currentAccessToken(): String`，唯一职责是"给我一个当前可用的 access token"，不关心它是怎么来的。
- `DriveAuthResult` — `isSuccess`/`needsConsent`/`accessToken`/`consentIntentSender`/`errorMessage`；三个工厂方法 `success(token)`/`consentRequired(intentSender)`/`failure(message)`。
- `GmsDriveAuthorizer(context, serverClientId)` — `suspend fun authorize(): DriveAuthResult`、`fun handleConsentResult(intent): DriveAuthResult`、`suspend fun clearCachedToken()`、`fun asTokenProvider(): DriveTokenProvider`。`TOKEN_TTL_MILLIS` 常量供宿主判断是否需要提前刷新。
- `DriveBackupStore(tokenProvider, rootFolderName, subPath = null, filesUrl = DEFAULT_FILES_URL, uploadUrl = DEFAULT_UPLOAD_URL)` — `RemoteBackupStore` 的 Google Drive REST v3 实现；`filesUrl`/`uploadUrl` 两个参数只在测试里指向假服务器时才需要覆盖，真实消费方不传即可。

## 已知限制 / 不要做的事

- **`rootFolderName` 没有默认值，必须显式传**——多个 app 共用同一个 Google 账号登录时，根目录名撞车会导致互相覆盖对方的备份数据，这是刻意不给默认值、强制调用方显式决定的设计。
- **`java.net.HttpURLConnection` 不支持 PATCH 方法**——这是从未被修复的 JDK 老限制（最新 JDK 依然如此，见 `DriveBackupStore.startResumableSession` 的实现注释），内部用 Google 官方支持的绕过方式（实际发 POST，加 `X-HTTP-Method-Override: PATCH` 头）解决，`DriveBackupStoreTest` 里的假服务器专门验证了这一点——如果未来有人想"简化"这段代码直接用 `setRequestMethod("PATCH")`，会在真实 Drive API 上抛 `ProtocolException`，不要这么改。
- **`folderIdCache` 是实例级别、进程内存中的**——`DriveBackupStore` 实例存活期间会缓存"文件夹名 → Drive folder id"的映射，减少重复的 `files.list` 查询；这意味着同一个文件夹在 Drive 网页端被手动删除后，同一个 `DriveBackupStore` 实例可能仍然认为它存在直到下次重建实例，不做跨进程/跨实例的缓存失效检测。
- **不对 `access token` 做本地持久化**——`GmsDriveAuthorizer`/`DriveTokenProvider` 只在内存/GMS 自己的 token 缓存里存活，进程重启后需要重新走一遍授权流程（GMS 侧通常是静默完成，不需要用户再次交互，除非 refresh token 已失效）。
- **不做账号切换的产品逻辑**——"记住上次登录的账号邮箱""切换账号时提示会看到新账号下的备份"这类 UX 决策留给宿主（本仓库消费方的做法：邮箱展示、账号切换确认弹窗都在 app 侧的 `DriveAuthCoordinator`，不在这个模块里）。
