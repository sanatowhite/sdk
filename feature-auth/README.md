# :feature-auth

## 这是什么 / 不是什么

登录 UI:邮箱+密码、Google、Apple、手机短信验证码四种方式的完整 Route/Screen/ViewModel,加上会话管理(`AuthSessionHost` 统一处理登出清栈)和一个账号页。

**不是**:不含任何登录后端实现(那是 `:auth-firebase` 或其他 `AuthRepository` 实现,本模块只依赖 `:core-auth` 的接口)。不含网络层认证粘合(那是 `:auth-net-hilt`)。不提供账号链接(account linking)流程——`AuthError.AccountExistsWithDifferentCredential` 只落一条错误文案,不做专门的冲突解决 UI(v1 刻意排除,见 `:core-auth` README)。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-auth:1.0.0")
    implementation("com.github.sanatowhite.sdk:auth-firebase:1.0.0")
    implementation("com.github.sanatowhite.sdk:feature-auth:1.0.0")
}
```

## AI 接入指南(可直接执行)

**要不要用这个模块**:想要开箱即用的登录 UI 时加。只需要 `AuthRepository` 接口自己写 UI 的消费方不需要这个模块。

**接入步骤**:
1. 加坐标(见上方)。
2. 在自己的 `NavHost` 里挂 `authGraph()`,并用 `AuthSessionHost` 包住整个 `NavHost`:

```kotlin
AuthSessionHost(
    onSignedOut = { reason ->
        navController.navigate(Home) {   // 或你自己的 startDestination
            popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            launchSingleTop = true
        }
    },
) {
    NavHost(navController, startDestination) {
        composable<Home> { ... }
        authGraph(
            navController = navController,
            // AuthGraphRoute 是从 Home(或任何一个"账号"入口)push 进来的,不是
            // startDestination——登录成功只需要弹掉这个嵌套图自己的所有目的地,
            // 回到下面本来就在的那个屏幕,不需要再 navigate 一次。
            onSignedIn = { navController.popBackStack<AuthGraphRoute>(inclusive = true) },
        )
    }
}
```

3. **登录是门禁还是可选功能,由消费方决定,这个模块两种都支持**:
   - **可选功能(推荐默认,`:app` 这个模板本身就是这么接的)**:不接 `AuthEntryViewModel`。在 Home/Settings 随便一个"账号"入口里,未登录时 `navController.navigate(AuthGraphRoute)`,已登录时 `navController.navigate(AccountRoute)`——分支逻辑很小,直接写在消费方的导航层就行,不需要专门的 ViewModel。适合"登录只是解锁部分功能,大部分体验不需要账号"的产品形态。
   - **强制门禁**:用 `AuthEntryViewModel.signInRequired: StateFlow<Boolean?>` 决定启动目的地——三态起始页(同意 → 登录 → 首页)的合成逻辑属于消费方,不属于这个模块;这个模块只负责吐出"要不要登录"这一个信号,形状对齐 `:feature-settings` 的 `AppEntryViewModel.consentRequired`。这种接法下 `AuthGraphRoute` 才是 `NavHost` 的 `startDestination`,`onSignedIn` 才需要显式 `navigate(Home) { popUpTo(AuthGraphRoute) { inclusive = true } }`(因为没有"下面本来就在的屏幕"可以弹回去)。
4. `:feature-settings` 想加账号入口,自己传 `onNavigateToAccount = { /* 同上,按 isSignedIn 分支 */ }`,零新增依赖边。

**验证**:`./gradlew :app:hiltJavaCompileDebug` 编译通过;运行时走一遍邮箱注册 → 登出 → 邮箱登录,确认登出后自动清栈回到 `onSignedOut` 指定的目的地。

**不要做的事**:见"已知限制"。

## 公开 API

- `Routes.kt` — `AuthGraphRoute`/`SignInRoute`/`SignUpRoute`/`ForgotPasswordRoute`/`PhoneNumberRoute`/`PhoneCodeRoute(verificationId, phoneNumberMasked)`/`AccountRoute`,类型安全导航路由。
- `fun NavGraphBuilder.authGraph(navController, config, onSignedIn)` — 挂载入口,跨 feature 依赖走可空回调,同 `settingsGraph` 的模式。
- `AuthPageConfig` — 控制四种登录方式是否展示的"意图"配置,和 `AuthRepository.availableProviders()` 的"运行期实际可用"求交后才决定按钮是否出现。
- `AuthSessionHost(onSignedOut, content)` — 唯一负责登出后清栈导航的组件;`SignedIn → SignedOut` 的每一次转换(无论是用户主动登出还是服务端作废会话)都从这里触发。
- `AuthEntryViewModel.signInRequired: StateFlow<Boolean?>` — 冷启动是否需要走登录页的信号,`null` = 还不知道。
- 每个屏幕的 `XxxRoute`(Hilt 入口)+ `XxxScreen`(无状态,可直接用于自定义状态管理或截图测试)。

## 已知限制 / 不要做的事

- **手机号必须已经是 E.164 格式**(如 `+14155552671`)——这个模块不提供国家区号选择器,是刻意的 v1 简化(见 `PhoneNumberViewModel` KDoc)。
- **手机验证码重发走"返回上一屏重新发送"**,不是原地重发——`AuthRepository.resendPhoneVerificationCode` 需要原始手机号,而路由参数刻意不携带原始手机号(只携带脱敏展示串),两者冲突时选择了不在路由里放敏感信息。
- **`PhoneCodeRoute.verificationId` 活不过进程死亡**——它是 Firebase 内存态验证会话的句柄,进程被杀后即使这个字符串本身通过 Bundle 恢复了,底层验证会话也已失效。`confirmPhoneVerificationCode` 会失败(通常是 `VerificationCodeExpired`),UI 侧的"返回上一屏"路径已经能优雅处理这种情况,不需要额外的"会话丢失"事件。
- **不要**给这个模块加账号链接(account-linking)UI——v1 刻意排除,见 `:core-auth` README。
- **不要**在 `authGraph` 里加 `onSignedOut` 回调——登出导航统一由 `AuthSessionHost` 负责,拆成两条路径会在用户点"登出"时产生双重导航竞态。
- **`SignInScreen` 没有返回箭头/取消按钮**——它是按"启动门禁场景下的 startDestination,系统返回键就是退出 App"设计的。如果按上面"可选功能"接法把 `AuthGraphRoute` 当成从别处 push 进来的普通目的地,取消登录目前只能靠系统返回键/手势(NavController 默认行为能正常弹回上一屏,只是 UI 上没有一个明显的"×"或"←"提示用户可以这么做)。想要一个可见的取消入口,消费方可以在 `SignInRoute` 外面自己包一层 `Scaffold`/`TopAppBar`,或者等后续版本给 `SignInScreen`/`SignInRoute` 加一个可空的 `onCancel` 参数。
- Roborazzi 截图基线尚未录制(需要 CI 环境的 `screenshot-record.yml` 手动触发)——`SignInScreen`/`PhoneCodeScreen` 是最值得录的两张(分别覆盖"全部登录方式"和"填码+倒计时"两种复杂布局),留作后续 PR。
