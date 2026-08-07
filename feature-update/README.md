# :feature-update

## 这是什么 / 不是什么

`:updatechecker` 的 Compose/Hilt 粘合层——检查更新对话框(下载进度/强制更新/校验失败等状态)+ `UpdateCheckHost` 一行接入的状态持有。`:feature-update` 和 `:updatechecker` 现在同属一个发布集,用 `project()` 依赖,不需要额外坐标替换。

**不是**:不做真正的下载/校验/安装逻辑(那些在 `:updatechecker`,零 Compose/Hilt 依赖),这个模块只是粘合。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:feature-update:1.0.0")
}
```

默认指向一个占位更新配置地址(`https://raw.githubusercontent.com/OWNER/REPO/main/update.json`)——`checkForUpdate()` 不会崩,只会稳定拿到 `UpdateResult.Error`,和 fork 出去当天的行为完全一致。真正接入前,提供一个可选 Hilt 绑定换成自己的地址:

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object MyUpdateConfigOverrideModule {
    @Provides
    fun override() = UpdateConfigOverride("https://raw.githubusercontent.com/OWNER/REPO/main/update.json")
}
```

JSON schema 见 `:updatechecker` 的 `UpdateConfigParser`/`UpdateInfo`。

## 挂进你的 Compose 树

```kotlin
setContent {
    UpdateCheckHost { onCheckForUpdates ->
        NavHost(navController, startDestination = Home) {
            composable<Home> { ... }
            settingsGraph(navController, onCheckForUpdates = onCheckForUpdates)   // 引了 :feature-settings 才传
        }
    }
}
```

`UpdateCheckHost` 持有跨屏幕的对话框状态、展示 `UpdateDialog`、UpToDate/Error 时弹 Toast——一行包住你自己的 `NavHost`;`onCheckForUpdates` 回调不知道也不需要知道谁触发它,不用 `:feature-settings` 也能自己接一个按钮调用。

## 公开 API

- `UpdateCheckHost(viewModel, content)` — 一行接入的状态持有 + 弹窗 + Toast。
- `UpdateViewModel` / `UpdateUiState` — 想自己接线(比如换 Toast 为 Snackbar)就直接用这两个 + `UpdateDialog`,跳过 `UpdateCheckHost`。
- `UpdateDialog(state, onDownload, onInstall, onDismiss)` — 无状态,不用 Hilt 也能直接用。
- `UpdateConfig` / `UpdateConfigOverride` — 更新配置地址的注入点,见上面"一行接入"。

## 已知限制 / 不要做的事

- 这个模块刻意不接入 `apiCheck`——理由同 `:core-ui`/`:feature-settings`/`:feature-feedback`。
- 不提供 `UpdateConfigOverride` 就一直停在占位地址上——这是刻意的"开箱即用但不做真事"默认值,不是 bug。
