# version-check-sdk

一个轻量级 Android 应用内更新检查库：请求一个远程 JSON,和当前 `versionCode` 比较,有新版本就弹出更新弹窗,下载 APK 并校验 SHA256 后引导安装。

配套的静态发行仓库是 [sanatowhite/version_check](https://github.com/sanatowhite/version_check) —— 一个"轻量应用商店":每个接入的 app 把 release APK 和一份更新 JSON 推到这个仓库,已装旧版的用户通过 `raw.githubusercontent.com` 拉取 JSON 得知新版本、下载链接和 SHA256。本 SDK 只负责**客户端读取和消费**这份 JSON;下面同时说明如何在你自己的发布流程里**生产**这份 JSON 并推送到该仓库。

## 模块结构

```
version-check-sdk/
└── updatechecker/     -- 唯一的 library module,namespace io.sanato.updatechecker
```

## 接入

### 1. 添加 JitPack 仓库

在 `settings.gradle` 的 `dependencyResolutionManagement.repositories` 里加上 JitPack:

```groovy
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

### 2. 添加依赖

在需要更新检查的 app module 的 `build.gradle` 里:

```groovy
dependencies {
    implementation 'com.github.sanatowhite:version-check-sdk:v1.0.2'
}
```

版本号对应本仓库的 git tag(见下方「发布 SDK 新版本」)。

### 3. 权限与 Manifest

SDK 自带的 `AndroidManifest.xml` 会被自动合并进宿主 app,包含:

- `INTERNET` —— 拉取更新 JSON。
- `REQUEST_INSTALL_PACKAGES` —— Android 8+ 上引导安装下载好的 APK。
- 一个内部 `FileProvider`(`io.sanato.updatechecker.UpdateCheckerFileProvider`),authority 固定为 `${applicationId}.versioncheck.fileprovider`。**这是 SDK 自己独立注册的 authority,和宿主 app 自己的 FileProvider(如 `${applicationId}.fileprovider`)不会冲突**,接入时不需要在宿主 app 里做任何额外的 FileProvider 配置。

宿主 app 不需要手动声明以上任何一项,正常 Gradle 构建会自动 manifest merge。

## API 用法

全部 API 都在 `io.sanato.updatechecker` 包下,核心入口是 `UpdateChecker`。

### 检查更新(协程,挂起函数)

```kotlin
lifecycleScope.launch {
    val result = UpdateChecker(context, CONFIG_URL).check()
    when (result) {
        is UpdateResult.Available -> UpdateChecker.showUpdateDialog(activity, result.info)
        is UpdateResult.UpToDate -> { /* 已是最新版本 */ }
        is UpdateResult.Error -> { /* 网络/JSON 错误,result.message 是原因 */ }
    }
}
```

- `check()` 内部会:请求 `configUrl` → 解析 JSON → 用 `PackageInfoCompat.getLongVersionCode` 读取当前 app 的 `versionCode` → 和远程 `versionCode` 比较(严格大于才算有更新)。
- 全程跑在 `Dispatchers.IO`,可以直接在主线程协程里调用。
- 任何异常(网络失败、JSON 格式错误、字段缺失)都会被捕获并转成 `UpdateResult.Error(message)`,不会抛出。

### 每日一次的自动检查节流

```kotlin
if (UpdateChecker.shouldAutoCheck(context)) {
    // 调用 check()
}
```

`check()` 成功返回后(无论 Available/UpToDate,包括 Error 之前也会先标记)会记一次"今天已检查"(`SharedPreferences`,按本地时区的 `yyyy-MM-dd` 比较),`shouldAutoCheck` 用来判断今天是否还没查过。典型用法是在启动页/首页 `onCreate` 里做自动检查,在设置页的"检查更新"按钮里跳过这个节流、无条件调用 `check()`。

### 弹窗、下载、校验、安装

```kotlin
UpdateChecker.showUpdateDialog(activity, info)
```

`showUpdateDialog` 之后的整条链路都是 SDK 内部自动完成的,调用方不需要再处理:

1. 弹出 `AlertDialog`,标题带版本号,正文是 `info.releaseNotes`。`info.force == true` 时弹窗不可取消(拦截返回键、隐藏"稍后提醒"按钮)。
2. 用户点"立即更新" → 若 Android 8+ 且未授权"安装未知来源应用",先跳转系统设置页申请该权限;否则用 `DownloadManager` 下载 APK 到 `getExternalFilesDir("apk_updates")`。
3. 下载完成广播到达后,**不直接信任广播**,而是用 `DownloadManager.query()` 二次确认下载状态确实是 `STATUS_SUCCESSFUL`(因为 Android 13+ 要求该广播用 `RECEIVER_EXPORTED` 注册,理论上任何 app 都能发送伪造广播)。
4. 用 `Sha256Verifier` 校验下载文件的 SHA256 是否匹配 `info.sha256`,不匹配则 Toast 提示校验失败、不安装。
5. 校验通过后,通过 SDK 自己的 `FileProvider` 生成 `content://` URI,发起 `ACTION_VIEW` 安装 Intent。

## 数据模型

### `UpdateInfo`

```kotlin
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val sha256: String,
    val releaseNotes: String,
    val force: Boolean
)
```

### `UpdateResult`(sealed class)

- `UpdateResult.Available(info: UpdateInfo)`
- `UpdateResult.UpToDate`
- `UpdateResult.Error(message: String)`

## 远程 JSON 契约

`UpdateConfigParser` 要求远程 JSON **必须**包含以下字段,缺一个就会抛 `MalformedConfigException`(被 `check()` 捕获并转成 `UpdateResult.Error`):

| 字段 | 类型 | 必需 | 说明 |
|---|---|---|---|
| `versionCode` | number(Long) | ✅ | 用于和本地 `versionCode` 比较 |
| `versionName` | string | ✅ | 展示在更新弹窗标题里 |
| `apkUrl` | string | ✅ | APK 直链,`DownloadManager` 直接下载这个 URL |
| `sha256` | string | ✅ | APK 的 SHA-256(hex,大小写不敏感),下载完成后强制校验 |
| `force` | boolean | ✅ | `true` = 强制升级,弹窗不可取消 |
| `releaseNotes` | string | ❌(默认空串) | 更新弹窗正文,建议中文用户可读的更新说明 |

示例:

```json
{
  "versionCode": 8,
  "versionName": "1.1.0",
  "apkUrl": "https://raw.githubusercontent.com/sanatowhite/version_check/main/sanato-diary-v1.1.0-code8-release.apk",
  "sha256": "3f9a...",
  "releaseNotes": "v1.1.0 更新内容\n- 新增私密日记转换\n- 修复若干问题",
  "force": false
}
```

## 与 sanatowhite/version_check 配合实现更新分发

`version_check` 仓库本身没有任何服务端逻辑,就是一个存放"每个 app 的更新 JSON + release APK"的 git 仓库,靠 `raw.githubusercontent.com` 当免费 CDN 用。整条链路:

```
宿主 app 发布新版本
  → 打 release APK
  → 生成一份符合上表字段的 JSON
  → 把 JSON 和 APK 推到 sanatowhite/version_check 仓库根目录
  → 宿主 app(所有已装旧版本的用户)启动时用本 SDK 请求这份 JSON 的 raw 链接
  → 发现 versionCode 更大 → 弹窗 → 下载 → 校验 → 安装
```

### 命名约定

`version_check` 仓库是**扁平结构**,同一目录下混放多个 app 的产物,靠文件名前缀区分:

- 更新 JSON:`<app-slug>_update_version.json`(例如 `sanato-diary_update_version.json`)
- APK:`<app-slug>-v<versionName>-code<versionCode>-release.apk`(例如 `sanato-diary-v1.1.0-code8-release.apk`)

`<app-slug>` 每个接入方自己定,只要在自己的发布脚本里固定下来即可(不同 app 的 slug 不同,互不冲突)。

### 每个 app 在仓库里只保留一个 APK

`version_check` 只是免费的 raw 文件托管,不是真正的对象存储,**同一个 app 的历史 APK 不需要留档**——旧版本用户升级后旧包就没有意义了,留着只会让仓库越来越大、clone 越来越慢。所以每次发布新版本时,推送新 APK 的**同时要删除该 app 之前推送的旧 APK**(按 `<app-slug>-v*-release.apk` 前缀匹配,只删自己 app 的,不动其它 app 的文件),仓库里始终保持"每个 app 恰好一个 APK + 一个 JSON"。

### 更新源仓库地址

- 网页 / clone:https://github.com/sanatowhite/version_check
- push 用 SSH remote:`git@github.com:sanatowhite/version_check.git`
- 客户端(本 SDK)实际请求的下载地址,规律是 `https://raw.githubusercontent.com/sanatowhite/version_check/main/<文件名>`——`git@github.com` 是**运维/发布方**推送用的地址,`raw.githubusercontent.com` 是**客户端 SDK**读取用的地址,两者指向同一个仓库,不要混淆。

### 完整发布步骤(AI 可以直接照抄执行,不依赖任何外部脚本)

下面每一步都是真实可执行的命令,把 `<占位符>` 换成具体值即可。这套步骤和 Sanato Diary 在 `tools/release/publish_update.sh` 里的自动化是同一套逻辑,写在这里是为了让任何接入了本 SDK 的新 app、在没有那个脚本的情况下,AI 也能照着这份 README 独立完成一次完整发布。

**准备好这几个值:**

- `APP_SLUG`:这个 app 自己定的短标识,一旦定下来以后每次发布都要用同一个(例如 `sanato-diary`、`fenfenbo`)。
- `VERSION_NAME` / `VERSION_CODE`:这次要发布的版本号,通常从宿主 app 的 `build.gradle`(`versionName` / `versionCode`)里读。
- `RELEASE_APK`:已经签名打好的 release APK 本地路径。
- `CHANGELOG_TEXT`:给用户看的中文更新说明。
- `FORCE`:是否强制升级(`true`/`false`),默认 `false`,只有重大安全修复等场景才设 `true`。

**1. clone 或更新本地对 `version_check` 的检出:**

```bash
# 第一次:
git clone git@github.com:sanatowhite/version_check.git /path/to/version_check_checkout
# 已经 clone 过:
cd /path/to/version_check_checkout
git fetch origin
git checkout main
git reset --hard origin/main   # 保证和远端一致,避免本地残留脏状态
```

**2. 算 release APK 的 SHA256(客户端安装前会校验这个值,必须准确):**

```bash
shasum -a 256 "<RELEASE_APK>" | awk '{print $1}'
```

**3. 删除这个 app 之前发布过的旧 APK(仓库里每个 app 只保留一个 APK,避免无限膨胀):**

```bash
cd /path/to/version_check_checkout
for f in "<APP_SLUG>"-v*-release.apk; do
  [ -e "$f" ] && git rm -q "$f"
done
```

只删 `<APP_SLUG>-v*-release.apk` 前缀匹配的文件,不要动其他 app 的产物。

**4. 按命名约定复制新 APK 进去:**

```bash
cp "<RELEASE_APK>" "/path/to/version_check_checkout/<APP_SLUG>-v<VERSION_NAME>-code<VERSION_CODE>-release.apk"
```

**5. 生成 / 覆盖这个 app 的更新配置 JSON**(文件名固定不变,所以直接覆盖写就天然只有一份,不需要额外删除旧 JSON):

```bash
cat > "/path/to/version_check_checkout/<APP_SLUG>_update_version.json" <<EOF
{
  "versionCode": <VERSION_CODE>,
  "versionName": "<VERSION_NAME>",
  "apkUrl": "https://raw.githubusercontent.com/sanatowhite/version_check/main/<APP_SLUG>-v<VERSION_NAME>-code<VERSION_CODE>-release.apk",
  "sha256": "<第2步算出来的sha256>",
  "releaseNotes": "<CHANGELOG_TEXT>",
  "force": <FORCE>
}
EOF
```

字段必须齐全(`versionCode`/`versionName`/`apkUrl`/`sha256`/`force` 缺一个客户端就会解析失败),`releaseNotes` 里的换行、引号注意做 JSON 转义(如果用脚本生成,优先用 `python3 -c "import json,sys; print(json.dumps(...))"` 或任意 JSON 库拼,不要手写字符串拼接,容易转义出错)。

**6. 提交并推送(这一步是真实对外发布,推送后所有装了旧版本的用户下次检查更新就会看到——执行前必须已经拿到用户对版本号/changelog/是否 force 的明确确认):**

```bash
cd /path/to/version_check_checkout
git add "<APP_SLUG>-v<VERSION_NAME>-code<VERSION_CODE>-release.apk" "<APP_SLUG>_update_version.json"
git commit -m "Publish <APP_SLUG> <VERSION_NAME>"
git push origin main
```

**7. 验证发布是否生效**(raw.githubusercontent.com 有缓存,推送后可能要等几十秒到几分钟才刷新):

```bash
curl -s "https://raw.githubusercontent.com/sanatowhite/version_check/main/<APP_SLUG>_update_version.json"
```

确认返回的 `versionCode`/`versionName`/`sha256` 和这次发布的一致,再告诉用户"已发布,下载链接是 `https://raw.githubusercontent.com/sanatowhite/version_check/main/<APP_SLUG>-v<VERSION_NAME>-code<VERSION_CODE>-release.apk`"。

> **设计决定(2026-07-30 讨论过,不要重新提议):把上面这套步骤包装成脚本时,脚本本身不要迁移进本仓库。**
> 曾经讨论过把 Sanato Diary 的 `tools/release/publish_update.sh` 通用化后迁到本 SDK 仓库、让所有接入方共用同一份脚本,最终决定**不迁移**——每个接入方按上面的步骤在自己的 app 仓库里各自维护一份发布脚本(改几个变量即可)。本仓库只负责客户端 SDK 和这份可执行的发布步骤文档,不维护发布侧的共享代码。

### 宿主 app 侧的最小接入示例

```kotlin
object UpdateCheckConfig {
    const val CONFIG_URL =
        "https://raw.githubusercontent.com/sanatowhite/version_check/main/<app-slug>_update_version.json"
}

// 首页启动时自动检查(每天最多一次)
if (UpdateChecker.shouldAutoCheck(this)) {
    lifecycleScope.launch {
        val result = UpdateChecker(this@HomeActivity, UpdateCheckConfig.CONFIG_URL).check()
        if (result is UpdateResult.Available) {
            UpdateChecker.showUpdateDialog(this@HomeActivity, result.info)
        }
    }
}
```

## 发布 SDK 新版本(维护本仓库时)

JitPack 直接从 git tag 构建,发布新版本不需要额外的 CI/发布步骤:

1. 改动完成、合并到 `main`。
2. 打 tag:`git tag v1.0.3 && git push origin v1.0.3`(tag 名要以 `v` 开头,和 `build.gradle` 里的依赖版本号对应)。
3. 消费方把依赖版本改成新 tag(如 `com.github.sanatowhite:version-check-sdk:v1.0.3`)即可,JitPack 会在第一次被请求时自动构建该 tag。

## 测试

```bash
./gradlew :updatechecker:test
```

单元测试用 JUnit4 + Robolectric,覆盖 `UpdateConfigParser`、`VersionCompare`、`Sha256Verifier`、`CurrentVersionReader`、`UpdateCheckPrefs`、`ApkDownloader` 等纯逻辑部分。

## 经验教训 / 踩过的坑

这些是开发过程中真实踩过、已经修过的坑,记录下来避免以后(包括 AI)重新踩一遍或者"优化"回去:

- **FileProvider 不能直接用 `androidx.core.content.FileProvider` 本类,必须建一个专属子类。** 如果 SDK 的 manifest 里声明的 `<provider>` 直接用 `android:name="androidx.core.content.FileProvider"`,而宿主 app 自己往往也声明了一个同类名的 FileProvider(哪怕 authority 不同),manifest merger 会冲突报错装不上。解法是 SDK 自己定义一个空的子类 `UpdateCheckerFileProvider : FileProvider()`(见 `UpdateCheckerFileProvider.kt`),manifest 里用这个子类名 + 固定 authority `${applicationId}.versioncheck.fileprovider`,和宿主 app 自己的 FileProvider 类名、authority 都不会撞。**以后任何库要往宿主 app 里塞 FileProvider,都得走这个"专属子类"套路,不能图省事直接用官方类名。**
- **`UpdateCheckerFileProvider` 必须加 consumer Proguard 规则 `-keep`,否则宿主 app 开 R8 混淆后会找不到这个类。** 因为它只在 manifest 里通过类名反射实例化,代码里没有任何地方直接 `new` 它,R8 静态分析看不出它被用到,默认会被裁掉或改名。规则在 `consumer-rules.pro` 里,会随 `implementation` 依赖自动应用到消费方,不需要消费方自己配置。
- **下载完成广播在 Android 13+ 必须用 `RECEIVER_EXPORTED` 注册,但这意味着任何 app 都能发送伪造广播。** `DownloadManager` 的 `ACTION_DOWNLOAD_COMPLETE` 广播来自系统的 `com.android.providers.downloads` 进程(和宿主 app 不同 UID),Android 13+ 要求跨 UID 广播接收方显式声明 `RECEIVER_EXPORTED` 才能收到;但这也打开了"同设备任何恶意 app 可以发一条伪造的下载完成广播"的口子。**对策是不信任广播本身,拿到广播后立刻用 `DownloadManager.query()` 反查真实的下载状态(`STATUS_SUCCESSFUL`),状态不对就当没收到、继续等真正的广播**——再加上安装前必定校验 SHA256,两层防护叠加,伪造广播拿不到任何好处(见 `UpdateDialogPresenter.kt` / `UpdateDownloadReceiver.kt` 里的详细注释)。
- **JitPack 是"首次请求才现场构建"的,不是预先构建好的。** 打新 tag 推上去之后,第一次有人声明依赖该 tag 时 JitPack 才会拉取仓库现场编译,可能要等一两分钟,期间 Gradle sync 会报"找不到该依赖"看起来像失败,重试/等一下通常就好,不代表 tag 或配置有问题。
- **每个 app 在 `version_check` 仓库里只应该保留一个 APK。** 这不是本仓库的逻辑(发布脚本不在这里维护,见上面的设计决定),但作为契约的消费方/生产方双方都要知道:如果哪天新增了 app 的发布脚本却忘了清理旧 APK,`version_check` 仓库会无限增大——发布脚本里必须包含"推送新 APK 前先删掉该 app 前缀下的旧 APK"这一步。
