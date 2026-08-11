# :auth-firebase

## 这是什么 / 不是什么

`:core-auth` 的唯一实现:Firebase Auth 全托管的邮箱+密码、Google、Apple、手机短信验证码四种登录方式,以及对应的 Hilt 绑定。

**不是**:不含 UI(那是 `:feature-auth`)。不含网络层粘合(那是 `:auth-net-hilt`)。**不是**"模板必须用 Firebase"的声明——`:core-auth` 对它一无所知,换成另一个后端只需要提供另一个 `AuthRepository` 实现并 exclude 这个模块。

## 为什么这个模块可以带三方依赖(ADR 0011)

本仓库对"可发布模块带 vendor 依赖"有四条硬性条件(见 `docs/adr/0011-vendor-backed-backend-modules.md`),`:telemetry-firebase`/`:backupkit-drive` 是既有先例。这个模块逐条核对:

1. **只为这一个 vendor 集成而存在**——`FirebaseAuthRepository` 只实现 `:core-auth` 已定义的 `AuthRepository`/`AuthTokenProvider`,不做别的任何事。Google 登录用到的 `androidx.credentials`/`googleid` 是 Firebase 登录集成本身的实现细节(拿到的只是一个 `String` idToken),不算第二个 vendor 集成。
2. **vendor 类型绝不出现在公开签名里**——`FirebaseUser`/`OAuthProvider`/`GoogleAuthProvider`/`PhoneAuthProvider.ForceResendingToken`/`CredentialManager` 等全部只活在方法体或 `private` 字段里。唯一转换点是 `FirebaseAuthRepository.kt` 里 `private fun FirebaseUser.toAuthUser(): AuthUser`。
   - ⚠️ **实测踩过的坑**:Kotlin 的 `internal` 顶层声明在 JVM 字节码层面通常是**普通 `public`**(不带名字修饰),`sanato.api.check` 这套 javap 快照工具因此会把 `internal` 函数当成公开 API 收进 golden——第一版把 `toAuthUser` 写成 `internal fun` 时,`FirebaseUser` 真的出现在了 `auth-firebase.api` 里。真正要在这套工具下做到"consumer 永远不需要引用 vendor 类名",必须是 **`private`**(文件级作用域),不能只满足于 Kotlin 源码层面的 `internal`。`toAuthProvider()`/`toAuthError()` 因为要被同模块测试直接调用、且签名本身不含 vendor 类型,才保留 `internal`。
   - `Activity`/`Intent` 是 framework 类型,不算 vendor 类型,出现在 `signInWithGoogle(activity)`/`signInWithApple(activity)` 里不违反这条——`:backupkit-drive` 的 `GmsDriveAuthorizer.handleConsentResult(Intent?)` 是同型先例。
3. **vendor 依赖全部 `implementation`,零 `api`**——`firebase-bom`/`firebase-auth`/`kotlinx-coroutines-play-services`/`androidx.credentials`/`googleid` 皆是。唯一的 `api` 依赖是 `project(":core-auth")`(因为 `AuthUser`/`AuthState`/`AuthError` 这些消费方确实需要引用的类型来自那里)。
4. **不依赖它时 `AuthRepository`/`AuthTokenProvider` 仍可用**——`:core-auth` 的 `FakeAuthRepository`(testFixtures)或未来其他后端实现可以完全替代它。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-auth:1.0.0")
    implementation("com.github.sanatowhite.sdk:auth-firebase:1.0.0")
}
```

**前置条件**(缺一样,对应登录方式直接失败而不是崩溃,见下方"已知限制"):
- Firebase 控制台里启用了对应的登录方式(Authentication → Sign-in method)。
- Google 登录需要:`google-services.json` 里 `oauth_client` 数组非空(能查到 web client id)+ debug/release 签名的 SHA-1/SHA-256 都已加入 Firebase 项目——仓库自带的共享 demo 项目(`sanato-app-template`)已经满足这两条,换成自己的项目后需要重新走一遍(见 `TEMPLATE.md`)。
- Apple 登录需要:Apple Developer 账号 + Services ID + Sign in with Apple 私钥,在 Firebase 控制台配置。
- 手机验证码需要:Firebase 项目开启 Blaze 计费 + Play Integrity。

## AI 接入指南(可直接执行)

**要不要用这个模块**:用了 `:core-auth` 且想要开箱即用的 Firebase 实现时加;想换成自己的后端就不要加,直接依赖 `:core-auth` 接口自己写 `@Binds`。

**接入步骤**:
1. 加坐标(见上方)。
2. 仓库自带的 `app/google-services.json` 已经指向一个真实、登录能力已配置好的共享 demo 项目——直接能用,不需要这一步。要上生产环境才需要换成自己的项目文件(`TEMPLATE.md` fork checklist 有完整步骤)。
3. `:app` 里注入 `AuthRepository`/`AuthTokenProvider` 即可用,零手写绑定代码。

**验证**:`./gradlew :app:hiltJavaCompileDebug` 编译通过代表绑定聚合正确;consumer-smoke 工程(`checks/consumer-smoke`)没有 `google-services.json` 也应该能正常 `assembleDebug`——如果不能,说明有代码在构造期就调用了 `FirebaseAuth.getInstance()`,违反了"推迟到第一次真正登录"这条设计。

**不要做的事**:见"已知限制"。

## 公开 API

- `FirebaseAuthRepository(context, googleWebClientId, appleScopes, externalScope)` — `AuthRepository` + `AuthTokenProvider` 的唯一实现。
- `FirebaseAuthModule` — `@Module`,提供 `AuthRepository`/`AuthTokenProvider`/`FirebaseAuthRepository` 三条绑定 + 一个内部 `@AuthExternalScope CoroutineScope`。
- `FirebaseAuthBindsModule` — `@Module`,`@BindsOptionalOf GoogleWebClientIdOverride` + `@Multibinds Set<SessionScopedStore>`(少了后者,没有任何 `@IntoSet` 绑定的消费方会在 Hilt 聚合阶段编译失败——同 `Set<Telemetry>`/`Set<AppInitializer>` 的既有坑)。
- `GoogleWebClientIdOverride` — `fun interface { fun get(): String }`,想绕开"按名字查 `:app` 资源"这套默认逻辑就绑定这个。

## 已知限制 / 不要做的事

- **不要**在任何 `@Provides`/构造函数里提前调用 `FirebaseAuth.getInstance()`——没有真实 `google-services.json` 的进程(consumer-smoke、或 fork 还没配置完)必须能正常构造并注入这个仓库,失败只应该发生在真正尝试登录的那一刻。
- **不要**把 `FirebaseUser`/`OAuthProvider`/`GoogleAuthProvider`/`CredentialManager` 等 vendor 类型标记成 `internal` 就以为够了——本模块的 golden 快照工具会把 `internal` 顶层声明当作公开 API,想让 vendor 类型真正不出现在 `auth-firebase.api` 里,必须用 `private`。
- **不要**同时保留这个模块的绑定又自己再写一条 `@Binds ... : AuthRepository`——Hilt 会报 duplicate binding。
- Apple 用户的 `email` 字段可能是 `@privaterelay.appleid.com` 中转地址(Hide My Email);`displayName`/`email` 只在**首次**登录时由 Apple 返回,之后不再返回(Firebase 已把它们存进 `FirebaseUser`,`toAuthUser()` 天然处理,不需要额外逻辑)。
- 手机验证码的 `PhoneVerificationId` 只是 `:auth-firebase` 内存态的句柄(`resendTokens` map 上限 8 条,超出淘汰最旧的);进程被杀后无法恢复,UI 侧必须处理 `PhoneAuthEvent` 流中断的情况(见 `:feature-auth` 的 `onSessionLost` 降级路径)。
