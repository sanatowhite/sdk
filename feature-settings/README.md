# :feature-settings

## 这是什么 / 不是什么

标准应用页面集合:设置页、关于页、隐私政策/用户协议(纯 Markdown 展示)、首启同意流程、What's New 弹窗。Screen(无状态,可截图测试)/ Route(绑 `hiltViewModel()`)双层拆分——不用 Hilt 也能直接用 Screen 层。

**不是**:不含反馈页(在 `:feature-feedback`)、不含开源许可页(在 `:feature-licenses`)、不含更新检查(在 `:feature-update`)——这三个各自有独一无二的接入成本(FileProvider、AboutLibraries 插件生成物、`:updatechecker` 依赖),拆开是为了让消费方自由排列组合,不被迫多装东西。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:feature-settings:1.0.0")
}
```

默认自带 Hilt 装配(`core-data-hilt` + `core-common-hilt` 已经在依赖图里),`@HiltAndroidApp` 的 `Application` 挂进 Navigation 就能用,不需要自己写任何 `@Module`。

## AI 接入指南(可直接执行)

**要不要用这个模块**:需要设置/关于/隐私政策/用户协议/首启同意/What's New 里任意一个页面时加——这是一个整体,不能只要其中一个页面。

**接入步骤**:
1. 加坐标(见上方)。
2. `Application` 用 Hilt(`@HiltAndroidApp`),因为 `SettingsRoute`/`AboutRoute` 等都是绑 `hiltViewModel()` 的 Route 层。
3. 按下面"挂进导航"的三个代码块,把 `settingsGraph(...)` 挂进自己的 `NavHost`,`AppEntryViewModel` 接进启动页逻辑,`WhatsNewRoute` 挂进首页。
4. `StandardPagesContent` 的三个 `@RawRes` 字段按需传:没有隐私政策/用户协议/更新日志就传 `null`(默认值),对应入口自动隐藏,不会崩。
5. 想要设置页里出现"反馈"/"许可"/"检查更新"行,分别加 `:feature-feedback`/`:feature-licenses`/`:feature-update` 并传对应的 `onNavigateToFeedback`/`onNavigateToLicenses`/`onCheckForUpdates` 回调;不加对应模块就不传,那一行自动不显示。

**验证**:`./gradlew :app:assembleDebug` 编译通过;运行时从首页进设置页,确认能看到主题切换/语言(如果传了 `supportedLanguageTags`)/隐私政策等行,且传 `null` 的项确实不显示。

**不要做的事**:见下面"已知限制"——不要指望它认识 `:feature-feedback`/`:feature-licenses`/`:feature-update` 的具体类型(全靠可空回调解耦,不要试图直接 import 那三个模块的类型进这里)。

## 挂进导航

```kotlin
NavHost(navController, startDestination = Home) {
    composable<Home> { ... }
    settingsGraph(
        navController = navController,
        content = StandardPagesContent(
            privacyPolicyRawRes = R.raw.privacy_policy,   // null = 不显示这一行
            termsOfServiceRawRes = R.raw.terms_of_service,
            changelogRawRes = R.raw.changelog,
        ),
        onNavigateToFeedback = { navController.navigate(FeedbackRoute) },   // 引了 :feature-feedback 才传
        onNavigateToLicenses = { navController.navigate(LicensesRoute) },   // 引了 :feature-licenses 才传
        onCheckForUpdates = { updateViewModel.checkForUpdate() },          // 引了 :feature-update 才传
        onConsentAccepted = { navController.navigate(Home) { popUpTo(ConsentRoute) { inclusive = true } } },
    )
}
```

首启同意流程:

```kotlin
val entryViewModel: AppEntryViewModel = hiltViewModel()
val consentRequired by entryViewModel.consentRequired.collectAsStateWithLifecycle()
// consentRequired == null 时还没读出来,建议配合 splashScreen.setKeepOnScreenCondition 卡住启动画面
val startDestination = consentRequired?.let { if (it) ConsentRoute else Home }
```

What's New 弹窗(挂在首页/入口页,不需要导航):

```kotlin
WhatsNewRoute(content = StandardPagesContent(changelogRawRes = R.raw.changelog))
```

## 公开 API

- `SettingsPageConfig(supportedLanguageTags)` / `StandardPagesContent(privacyPolicyRawRes, termsOfServiceRawRes, changelogRawRes)` — 两个配置数据类,字段为 `null` 就隐藏对应入口/功能,库侧不做任何硬编码假设。
- `settingsGraph(navController, content, config, onNavigateToFeedback, onNavigateToLicenses, onCheckForUpdates, onConsentAccepted)` — 挂进消费方 `NavHost` 的扩展函数,四个跨 feature 回调全部可空。
- `SettingsRoute` / `AboutRoute` / `PrivacyPolicyRoute` / `TermsOfServiceRoute` / `ConsentRoute` — `@Serializable data object` 路由类型。
- `AppEntryViewModel` — 首启同意判定,`consentRequired: StateFlow<Boolean?>`(构造时算好、`stateIn` 一次,不是每次调用新建流)。
- `WhatsNewRoute(content, viewModel)` — 挂在首页的 What's New 弹窗入口,`content.changelogRawRes == null` 时整个逻辑不触发。
- `SettingsScreen` / `AboutScreen` / `LegalDocScreen` / `ConsentScreen` — 无状态 Composable,不用 Hilt 也能直接用(自己接数据/回调)。

## 已知限制 / 不要做的事

- 想换掉 DataStore 存储实现?排掉 `core-data-hilt`(`exclude(group = "com.github.sanatowhite.sdk", module = "core-data-hilt")`),自己写一条 `@Binds ... : UserSettingsRepository`——见 `core-data-hilt/README.md`。
- `LocaleManager`(应用内语言切换)需要宿主 `Activity` 继承 `AppCompatActivity`,不是普通 `ComponentActivity`。
- 这个模块和 `:core-ui` 一样刻意不接入 `apiCheck`——公开签名里全是 `@Composable` 函数,Compose 编译器注入的参数会随版本漂移,API 稳定性改由 consumer-smoke 兜底。
- 52 个字符串统一加 `appkit_` 前缀 + `resourcePrefix = "appkit_"`,想改文案/加语言,在消费方自己的 `values*/strings.xml` 里声明同名 key 覆盖即可,不需要改这个模块的源码。
