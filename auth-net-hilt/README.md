# :auth-net-hilt

## 这是什么 / 不是什么

`:core-auth` 与 `:core-net` 之间的粘合桥——本仓库第二座跨 Tier-1 桥(第一座是 `:net-telemetry-hilt`)。提供:HTTP 侧的 `AuthInterceptor`/`AuthTokenAuthenticator`/`@Authenticated OkHttpClient`,以及 WebSocket 侧的 `AuthWebSocketTokenProvider`(`core-net` 的 `ws.WebSocketTokenProvider` 接口的认证实现)。

**不是**:不含任何登录后端的实现(那是 `:auth-firebase` 或其他 `AuthRepository` 实现)。不含 HTTP/WebSocket 的组装本体(那些在 `:core-net`)。

**为什么 HTTP 和 WebSocket 认证放进同一个模块**:两者服务的是完全相同的一对 Tier-1 模块(`:core-auth` × `:core-net`),没有理由为同一对模块注册两条 `allowedProjectDeps`、发布两个坐标、写两份 README。这不是"过度收敛",是仓库既有的"一对 Tier-1 模块只配一座桥"原则的直接推论。

## 一行接入

只有**同时**用了 `:core-auth`(及某个实现,如 `:auth-firebase`)和 `:core-net` 的消费方才需要它:

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-auth:1.0.0")
    implementation("com.github.sanatowhite.sdk:auth-firebase:1.0.0")
    implementation("com.github.sanatowhite.sdk:core-net:1.0.0")
    implementation("com.github.sanatowhite.sdk:auth-net-hilt:1.0.0")
}
```

## AI 接入指南(可直接执行)

**要不要用这个模块**:只有**同时**依赖 `:core-auth` 和 `:core-net` 才需要;任一边缺失都不需要加。

**接入步骤**:
1. 加坐标(见上方)。
2. 需要带认证的 HTTP 客户端:注入 `@Authenticated OkHttpClient`(而不是自己组装),它已经带上 `AuthInterceptor`(附加 token)+ `AuthTokenAuthenticator`(401 自愈,强制刷新一次)+ 可选的 `NetworkMetricsSink`。
3. 需要带认证的 WebSocket 连接:注入 `WebSocketTokenProvider`(本模块的 `@Provides` 已经绑定 `AuthWebSocketTokenProvider`),传给 `WebSocketFactory.create(tokenProvider = ...)`。
4. 想同时保留一个**不带认证**的客户端(比如打公开的状态接口)?直接注入不带 `@Authenticated` 限定符的普通 `OkHttpClient`——`:core-net`/`:net-telemetry-hilt` 提供的那条绑定完全不受这个模块影响。

**验证**:`./gradlew :app:hiltJavaCompileDebug` 编译通过;运行时发一个请求到会返回 401 的接口,确认 401 后自动重试一次(用新 token)而不是无限重试。

**不要做的事**:见"已知限制"。

## 公开 API

- `AuthInterceptor(tokenProvider)` — `okhttp3.Interceptor`,非阻塞,从 `tokenProvider.cachedIdToken()` 读缓存附加 `Authorization: Bearer <token>`;已有 `Authorization` header 时不覆盖。
- `AuthTokenAuthenticator(tokenProvider)` — `okhttp3.Authenticator`,401 时强制刷新一次并重试;`response.priorResponse != null`(已经重试过一次)或拿不到新 token 或新 token 和旧的一样时放弃,不会死循环。
- `@Authenticated` — Hilt qualifier,标记带认证的 `OkHttpClient`。
- `AuthWebSocketTokenProvider(tokenProvider)` — `:core-net` 的 `ws.WebSocketTokenProvider` 接口的实现,直接转发到 `AuthTokenProvider.currentIdToken(forceRefresh)`。
- `AuthNetModule` — `@Module`,提供 `AuthInterceptor`/`AuthTokenAuthenticator`/`WebSocketTokenProvider`/`@Authenticated OkHttpClient` 四条绑定。
- `AuthNetBindsModule` — `@Module`,`@BindsOptionalOf NetworkMetricsSink`(只有引了 `:net-telemetry-hilt` 才有真实绑定)。

## 已知限制 / 不要做的事

- **不要**指望这个模块自动在登出时关闭你自己创建的 `WebSocketConnection`——本模块不持有任何具体的连接实例(URL/消息类型都是业务方决定的),你需要自己注册一个 `@IntoSet SessionScopedStore` 在其中调用你那个连接的 `close()`。
- **不要**给 `HttpClientFactory.okHttpClient()` 加 `authenticator` 参数来"更方便地"接入认证——那个签名被 `apiCheck` 冻结,且哪怕加的是带默认值的重载,也会让所有现有调用点的具名/位置参数解析出现歧义,是源码层面的破坏性变更(`apiCheck` 的 javap 快照看不出来,但真实会破坏调用方编译)。正确做法就是本模块这样:`HttpClientFactory.okHttpClient(...).newBuilder().authenticator(...).build()`,`:core-net` 一个字节不用改。
- `AuthInterceptor` 绝不 `runBlocking`(每个请求都会被拖慢);`AuthTokenAuthenticator` 里的一次 `runBlocking` 是刻意的,因为 `okhttp3.Authenticator.authenticate()` 的官方契约本就允许阻塞,且从不在调用方原始线程上执行。
