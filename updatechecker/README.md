# :updatechecker

## 这是什么 / 不是什么

一个轻量级 Android 应用内更新检查库:请求一个远程 JSON,和当前 `versionCode` 比较,有新版本就让消费方展示更新弹窗,下载 APK、校验 SHA256、引导安装。发布坐标是 `com.github.sanatowhite.sdk:updatechecker:<version>`——现在和仓库里其余 18 个 SDK 模块同属一个统一发布集/同一个 tag(见 ADR 0008),不再是当年那个独立镜像仓库(`com.github.sanatowhite:version-check-sdk`,已废弃且对应的 tag 已删除,不再解析)。零内部模块依赖仍然是这个模块独有的硬约束(见下面的"四条铁律")。

**不是**:不含 UI(下载进度/更新弹窗由消费方自己用 `UpdateDownloadState`/`UpdateResult` 拼);不做认证;不依赖 OkHttp/Retrofit(刻意保持零第三方依赖,只用 `HttpURLConnection` + `org.json`);不依赖本仓库任何其他模块(硬约束,见下)。

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
    implementation 'com.github.sanatowhite.sdk:updatechecker:1.0.0' // 版本号对应仓库统一的 SDK tag(裸 semver,不带 v 前缀)
}
```

想一次引入多个模块并保持版本对齐,用 `:sdk-bom`(见根 README):`implementation(platform('com.github.sanatowhite.sdk:sdk-bom:1.0.0'))` 之后这里就不用再写版本号。

配套的静态发行仓库是 [sanatowhite/version_check](https://github.com/sanatowhite/version_check)——一个"轻量应用商店":推 release APK + 更新 JSON 上去,已装旧版的用户通过 `raw.githubusercontent.com` 拉取 JSON 得知新版本、下载链接和 SHA256。

## AI 接入指南(可直接执行)

**要不要用这个模块**:想要"应用内检查更新"能力,且不想自己搭一套下载/校验/安装流程时用。想要 Compose UI 而不是原生 `AlertDialog`,用 `:feature-update`(它内部就依赖这个模块,`UpdateCheckHost` 一行接入)。

**接入步骤(裸用这个模块,原生 UI)**:
1. 加坐标(见上方"独立引入"的两个代码块)。
2. 照抄本文件"API 用法"一节的三个代码块(检查更新 / 自动检查节流 / 弹窗下载校验安装)。
3. 把 `CONFIG_URL` 换成自己的更新配置 JSON 地址,JSON 字段按下面"远程 JSON 契约"表格来。

**验证**:`./gradlew :updatechecker:test` 通过;真机上指向一个真实可访问的测试 JSON,确认 `check()` 返回 `UpdateResult.Available`/`UpToDate`/`Error` 符合预期(伪造一个比当前 `versionCode` 大的值触发 `Available`)。

**不要做的事**:不要给这个模块加认证/token 刷新;不要换成 OkHttp/Retrofit(见下面"四条铁律")。

## 公开 API

- `UpdateChecker(context, configUrl)` / `UpdateChecker(context, configUrl, fetcher)` — `suspend fun check(): UpdateResult`。
- `UpdateResult` — sealed:`Available(info)` / `UpToDate` / `Error(message)`。
- `UpdateInfo` — `versionCode`/`versionName`/`apkUrl`/`sha256`/`releaseNotes`/`force`。
- `ConfigFetcher` — 接口,默认实现 `HttpConfigFetcher`;测试时可以自己实现注入。
- `Sha256Verifier.matches(file, expectedHex)`。
- `UpdateDownloader(context)`(**Phase 8 新增**)——`fun download(info): Flow<UpdateDownloadState>` 带进度的下载,`fun install(file)` 引导安装。旧的 `ApkDownloader` 是 internal 且无进度,新增这个类是为了给消费方一条能观察进度的公开路径,不是替换。
- `UpdateDownloadState`(**Phase 8 新增**)——sealed:`Idle`/`InProgress(downloaded, total)`/`Verifying(file)`/`ReadyToInstall(file)`/`Failed(reason)`。

## API 用法

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

如果想要带进度的下载(而不是用 `showUpdateDialog` 的一体化弹窗流程),用 Phase 8 新增的 `UpdateDownloader`:

```kotlin
UpdateDownloader(context).download(info).collect { state ->
    when (state) {
        is UpdateDownloadState.InProgress -> // state.downloaded / state.total
        is UpdateDownloadState.Verifying -> // SHA256 校验中
        is UpdateDownloadState.ReadyToInstall -> UpdateDownloader(context).install(state.file)
        is UpdateDownloadState.Failed -> // state.reason
        UpdateDownloadState.Idle -> {}
    }
}
```

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

下面每一步都是真实可执行的命令,把 `<占位符>` 换成具体值即可。

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

> **设计决定(不要重新提议):把上面这套步骤包装成脚本时,脚本本身不要迁移进本仓库。** 每个接入方按上面的步骤在自己的 app 仓库里各自维护一份发布脚本(改几个变量即可)。本仓库只负责客户端 SDK 和这份可执行的发布步骤文档,不维护发布侧的共享代码。

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

## 发布 SDK 新版本

这个模块不再单独打 tag——它和其余 18 个 SDK 模块共用同一个发布流程(`sdk-release.yml`,见 ADR 0008),发布新版本的步骤:

1. 改动完成、合并到 `main`,更新 `gradle/version.properties` 的 `sdkVersion=`。
2. 打裸 semver tag(不带 `v` 前缀,例如 `1.0.1`)并推送:`git tag 1.0.1 && git push origin 1.0.1`——这会触发 `sdk-release.yml`,一次性构建、验证、发布全部 19 个模块(含 `:updatechecker`)。
3. 消费方把依赖版本改成新 tag 即可(`com.github.sanatowhite.sdk:updatechecker:1.0.1`),JitPack 会在第一次被请求时自动构建该 tag。

## 经验教训 / 踩过的坑

这些是开发过程中真实踩过、已经修过的坑,记录下来避免以后(包括 AI)重新踩一遍或者"优化"回去:

- **FileProvider 不能直接用 `androidx.core.content.FileProvider` 本类,必须建一个专属子类。** 如果 SDK 的 manifest 里声明的 `<provider>` 直接用 `android:name="androidx.core.content.FileProvider"`,而宿主 app 自己往往也声明了一个同类名的 FileProvider(哪怕 authority 不同),manifest merger 会冲突报错装不上。解法是 SDK 自己定义一个空的子类 `UpdateCheckerFileProvider : FileProvider()`(见 `UpdateCheckerFileProvider.kt`),manifest 里用这个子类名 + 固定 authority `${applicationId}.versioncheck.fileprovider`,和宿主 app 自己的 FileProvider 类名、authority 都不会撞。**以后任何库要往宿主 app 里塞 FileProvider,都得走这个"专属子类"套路,不能图省事直接用官方类名。**
- **`UpdateCheckerFileProvider` 必须加 consumer Proguard 规则 `-keep`,否则宿主 app 开 R8 混淆后会找不到这个类。** 因为它只在 manifest 里通过类名反射实例化,代码里没有任何地方直接 `new` 它,R8 静态分析看不出它被用到,默认会被裁掉或改名。规则在 `consumer-rules.pro` 里,会随 `implementation` 依赖自动应用到消费方,不需要消费方自己配置。
- **下载完成广播在 Android 13+ 必须用 `RECEIVER_EXPORTED` 注册,但这意味着任何 app 都能发送伪造广播。** `DownloadManager` 的 `ACTION_DOWNLOAD_COMPLETE` 广播来自系统的 `com.android.providers.downloads` 进程(和宿主 app 不同 UID),Android 13+ 要求跨 UID 广播接收方显式声明 `RECEIVER_EXPORTED` 才能收到;但这也打开了"同设备任何恶意 app 可以发一条伪造的下载完成广播"的口子。**对策是不信任广播本身,拿到广播后立刻用 `DownloadManager.query()` 反查真实的下载状态(`STATUS_SUCCESSFUL`),状态不对就当没收到、继续等真正的广播**——再加上安装前必定校验 SHA256,两层防护叠加,伪造广播拿不到任何好处(见 `UpdateDialogPresenter.kt` / `UpdateDownloadReceiver.kt` 里的详细注释)。
- **JitPack 是"首次请求才现场构建"的,不是预先构建好的。** 打新 tag 推上去之后,第一次有人声明依赖该 tag 时 JitPack 才会拉取仓库现场编译,可能要等一两分钟,期间 Gradle sync 会报"找不到该依赖"看起来像失败,重试/等一下通常就好,不代表 tag 或配置有问题。
- **每个 app 在 `version_check` 仓库里只应该保留一个 APK。** 这不是本仓库的逻辑(发布脚本不在这里维护,见上面的设计决定),但作为契约的消费方/生产方双方都要知道:如果哪天新增了 app 的发布脚本却忘了清理旧 APK,`version_check` 仓库会无限增大——发布脚本里必须包含"推送新 APK 前先删掉该 app 前缀下的旧 APK"这一步。

## 已知限制 / 不要做的事(四条铁律)

这几条不是建议,是发布出去之后不可逆的约束——CI 有 `apiCheck`(`api/updatechecker.api` 快照对比)机械检查"只许新增,不许删改",但下面这几条它检查不到,只能靠开发者自己守:

1. **依赖内部模块数必须是 0**——这是"可发布模块不得依赖不可发布模块"这条通用规则(`verifyModuleGraph`)在这个模块上最严格的特例:不只是不能依赖不可发布模块,是压根不能有任何 `project()` 依赖,发布的 POM 不能带上消费方拿不到的坐标。
2. **零第三方依赖只有两个,且必须声明对**——`androidx.core:core-ktx`(`implementation`,见 ADR 0009 的 `internal FileProvider` 判例)和 `kotlinx-coroutines-android`(`api`,`UpdateDownloader.download(): Flow<...>` 是真泄漏)。不要"顺手"换成 OkHttp,那会给所有消费方强塞一个依赖。
3. **只 apply 两个纯叠加 mix-in 插件**(`sanato.android.library.published` + `sanato.api.check`),不 apply `sanato.android.library` 本身或任何其他 convention plugin——那些插件很可能被未来改动加上 Compose/`javax.inject`、改 `consumerProguardFiles` 甚至 `namespace`,全都会改变发布产物。前两个插件结构上做不到这件事(见 `SanatoPublishedLibraryConventionPlugin.kt` 的说明),这正是这条铁律能被遵守而不是被违反的原因。
4. **Java 字节码目标保持 11**——现在是每个发布模块的统一家规(仓库里只有不发布的 `:app` 是 17),消费方可能还在更旧的 AGP/JDK 上。

新增公开 API 之后的检查点(已走过一遍,记录在案供下次参考):

```bash
./gradlew :updatechecker:apiCheck   # 应该失败,报告"Public API of :updatechecker changed"
# 人工 review：diff 只能是新增的类/方法，不能有已存在签名被删除或修改
git diff updatechecker/api/updatechecker.api
./gradlew :updatechecker:apiDump    # 确认只有新增后，重新生成 golden 文件并提交
```

`consumer-rules.pro` 里那条 `UpdateCheckerFileProvider` 的 keep 规则同理不能动;`install()` 相关代码的 FileProvider authority 必须继续是 `${applicationId}.versioncheck.fileprovider`,不要与 `:app` 自己的 `${applicationId}.fileprovider`(反馈页附件用)混淆——两个是完全独立的 provider 声明。
