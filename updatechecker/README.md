# :updatechecker

## 这是什么 / 不是什么

一个轻量级 Android 应用内更新检查库:请求一个远程 JSON,和当前 `versionCode` 比较,有新版本就让消费方展示更新弹窗,下载 APK、校验 SHA256、引导安装。**唯一已经真正独立发布到 JitPack 的模块**(`com.github.sanatowhite:version-check-sdk`),模板里其他模块都还没有单独发布。

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
    implementation 'com.github.sanatowhite:version-check-sdk:v1.0.2' // 版本号对应 git tag
}
```

配套的静态发行仓库是 [sanatowhite/version_check](https://github.com/sanatowhite/version_check)——一个"轻量应用商店":推 release APK + 更新 JSON 上去,已装旧版的用户通过 `raw.githubusercontent.com` 拉取 JSON 得知新版本、下载链接和 SHA256。

## 公开 API

- `UpdateChecker(context, configUrl)` / `UpdateChecker(context, configUrl, fetcher)` — `suspend fun check(): UpdateResult`。
- `UpdateResult` — sealed:`Available(info)` / `UpToDate` / `Error(message)`。
- `UpdateInfo` — `versionCode`/`versionName`/`apkUrl`/`sha256`/`releaseNotes`/`force`。
- `ConfigFetcher` — 接口,默认实现 `HttpConfigFetcher`;测试时可以自己实现注入。
- `Sha256Verifier.matches(file, expectedHex)`。
- `UpdateDownloader(context)`(**Phase 8 新增**)——`fun download(info): Flow<UpdateDownloadState>` 带进度的下载,`fun install(file)` 引导安装。旧的 `ApkDownloader` 是 internal 且无进度,新增这个类是为了给消费方一条能观察进度的公开路径,不是替换。
- `UpdateDownloadState`(**Phase 8 新增**)——sealed:`Idle`/`InProgress(downloaded, total)`/`Verifying(file)`/`ReadyToInstall(file)`/`Failed(reason)`。

## 已知限制 / 不要做的事(四条铁律)

这几条不是建议,是发布出去之后不可逆的约束——CI 有 `apiCheck`(`api/updatechecker.api` 快照对比)机械检查"只许新增,不许删改",但下面这几条它检查不到,只能靠开发者自己守:

1. **依赖内部模块数必须是 0**——一旦 `implementation(project(":core-xxx"))`,`JITPACK=true` 的 sdkOnly 构建会直接崩(那些模块被 gate 掉了),且发布的 POM 会带消费方拿不到的坐标。
2. **保持零第三方依赖**(`HttpURLConnection` + `org.json`)——不要"顺手"换成 OkHttp,那会给所有消费方强塞一个依赖。
3. **不套用 build-logic 的 convention plugin**——那些插件很可能被未来改动加上 Compose/`javax.inject`、改 `consumerProguardFiles` 甚至 `namespace`,全都会改变发布产物。保持独立最小构建文件。
4. **Java 字节码目标保持 11**(仓库其余模块是 17)——消费方可能还在更旧的 AGP/JDK 上。

新增公开 API 之后的检查点(已走过一遍,记录在案供下次参考):

```bash
./gradlew :updatechecker:apiCheck   # 应该失败,报告"Public API of :updatechecker changed"
# 人工 review：diff 只能是新增的类/方法，不能有已存在签名被删除或修改
git diff updatechecker/api/updatechecker.api
./gradlew :updatechecker:apiDump    # 确认只有新增后，重新生成 golden 文件并提交
```

`consumer-rules.pro` 里那条 `UpdateCheckerFileProvider` 的 keep 规则同理不能动;`install()` 相关代码的 FileProvider authority 必须继续是 `${applicationId}.versioncheck.fileprovider`,不要与 `:app` 自己的 `${applicationId}.fileprovider`(反馈页附件用)混淆——两个是完全独立的 provider 声明。
