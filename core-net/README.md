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
