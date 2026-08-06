# checks/consumer-smoke

## 这是什么

一个**完全独立的 Gradle build**(不被根 `settings.gradle.kts` include),扮演"从 JitPack/mavenLocal 拉坐标的真实第三方消费方"。它是这次改造(把 `core-*`/`feature-*` 从"fork 复制代码"改成"发布版本化 AAR")唯一能验证"发布出去的坐标真的可用"的机制。

## 为什么必须独立

如果这个工程是根 build 的一个子项目,Gradle 的 project substitution 会把 `com.github.sanatowhite.sdk:core-net` 这种坐标依赖悄悄换回同一个 build 里的 `project(":core-net")`——这样任何 `api()`/`implementation` 判定错误、任何发布产物(POM、Gradle Module Metadata)的缺陷都会被 project() 依赖的透明性掩盖过去,编译永远不会失败,验证就是假的。`:app` 用的正是 `project(...)` 依赖(理由见 `docs/adr/`),测不出这类问题——这正是这个独立工程存在的唯一理由。

## 验证的东西

1. **`api()`/`implementation` 判定正确性**——`NetSmoke.kt`/`DispatcherSmoke.kt`/`DataSmoke.kt` 逐个引用 `HttpClientFactory`/`safeApiCall`/dispatcher qualifier/`UserSettingsRepository` 等公开入口的完整类型签名,任何一条判定错误(该 `api` 却写成 `implementation`)都会导致这些文件里出现 unresolved reference,编译直接失败。
2. **Hilt 跨模块聚合在真实 AAR 消费下依然成立**——`SmokeApplication`/`MainActivity` 把 5 个 `-hilt` 伴生模块 + `telemetry-firebase` + 3 个 Hilt-based feature 模块全部接在一起,`NetSmoke`/`DataSmoke`/`DispatcherSmoke` 作为 `MainActivity` 的 `@Inject` 字段被真正注入(不是挂着不用的死代码),逼 `hiltJavaCompile` 走一遍完整的绑定解析。Phase 0 的 spike 已经证明"库模块必须自己跑 `hilt-compiler`",但那次验证走的是 `project()` 依赖;这里验证的是发布流程本身(AAR 打包、POM、Gradle Module Metadata 的 api/implementation 变体划分)有没有不小心丢东西。
3. **`sdk-bom` 的版本约束真的生效**——`app/build.gradle.kts` 里每个模块坐标都不写版本号,全靠 `implementation(platform("...:sdk-bom:$smokeVersion"))` 解析。
4. **导航层的跨模块可空回调解耦**——`MainActivity` 把 `:feature-settings`/`:feature-feedback`/`:feature-licenses`/`:feature-update` 接进同一个 `NavHost`,验证它们之间不需要互相依赖也能协作。

## 怎么跑

### 本地模式(默认,用 mavenLocal())

```bash
# 1. 在仓库根目录发布到本地 m2
cd ../..
./gradlew publishSdkToMavenLocal -Pversion=0.0.0-smoke

# 2. 回到这个独立工程,用同一个版本号编译
cd checks/consumer-smoke
./gradlew :app:assembleDebug -PsmokeVersion=0.0.0-smoke
```

不传 `-PsmokeVersion` 时,默认读仓库根目录 `gradle/version.properties` 的 `sdkVersion`——本地跑 `publishSdkToMavenLocal`(不传 `-Pversion`)时用的就是这个值,两边自动对上。

### CI 远程模式(`-PsmokeRemote=true`,用真实 JitPack 坐标)

```bash
./gradlew :app:assembleDebug -PsmokeRemote=true -PsmokeVersion=<真实发布过的 tag>
```

验证 tag 发布之后,坐标真的能被外部消费方从 JitPack 解析到——这是本地模式测不出来的(mavenLocal 只证明"构建产物长这样",不证明"JitPack 真的把它们发出去了")。

## 已知限制

- 只做编译期验证(`assembleDebug` 通过即可),不追求运行期正确——比如 `licensesGraph` 传的 `librariesRawRes = 0` 是占位值,真实渲染需要消费方自己 apply AboutLibraries 插件生成资源,不在这个工程的验证范围内。
- 不 apply `google-services`/`firebase-crashlytics` 插件,`telemetry-firebase` 的 Hilt 聚合仍然会被编译验证到,只是运行时 `FirebaseApp` 不会真正初始化——同样超出这个工程"编译期坐标验证"的范围。
