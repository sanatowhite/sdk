# :safemodekit

## 这是什么 / 不是什么

**安全模式（伪装启动页）**：开启后，App 的桌面图标换成一个伪装入口（比如「浏览器」），打开看到的是一个普通网页（默认百度首页）。只有做出**暗号手势**并通过**指纹/人脸或锁屏密码**验证，才会进入 App 的真实首页。App 一退到后台或手机锁屏，就自动重新上锁。

防的是两类场景：
- 别人拿到已解锁的手机后随手点开 App。
- 有人当面让你打开 App 看看。

发布坐标 `com.github.sanatowhite.sdk:safemodekit:<version>`。零内部模块依赖，只依赖 AndroidX（`biometric` / `appcompat` / `swiperefreshlayout`），纯 View 实现，不需要 Compose、Hilt 或任何 DI 框架。minSdk 24。

宿主要求：调用 `SafeMode.authenticate(...)` 的页面（通常是设置页）必须是 `FragmentActivity`（`AppCompatActivity` 即可）——这是 `androidx.biometric` 的要求。纯 Compose 的 App 把承载设置页的 Activity 从 `ComponentActivity` 换成 `AppCompatActivity`/`FragmentActivity` 即可，Compose 照常使用。

**不是**：
- **不能只认某一根手指。** Android 的 `BiometricPrompt` 只返回「成功/失败」，不告诉 App 是哪一根手指，所以手机上录入的任何指纹都能通过。真正的门槛是「暗号 + 验证」两道。
- **不是加密。** 它只挡住界面，不加密 App 的数据。被 root、被 adb 调试、被取证工具读盘时没有任何保护作用。
- **不提供设置页 UI。** 开关、时长选择要宿主自己画（每个 App 的设置页风格不同），SDK 只提供 API 和一段现成的说明文案。
- **不提供伪装图标和名称。** 由宿主自己准备，SDK 不模仿任何真实品牌。

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
    implementation 'com.github.sanatowhite.sdk:safemodekit:1.3.0'
}
```

## 使用说明（给最终用户看的行为）

| 场景 | 行为 |
|---|---|
| 开启安全模式 | 先验证身份 → 确认说明 → 桌面图标变成伪装入口。部分桌面会把图标从主屏幕移走，需要在应用列表里重新拖回来。 |
| 打开伪装入口 | 显示伪装网页，表现和普通浏览器一样：能刷新、能点链接、返回键回退网页历史、能截屏。唤起其他 App 的链接（如 `baiduboxapp://`、`intent://`）会被静默拦截。 |
| 暗号 | 在网页**顶部**往下拉，拉过刷新触发点（和正常下拉刷新的距离一样）后**按住不松手**，满设定时长（默认 1.2 秒）时手机轻震一下，松手 → 弹出「验证身份」。 |
| 松手太早 | 只是一次普通刷新，什么都不弹。 |
| 往回推到触发点以下 / 第二根手指按下 | 本次计时作废。 |
| 验证失败或取消 | 静默留在伪装页，没有任何提示。 |
| 设备既没录指纹也没设锁屏密码 | 只凭暗号放行（避免把用户锁在外面）；这种设备上宿主应禁止**开启**安全模式。 |
| 按 Home / 切到别的 App | 立即上锁，所有真实页面被关闭；回来看到的是伪装页。 |
| 锁屏 | 立即上锁，外跳放行也一并作废。 |
| 分享、用外部播放器打开、系统选文件等 | 宿主在跳出前调用了 `allowExternalTrip()` 的，10 分钟内回来不上锁。 |
| 通知 / 深链直达真实页面 | 锁定状态下被拦回伪装页。 |
| 关闭安全模式 | 先验证身份 → 桌面图标恢复。 |

**副作用**：上锁时会 `finish()` 所有真实页面，所以后台播放、息屏播放这类依赖页面存活的功能会随之停止。前台服务（下载等）不受影响。

## AI 接入指南（可直接执行）

**要不要用这个模块**：想让 App 在桌面上伪装成别的东西、打开先看到一个无害页面时用。只想给某个页面加指纹锁，直接用 `androidx.biometric` 就够了，不需要这个模块。

**接入步骤**：

1. **准备伪装图标和名称**（宿主资源），例如 `@mipmap/ic_launcher_decoy`、`@string/decoy_label`（「浏览器」）。

2. **manifest**：去掉首页 Activity 自己的 `LAUNCHER` intent-filter，改由两个 `activity-alias` 提供桌面入口。alias 的 `android:name` 按宿主 **namespace** 拼（不是 applicationId）：

   ```xml
   <activity android:name=".ui.MainActivity" android:exported="true" />

   <activity-alias
       android:name=".LauncherReal"
       android:enabled="true"
       android:exported="true"
       android:targetActivity=".ui.MainActivity">
       <intent-filter>
           <action android:name="android.intent.action.MAIN" />
           <category android:name="android.intent.category.LAUNCHER" />
       </intent-filter>
   </activity-alias>

   <activity-alias
       android:name=".LauncherDecoy"
       android:enabled="false"
       android:exported="true"
       android:icon="@mipmap/ic_launcher_decoy"
       android:roundIcon="@mipmap/ic_launcher_decoy"
       android:label="@string/decoy_label"
       android:targetActivity="io.sanato.appkit.safemode.DecoyActivity">
       <intent-filter>
           <action android:name="android.intent.action.MAIN" />
           <category android:name="android.intent.category.LAUNCHER" />
       </intent-filter>
   </activity-alias>
   ```
   `DecoyActivity` 本身由 SDK 的 manifest 声明，宿主不用再写。

3. **Application.onCreate()** 里调用一次：

   ```kotlin
   SafeMode.install(
       this,
       SafeModeConfig(
           homeActivity = MainActivity::class.java,
           realLauncherAlias = "com.example.app.LauncherReal",   // namespace + alias 名
           decoyLauncherAlias = "com.example.app.LauncherDecoy",
           // decoyUrl = "https://m.baidu.com",                  // 可选，默认就是这个
       ),
   )
   ```
   如果宿主对所有 Activity 统一加了 `FLAG_SECURE`，用 `SafeMode.isDecoy(activity)` 跳过伪装页——伪装页不能截屏、最近任务里没有缩略图，本身就是破绽。

4. **外跳调用点**：凡是 App 主动跳到外部、而且用户还要回来的地方，在 `startActivity` 之前调用 `SafeMode.allowExternalTrip()`：分享、外部播放器、系统选文件/选图、安装更新等。漏了的话，用户回来会被锁住重新验证（安全，只是麻烦）。

5. **设置页**（示例，按宿主自己的 UI 改）：

   ```kotlin
   // 开关
   fun onToggle(enable: Boolean) {
       if (enable && !SafeMode.canAuthenticate(this)) return // 提示用户先设锁屏密码
       val apply = {
           if (enable) {
               AlertDialog.Builder(this)
                   .setTitle("开启安全模式")
                   .setMessage(SafeMode.enableExplanation(this, getString(R.string.decoy_label)))
                   .setNegativeButton("取消", null)
                   .setPositiveButton("开启") { _, _ -> SafeMode.setEnabled(this, true) }
                   .show()
           } else {
               SafeMode.setEnabled(this, false)
           }
       }
       if (SafeMode.canAuthenticate(this)) SafeMode.authenticate(this, onSuccess = apply) else apply()
   }

   // 暗号按住时长
   val options = SafeMode.HOLD_DURATION_OPTIONS_MS
   val labels = options.map { "${SafeMode.formatSeconds(it)} 秒" }.toTypedArray()
   AlertDialog.Builder(this)
       .setTitle("暗号按住时长")
       .setSingleChoiceItems(labels, options.indexOf(SafeMode.holdDurationMs(this))) { dialog, which ->
           SafeMode.setHoldDurationMs(this, options[which])
           dialog.dismiss()
       }
       .show()
   ```
   已开启但设备验证不可用时（用户后来删了锁屏密码），仍要允许**关闭**，别把用户锁在开启状态。

**API 一览**

| API | 说明 |
|---|---|
| `install(application, config)` | 放在 `Application.onCreate()`，**无条件**调用（不要放在功能开关/远程配置后面：开启过安全模式后不再 install，伪装入口会打开即退出）。同一个 Application 重复调用会被忽略。启动时会按开关状态校正桌面入口。 |
| `isEnabled(context)` / `setEnabled(context, enabled)` | 读写开关并切换桌面入口；开启时同时解锁当前会话。 |
| `holdDurationMs(context)` / `setHoldDurationMs(context, ms)` | 暗号按住时长，夹紧在 500–5000ms，默认 `DEFAULT_HOLD_DURATION_MS`（1200）。下次打开伪装页生效。 |
| `HOLD_DURATION_OPTIONS_MS` | 推荐档位 `[500, 800, 1200, 1500, 2000, 3000]`。 |
| `formatSeconds(ms)` | `1200 → "1.2"`、`2000 → "2"`，设置页展示用。 |
| `allowExternalTrip()` | 跳出 App 前调用；5 秒内没真的离开就作废，最长 10 分钟，锁屏作废。 |
| `isDecoy(activity)` | 宿主全局处理跳过伪装页用。 |
| `canAuthenticate(context)` / `authenticate(activity, onSuccess, onFailOrCancel)` | 系统验证框，允许指纹/人脸（弱）+ 锁屏密码。 |
| `enableExplanation(context, decoyLabel)` | 开启前说明框的现成文案，含当前时长。 |

## 常见问题

- **开启后桌面上找不到图标了**：部分桌面切换 alias 时会把图标从主屏幕移走，去应用列表里找伪装名称，拖回主屏幕即可。
- **关闭后原图标没回来**：同上，在应用列表里找 App 原名。
- **升级到首个接入版本后，桌面图标消失一次**：宿主把 `LAUNCHER` 从首页 Activity 挪到 alias，组件名变了，桌面会当成新入口。属正常现象，提前告知用户。
- **下拉没震动**：要在网页**最顶部**往下拉，而且要拉过刷新圈出现的位置再多拉一点；系统关闭了「触摸反馈」时也不会震，但松手照样有效。
- **Android 10 及以下**：锁屏密码验证会打开系统的确认页面，SDK 已在验证前自动放行一次，不会因此误锁。

## 维护

- 改动前后必须跑：`./gradlew :safemodekit:testDebugUnitTest :safemodekit:apiCheck :safemodekit:lintDebug`
- 设计取舍见 `docs/adr/0015-safemodekit.md`；模块内规则见 `CLAUDE.md`。
