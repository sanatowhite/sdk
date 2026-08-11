# :core-net

## 这是什么 / 不是什么

网络层的通用地基:`AppError` 错误分类 + `safeApiCall` 包装器 + `RetryInterceptor` + `NetworkMonitor` + 组装 OkHttp/Retrofit 的 `HttpClientFactory`。

**不是**:不含任何具体后端的 API 定义(比如远程配置、更新检查用的 service 接口都是消费方自己定义,本模块只提供组装原料)。不含认证/token 刷新逻辑(设计裁决:模板不做认证)。不依赖 `core-data`/`core-ui`(网络层不应该知道 UI 或本地存储长什么样)。

## 一行接入

```kotlin
dependencies {
    implementation("com.github.sanatowhite.sdk:core-net:1.0.0")
}
```

传递依赖:`api()` 已经把 OkHttp 5.4.0(通过 BOM 对齐,覆盖 Retrofit 3.0.0 传递声明的 4.12——4/5 二进制兼容,见 `build.gradle.kts` 里的 `constraints` 注释)、Retrofit 3.0.0、`kotlinx-serialization-json` 传给消费方,不用自己再声明这些坐标就能直接调用 `HttpClientFactory`/`safeApiCall`。

`AndroidManifest.xml` 会带 `INTERNET` + `ACCESS_NETWORK_STATE` 两个权限——消费方不需要在自己的 manifest 里重复声明,会被合并。

想把网络耗时接进 `:core-telemetry` 的 `Telemetry`?加 `:net-telemetry-hilt`,不用自己写桥接代码。

## AI 接入指南(可直接执行)

**要不要用这个模块**:消费方需要发起 HTTP 请求(Retrofit/OkHttp)时用。只需要 `AppResult`/`UiState` 这类通用类型、不发网络请求的话不需要这个模块。

**接入步骤**:
1. 加坐标:
   ```kotlin
   dependencies {
       implementation("com.github.sanatowhite.sdk:core-net:1.0.0")
   }
   ```
2. 组装客户端(在自己的 Hilt Module 或手动组装代码里):
   ```kotlin
   val client = HttpClientFactory.okHttpClient(enableLogging = BuildConfig.DEBUG)
   val retrofit = HttpClientFactory.retrofit(baseUrl = "https://api.example.com/", client = client)
   val service = retrofit.create(MyApiService::class.java)   // MyApiService 是消费方自己定义的 Retrofit 接口
   ```
3. 调用处用 `safeApiCall` 包一层,不要直接 try/catch Retrofit 抛出的异常:
   ```kotlin
   suspend fun fetchThing(): AppResult<Thing> = safeApiCall { service.getThing() }
   ```
4. (可选)需要网络耗时进遥测系统时,额外加 `:net-telemetry-hilt`,把 `HttpClientFactory.okHttpClient(metricsSink = ...)` 的 `metricsSink` 参数接上注入进来的 `NetworkMetricsSink`。

**验证**:`./gradlew :<your-module>:compileDebugKotlin` 编译通过;运行时验证——调用一次 `fetchThing()`,确认返回 `AppResult.Success`(网络正常)或 `AppResult.Failure`(网络异常时不抛异常,不崩溃)。

**不要做的事**:不要在 `service.getThing()` 外面自己再套一层 try/catch——`safeApiCall` 已经把 Retrofit 的裸异常分类进 `AppError`;不要给这个模块加认证/token 刷新逻辑,那是设计上明确排除的能力(见 `TEMPLATE.md`)。

## 公开 API

- `AppError` — sealed class:`Http(code, body)` / `Timeout` / `NoConnectivity` / `Ssl` / `Serialization` / `Unknown`,都是 `Throwable` 的子类,能直接装进 `AppResult.Failure`。
- `suspend fun <T> safeApiCall(block: suspend () -> T): AppResult<T>` — 把 Retrofit 的裸异常转换成 `AppResult`,内部按 `HttpException`/`SocketTimeoutException`/`UnknownHostException`/`SSLException`/`SerializationException`/`IOException` 依次分类,`CancellationException` 原样重新抛出(绝不吞取消)。
- `RetryInterceptor(maxRetries, baseDelayMillis, maxDelayMillis)` — OkHttp `Interceptor`:5xx/429 指数退避重试,429 优先读 `Retry-After` 头;**只重试幂等方法**(GET/HEAD/OPTIONS/PUT/DELETE),POST/PATCH 不重试。
- `NetworkMonitor(context).isOnline(): Flow<Boolean>` — 基于 `registerDefaultNetworkCallback`(API 24 起可用,等于 minSdk,无需版本分支)。
- `HttpClientFactory.okHttpClient(enableLogging, additionalInterceptors, metricsSink)` / `.retrofit(baseUrl, client, json)` — 组装入口,超时用 `kotlin.time.Duration`(OkHttp 5 起 Builder 直接接受)。
- `NetworkMetricsSink` — `fun interface`,网络耗时上报的落点,**归本模块所有**(不是 `:core-telemetry`),这样 `core-net` 不需要依赖 `core-telemetry`;`:net-telemetry-hilt` 提供桥接实现连接两边(见该模块 README)。
- `TelemetryEventListenerFactory(sink)` — OkHttp `EventListener.Factory`,per-call 无状态共享;URL 走路由模板化(优先读 Retrofit `Invocation` tag,拿不到才退回 `encodedPath`),避免指标基数爆炸。

## 已知限制 / 不要做的事

- **不要**给 `RetryInterceptor` 加对 POST/PATCH 的重试——非幂等请求重试有制造重复副作用的风险(比如重复扣款/重复创建),这条限制是有意为之。
- **不要**在这里定义任何具体的 Retrofit service 接口或 DTO——那是消费方的职责,本模块保持"网络地基"这一层定位,不然每加一个 app 特定的接口就会污染这个可独立复用的模块。
- release 构建下引入自己的 `@Serializable` 类型时,记得对着 `consumer-rules.pro` 的 keep 规则模式加一条——kotlinx.serialization 的生成代码在 R8 minify 后如果没有 keep 规则会直接崩,而且**只在 release 构建暴露**,debug 构建完全测不出来。
- `NetworkMonitor` 的 `Context` 用 `javax.inject` 构造函数注入,不依赖 Hilt——真正把 `@ApplicationContext Context` 绑定进去是消费方自己 Hilt Module 的职责(本模块没有对应的 `-hilt` 伴生模块,因为目前没有需要装配的绑定)。

## WebSocket 长连接(`io.sanato.appkit.core.net.ws` 子包)

**这是什么**:一个带自动重连/指数退避/背压控制/token 刷新的托管长连接。**不是**认证——`:core-net` 不知道
Firebase/OAuth/JWT 的存在,握手凭证由 `WebSocketTokenProvider` 这个接口表达,真正的实现由同时依赖
`:core-net` 和认证能力的 Tier-3 桥模块(如 `:auth-net-hilt`)提供。

**⚠️ 绝不要把 `HttpClientFactory.okHttpClient()` 的返回值直接喂给 WebSocket。** 它的
`callTimeout(30.seconds)` 会在 30 秒后无条件掐断整条长连接会话(WebSocket 会话本身就是一个 Call),
`readTimeout(15.seconds)` 会让空闲连接抛 `SocketTimeoutException`。正确用法是先派生一个专用 client:

```kotlin
val base = HttpClientFactory.okHttpClient(enableLogging = BuildConfig.DEBUG)
val wsClient = WebSocketFactory.webSocketOkHttpClient(base) // 覆盖 callTimeout/readTimeout/pingInterval
val connection = WebSocketFactory.create(
    client = wsClient,
    config = WebSocketConfig(url = "wss://example.com/socket"),
    scope = applicationScope, // 消费方持有的 app 级 CoroutineScope,不要传 viewModelScope
    tokenProvider = null,     // 需要认证的连接由桥模块提供
    onlineSignal = NetworkMonitor(context).isOnline(),
    metricsSink = null,       // 需要遥测就加 :net-telemetry-hilt
)
connection.connect()
```

**公开 API**:

- `WebSocketFactory.webSocketOkHttpClient(base, pingInterval)` / `.create(client, config, scope, tokenProvider, onlineSignal, metricsSink)` — 组装入口。
- `WebSocketConnection` — `state: StateFlow<WebSocketState>`、`messages: SharedFlow<WebSocketMessage>`(热流,`replay=0`,零收集者时消息丢弃是正确语义)、`connect()`/`reconnect()`/`close()`/`send()`。
- `WebSocketState` — `Idle`/`Connecting`/`Connected`/`Reconnecting(attempt, delayMillis, cause)`/`Closed`/`Failed`。
- `WebSocketError` — 独立 sealed 层级(继承 `IOException`,不并入 `AppError`——加子类对 `AppError` 的穷尽 `when` 是源码破坏,`apiCheck` 看不见这类破坏),配一个单向 `WebSocketError.toAppError()` 桥。
- `WebSocketConfig` / `WebSocketRetryPolicy` / `WebSocketOverflowPolicy` / `TokenPlacement` / `AppHeartbeat` — 逐项可配置退避、背压、token 放置方式、应用层心跳。
- `WebSocketTokenProvider` — `fun interface`,握手凭证来源,**归本模块所有**(同 `NetworkMetricsSink` 的套路),真正的认证实现在桥模块里。
- `WebSocketMetricsSink` — 并列于 `NetworkMetricsSink` 的独立遥测接口(长连接的生命周期/频率模型和"一次请求一次回调"完全不同,不能共用)。

**不要做的事**:

- **不要**手写 `client.newWebSocket(request, listener)`——那样拿不到自动重连、退避、token 刷新、背压这些能力,等于重新发明这个子包。
- **不要**在没有收集 `messages` 的情况下发大量消息并指望它们被缓存——`SharedFlow(replay=0)` 是热流,零收集者时消息按设计丢弃。
- **不要**给 `WebSocketOverflowPolicy.SUSPEND_READER` 配一个可能长时间不消费的收集者——它会阻塞 OkHttp 的 reader 线程,连 pong/close 帧都处理不了。
