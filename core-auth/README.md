# :core-auth

## 这是什么 / 不是什么

Provider 无关的身份/会话抽象:`AuthUser`/`AuthState`/`AuthError`/`AuthRepository`/`AuthTokenProvider`/`SessionScopedStore`。

**不是**:不含任何具体登录后端的实现——Firebase 是唯一已发布的实现(`:auth-firebase`),但这个模块对它一无所知。不含 UI(那是 `:feature-auth`)。不含网络层粘合(那是 `:auth-net-hilt`)。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-auth:1.0.0")
    // 至少还要引一个实现,目前只有:
    implementation("com.github.sanatowhite.sdk:auth-firebase:1.0.0")
}
```

⚠️ **只引 `:core-auth` 不会有任何登录功能可用**——它只是接口。`:auth-firebase` 提供 Hilt 绑定;不引它(也不自己写 `@Binds`)会在 `hiltJavaCompile` 阶段报 `MissingBinding`。这是刻意的:一个 no-op 默认实现会让"登录一直失败"变成运行期谜题,而不是编译期错误。

## AI 接入指南(可直接执行)

**要不要用这个模块**:消费方需要用户登录/会话状态时用。只需要网络栈或只需要设置存储的消费方不需要这个模块。

**接入步骤**:
1. 加坐标(见上方"一行接入"的两行——`:core-auth` 本身 + 一个实现)。
2. 注入 `AuthRepository`,监听 `authState: StateFlow<AuthState>` 决定 UI 该显示登录页还是已登录内容(`AuthState.Unknown` = 还不知道,配合 `splashScreen.setKeepOnScreenCondition`;`SignedOut`/`SignedIn` 才是确定态)。
3. 需要网络请求带上登录态的 token,加 `:auth-net-hilt`(见该模块 README)。
4. 需要现成登录 UI,加 `:feature-auth`(见该模块 README)。

**验证**:`./gradlew :app:hiltJavaCompileDebug` 编译通过即代表绑定聚合正确;运行时调用一次 `signInWithEmail`,确认 `authState` 翻转为 `SignedIn`。

**不要做的事**:见"已知限制"。

## 公开 API

- `AuthUser` — data class:`uid`/`email`/`displayName`/`photoUrl`/`phoneNumber`/`isEmailVerified`/`isAnonymous`/`providers: Set<AuthProvider>`/`createdAtMillis`/`lastSignInAtMillis`。
- `AuthProvider` — 枚举:`Password`/`Google`/`Apple`/`Phone`/`Anonymous`/`Unknown`。
- `AuthState` — sealed interface:`Unknown`(还不知道,冷启动初值)/`SignedOut(reason: SignOutReason)`/`SignedIn(user: AuthUser)`。
- `SignOutReason` — 枚举:`UserInitiated`/`SessionExpired`/`AccountDisabled`/`AccountDeleted`/`NeverSignedIn`。只是文案依赖,不是正确性依赖——拿不到具体原因时导航仍然正确,只是提示词退化成通用文案。
- `PhoneVerificationId` — `value class`,不透明句柄,包装手机验证会话 id。
- `PhoneAuthEvent` — sealed interface:`CodeSent`/`AutoRetrieved`(同设备自动填码,已直接登录)/`AutoRetrievalTimeout`/`Failed`。手机验证码流程结构性地是多次发射,唯一不返回 `AppResult` 的入口。
- `AuthRepository` — 接口:邮箱+密码(`signInWithEmail`/`signUpWithEmail`/`sendPasswordResetEmail`/`sendEmailVerification`/`updatePassword`)、Google/Apple(`signInWithGoogle(activity)`/`signInWithApple(activity)`)、手机验证码(`signInWithPhoneNumber`/`resendPhoneVerificationCode`/`confirmPhoneVerificationCode`)、会话管理(`signOut`/`deleteAccount`/`reauthenticateWithPassword`/`reloadUser`/`availableProviders`)。
- `AuthTokenProvider` — 接口:`currentIdToken(forceRefresh)`(挂起)/`cachedIdToken()`(非挂起,只读缓存,供 OkHttp Interceptor 用)/`invalidateToken()`。
- `SessionScopedStore` — `fun interface`,登出时清空用户态数据的 multibinding 挂钩(`@IntoSet`,同 `Set<Telemetry>`/`Set<AppInitializer>` 的套路)。
- `AuthError` — sealed class(`Throwable` 子类):`InvalidEmail`/`InvalidCredentials`/`UserNotFound`/`UserDisabled`/`EmailAlreadyInUse`/`WeakPassword`/`AccountExistsWithDifferentCredential`/`ProviderNotEnabled`/`InvalidPhoneNumber`/`InvalidVerificationCode`/`VerificationCodeExpired`/`Cancelled`/`NoCredentialAvailable`/`RequiresRecentLogin`/`NotSignedIn`/`TooManyRequests`/`Network`/`Unknown`。
- **testFixtures**:`FakeAuthRepository` — 纯内存实现(`AuthRepository` + `AuthTokenProvider`),`MutableStateFlow` 驱动,`Activity` 参数一律忽略,`nextResult`/`nextUnitResult`/`nextPhoneEvents`/`token` 可配置驱动测试场景。仓库里 `:core-data` 是唯一先例,这是第二个真的发布 `-test-fixtures.aar` 的模块。

## 已知限制 / 不要做的事

- **不要**给 `AuthRepository` 加 `signInAnonymously()`/`linkWithGoogle()`/`unlink()`/`fetchSignInMethodsForEmail()`/`updateEmail()`——这些是 v1 刻意排除的能力(账号链接、匿名升级、邮箱枚举都各自有一整套边界问题),需要时另开设计讨论,不要顺手加。
- **不要**把 `AuthError` 提升到 `:core-common`——领域错误跟着能力模块走(同 `:core-net` 的 `AppError`),不应该上升。
- **不要**新建 `:core-auth-hilt`——这个模块唯一的实现在 `:auth-firebase`,Hilt 绑定必须跟实现走,建一个只有空壳/`@Multibinds` 的 `-hilt` 模块是纯粹的清单同步负担。
